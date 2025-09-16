package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lite.gateway.service.JsonParseService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class JsonParseServiceImpl implements JsonParseService {
    
    private final ObjectMapper objectMapper;
    
    public JsonParseServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<Map<String, Object>> parseJsonFields(String jsonString, String... fields) {
        return Mono.fromCallable(() -> {
            try {
                log.info("Parsing JSON string: {}", jsonString);
                log.info("Extracting fields: {}", (Object) fields);
                
                Map<String, Object> result = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                
                for (String field : fields) {
                    if (jsonNode.has(field)) {
                        JsonNode fieldNode = jsonNode.get(field);
                        log.info("Field '{}' found, type: {}, value: {}", field, fieldNode.getNodeType(), fieldNode);
                        
                        if (fieldNode.isTextual()) {
                            result.put(field, fieldNode.asText());
                        } else if (fieldNode.isArray()) {
                            result.put(field, fieldNode.toString());
                        } else if (fieldNode.isObject()) {
                            result.put(field, fieldNode.toString());
                        } else {
                            result.put(field, fieldNode.asText());
                        }
                        
                        log.info("Field '{}' extracted as: {}", field, result.get(field));
                    } else {
                        log.warn("Field '{}' not found in JSON", field);
                        result.put(field, "");
                    }
                }
                
                log.info("Final result: {}", result);
                return result;
            } catch (Exception e) {
                log.error("Error parsing JSON fields: {}", e.getMessage(), e);
                Map<String, Object> errorResult = new LinkedHashMap<>();
                for (String field : fields) {
                    errorResult.put(field, "");
                }
                return errorResult;
            }
        });
    }
    
    @Override
    public Mono<Map<String, Object>> parseJson(String jsonString) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                Map<String, Object> result = new HashMap<>();
                
                jsonNode.fieldNames().forEachRemaining(fieldName -> {
                    JsonNode fieldNode = jsonNode.get(fieldName);
                    if (fieldNode.isTextual()) {
                        result.put(fieldName, fieldNode.asText());
                    } else if (fieldNode.isArray()) {
                        result.put(fieldName, fieldNode.toString());
                    } else if (fieldNode.isObject()) {
                        result.put(fieldName, fieldNode.toString());
                    } else {
                        result.put(fieldName, fieldNode.asText());
                    }
                });
                
                return result;
            } catch (Exception e) {
                log.error("Error parsing JSON: {}", e.getMessage());
                return new HashMap<>();
            }
        });
    }
    
    @Override
    public Mono<Object> extractField(String jsonString, String fieldName) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                if (jsonNode.has(fieldName)) {
                    JsonNode fieldNode = jsonNode.get(fieldName);
                    if (fieldNode.isTextual()) {
                        return fieldNode.asText();
                    } else if (fieldNode.isArray()) {
                        return fieldNode.toString();
                    } else if (fieldNode.isObject()) {
                        return fieldNode.toString();
                    } else {
                        return fieldNode.asText();
                    }
                }
                return "";
            } catch (Exception e) {
                log.error("Error extracting field {}: {}", fieldName, e.getMessage());
                return "";
            }
        });
    }
} 