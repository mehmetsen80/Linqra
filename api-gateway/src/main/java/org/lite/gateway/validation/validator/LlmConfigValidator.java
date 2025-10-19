package org.lite.gateway.validation.validator;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LlmConfigValidator {

    public ValidationResult validateLlmConfig(LinqRequest.Query.LlmConfig llmConfig, int stepNumber) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        if (llmConfig == null) {
            errors.add("LLM configuration is required");
            result.setValid(false);
            result.setErrors(errors);
            return result;
        }

        if (llmConfig.getModel() == null || llmConfig.getModel().trim().isEmpty()) {
            errors.add("Model is required in LLM configuration");
        }

        if (llmConfig.getSettings() != null) {
            Map<String, Object> settings = llmConfig.getSettings();
            
            // Validate temperature if present
            if (settings.containsKey("temperature")) {
                Object temp = settings.get("temperature");
                if (temp instanceof Number) {
                    double temperature = ((Number) temp).doubleValue();
                    if (temperature < 0 || temperature > 1) {
                        errors.add("Temperature must be between 0 and 1");
                    }
                } else {
                    errors.add("Temperature must be a number");
                }
            }

            // Validate max_tokens if present
            if (settings.containsKey("max_tokens")) {
                Object maxTokens = settings.get("max_tokens");
                if (maxTokens instanceof Number) {
                    int tokens = ((Number) maxTokens).intValue();
                    if (tokens <= 0) {
                        errors.add("max_tokens must be greater than 0");
                    }
                } else {
                    errors.add("max_tokens must be a number");
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