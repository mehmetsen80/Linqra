package org.lite.gateway.controller;

import org.lite.gateway.service.JsonParseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@RequestMapping("/api/json-parse")
@RequiredArgsConstructor
@Slf4j
public class JsonParseController {
    
    private final JsonParseService jsonParseService;
    
    @PostMapping("/parse")
    public Mono<ResponseEntity<Map<String, Object>>> parseJson(@RequestBody String jsonString) {
        log.info("Parsing JSON string: {}", jsonString);
        return jsonParseService.parseJson(jsonString)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/extract-fields")
    public Mono<ResponseEntity<Map<String, Object>>> extractFields(
            @RequestBody String jsonString,
            @RequestParam(value = "fields") String fieldsParam) {
        log.info("Extracting fields from JSON: {}", jsonString);
        
        // Handle both comma-separated string and array formats
        String[] fields;
        if (fieldsParam.startsWith("[") && fieldsParam.endsWith("]")) {
            // Remove brackets and split by comma
            String cleanFields = fieldsParam.substring(1, fieldsParam.length() - 1);
            fields = cleanFields.split(",");
        } else {
            // Split by comma directly
            fields = fieldsParam.split(",");
        }
        
        // Clean up field names (remove quotes and whitespace)
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].trim().replaceAll("^\"|\"$", "");
        }
        
        log.info("Processed fields: {}", (Object) fields);
        return jsonParseService.parseJsonFields(jsonString, fields)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/extract-field")
    public Mono<ResponseEntity<Object>> extractField(
            @RequestBody String jsonString,
            @RequestParam String fieldName) {
        log.info("Extracting field {} from JSON: {}", fieldName, jsonString);
        return jsonParseService.extractField(jsonString, fieldName)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
} 