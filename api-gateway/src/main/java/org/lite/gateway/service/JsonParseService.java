package org.lite.gateway.service;

import reactor.core.publisher.Mono;
import java.util.Map;

public interface JsonParseService {
    
    /**
     * Parse a JSON string and extract specific fields
     * @param jsonString The JSON string to parse
     * @param fields The fields to extract
     * @return Map containing the extracted field values
     */
    Mono<Map<String, Object>> parseJsonFields(String jsonString, String... fields);
    
    /**
     * Parse a JSON string and return all fields as a map
     * @param jsonString The JSON string to parse
     * @return Map containing all parsed fields
     */
    Mono<Map<String, Object>> parseJson(String jsonString);
    
    /**
     * Extract a specific field from a JSON string
     * @param jsonString The JSON string to parse
     * @param fieldName The field name to extract
     * @return The extracted field value
     */
    Mono<Object> extractField(String jsonString, String fieldName);
} 