package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqLlmModel;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.repository.LlmModelRepository;
import org.lite.gateway.service.LinqLlmModelService;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.StringUtils;
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
    private final LlmModelRepository llmModelRepository;

    @NonNull
    private final WebClient.Builder webClientBuilder;

    private Mono<LinqLlmModel> enrichWithModelMetadata(LinqLlmModel linqLlmModel) {
        if (linqLlmModel == null) {
            return Mono.empty();
        }
        if (!StringUtils.hasText(linqLlmModel.getModelName())) {
            return Mono.just(linqLlmModel);
        }

        return llmModelRepository.findByModelName(linqLlmModel.getModelName())
                .map(llmModel -> {
                    linqLlmModel.setEmbeddingDimension(llmModel.getDimensions());
                    linqLlmModel.setInputPricePer1M(llmModel.getInputPricePer1M());
                    linqLlmModel.setOutputPricePer1M(llmModel.getOutputPricePer1M());
                    linqLlmModel.setContextWindowTokens(llmModel.getContextWindowTokens());
                    return linqLlmModel;
                })
                .defaultIfEmpty(linqLlmModel);
    }

    @Override
    public Mono<LinqLlmModel> saveLinqLlmModel(LinqLlmModel linqLlmModel) {
        // Validate required fields
        if (linqLlmModel.getModelCategory() == null || linqLlmModel.getModelCategory().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel modelCategory is required"));
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

        log.info("Saving LinqLlmModel with modelCategory: {}, modelName: {} for team: {}", 
            linqLlmModel.getModelCategory(), linqLlmModel.getModelName(), linqLlmModel.getTeamId());
        
        return linqLlmModelRepository.findByModelCategoryAndModelNameAndTeamId(
                linqLlmModel.getModelCategory(), linqLlmModel.getModelName(), linqLlmModel.getTeamId())
            .<LinqLlmModel>flatMap(existingLlmModel -> {
                // Update existing linq llm model
                existingLlmModel.setEndpoint(linqLlmModel.getEndpoint());
                existingLlmModel.setProvider(linqLlmModel.getProvider());
                existingLlmModel.setMethod(linqLlmModel.getMethod());
                existingLlmModel.setHeaders(linqLlmModel.getHeaders());
                existingLlmModel.setAuthType(linqLlmModel.getAuthType());
                existingLlmModel.setApiKey(linqLlmModel.getApiKey());
                existingLlmModel.setSupportedIntents(linqLlmModel.getSupportedIntents());
                
                log.info("Updating existing LinqLlmModel with ID: {}", existingLlmModel.getId());
                return linqLlmModelRepository.save(existingLlmModel)
                    .doOnSuccess(saved -> log.info("Updated LinqLlmModel with ID: {}", saved.getId()))
                    .doOnError(error -> log.error("Failed to update LinqLlmModel: {}", error.getMessage()));
            })
            .switchIfEmpty(Mono.<LinqLlmModel>defer(() -> {
                // Create new linq llm model
                log.info("Creating new LinqLlmModel for modelCategory: {}, modelName: {} and team: {}", 
                    linqLlmModel.getModelCategory(), linqLlmModel.getModelName(), linqLlmModel.getTeamId());
                return linqLlmModelRepository.save(linqLlmModel)
                    .doOnSuccess(saved -> log.info("Created new LinqLlmModel with ID: {}", saved.getId()))
                    .doOnError(error -> log.error("Failed to create LinqLlmModel: {}", error.getMessage()));
            }));
    }

    @Override
    public Mono<Void> deleteLinqLlmModel(String id) {
        log.info("Deleting LinqLlmModel with ID: {}", id);
        return linqLlmModelRepository.findById(id)
            .flatMap(llmModel -> {
                log.info("Found LinqLlmModel to delete: modelCategory={}, modelName={}", 
                    llmModel.getModelCategory(), llmModel.getModelName());
                return linqLlmModelRepository.deleteById(id)
                    .doOnSuccess(v -> log.info("Deleted LinqLlmModel with ID: {}", id))
                    .doOnError(error -> log.error("Failed to delete LinqLlmModel with ID {}: {}", id, error.getMessage()));
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("LinqLlmModel not found with ID: {} (may have been already deleted)", id);
                return Mono.empty();
            }));
    }

    @Override
    public Mono<LinqLlmModel> findById(String id) {
        log.info("Finding LLM model configuration by ID: {}", id);
        return linqLlmModelRepository.findById(id)
                .flatMap(this::enrichWithModelMetadata)
                .doOnSuccess(llmModel -> {
                    if (llmModel != null) {
                        log.info("✅ Found LLM model configuration: id={}, modelCategory={}, modelName={}", 
                            id, llmModel.getModelCategory(), llmModel.getModelName());
                    } else {
                        log.warn("⚠️ No LLM model configuration found for ID: {}", id);
                    }
                })
                .doOnError(error -> log.error("❌ Error finding LLM model configuration for ID {}: {}", 
                    id, error.getMessage()));
    }

    @Override
    public Flux<LinqLlmModel> findByTeamId(String teamId) {
        log.info("Finding LLM model configurations for team: {}", teamId);
        return linqLlmModelRepository.findByTeamId(teamId)
                .flatMap(this::enrichWithModelMetadata)
                .doOnNext(llmModel -> log.info("Found LLM model configuration for team {}: modelCategory={}, modelName={}", 
                    teamId, llmModel.getModelCategory(), llmModel.getModelName()))
                .doOnComplete(() -> log.info("Completed fetching {} LLM model configurations for team: {}", teamId))
                .doOnError(error -> log.error("Error finding LLM model configurations for team {}: {}", teamId, error.getMessage()));
    }



    @Override
    public Flux<LinqLlmModel> findByModelCategoryAndTeamId(String modelCategory, String teamId) {
        log.info("Finding LLM model configurations for modelCategory: {} and team: {}", modelCategory, teamId);
        return linqLlmModelRepository.findByModelCategoryAndTeamId(modelCategory, teamId)
                .flatMap(this::enrichWithModelMetadata)
                .doOnNext(llmModel -> log.info("Found LLM model configuration: modelCategory={}, modelName={}", 
                modelCategory, llmModel.getModelName()))
                .doOnComplete(() -> log.info("Completed fetching LLM model configurations for modelCategory: {} and team: {}", modelCategory, teamId))
                .doOnError(error -> log.error("Error finding LLM model configurations for modelCategory: {} and team: {}: {}", 
                modelCategory, teamId, error.getMessage()));
    }

    @Override
    public Flux<LinqLlmModel> findByModelCategoriesAndTeamId(java.util.List<String> modelCategories, String teamId) {
        log.info("Finding LLM model configurations for modelCategories: {} and team: {}", modelCategories, teamId);
        return Flux.fromIterable(modelCategories)
                .flatMap(modelCategory -> linqLlmModelRepository.findByModelCategoryAndTeamId(modelCategory, teamId))
                .flatMap(this::enrichWithModelMetadata)
                .doOnNext(llmModel -> log.info("Found LLM model configuration: modelCategory={}, modelName={}", 
                    llmModel.getModelCategory(), llmModel.getModelName()))
                .doOnComplete(() -> log.info("Completed fetching LLM model configurations for modelCategories: {} and team: {}", modelCategories, teamId))
                .doOnError(error -> log.error("Error finding LLM model configurations for modelCategories: {} and team: {}: {}", 
                modelCategories, teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqLlmModel> findByModelCategoryAndModelNameAndTeamId(String modelCategory, String modelName, String teamId) {
        log.info("Finding LLM model configuration for modelCategory: {}, modelName: {} and team: {}", modelCategory, modelName, teamId);
        return linqLlmModelRepository.findByModelCategoryAndModelNameAndTeamId(modelCategory, modelName, teamId)
                .flatMap(this::enrichWithModelMetadata)
                .doOnSuccess(llmModel -> {
                    if (llmModel != null) {
                        log.info("✅ Found LLM model configuration: modelCategory={}, modelName={}, endpoint={}", 
                        modelCategory, modelName, llmModel.getEndpoint());
                    } else {
                        log.warn("⚠️ No LLM model configuration found for modelCategory: {}, modelName: {} and team: {}", 
                        modelCategory, modelName, teamId);
                    }
                })
                .doOnError(error -> log.error("❌ Error finding LLM model configuration for modelCategory: {}, modelName: {} and team: {}: {}", 
                modelCategory, modelName, teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqResponse> executeLlmRequest(LinqRequest request, LinqLlmModel llmModel) {
        String intent = request.getQuery().getIntent();
        if (!llmModel.getSupportedIntents().contains(intent)) {
            return Mono.error(new IllegalArgumentException("Intent '" + intent + "' not supported by " + llmModel.getModelCategory() + " " + llmModel.getModelName()));
        }

        AtomicReference<String> url = new AtomicReference<>(buildLlmUrl(llmModel, request));
        String method = llmModel.getMethod();
        Object payload = buildLlmPayload(request, llmModel);

        log.info("🚀 Executing LLM request - modelCategory: {}, modelName: {}, URL: {}", 
            llmModel.getModelCategory(), llmModel.getModelName(), url.get());

        return Mono.just(llmModel.getApiKey())
                .flatMap(apiKey -> {
                    Map<String, String> headers = new HashMap<>(llmModel.getHeaders());
                    String authType = llmModel.getAuthType() != null ? llmModel.getAuthType() : "none";

                    switch (authType) {
                        case "bearer":
                            headers.put("Authorization", "Bearer " + apiKey);
                            log.debug("Using Bearer token authentication for {}", llmModel.getModelCategory());
                            break;
                        case "api_key":
                            headers.put("x-api-key", apiKey);
                            log.debug("Using x-api-key header authentication for {}", llmModel.getModelCategory());
                            break;
                        case "api_key_query":
                            url.set(url.get() + (url.get().contains("?") ? "&" : "?") + "key=" + apiKey);
                            log.debug("Using API key query parameter authentication for {}", llmModel.getModelCategory());
                            break;
                        case "none":
                        default:
                            log.debug("No authentication required for {}", llmModel.getModelCategory());
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
                    metadata.setSource(llmModel.getModelCategory());
                    metadata.setStatus("success");
                    metadata.setTeamId(llmModel.getTeamId());
                    metadata.setCacheHit(false);
                    response.setMetadata(metadata);
                    log.info("✅ LLM request completed successfully for {}/{}", llmModel.getModelCategory(), llmModel.getModelName());
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
        String modelCategory = llmModel.getModelCategory();

        switch (modelCategory) {
            case "openai-chat":
            case "mistral-chat":
                payload.put("model", llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "default");
                payload.put("messages", request.getQuery().getPayload());
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    payload.putAll(llmConfig.getSettings());
                }
                break;
            case "huggingface-chat":
                payload.put("inputs", request.getQuery().getParams().getOrDefault("prompt", ""));
                payload.put("model", llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "sentence-transformers/all-MiniLM-L6-v2");
                payload.put("parameters", llmConfig != null ? llmConfig.getSettings() : new HashMap<>());
                break;
            case "gemini-chat":
                payload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", request.getQuery().getParams().getOrDefault("prompt", ""))))));
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    payload.put("generationConfig", llmConfig.getSettings());
                }
                break;
            case "claude-chat":
                // Claude API format
                String claudeModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "claude-sonnet-4-5";
                payload.put("model", claudeModel);
                
                // Handle messages array from payload
                if (request.getQuery().getPayload() instanceof List) {
                    payload.put("messages", request.getQuery().getPayload());
                } else if (request.getQuery().getPayload() instanceof Map) {
                    // If payload is a Map, convert to messages format
                    Map<String, Object> payloadMap = (Map<String, Object>) request.getQuery().getPayload();
                    if (payloadMap.containsKey("messages")) {
                        payload.put("messages", payloadMap.get("messages"));
                    } else {
                        // Create a single user message from the payload
                        payload.put("messages", List.of(Map.of("role", "user", "content", payloadMap.getOrDefault("content", ""))));
                    }
                }
                
                // Add settings from llmConfig (e.g., max_tokens)
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    payload.putAll(llmConfig.getSettings());
                }
                log.info("Building Claude payload for model: {}", claudeModel);
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
            case "cohere-chat":
                // Cohere Chat API format
                String cohereModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "command-r-08-2024";
                payload.put("model", cohereModel);
                
                // Handle payload which can be either a Map (with message/preamble) or fall back to params
                if (request.getQuery().getPayload() instanceof Map) {
                    Map<String, Object> coherePayload = (Map<String, Object>) request.getQuery().getPayload();
                    if (coherePayload.containsKey("message")) {
                        payload.put("message", coherePayload.get("message"));
                    }
                    if (coherePayload.containsKey("preamble")) {
                        payload.put("preamble", coherePayload.get("preamble"));
                    }
                    // Add any other fields from the payload
                    coherePayload.forEach((key, value) -> {
                        if (!key.equals("message") && !key.equals("preamble")) {
                            payload.put(key, value);
                        }
                    });
                }
                
                // Add settings from llmConfig
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    payload.putAll(llmConfig.getSettings());
                }
                log.info("Building Cohere payload for model: {}", cohereModel);
                break;
            case "cohere-embed":
                // Cohere Embed API format
                Object cohereTextParam = request.getQuery().getParams().getOrDefault("text", "");
                String cohereText = cohereTextParam != null ? cohereTextParam.toString() : "";
                String cohereEmbedModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "embed-english-v3.0";
                payload.put("texts", List.of(cohereText));
                payload.put("model", cohereEmbedModel);
                payload.put("input_type", "search_document");
                log.info("Building Cohere embedding payload - text: {}, model: {}", cohereText, cohereEmbedModel);
                break;
            default:
                payload.putAll(request.getQuery().getParams());
                break;
        }

        log.info("📦 Built {} payload for {}: {}", modelCategory, llmModel.getModelName(), payload);
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
                .onStatus(status -> status.isError(), response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                log.error("❌ Error calling LLM service {}: {} - Response body: {}", url, response.statusCode(), body);
                                return Mono.error(new RuntimeException("HTTP " + response.statusCode() + ": " + body));
                            });
                })
                .bodyToMono(Object.class)
                .doOnNext(response -> log.info("✅ Received response from LLM service: {}", url))
                .doOnError(error -> log.error("❌ Error calling LLM service {}: {}", url, error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Error details for LLM service {}: {}", url, error.getMessage());
                    return Mono.just(Map.of("error", error.getMessage()));
                });
    }
}

