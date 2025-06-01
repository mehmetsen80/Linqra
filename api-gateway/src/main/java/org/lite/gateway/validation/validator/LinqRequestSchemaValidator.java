package org.lite.gateway.validation.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class LinqRequestSchemaValidator {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ValidationResult validateSchema(Object request) {
        log.info("Starting schema validation for request: {}", request);
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        try {
            JsonNode rootNode;
            if (request instanceof String) {
                // For string input, validate the raw JSON first
                rootNode = objectMapper.readTree((String) request);
                validateRawJson(rootNode, errors);
                if (!errors.isEmpty()) {
                    result.setValid(false);
                    result.setErrors(errors);
                    return result;
                }
            } else {
                rootNode = objectMapper.valueToTree(request);
            }

            // Then validate the deserialized object
            validateRootNode(rootNode, errors);
        } catch (Exception e) {
            log.error("Error during schema validation: ", e);
            errors.add("Error during schema validation: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            result.setValid(false);
            result.setErrors(errors);
        }

        log.info("Schema validation completed. Result: {}", result);
        return result;
    }

    private void validateRawJson(JsonNode rootNode, List<String> errors) {
        if (!rootNode.isObject()) {
            errors.add("Request must be a JSON object");
            return;
        }

        // Validate link object
        if (rootNode.has("link")) {
            JsonNode linkNode = rootNode.get("link");
            if (!linkNode.isObject()) {
                errors.add("Link must be a JSON object");
            } else {
                // Check for invalid fields in link
                linkNode.fields().forEachRemaining(entry -> {
                    if (!hasField(LinqRequest.Link.class, entry.getKey())) {
                        errors.add("Invalid field in link object: '" + entry.getKey() + "'. Valid fields are: " + getFieldNames(LinqRequest.Link.class));
                    }
                });
            }
        } else {
            errors.add("Request must contain 'link' field");
        }

        // Validate query object
        if (rootNode.has("query")) {
            JsonNode queryNode = rootNode.get("query");
            if (!queryNode.isObject()) {
                errors.add("Query must be a JSON object");
            } else {
                // Check for invalid fields in query
                queryNode.fields().forEachRemaining(entry -> {
                    String fieldName = entry.getKey();
                    JsonNode fieldValue = entry.getValue();
                    
                    if (!hasField(LinqRequest.Query.class, fieldName)) {
                        errors.add("Invalid field in query object: '" + fieldName + "'. Valid fields are: " + getFieldNames(LinqRequest.Query.class));
                    } else if (fieldName.equals("workflow") && fieldValue.isArray()) {
                        // Validate each workflow step
                        for (int i = 0; i < fieldValue.size(); i++) {
                            final int stepIndex = i;
                            JsonNode stepNode = fieldValue.get(stepIndex);
                            if (!stepNode.isObject()) {
                                errors.add("Workflow step at index " + stepIndex + " must be an object");
                            } else {
                                // Check for invalid fields in workflow step
                                stepNode.fields().forEachRemaining(stepEntry -> {
                                    if (!hasField(LinqRequest.Query.WorkflowStep.class, stepEntry.getKey())) {
                                        errors.add("Invalid field in workflow step at index " + stepIndex + ": '" + stepEntry.getKey() + "'. Valid fields are: " + getFieldNames(LinqRequest.Query.WorkflowStep.class));
                                    }
                                });
                            }
                        }
                    }
                });
            }
        } else {
            errors.add("Request must contain 'query' field");
        }
    }

    private void validateRootNode(JsonNode rootNode, List<String> errors) {
        if (!rootNode.isObject()) {
            errors.add("Request must be a JSON object");
            return;
        }

        // Check for invalid fields at root level
        rootNode.fields().forEachRemaining(entry -> {
            if (!hasField(LinqRequest.class, entry.getKey())) {
                errors.add("Invalid field in root object: '" + entry.getKey() + "'. Valid fields are: " + getFieldNames(LinqRequest.class));
            }
        });

        // Validate link
        if (rootNode.has("link")) {
            JsonNode linkNode = rootNode.get("link");
            if (!linkNode.isObject()) {
                errors.add("Link must be a JSON object");
            } else {
                validateLinkNode(linkNode, errors);
            }
        } else {
            errors.add("Request must contain 'link' field");
        }

        // Validate query
        if (rootNode.has("query")) {
            JsonNode queryNode = rootNode.get("query");
            if (!queryNode.isObject()) {
                errors.add("Query must be a JSON object");
            } else {
                validateQueryNode(queryNode, errors);
            }
        } else {
            errors.add("Request must contain 'query' field");
        }
    }

    private void validateLinkNode(JsonNode linkNode, List<String> errors) {
        // Check for invalid fields
        linkNode.fields().forEachRemaining(entry -> {
            if (!hasField(LinqRequest.Link.class, entry.getKey())) {
                errors.add("Invalid field in link object: '" + entry.getKey() + "'. Valid fields are: " + getFieldNames(LinqRequest.Link.class));
            }
        });

        // Validate required fields
        if (!linkNode.has("target")) {
            errors.add("Link must contain 'target' field");
        }
        if (!linkNode.has("action")) {
            errors.add("Link must contain 'action' field");
        }
    }

    private void validateQueryNode(JsonNode queryNode, List<String> errors) {
        // Get all valid fields from the Query class
        List<String> validFields = new ArrayList<>();
        for (Field field : LinqRequest.Query.class.getDeclaredFields()) {
            validFields.add(field.getName());
        }
        log.info("Valid fields for Query: {}", validFields);

        // Check for invalid fields and validate their values
        queryNode.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();
            log.info("Checking field: {} with value: {}", fieldName, fieldValue);

            if (!validFields.contains(fieldName)) {
                log.info("Invalid field found: {}", fieldName);
                errors.add("Invalid field '" + fieldName + "' in query object. Valid fields are: " + String.join(", ", validFields));
            } else {
                // Validate field values based on their type
                switch (fieldName) {
                    case "intent":
                        // Allow null values, but if not null, ensure it's not an empty string
                        if (!fieldValue.isNull() && fieldValue.isTextual() && fieldValue.asText().trim().isEmpty()) {
                            errors.add("Field 'intent' cannot be empty if provided");
                        }
                        break;
                    case "params":
                        if (!fieldValue.isNull() && !fieldValue.isObject()) {
                            errors.add("Field 'params' must be an object");
                        }
                        break;
                    case "workflow":
                        if (!fieldValue.isNull() && !fieldValue.isArray()) {
                            errors.add("Field 'workflow' must be an array");
                        } else if (fieldValue.isArray()) {
                            validateWorkflowArray(fieldValue, errors);
                        }
                        break;
                    case "toolConfig":
                        if (!fieldValue.isNull() && !fieldValue.isObject()) {
                            errors.add("Field 'toolConfig' must be an object");
                        } else if (fieldValue.isObject()) {
                            validateToolConfig(fieldValue, -1, errors);
                        }
                        break;
                }
            }
        });

        // Check for required fields - removed since fields can be null
    }

    private void validateWorkflowArray(JsonNode workflowNode, List<String> errors) {
        for (int i = 0; i < workflowNode.size(); i++) {
            JsonNode stepNode = workflowNode.get(i);
            if (!stepNode.isObject()) {
                errors.add("Workflow step at index " + i + " must be a JSON object");
                continue;
            }
            validateWorkflowStep(stepNode, i, errors);
        }
    }

    private void validateWorkflowStep(JsonNode stepNode, int index, List<String> errors) {
        // Check for invalid fields
        stepNode.fields().forEachRemaining(entry -> {
            if (!hasField(LinqRequest.Query.WorkflowStep.class, entry.getKey())) {
                errors.add("Invalid field in workflow step " + (index + 1) + ": '" + entry.getKey() + "'. Valid fields are: " + getFieldNames(LinqRequest.Query.WorkflowStep.class));
            }
        });

        // Validate required fields
        if (!stepNode.has("step")) {
            errors.add("Workflow step " + (index + 1) + " must contain 'step' field");
        }
        if (!stepNode.has("target")) {
            errors.add("Workflow step " + (index + 1) + " must contain 'target' field");
        }
        if (!stepNode.has("action")) {
            errors.add("Workflow step " + (index + 1) + " must contain 'action' field");
        }
        if (!stepNode.has("intent")) {
            errors.add("Workflow step " + (index + 1) + " must contain 'intent' field");
        } else {
            JsonNode intentNode = stepNode.get("intent");
            if (intentNode.isNull() || (intentNode.isTextual() && intentNode.asText().trim().isEmpty())) {
                errors.add("Workflow step " + (index + 1) + " intent cannot be null or empty");
            }
        }

        // Validate toolConfig if present and not null
        if (stepNode.has("toolConfig") && !stepNode.get("toolConfig").isNull()) {
            JsonNode toolConfigNode = stepNode.get("toolConfig");
            if (!toolConfigNode.isObject()) {
                errors.add("ToolConfig in workflow step " + (index + 1) + " must be a JSON object");
            } else {
                validateToolConfig(toolConfigNode, index, errors);
            }
        }
    }

    private void validateToolConfig(JsonNode toolConfigNode, int stepIndex, List<String> errors) {
        // Check for invalid fields
        toolConfigNode.fields().forEachRemaining(entry -> {
            if (!hasField(LinqRequest.Query.ToolConfig.class, entry.getKey())) {
                errors.add("Invalid field in toolConfig of workflow step " + (stepIndex + 1) + ": '" + entry.getKey() + "'. Valid fields are: " + getFieldNames(LinqRequest.Query.ToolConfig.class));
            }
        });

        // Validate settings if present
        if (toolConfigNode.has("settings")) {
            JsonNode settingsNode = toolConfigNode.get("settings");
            if (!settingsNode.isObject()) {
                errors.add("Settings in toolConfig of workflow step " + (stepIndex + 1) + " must be a JSON object");
            } else {
                validateSettings(settingsNode, stepIndex, errors);
            }
        }
    }

    private void validateSettings(JsonNode settingsNode, int stepIndex, List<String> errors) {
        // For settings, we'll keep the hardcoded set since it's a Map<String, Object>
        List<String> validSettingsFields = List.of("temperature", "maxOutputTokens");

        // Check for invalid fields
        settingsNode.fields().forEachRemaining(entry -> {
            if (!validSettingsFields.contains(entry.getKey())) {
                errors.add("Invalid field in settings of workflow step " + (stepIndex + 1) + ": '" + entry.getKey() + "'. Valid fields are: " + String.join(", ", validSettingsFields));
            }
        });
    }

    private boolean hasField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName) != null;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private String getFieldNames(Class<?> clazz) {
        List<String> fieldNames = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            fieldNames.add(field.getName());
        }
        return String.join(", ", fieldNames);
    }
} 