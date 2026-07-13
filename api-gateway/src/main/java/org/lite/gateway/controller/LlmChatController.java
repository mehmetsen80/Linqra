package org.lite.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.ExternalUsageLoggerService;
import org.lite.gateway.service.ExternalUserCreditService;
import org.lite.gateway.service.LlmModelService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.lite.gateway.service.LinqLlmModelService;
import org.springframework.beans.factory.annotation.Value;
import org.lite.gateway.entity.LinqLlmModel;

@RestController
@RequestMapping("/api/llm/chat")
@Slf4j
public class LlmChatController {

    private final LlmModelService llmModelService;
    private final LinqLlmModelService linqLlmModelService;
    private final ExternalUsageLoggerService externalUsageLoggerService;
    private final ExternalUserCreditService externalUserCreditService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public LlmChatController(LlmModelService llmModelService,
            LinqLlmModelService linqLlmModelService,
            ExternalUsageLoggerService externalUsageLoggerService,
            ExternalUserCreditService externalUserCreditService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.llmModelService = llmModelService;
        this.linqLlmModelService = linqLlmModelService;
        this.externalUsageLoggerService = externalUsageLoggerService;
        this.externalUserCreditService = externalUserCreditService;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-LLM-Model", required = false) String modelId,
            @RequestHeader(value = "X-External-User-Id", required = false) String externalUserId,
            @RequestHeader(value = "X-Is-BYOK", required = false, defaultValue = "false") boolean isByok,
            @RequestHeader(value = "X-Is-Managed", required = false, defaultValue = "false") boolean isManaged,
            @RequestHeader(value = "X-Machine-Id", required = false) String machineId,
            @RequestHeader(value = "X-Service-Name", required = false) String serviceName,
            @RequestHeader(value = "X-Team-ID", required = false) String managedTeamId,
            @RequestBody Map<String, Object> request) {

        final String effectiveModelId;
        if (modelId == null || modelId.trim().isEmpty()) {
            if (isManaged) {
                effectiveModelId = "gpt-4o-mini";
            } else {
                return Flux.just("data: {\"error\": \"Missing X-LLM-Model header\"}\n\n");
            }
        } else {
            effectiveModelId = modelId;
        }

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Flux.just("data: {\"error\": \"Missing or invalid Authorization header\"}\n\n");
        }

        String resolvedUserId = externalUserId;
        if (resolvedUserId == null) {
            try {
                String[] parts = authorization.substring(7).split("\\.");
                if (parts.length >= 2) {
                    String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    JsonNode payload = objectMapper.readTree(payloadJson);
                    if (payload.has("sub")) {
                        resolvedUserId = payload.get("sub").asText();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract sub from JWT token", e);
            }
        }
        final String finalUserId = resolvedUserId;

        log.info("Received chat stream request. Model: {}, BYOK: {}, Managed: {}, User: {}", effectiveModelId, isByok,
                isManaged, finalUserId);

        // Inject stream_options for usage (OpenAI standard)
        @SuppressWarnings("unchecked")
        Map<String, Object> streamOptions = (Map<String, Object>) request.getOrDefault("stream_options",
                new HashMap<>());
        streamOptions.put("include_usage", true);
        request.put("stream_options", streamOptions);

        String safeServiceName = serviceName != null ? serviceName : "unknown";

        Mono<Boolean> creditCheckMono = isManaged && finalUserId != null
                ? externalUserCreditService.hasSufficientCredits(finalUserId, safeServiceName, machineId)
                : Mono.just(true);

        Mono<String> apiKeyMono;
        if (isManaged) {
            if (managedTeamId == null || managedTeamId.isBlank()) {
                return Flux.just("data: {\"error\": \"Managed team ID not configured on server.\"}\n\n");
            }
            apiKeyMono = llmModelService.getModelByName(effectiveModelId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Model not found: " + effectiveModelId)))
                    .flatMap(model -> linqLlmModelService.deriveModelCategory(effectiveModelId, model.getProvider(),
                            managedTeamId))
                    .flatMap(category -> linqLlmModelService.findByModelCategoryAndModelNameAndTeamId(category,
                            effectiveModelId, managedTeamId))
                    .map(LinqLlmModel::getApiKey)
                    .switchIfEmpty(
                            Mono.error(new IllegalArgumentException("Managed LLM Configuration not found for team")));
        } else {
            apiKeyMono = Mono.just(authorization.substring(7));
        }

        return creditCheckMono.flatMapMany(hasCredits -> {
            if (!hasCredits) {
                return Flux.just(
                        "data: {\"error\": \"Insufficient AI credits. Please upgrade your account to continue.\"}\n\n");
            }

            return apiKeyMono.flatMapMany(apiKey -> llmModelService.getModelByName(effectiveModelId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Model not found: " + effectiveModelId)))
                    .flatMapMany(model -> {
                        AtomicInteger promptTokens = new AtomicInteger(0);
                        AtomicInteger completionTokens = new AtomicInteger(0);

                        return webClient.post()
                                .uri(model.getEndpoint())
                                .headers(h -> {
                                    if ("anthropic".equalsIgnoreCase(model.getProvider())) {
                                        h.set("x-api-key", apiKey);
                                        h.set("anthropic-version", "2023-06-01");
                                    } else {
                                        h.set("Authorization", "Bearer " + apiKey);
                                    }
                                    h.set("Content-Type", "application/json");
                                    h.set("Accept", "text/event-stream");
                                })
                                .bodyValue(request)
                                .retrieve()
                                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                                .map(sse -> sse.data() != null ? sse.data() : "")
                                .filter(payload -> !payload.isEmpty())
                                .doOnNext(payload -> {
                                    try {
                                        if (!payload.equals("[DONE]")) {
                                            JsonNode root = objectMapper.readTree(payload);

                                            // OpenAI style usage
                                            if (root.has("usage") && !root.get("usage").isNull()) {
                                                JsonNode usage = root.get("usage");
                                                if (usage.has("prompt_tokens")) {
                                                    promptTokens.set(usage.get("prompt_tokens").asInt());
                                                }
                                                if (usage.has("completion_tokens")) {
                                                    completionTokens.set(usage.get("completion_tokens").asInt());
                                                }
                                            }

                                            // Anthropic style usage
                                            if (root.has("type")) {
                                                String type = root.get("type").asText();
                                                if ("message_start".equals(type) && root.has("message")
                                                        && root.get("message").has("usage")) {
                                                    JsonNode usage = root.get("message").get("usage");
                                                    if (usage.has("input_tokens")) {
                                                        promptTokens.set(usage.get("input_tokens").asInt());
                                                    }
                                                } else if ("message_delta".equals(type) && root.has("usage")) {
                                                    JsonNode usage = root.get("usage");
                                                    if (usage.has("output_tokens")) {
                                                        completionTokens.addAndGet(usage.get("output_tokens").asInt());
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.debug("Failed to parse chunk for usage: {}", e.getMessage());
                                    }
                                })
                                .doFinally(signalType -> {
                                    if (finalUserId != null && (promptTokens.get() > 0 || completionTokens.get() > 0)) {
                                        externalUsageLoggerService.logUsage(
                                                serviceName != null ? serviceName : "unknown",
                                                finalUserId,
                                                effectiveModelId,
                                                isByok,
                                                promptTokens.get(),
                                                completionTokens.get()).subscribe();

                                        if (isManaged) {
                                            externalUserCreditService.consumeCredit(finalUserId, safeServiceName, 1)
                                                    .subscribe();
                                        }
                                    }
                                });
                    }));
        });
    }
}
