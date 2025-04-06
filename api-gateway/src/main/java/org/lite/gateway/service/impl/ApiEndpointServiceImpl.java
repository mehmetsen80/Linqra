package org.lite.gateway.service.impl;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiEndpointServiceImpl implements ApiEndpointService {

    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiEndpointVersionRepository apiEndpointVersionRepository;
    private final ApiEndpointVersionMetadataRepository apiEndpointVersionMetadataRepository;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy-HH-mm-ss");
    private JsonNode swagger; // Add this as a class field
    private final Random random = new Random();
    private final String[] exampleStrings = {
        "Sample", "Test", "Demo", "Example", "Product", "Item", "Basic", "Premium", "Standard", "Custom"
    };

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
                existing.setSwaggerJson(endpoint.getSwaggerJson());
                existing.setName(endpoint.getName());
                existing.setDescription(endpoint.getDescription());
                existing.setUpdatedAt(System.currentTimeMillis());
                existing.setVersion(existing.getVersion() + 1);
                return existing;
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

    @Override
    public Mono<Map<String, List<SwaggerEndpointInfo>>> extractEndpointsByTag(String swaggerJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.swagger = mapper.readTree(swaggerJson);
            Map<String, List<SwaggerEndpointInfo>> endpointsByTag = new HashMap<>();
            
            JsonNode paths = swagger.get("paths");
            if (paths != null) {
                paths.fields().forEachRemaining(pathEntry -> {
                    String path = pathEntry.getKey();
                    JsonNode pathNode = pathEntry.getValue();
                    
                    pathNode.fields().forEachRemaining(methodEntry -> {
                        String method = methodEntry.getKey().toUpperCase();
                        JsonNode endpointNode = methodEntry.getValue();
                        
                        // Create endpoint info
                        SwaggerEndpointInfo endpointInfo = createEndpointInfo(path, method, endpointNode);
                        
                        // Get tags and add endpoint to each tag's list
                        if (endpointNode.has("tags") && endpointNode.get("tags").isArray()) {
                            endpointNode.get("tags").forEach(tag -> {
                                String tagName = tag.asText();
                                endpointsByTag.computeIfAbsent(tagName, k -> new ArrayList<>())
                                            .add(endpointInfo);
                            });
                        }
                    });
                });
            }
            
            return Mono.just(endpointsByTag);
        } catch (Exception e) {
            log.error("Error extracting endpoints by tag: ", e);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Map<String, SwaggerSchemaInfo>> extractSchemas(String swaggerJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode swagger = mapper.readTree(swaggerJson);
            Map<String, SwaggerSchemaInfo> schemas = new HashMap<>();
            
            JsonNode components = swagger.get("components");
            if (components != null && components.has("schemas")) {
                JsonNode schemasNode = components.get("schemas");
                schemasNode.fields().forEachRemaining(entry -> {
                    String schemaName = entry.getKey();
                    JsonNode schemaNode = entry.getValue();
                    SwaggerSchemaInfo schemaInfo = createSchemaInfo(schemaName, schemaNode);
                    schemas.put(schemaName, schemaInfo);
                });
            }
            
            return Mono.just(schemas);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private SwaggerEndpointInfo createEndpointInfo(String path, String method, JsonNode endpointNode) {
        List<String> tags = new ArrayList<>();
        if (endpointNode.has("tags")) {
            endpointNode.get("tags").forEach(tag -> tags.add(tag.asText()));
        }

        return SwaggerEndpointInfo.builder()
            .path(path)
            .method(method)
            .summary(endpointNode.has("summary") ? endpointNode.get("summary").asText() : null)
            .operationId(endpointNode.has("operationId") ? endpointNode.get("operationId").asText() : null)
            .parameters(endpointNode.has("parameters") ? parseParameters(endpointNode.get("parameters")) : null)
            .requestBody(endpointNode.has("requestBody") ? parseRequestBody(endpointNode.get("requestBody")) : null)
            .responses(endpointNode.has("responses") ? parseResponses(endpointNode.get("responses"), path, method) : null)
            .tags(tags)
            .build();
    }

    private SwaggerSchemaInfo createSchemaInfo(String name, JsonNode schemaNode) {
        Map<String, SwaggerProperty> properties = new HashMap<>();
        
        if (schemaNode.has("properties")) {
            schemaNode.get("properties").fields().forEachRemaining(entry -> {
                String propertyName = entry.getKey();
                JsonNode propertyNode = entry.getValue();
                
                SwaggerProperty property = SwaggerProperty.builder()
                    .type(propertyNode.has("type") ? propertyNode.get("type").asText() : null)
                    .format(propertyNode.has("format") ? propertyNode.get("format").asText() : null)
                    .additionalProperties(propertyNode.has("additionalProperties") ? 
                        parseAdditionalProperties(propertyNode.get("additionalProperties")) : null)
                    .reference(propertyNode.has("$ref") ? propertyNode.get("$ref").asText() : null)
                    .build();
                
                properties.put(propertyName, property);
            });
        }

        return SwaggerSchemaInfo.builder()
            .name(name)
            .type(schemaNode.has("type") ? schemaNode.get("type").asText() : null)
            .properties(properties)
            .build();
    }

    private Object parseValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.numberValue();
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(entry -> 
                map.put(entry.getKey(), parseValue(entry.getValue())));
            return map;
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.elements().forEachRemaining(element -> 
                list.add(parseValue(element)));
            return list;
        }
        return null;
    }

    private List<SwaggerParameter> parseParameters(JsonNode parameters) {
        List<SwaggerParameter> paramList = new ArrayList<>();
        if (parameters != null && parameters.isArray()) {
            parameters.forEach(param -> {
                Map<String, Object> schema = null;
                if (param.has("schema")) {
                    JsonNode schemaNode = param.get("schema");
                    schema = parseSchema(schemaNode);
                }

                SwaggerParameter parameter = SwaggerParameter.builder()
                    .name(param.has("name") ? param.get("name").asText() : null)
                    .in(param.has("in") ? param.get("in").asText() : null)
                    .description(param.has("description") ? param.get("description").asText() : null)
                    .required(param.has("required") && param.get("required").asBoolean())
                    .schema(schema)
                    .build();
                
                paramList.add(parameter);
            });
        }
        return paramList;
    }

    private SwaggerRequestBody parseRequestBody(JsonNode requestBody) {
        if (requestBody == null) {
            return null;
        }

        Map<String, SwaggerMediaType> content = new HashMap<>();
        if (requestBody.has("content")) {
            JsonNode contentNode = requestBody.get("content");
            contentNode.fields().forEachRemaining(entry -> {
                String mediaType = entry.getKey();
                JsonNode mediaTypeNode = entry.getValue();
                JsonNode schemaNode = mediaTypeNode.get("schema");
                
                log.debug("Raw schema node: {}", schemaNode);
                
                Map<String, Object> schema = new HashMap<>();
                if (schemaNode != null && !schemaNode.isEmpty()) {
                    if (schemaNode.has("$ref")) {
                        String ref = schemaNode.get("$ref").asText();
                        String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                        if (swagger != null && swagger.has("components") && 
                            swagger.get("components").has("schemas")) {
                            JsonNode actualSchema = swagger.get("components")
                                .get("schemas")
                                .get(schemaName);
                            if (actualSchema != null) {
                                schema = parseSchema(actualSchema);
                            }
                        }
                    } else {
                        // Direct schema definition
                        schema = parseSchema(schemaNode);
                    }
                }
                
                log.debug("Parsed schema: {}", schema);
                
                SwaggerMediaType swaggerMediaType = SwaggerMediaType.builder()
                    .schema(schema)
                    .build();
                
                // Parse example if it exists in the original schema
                if (mediaTypeNode.has("example")) {
                    try {
                        Object example = objectMapper.convertValue(mediaTypeNode.get("example"), Map.class);
                        swaggerMediaType.setExample(example);
                    } catch (Exception e) {
                        log.warn("Failed to parse example: {}", e.getMessage());
                    }
                }
                
                content.put(mediaType, swaggerMediaType);
            });
        }

        // After creating the SwaggerMediaType, add example if none exists
        content.forEach((mediaType, swaggerMediaType) -> {
            if (swaggerMediaType.getExample() == null) {
                Map<String, Object> example = generateExample(swaggerMediaType.getSchema());
                if (example != null) {
                    swaggerMediaType.setExample(example);
                }
            }
        });

        return SwaggerRequestBody.builder()
            .content(content)
            .required(requestBody.has("required") && requestBody.get("required").asBoolean())
            .build();
    }

    private Map<String, SwaggerResponse> parseResponses(JsonNode responses, String path, String method) {
        Map<String, SwaggerResponse> responseMap = new HashMap<>();
        try {
            if (responses != null) {
                responses.fields().forEachRemaining(entry -> {
                    String statusCode = entry.getKey();
                    JsonNode response = entry.getValue();
                    Map<String, SwaggerMediaType> content = new HashMap<>();
                    
                    // Special handling for OPTIONS method
                    if (method.equals("OPTIONS") && statusCode.equals("200")) {
                        Map<String, Object> schema = new HashMap<>();
                        schema.put("type", "object");
                        Map<String, Object> properties = new HashMap<>();
                        properties.put("Allow", Map.of(
                            "type", "string",
                            "description", "Allowed HTTP methods"
                        ));
                        schema.put("properties", properties);

                        Map<String, Object> example = new HashMap<>();
                        example.put("Allow", "GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS");
                        
                        SwaggerMediaType swaggerMediaType = SwaggerMediaType.builder()
                            .schema(schema)
                            .example(example)
                            .build();
                        
                        content.put("application/json", swaggerMediaType);
                    } else if (response.has("content")) {
                        JsonNode contentNode = response.get("content");
                        contentNode.fields().forEachRemaining(contentEntry -> {
                            try {
                                String mediaType = contentEntry.getKey();
                                JsonNode mediaTypeNode = contentEntry.getValue();
                                JsonNode schemaNode = mediaTypeNode.get("schema");
                                
                                // Resolve $ref if present
                                Map<String, Object> schema;
                                if (schemaNode.has("$ref")) {
                                    String ref = schemaNode.get("$ref").asText();
                                    String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                                    JsonNode actualSchema = swagger.get("components").get("schemas").get(schemaName);
                                    schema = parseSchema(actualSchema);
                                } else {
                                    schema = parseSchema(schemaNode);
                                }
                                
                                // Use example from the Swagger spec if available
                                Map<String, Object> example = null;
                                if (mediaTypeNode.has("example")) {
                                    JsonNode exampleNode = mediaTypeNode.get("example");
                                    if (!exampleNode.isNull()) {
                                        example = objectMapper.convertValue(exampleNode, Map.class);
                                    }
                                }
                                
                                // Generate example if not provided
                                if (example == null && !statusCode.equals("204")) {
                                    if (statusCode.startsWith("4") || statusCode.startsWith("5")) {
                                        example = generateErrorExample(statusCode, response.get("description").asText(), path);
                                    } else {
                                        Object generatedExample = generateSuccessExample(schema);
                                        if (generatedExample instanceof Map) {
                                            example = (Map<String, Object>) generatedExample;
                                        } else if (generatedExample instanceof List) {
                                            // For array responses, use the list directly
                                            if (schema != null && "array".equals(schema.get("type"))) {
                                                example = Collections.singletonMap("items", generatedExample);
                                            } else {
                                                example = Collections.singletonMap("value", generatedExample);
                                            }
                                        } else {
                                            example = Collections.singletonMap("value", generatedExample);
                                        }
                                    }
                                }

                                // Handle array responses
                                if (schema != null && "array".equals(schema.get("type")) && example != null) {
                                    Object arrayExample = example.get("items");
                                    if (arrayExample != null) {
                                        example = Collections.singletonMap("items", arrayExample);
                                    }
                                }
                                
                                SwaggerMediaType swaggerMediaType = SwaggerMediaType.builder()
                                    .schema(schema)
                                    .example(example)
                                    .build();
                                
                                content.put(mediaType, swaggerMediaType);
                            } catch (Exception e) {
                                log.error("Error processing content entry: {}", e.getMessage());
                            }
                        });
                    }
                    
                    SwaggerResponse swaggerResponse = SwaggerResponse.builder()
                        .description(response.has("description") ? response.get("description").asText() : null)
                        .content(content)
                        .build();
                    
                    responseMap.put(entry.getKey(), swaggerResponse);
                });
            }
        } catch (Exception e) {
            log.error("Error parsing responses: {}", e.getMessage());
        }
        return responseMap;
    }

    private Map<String, Object> parseSchema(JsonNode schemaNode) {
        Map<String, Object> schema = new HashMap<>();
        
        if (schemaNode == null || schemaNode.isEmpty()) {
            return schema;
        }

        // If it's a $ref, resolve it
        if (schemaNode.has("$ref")) {
            String ref = schemaNode.get("$ref").asText();
            String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
            JsonNode componentsNode = swagger.get("components");
            if (componentsNode != null && componentsNode.has("schemas")) {
                JsonNode actualSchema = componentsNode.get("schemas").get(schemaName);
                if (actualSchema != null) {
                    return parseSchema(actualSchema);
                }
            }
        }

        // If schema has an example but no type/properties, construct schema from example
        if (schemaNode.has("example") && !schemaNode.has("type") && !schemaNode.has("properties")) {
            JsonNode example = schemaNode.get("example");
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            
            example.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                Map<String, Object> property = new HashMap<>();
                
                if (value.isNumber()) {
                    if (value.isInt()) {
                        property.put("type", "integer");
                        property.put("format", "int32");
                    } else {
                        property.put("type", "number");
                        property.put("format", "double");
                    }
                } else if (value.isTextual()) {
                    property.put("type", "string");
                } else if (value.isBoolean()) {
                    property.put("type", "boolean");
                }
                
                properties.put(key, property);
            });
            
            schema.put("properties", properties);
            if (schemaNode.has("description")) {
                schema.put("description", schemaNode.get("description").asText());
            }
            return schema;
        }

        // Handle type
        if (schemaNode.has("type")) {
            schema.put("type", schemaNode.get("type").asText());
            
            // Special handling for array type
            if ("array".equals(schemaNode.get("type").asText()) && schemaNode.has("items")) {
                JsonNode itemsNode = schemaNode.get("items");
                // Recursively parse the items schema
                Map<String, Object> itemsSchema = parseSchema(itemsNode);
                schema.put("items", itemsSchema);
            }
        }
        
        if (schemaNode.has("format")) {
            schema.put("format", schemaNode.get("format").asText());
        }
        
        if (schemaNode.has("description")) {
            schema.put("description", schemaNode.get("description").asText());
        }
        
        if (schemaNode.has("properties")) {
            Map<String, Object> properties = new HashMap<>();
            schemaNode.get("properties").fields().forEachRemaining(entry -> {
                properties.put(entry.getKey(), parseSchema(entry.getValue()));
            });
            schema.put("properties", properties);
        }

        return schema;
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            arrayNode.forEach(node -> result.add(node.asText()));
        }
        return result;
    }

    private Map<String, Object> parseAdditionalProperties(JsonNode node) {
        Map<String, Object> props = new HashMap<>();
        node.fields().forEachRemaining(entry -> 
            props.put(entry.getKey(), parseValue(entry.getValue())));
        return props;
    }

    @Override
    public Flux<ApiEndpoint> getEndpointVersionsByRoute(String routeIdentifier) {
        return apiEndpointVersionRepository.findByRouteIdentifier(routeIdentifier)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                String.format("No versions found for route %s", routeIdentifier),
                ErrorCode.ENDPOINT_NOT_FOUND
            )))
            .map(ApiEndpointVersion::getEndpointData);  // Convert ApiEndpointVersion to ApiEndpoint
    }

    // Add this helper method to generate example values
    private Object generateExampleValue(String type, String format) {
        if (type == null) return null;
        
        switch (type) {
            case "integer":
                if (format != null && format.equals("int64")) {
                    return random.nextLong(1000, 10000); // Random long between 1000-9999
                } else {
                    return random.nextInt(1, 100); // Random int between 1-99
                }
            case "number":
                if (format != null) {
                    switch (format) {
                        case "double":
                            return Math.round(random.nextDouble(10, 1000) * 100.0) / 100.0;
                        case "float":
                            return Math.round(random.nextFloat(1, 100) * 10.0) / 10.0;
                        default:
                            return random.nextDouble(1, 100);
                    }
                }
                return Math.round(random.nextDouble(1, 100) * 10.0) / 10.0;
            case "string":
                if (format != null) {
                    switch (format) {
                        case "date-time":
                            return LocalDateTime.now()
                                .plusDays(random.nextInt(30))
                                .plusHours(random.nextInt(24))
                                .plusMinutes(random.nextInt(60))
                                .format(DateTimeFormatter.ISO_DATE_TIME);
                        case "date":
                            return LocalDate.now()
                                .plusDays(random.nextInt(30))
                                .format(DateTimeFormatter.ISO_DATE);
                        case "time":
                            return LocalTime.of(
                                random.nextInt(24),
                                random.nextInt(60),
                                random.nextInt(60))
                                .format(DateTimeFormatter.ISO_TIME);
                        case "email":
                            return "user" + random.nextInt(100) + "@example.com";
                        case "uuid":
                            return UUID.randomUUID().toString();
                        case "uri":
                            return "https://example.com/resource/" + random.nextInt(100);
                        case "hostname":
                            return "server-" + random.nextInt(100) + ".example.com";
                        case "ipv4":
                            return random.nextInt(256) + "." + 
                                   random.nextInt(256) + "." + 
                                   random.nextInt(256) + "." + 
                                   random.nextInt(256);
                        case "ipv6":
                            return "2001:0db8:85a3:0000:0000:8a2e:0370:" + 
                                   String.format("%04x", random.nextInt(65536));
                        case "password":
                            return "********";
                        default:
                            return exampleStrings[random.nextInt(exampleStrings.length)];
                    }
                }
                return exampleStrings[random.nextInt(exampleStrings.length)];
            case "boolean":
                return random.nextBoolean();
            default:
                return null;
        }
    }

    // Add this helper method to generate example object from schema
    private Map<String, Object> generateExample(Map<String, Object> schema) {
        if (schema == null || !schema.containsKey("properties")) {
            return null;
        }

        Map<String, Object> example = new HashMap<>();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        
        properties.forEach((key, value) -> {
            Map<String, Object> property = (Map<String, Object>) value;
            String type = (String) property.get("type");
            String format = (String) property.get("format");

            if ("array".equals(type)) {
                // Handle nested arrays
                Map<String, Object> items = (Map<String, Object>) property.get("items");
                if (items != null) {
                    List<Object> arrayExample = new ArrayList<>();
                    // Add two example items for the nested array
                    for (int i = 0; i < 2; i++) {
                        if ("object".equals(items.get("type"))) {
                            arrayExample.add(generateExample(items));
                        } else {
                            arrayExample.add(generateExampleValue(items.get("type").toString(), (String) items.get("format")));
                        }
                    }
                    example.put(key, arrayExample);
                }
            } else {
                example.put(key, generateExampleValue(type, format));
            }
        });
        
        return example;
    }

    private Map<String, Object> generateErrorExample(String statusCode, String description, String path) {
        Map<String, Object> errorExample = new HashMap<>();
        errorExample.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        errorExample.put("status", Integer.parseInt(statusCode));
        errorExample.put("error", getErrorTypeForStatus(statusCode));
        errorExample.put("message", description);
        errorExample.put("path", path);
        return errorExample;
    }

    private String getErrorTypeForStatus(String statusCode) {
        switch (statusCode) {
            case "400": return "Bad Request";
            case "401": return "Unauthorized";
            case "403": return "Forbidden";
            case "404": return "Not Found";
            case "405": return "Method Not Allowed";
            case "409": return "Conflict";
            case "500": return "Internal Server Error";
            case "502": return "Bad Gateway";
            case "503": return "Service Unavailable";
            default: return "Unknown Error";
        }
    }

    private Object generateSuccessExample(Map<String, Object> schema) {
        if (schema == null) return null;

        String type = (String) schema.get("type");
        if ("array".equals(type)) {
            List<Object> arrayExample = new ArrayList<>();
            Map<String, Object> itemSchema = (Map<String, Object>) schema.get("items");
            if (itemSchema != null) {
                // Add two example items
                arrayExample.add(generateExample(itemSchema));
                arrayExample.add(generateExample(itemSchema));
            }
            return arrayExample;
        } else {
            return generateExample(schema);
        }
    }
} 