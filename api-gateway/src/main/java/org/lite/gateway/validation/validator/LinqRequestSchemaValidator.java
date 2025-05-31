package org.lite.gateway.validation.validator;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LinqRequestSchemaValidator {

    public ValidationResult validateSchema(Object request) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        if (!(request instanceof Map)) {
            errors.add("Request must be a JSON object");
            result.setValid(false);
            result.setErrors(errors);
            return result;
        }

        Map<String, Object> requestMap = (Map<String, Object>) request;

        // Validate link
        if (!requestMap.containsKey("link")) {
            errors.add("Missing required field: link");
        } else {
            Object linkObj = requestMap.get("link");
            if (!(linkObj instanceof Map)) {
                errors.add("'link' must be a JSON object");
            } else {
                Map<String, Object> link = (Map<String, Object>) linkObj;
                if (!link.containsKey("target")) {
                    errors.add("Missing required field: link.target");
                }
                if (!link.containsKey("action")) {
                    errors.add("Missing required field: link.action");
                }
            }
        }

        // Validate query
        if (!requestMap.containsKey("query")) {
            errors.add("Missing required field: query");
        } else {
            Object queryObj = requestMap.get("query");
            if (!(queryObj instanceof Map)) {
                errors.add("'query' must be a JSON object");
            } else {
                Map<String, Object> query = (Map<String, Object>) queryObj;
                
                // Validate workflow steps if present
                if (query.containsKey("workflow")) {
                    Object workflowObj = query.get("workflow");
                    if (!(workflowObj instanceof List)) {
                        errors.add("'query.workflow' must be an array");
                    } else {
                        List<Object> workflow = (List<Object>) workflowObj;
                        for (int i = 0; i < workflow.size(); i++) {
                            Object stepObj = workflow.get(i);
                            if (!(stepObj instanceof Map)) {
                                errors.add(String.format("Workflow step %d must be a JSON object", i + 1));
                            } else {
                                Map<String, Object> step = (Map<String, Object>) stepObj;
                                if (!step.containsKey("target")) {
                                    errors.add(String.format("Missing required field: query.workflow[%d].target", i));
                                }
                                if (!step.containsKey("action")) {
                                    errors.add(String.format("Missing required field: query.workflow[%d].action", i));
                                }
                                if (!step.containsKey("intent")) {
                                    errors.add(String.format("Missing required field: query.workflow[%d].intent", i));
                                }
                            }
                        }
                    }
                }

                // Validate toolConfig if present
                if (query.containsKey("toolConfig")) {
                    Object toolConfigObj = query.get("toolConfig");
                    if (!(toolConfigObj instanceof Map)) {
                        errors.add("'query.toolConfig' must be a JSON object");
                    } else {
                        Map<String, Object> toolConfig = (Map<String, Object>) toolConfigObj;
                        if (!toolConfig.containsKey("model")) {
                            errors.add("Missing required field: query.toolConfig.model");
                        }
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            result.setValid(false);
            result.setErrors(errors);
        }

        return result;
    }
} 