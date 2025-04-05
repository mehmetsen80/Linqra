package org.lite.gateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiEndpoint;
import org.lite.gateway.entity.ApiEndpointVersion;
import org.lite.gateway.entity.ApiEndpointVersionMetadata;
import org.lite.gateway.exception.ResourceNotFoundException;
import org.lite.gateway.repository.ApiEndpointRepository;
import org.lite.gateway.repository.ApiEndpointVersionMetadataRepository;
import org.lite.gateway.repository.ApiEndpointVersionRepository;
import org.lite.gateway.service.ApiEndpointService;
import org.lite.gateway.dto.ErrorCode;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import org.lite.gateway.dto.SwaggerMediaType;
import org.lite.gateway.dto.SwaggerParameter;
import org.lite.gateway.dto.SwaggerProperty;
import org.lite.gateway.dto.SwaggerRequestBody;
import org.lite.gateway.dto.SwaggerResponse;
import org.lite.gateway.dto.SwaggerSchemaInfo;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiEndpointServiceImpl implements ApiEndpointService {

    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiEndpointVersionRepository apiEndpointVersionRepository;
    private final ApiEndpointVersionMetadataRepository apiEndpointVersionMetadataRepository;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy-HH-mm-ss");

    private Mono<ApiEndpoint> saveVersionIfNotExists(ApiEndpoint endpoint) {
        ApiEndpointVersion version = ApiEndpointVersion.builder()
                .endpointId(endpoint.getId())
                .routeIdentifier(endpoint.getRouteIdentifier())
                .version(endpoint.getVersion())
                .endpointData(endpoint)
                .createdAt(System.currentTimeMillis())
                .build();

        return apiEndpointVersionRepository.findByEndpointIdAndVersion(endpoint.getId(), endpoint.getVersion())
                .switchIfEmpty(apiEndpointVersionRepository.save(version))
                .thenReturn(endpoint);
    }

    @Override
    public Mono<ApiEndpoint> createEndpoint(ApiEndpoint endpoint) {
        return Mono.just(endpoint)
            .doOnNext(e -> log.info("Processing endpoint creation for route: {}", e.getRouteIdentifier()))
            .flatMap(e -> validateSwaggerJson(e.getSwaggerJson())
                .flatMap(isValid -> isValid 
                    ? Mono.just(e)
                    : Mono.error(new IllegalArgumentException("Invalid OpenAPI/Swagger JSON"))
                ))
            .map(e -> {
                e.setCreatedAt(System.currentTimeMillis());
                if (e.getName() == null) {
                    String timestamp = LocalDateTime.now().format(formatter);
                    e.setName("Endpoint-" + timestamp);
                    log.info("Generated name: {}", e.getName());
                }
                return e;
            })
            .flatMap(apiEndpointRepository::save)
            .flatMap(this::saveVersionIfNotExists)
            .doOnSuccess(saved -> log.info("Created endpoint with ID: {}", saved.getId()))
            .doOnError(error -> log.error("Error creating endpoint: {}", error.getMessage()));
    }

    @Override
    public Mono<ApiEndpoint> getEndpoint(String id) {
        return apiEndpointRepository.findById(id)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                String.format("Endpoint with id %s not found", id),
                ErrorCode.ENDPOINT_NOT_FOUND
            )));
    }

    @Override
    public Flux<ApiEndpoint> getEndpointsByRouteIdentifier(String routeIdentifier) {
        return apiEndpointRepository.findByRouteIdentifier(routeIdentifier);
    }

    @Override
    public Mono<ApiEndpoint> getEndpointVersion(String routeIdentifier, Integer version) {
        return apiEndpointVersionRepository.findByRouteIdentifierAndVersion(routeIdentifier, version)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                String.format("Endpoint version %d not found for route: %s", version, routeIdentifier),
                ErrorCode.ENDPOINT_VERSION_NOT_FOUND
            )))
            .map(ApiEndpointVersion::getEndpointData);  // Convert ApiEndpointVersion to ApiEndpoint
    }

    @Override
    public Mono<ApiEndpoint> updateEndpoint(String id, ApiEndpoint endpoint) {
        return getEndpoint(id)
            .flatMap(existing -> validateSwaggerJson(endpoint.getSwaggerJson())
                .flatMap(isValid -> isValid 
                    ? Mono.just(existing)
                    : Mono.error(new IllegalArgumentException("Invalid OpenAPI/Swagger JSON"))
                ))
            .map(existing -> {
                // Copy required fields from existing endpoint
                endpoint.setId(existing.getId());
                endpoint.setRouteIdentifier(existing.getRouteIdentifier());
                endpoint.setVersion(existing.getVersion() + 1);
                
                // Generate name if not provided
                if (endpoint.getName() == null) {
                    String timestamp = LocalDateTime.now().format(formatter);
                    endpoint.setName("Endpoint-" + timestamp);
                }
                
                // Set timestamps
                endpoint.setCreatedAt(existing.getCreatedAt());
                endpoint.setUpdatedAt(System.currentTimeMillis());
                
                return endpoint;
            })
            .flatMap(updated -> {
                ApiEndpointVersionMetadata apiEndpointVersionMetadata = ApiEndpointVersionMetadata.builder()
                    .endpointId(id)
                    .routeIdentifier(updated.getRouteIdentifier())
                    .version(updated.getVersion())
                    .changeType(ApiEndpointVersionMetadata.ChangeType.SWAGGER_UPDATE)
                    .changeReason("Swagger configuration updated")
                    .changeDescription("Updated Swagger configuration via UI")
                    .createdAt(System.currentTimeMillis())
                    .build();

                return apiEndpointVersionMetadataRepository.save(apiEndpointVersionMetadata)
                    .then(apiEndpointRepository.save(updated))
                    .flatMap(this::saveVersionIfNotExists);
            });
    }

    @Override
    public Mono<Void> deleteEndpoint(String id) {
        return apiEndpointRepository.existsById(id)
            .flatMap(exists -> exists 
                ? apiEndpointRepository.deleteById(id)
                : Mono.error(new ResourceNotFoundException(
                    String.format("Endpoint with id %s not found", id),
                    ErrorCode.ENDPOINT_NOT_FOUND
                  )));
    }

    @Override
    public Mono<ApiEndpoint> createNewVersion(String id, ApiEndpoint endpoint) {
        return apiEndpointRepository.findById(id)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                String.format("Endpoint with id %s not found", id),
                ErrorCode.ENDPOINT_NOT_FOUND
            )))
            .flatMap(existingEndpoint -> validateSwaggerJson(endpoint.getSwaggerJson())
                .flatMap(isValid -> isValid 
                    ? Mono.just(existingEndpoint)
                    : Mono.error(new IllegalArgumentException("Invalid OpenAPI/Swagger JSON"))
                )
                .map(existing -> {
                    // Create a new endpoint instance with required fields
                    ApiEndpoint newVersion = new ApiEndpoint();
                    newVersion.setId(existing.getId());
                    newVersion.setRouteIdentifier(existing.getRouteIdentifier());
                    newVersion.setVersion(existing.getVersion() + 1);
                    newVersion.setSwaggerJson(endpoint.getSwaggerJson());
                    
                    // Use existing name if not provided
                    newVersion.setName(endpoint.getName() != null ? endpoint.getName() : existing.getName());
                    newVersion.setDescription(endpoint.getDescription());
                    
                    // Set timestamps
                    newVersion.setCreatedAt(System.currentTimeMillis());
                    newVersion.setUpdatedAt(System.currentTimeMillis());
                    
                    return newVersion;
                })
                .flatMap(newVersion -> {
                    ApiEndpointVersionMetadata metadata = ApiEndpointVersionMetadata.builder()
                        .endpointId(id)
                        .routeIdentifier(newVersion.getRouteIdentifier())
                        .version(newVersion.getVersion())
                        .changeType(ApiEndpointVersionMetadata.ChangeType.SWAGGER_UPDATE)
                        .changeReason("Swagger configuration updated")
                        .changeDescription("Updated Swagger configuration via UI")
                        .createdAt(System.currentTimeMillis())
                        .build();
                    
                    return apiEndpointVersionMetadataRepository.save(metadata)
                        .then(apiEndpointRepository.save(newVersion))
                        .flatMap(this::saveVersionIfNotExists);
                }));
    }

    @Override
    public Mono<Boolean> validateSwaggerJson(String swaggerJson) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode jsonNode = objectMapper.readTree(swaggerJson);
                return jsonNode.has("openapi") && 
                       jsonNode.has("info") && 
                       jsonNode.has("paths");
            } catch (Exception e) {
                log.error("Error validating swagger JSON: {}", e.getMessage());
                return false;
            }
        });
    }

    public Mono<Map<String, List<SwaggerEndpointInfo>>> extractEndpointsByTag(String swaggerJson) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode rootNode = objectMapper.readTree(swaggerJson);
                
                // Validate required fields
                if (!rootNode.has("paths")) {
                    throw new IllegalArgumentException("Invalid Swagger JSON: 'paths' field is required");
                }

                JsonNode pathsNode = rootNode.get("paths");
                if (pathsNode.isNull() || !pathsNode.isObject()) {
                    throw new IllegalArgumentException("Invalid Swagger JSON: 'paths' must be an object");
                }

                Map<String, List<SwaggerEndpointInfo>> endpointsByTag = new HashMap<>();

                pathsNode.fields().forEachRemaining(pathEntry -> {
                    String path = pathEntry.getKey();
                    JsonNode methodsNode = pathEntry.getValue();

                    methodsNode.fields().forEachRemaining(methodEntry -> {
                        String method = methodEntry.getKey().toUpperCase();
                        JsonNode operationNode = methodEntry.getValue();

                        SwaggerEndpointInfo endpoint = parseEndpointInfo(path, method, operationNode);
                        
                        // If no tags are specified, use "default" as the tag
                        List<String> tags = endpoint.getTags();
                        if (tags == null || tags.isEmpty()) {
                            tags = List.of("default");
                        }
                        
                        tags.forEach(tag -> {
                            endpointsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(endpoint);
                        });
                    });
                });

                return endpointsByTag;
            } catch (JsonProcessingException e) {
                log.error("Error parsing Swagger JSON: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
            } catch (Exception e) {
                log.error("Error processing Swagger JSON: {}", e.getMessage());
                throw new IllegalArgumentException("Error processing Swagger JSON: " + e.getMessage());
            }
        });
    }

    public Mono<Map<String, SwaggerSchemaInfo>> extractSchemas(String swaggerJson) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode rootNode = objectMapper.readTree(swaggerJson);
                JsonNode componentsNode = rootNode.path("components");
                JsonNode schemasNode = componentsNode.path("schemas");

                // Handle case when schemas are not present
                if (schemasNode.isMissingNode() || schemasNode.isNull()) {
                    log.info("No schemas found in Swagger JSON");
                    return new HashMap<>();
                }

                Map<String, SwaggerSchemaInfo> schemas = new HashMap<>();

                schemasNode.fields().forEachRemaining(schemaEntry -> {
                    String schemaName = schemaEntry.getKey();
                    JsonNode schemaNode = schemaEntry.getValue();
                    
                    Map<String, SwaggerProperty> properties = new HashMap<>();
                    JsonNode propertiesNode = schemaNode.path("properties");
                    
                    if (!propertiesNode.isMissingNode() && !propertiesNode.isNull()) {
                        propertiesNode.fields().forEachRemaining(propertyEntry -> {
                            String propertyName = propertyEntry.getKey();
                            JsonNode propertyNode = propertyEntry.getValue();
                            
                            SwaggerProperty property = SwaggerProperty.builder()
                                .type(propertyNode.path("type").asText(null))
                                .format(propertyNode.path("format").asText(null))
                                .reference(propertyNode.path("$ref").asText(null))
                                .additionalProperties(parseAdditionalProperties(propertyNode))
                                .build();
                            
                            properties.put(propertyName, property);
                        });
                    }

                    SwaggerSchemaInfo schemaInfo = SwaggerSchemaInfo.builder()
                        .name(schemaName)
                        .type(schemaNode.path("type").asText("object"))
                        .properties(properties)
                        .build();

                    schemas.put(schemaName, schemaInfo);
                });

                return schemas;
            } catch (JsonProcessingException e) {
                log.error("Error parsing Swagger JSON: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
            } catch (Exception e) {
                log.error("Error processing Swagger JSON: {}", e.getMessage());
                throw new IllegalArgumentException("Error processing Swagger JSON: " + e.getMessage());
            }
        });
    }

    private SwaggerEndpointInfo parseEndpointInfo(String path, String method, JsonNode operationNode) {
        List<SwaggerParameter> parameters = new ArrayList<>();
        if (operationNode.has("parameters")) {
            operationNode.get("parameters").forEach(paramNode -> {
                SwaggerParameter parameter = SwaggerParameter.builder()
                    .name(paramNode.path("name").asText())
                    .in(paramNode.path("in").asText())
                    .description(paramNode.path("description").asText())
                    .required(paramNode.path("required").asBoolean(false))
                    .schema(parseSchema(paramNode.path("schema")))
                    .build();
                parameters.add(parameter);
            });
        }

        SwaggerRequestBody requestBody = null;
        if (operationNode.has("requestBody")) {
            JsonNode requestBodyNode = operationNode.get("requestBody");
            Map<String, SwaggerMediaType> content = parseContent(requestBodyNode.path("content"));
            requestBody = SwaggerRequestBody.builder()
                .content(content)
                .required(requestBodyNode.path("required").asBoolean(false))
                .build();
        }

        Map<String, SwaggerResponse> responses = new HashMap<>();
        operationNode.path("responses").fields().forEachRemaining(responseEntry -> {
            String statusCode = responseEntry.getKey();
            JsonNode responseNode = responseEntry.getValue();
            
            SwaggerResponse response = SwaggerResponse.builder()
                .description(responseNode.path("description").asText())
                .content(parseContent(responseNode.path("content")))
                .build();
            
            responses.put(statusCode, response);
        });

        return SwaggerEndpointInfo.builder()
            .path(path)
            .method(method)
            .summary(operationNode.path("summary").asText())
            .operationId(operationNode.path("operationId").asText())
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .tags(parseStringArray(operationNode.path("tags")))
            .build();
    }

    private Map<String, Object> parseSchema(JsonNode schemaNode) {
        try {
            return objectMapper.convertValue(schemaNode, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error parsing schema: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, SwaggerMediaType> parseContent(JsonNode contentNode) {
        Map<String, SwaggerMediaType> content = new HashMap<>();
        contentNode.fields().forEachRemaining(entry -> {
            String mediaType = entry.getKey();
            JsonNode mediaTypeNode = entry.getValue();
            
            SwaggerMediaType swaggerMediaType = SwaggerMediaType.builder()
                .schema(parseSchema(mediaTypeNode.path("schema")))
                .build();
            
            content.put(mediaType, swaggerMediaType);
        });
        return content;
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            arrayNode.forEach(node -> result.add(node.asText()));
        }
        return result;
    }

    private Map<String, Object> parseAdditionalProperties(JsonNode node) {
        try {
            Map<String, Object> properties = new HashMap<>();
            node.fields().forEachRemaining(entry -> {
                if (!Arrays.asList("type", "format", "$ref").contains(entry.getKey())) {
                    properties.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
                }
            });
            return properties;
        } catch (Exception e) {
            log.error("Error parsing additional properties: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public Flux<ApiEndpoint> getEndpointVersionsByRoute(String routeIdentifier) {
        return apiEndpointVersionRepository.findByRouteIdentifier(routeIdentifier)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                String.format("No versions found for route %s", routeIdentifier),
                ErrorCode.ENDPOINT_NOT_FOUND
            )))
            .map(version -> version.getEndpointData());  // Convert ApiEndpointVersion to ApiEndpoint
    }
} 