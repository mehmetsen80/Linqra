package org.lite.gateway.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ToolDefinition;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service for validating tool-specific parameters against the ToolDefinition's inputSchema.
 * This ensures that parameters like enums, types, and required fields are strictly followed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolParameterValidationService {
    private final ObjectMapper objectMapper;

    /**
     * Validates input parameters against the tool's defined inputSchema.
     *
     * @param params The parameters provided in the request
     * @param tool   The ToolDefinition containing the schema
     * @return A ValidationResult containing any errors found
     */
    public Mono<ValidationResult> validate(Map<String, Object> params, ToolDefinition tool) {
        ValidationResult result = new ValidationResult();
        String schemaStr = tool.getInputSchema();

        if (schemaStr == null || schemaStr.isBlank()) {
            log.debug("No inputSchema found for tool {}, skipping parameter validation", tool.getToolId());
            return Mono.just(result);
        }

        try {
            JsonNode schema = objectMapper.readTree(schemaStr);
            
            // 1. Validate 'properties' if it exists
            JsonNode properties = schema.get("properties");
            if (properties != null && properties.isObject()) {
                properties.fields().forEachRemaining(entry -> {
                    String fieldName = entry.getKey();
                    JsonNode fieldSchema = entry.getValue();
                    Object value = params != null ? params.get(fieldName) : null;

                    // 1a. Type validation
                    if (fieldSchema.has("type")) {
                        validateType(fieldName, value, fieldSchema.get("type").asText(), result);
                    }

                    // 1b. Enum validation
                    if (fieldSchema.has("enum")) {
                        validateEnum(fieldName, value, fieldSchema.get("enum"), result);
                    }
                });
            }

            // 2. Validate 'required' fields
            if (schema.has("required") && schema.get("required").isArray()) {
                schema.get("required").forEach(req -> {
                    String reqFieldName = req.asText();
                    if (params == null || !params.containsKey(reqFieldName) || params.get(reqFieldName) == null) {
                        result.addError("Required parameter '" + reqFieldName + "' is missing");
                    }
                });
            }

        } catch (Exception e) {
            log.error("Failed to parse or validate inputSchema for tool {}: {}", tool.getToolId(), e.getMessage());
            result.addError("Failed to validate parameters: Schema parsing error");
        }

        return Mono.just(result);
    }

    private void validateType(String fieldName, Object value, String expectedType, ValidationResult result) {
        if (value == null) return;

        switch (expectedType.toLowerCase()) {
            case "string":
                if (!(value instanceof String)) {
                    result.addError(String.format("Parameter '%s' should be a string", fieldName));
                }
                break;
            case "number":
            case "integer":
                if (!(value instanceof Number)) {
                    // Try to parse if it's a string representing a number
                    if (value instanceof String s) {
                        try {
                            Double.parseDouble(s);
                        } catch (NumberFormatException e) {
                            result.addError(String.format("Parameter '%s' should be a number", fieldName));
                        }
                    } else {
                        result.addError(String.format("Parameter '%s' should be a number", fieldName));
                    }
                }
                break;
            case "boolean":
                if (!(value instanceof Boolean)) {
                    if (value instanceof String s) {
                        if (!"true".equalsIgnoreCase(s) && !"false".equalsIgnoreCase(s)) {
                            result.addError(String.format("Parameter '%s' should be a boolean", fieldName));
                        }
                    } else {
                        result.addError(String.format("Parameter '%s' should be a boolean", fieldName));
                    }
                }
                break;
            default:
                // Skip other types (object, array) for now or handle them if needed
                break;
        }
    }

    private void validateEnum(String fieldName, Object value, JsonNode enumNode, ValidationResult result) {
        if (value == null) return; // Null handled by 'required' check if applicable

        String stringValue = String.valueOf(value);
        boolean match = false;
        StringBuilder allowed = new StringBuilder();

        for (JsonNode option : enumNode) {
            String optionText = option.asText();
            if (optionText.equals(stringValue)) {
                match = true;
                break;
            }
            if (!allowed.isEmpty()) allowed.append(", ");
            allowed.append("'").append(optionText).append("'");
        }

        if (!match) {
            result.addError(String.format("Invalid value '%s' for parameter '%s'. Expected one of: [%s]", 
                    stringValue, fieldName, allowed));
        }
    }
}
