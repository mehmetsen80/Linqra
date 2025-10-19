package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqLlmModel;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.LinqLlmModelService;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinqLlmModelServiceImpl implements LinqLlmModelService {

    @NonNull
    private final LinqLlmModelRepository linqLlmModelRepository;

    @NonNull
    private final WebClient.Builder webClientBuilder;

    @Override
    public Mono<LinqLlmModel> saveLinqLlmModel(LinqLlmModel linqLlmModel) {
        // Validate required fields
        if (linqLlmModel.getTarget() == null || linqLlmModel.getTarget().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel target is required"));
        }
        if (linqLlmModel.getTeamId() == null || linqLlmModel.getTeamId().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel team ID is required"));
        }
        if (linqLlmModel.getEndpoint() == null || linqLlmModel.getEndpoint().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel endpoint is required"));
        }
        if (linqLlmModel.getMethod() == null || linqLlmModel.getMethod().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel method is required"));
        }

        log.info("Saving LinqLlmModel with target: {}, modelType: {} for team: {}", 
            linqLlmModel.getTarget(), linqLlmModel.getModelType(), linqLlmModel.getTeamId());
        
        return linqLlmModelRepository.findByTargetAndModelTypeAndTeamId(
                linqLlmModel.getTarget(), linqLlmModel.getModelType(), linqLlmModel.getTeamId())
            .<LinqLlmModel>flatMap(existingTool -> {
                // Update existing linq llm model
                existingTool.setEndpoint(linqLlmModel.getEndpoint());
                existingTool.setMethod(linqLlmModel.getMethod());
                existingTool.setHeaders(linqLlmModel.getHeaders());
                existingTool.setAuthType(linqLlmModel.getAuthType());
                existingTool.setApiKey(linqLlmModel.getApiKey());
                existingTool.setSupportedIntents(linqLlmModel.getSupportedIntents());
                
                log.info("Updating existing LinqLlmModel with ID: {}", existingTool.getId());
                return linqLlmModelRepository.save(existingTool)
                    .doOnSuccess(saved -> log.info("Updated LinqLlmModel with ID: {}", saved.getId()))
                    .doOnError(error -> log.error("Failed to update LinqLlmModel: {}", error.getMessage()));
            })
            .switchIfEmpty(Mono.<LinqLlmModel>defer(() -> {
                // Create new linq llm model
                log.info("Creating new LinqLlmModel for target: {}, modelType: {} and team: {}", 
                    linqLlmModel.getTarget(), linqLlmModel.getModelType(), linqLlmModel.getTeamId());
                return linqLlmModelRepository.save(linqLlmModel)
                    .doOnSuccess(saved -> log.info("Created new LinqLlmModel with ID: {}", saved.getId()))
                    .doOnError(error -> log.error("Failed to create LinqLlmModel: {}", error.getMessage()));
            }));
    }

    @Override
    public Flux<LinqLlmModel> findByTeamId(String teamId) {
        log.info("Finding LLM model configurations for team: {}", teamId);
        return linqLlmModelRepository.findByTeamId(teamId)
                .doOnNext(llmModel -> log.info("Found LLM model configuration for team {}: target={}, modelType={}", 
                    teamId, llmModel.getTarget(), llmModel.getModelType()))
                .doOnComplete(() -> log.info("Completed fetching {} LLM model configurations for team: {}", teamId))
                .doOnError(error -> log.error("Error finding LLM model configurations for team {}: {}", teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqLlmModel> findByTargetAndTeam(String target, String teamId) {
        log.info("Finding LLM model configuration for target: {} and team: {}", target, teamId);
        return linqLlmModelRepository.findByTargetAndTeamId(target, teamId)
                .doOnSuccess(llmModel -> {
                    if (llmModel != null) {
                        log.info("✅ Found LLM model configuration: target={}, modelType={}, endpoint={}", 
                            target, llmModel.getModelType(), llmModel.getEndpoint());
                    } else {
                        log.warn("⚠️ No LLM model configuration found for target: {} and team: {}", target, teamId);
                    }
                })
                .doOnError(error -> log.error("❌ Error finding LLM model configuration for target: {} and team: {}: {}", 
                    target, teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqLlmModel> findByTargetAndModelTypeAndTeamId(String target, String modelType, String teamId) {
        log.info("Finding LLM model configuration for target: {}, modelType: {} and team: {}", target, modelType, teamId);
        return linqLlmModelRepository.findByTargetAndModelTypeAndTeamId(target, modelType, teamId)
                .doOnSuccess(llmModel -> {
                    if (llmModel != null) {
                        log.info("✅ Found LLM model configuration: target={}, modelType={}, endpoint={}", 
                            target, modelType, llmModel.getEndpoint());
                    } else {
                        log.warn("⚠️ No LLM model configuration found for target: {}, modelType: {} and team: {}", 
                            target, modelType, teamId);
                    }
                })
                .doOnError(error -> log.error("❌ Error finding LLM model configuration for target: {}, modelType: {} and team: {}: {}", 
                    target, modelType, teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqResponse> executeLlmRequest(LinqRequest request, LinqLlmModel llmModel) {
        String intent = request.getQuery().getIntent();
        if (!llmModel.getSupportedIntents().contains(intent)) {
            return Mono.error(new IllegalArgumentException("Intent '" + intent + "' not supported by " + llmModel.getTarget() + " " + llmModel.getModelType()));
        }

        AtomicReference<String> url = new AtomicReference<>(buildLlmUrl(llmModel, request));
        String method = llmModel.getMethod();
        Object payload = buildLlmPayload(request, llmModel);

        log.info("🚀 Executing LLM request - target: {}, modelType: {}, URL: {}", 
            llmModel.getTarget(), llmModel.getModelType(), url.get());

        return Mono.just(llmModel.getApiKey())
                .flatMap(apiKey -> {
                    Map<String, String> headers = new HashMap<>(llmModel.getHeaders());
                    String authType = llmModel.getAuthType() != null ? llmModel.getAuthType() : "none";

                    switch (authType) {
                        case "bearer":
                            headers.put("Authorization", "Bearer " + apiKey);
                            log.info("Using Bearer token authentication for {}", llmModel.getTarget());
                            break;
                        case "api_key_query":
                            url.set(url.get() + (url.get().contains("?") ? "&" : "?") + "key=" + apiKey);
                            log.info("Using API key query parameter authentication for {}", llmModel.getTarget());
                            break;
                        case "none":
                        default:
                            log.info("No authentication required for {}", llmModel.getTarget());
                            break;
                    }

                    // Add cache-busting headers
                    headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
                    headers.put("Pragma", "no-cache");
                    headers.put("Expires", "0");

                    return invokeLlmService(method, url.get(), payload, headers);
                })
                .map(result -> {
                    LinqResponse response = new LinqResponse();
                    response.setResult(result);
                    LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                    metadata.setSource(llmModel.getTarget());
                    metadata.setStatus("success");
                    metadata.setTeamId(llmModel.getTeamId());
                    metadata.setCacheHit(false);
                    response.setMetadata(metadata);
                    log.info("✅ LLM request completed successfully for {}/{}", llmModel.getTarget(), llmModel.getModelType());
                    return response;
                });
    }

    private String buildLlmUrl(LinqLlmModel llmModel, LinqRequest request) {
        String endpoint = llmModel.getEndpoint();
        LinqRequest.Query.LlmConfig llmConfig = request.getQuery().getLlmConfig();
        if (llmConfig != null && llmConfig.getModel() != null) {
            endpoint = endpoint.replace("{model}", llmConfig.getModel());
            log.debug("Replaced {model} placeholder with: {}", llmConfig.getModel());
        }
        return endpoint;
    }

    private Object buildLlmPayload(LinqRequest request, LinqLlmModel llmModel) {
        Map<String, Object> payload = new HashMap<>();
        LinqRequest.Query.LlmConfig llmConfig = request.getQuery().getLlmConfig();
        String target = llmModel.getTarget();

        switch (target) {
            case "openai":
            case "mistral":
                payload.put("model", llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "default");
                payload.put("messages", request.getQuery().getPayload());
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    payload.putAll(llmConfig.getSettings());
                }
                break;
            case "huggingface":
                payload.put("inputs", request.getQuery().getParams().getOrDefault("prompt", ""));
                payload.put("model", llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "sentence-transformers/all-MiniLM-L6-v2");
                payload.put("parameters", llmConfig != null ? llmConfig.getSettings() : new HashMap<>());
                break;
            case "gemini":
                payload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", request.getQuery().getParams().getOrDefault("prompt", ""))))));
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    payload.put("generationConfig", llmConfig.getSettings());
                }
                break;
            case "openai-embed":
                Object textParam = request.getQuery().getParams().getOrDefault("text", "");
                String text = textParam != null ? textParam.toString() : "";
                String model = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "text-embedding-ada-002";
                payload.put("input", text);
                payload.put("model", model);
                log.info("Building OpenAI embedding payload - text: {}, model: {}", text, model);
                break;
            case "gemini-embed":
                Object geminiTextParam = request.getQuery().getParams().getOrDefault("text", "");
                String geminiText = geminiTextParam != null ? geminiTextParam.toString() : "";
                String geminiModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "text-embedding-004";
                // Gemini embedContent API format
                payload.put("content", Map.of("parts", List.of(Map.of("text", geminiText))));
                log.info("Building Gemini embedding payload - text: {}, model: {}", geminiText, geminiModel);
                break;
            default:
                payload.putAll(request.getQuery().getParams());
                break;
        }

        log.info("📦 Built {} payload for {}: {}", target, llmModel.getModelType(), payload);
        return payload;
    }

    private Mono<Object> invokeLlmService(String method, String url, Object payload, Map<String, String> headers) {
        WebClient webClient = webClientBuilder.build();
        WebClient.RequestBodySpec requestSpec = webClient.method(HttpMethod.valueOf(method))
                .uri(url)
                .headers(httpHeaders -> headers.forEach(httpHeaders::add));

        log.info("🌐 Making {} request to LLM service: {}", method, url);
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            log.debug("Request payload: {}", payload);
            requestSpec.bodyValue(payload);
        }

        return requestSpec
                .retrieve()
                .bodyToMono(Object.class)
                .doOnNext(response -> log.info("✅ Received response from LLM service: {}", url))
                .doOnError(error -> log.error("❌ Error calling LLM service {}: {}", url, error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Error details for LLM service {}: {}", url, error.getMessage());
                    return Mono.just(Map.of("error", error.getMessage()));
                });
    }
}

