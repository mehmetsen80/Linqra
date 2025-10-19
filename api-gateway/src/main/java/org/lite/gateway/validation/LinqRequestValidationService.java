package org.lite.gateway.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.validation.annotations.Required;
import org.lite.gateway.validation.annotations.ValidStep;
import org.lite.gateway.validation.annotations.ValidLlmConfig;
import org.lite.gateway.validation.annotations.ValidAction;
import org.lite.gateway.validation.validator.StepValidator;
import org.lite.gateway.validation.validator.LlmConfigValidator;
import org.lite.gateway.validation.validator.ActionValidator;
import org.lite.gateway.validation.validator.LinqRequestSchemaValidator;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqRequestValidationService {
    private final ActionValidator actionValidator;
    private final StepValidator stepValidator;
    private final LlmConfigValidator llmConfigValidator;
    private final LinqRequestSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    private static final String OUR_PACKAGE_PREFIX = "org.lite.gateway";

    public ValidationResult validate(Object request) {
        log.info("Starting validation for request: {}", request);
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        // Validate schema first
        ValidationResult schemaResult = schemaValidator.validateSchema(request);
        log.info("Schema validation result: {}", schemaResult);
        if (!schemaResult.isValid()) {
            log.info("Schema validation failed: {}", schemaResult.getErrors());
            return schemaResult;
        }

        // Then validate the object structure
        try {
            validateObject(request, errors);
        } catch (IllegalAccessException e) {
            log.error("Error during validation: ", e);
            errors.add("Error during validation: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            result.setValid(false);
            result.setErrors(errors);
        }

        log.info("Validation completed. Result: {}", result);
        return result;
    }

    private void validateObject(Object obj, List<String> errors) throws IllegalAccessException {
        if (obj == null) return;

        Class<?> clazz = obj.getClass();
        // Skip validation for non-our-package classes
        if (!clazz.getPackageName().startsWith(OUR_PACKAGE_PREFIX)) {
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(obj);

            // Check for @Required annotation
            if (field.isAnnotationPresent(Required.class)) {
                Required required = field.getAnnotation(Required.class);
                if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                    log.info("Required field validation failed for: {}", field.getName());
                    errors.add(required.message());
                }
            }

            // Check for @ValidAction annotation
            if (field.isAnnotationPresent(ValidAction.class)) {
                if (value instanceof String) {
                    log.info("Validating action: {}", value);
                    ValidationResult actionResult = actionValidator.validateAction((String) value);
                    if (!actionResult.isValid()) {
                        log.info("Action validation failed: {}", actionResult.getErrors());
                        errors.addAll(actionResult.getErrors());
                    }
                }
            }

            // Check for @ValidStep annotation
            if (field.isAnnotationPresent(ValidStep.class)) {
                if (value instanceof List) {
                    log.info("Validating workflow steps");
                    List<?> steps = (List<?>) value;
                    
                    // First validate the steps list using StepValidator
                    ValidationResult stepResult = stepValidator.validateSteps((List<LinqRequest.Query.WorkflowStep>) value);
                    if (!stepResult.isValid()) {
                        log.info("Step validation failed: {}", stepResult.getErrors());
                        errors.addAll(stepResult.getErrors());
                    }
                    
                    // Then validate each step's fields recursively
                    for (int i = 0; i < steps.size(); i++) {
                        Object step = steps.get(i);
                        if (step != null) {
                            validateObject(step, errors);
                        }
                    }
                }
            }

            // Check for @ValidLlmConfig annotation
            if (field.isAnnotationPresent(ValidLlmConfig.class)) {
                if (value instanceof LinqRequest.Query.LlmConfig) {
                    log.info("Validating LLM config");
                    ValidationResult llmConfigResult = llmConfigValidator.validateLlmConfig((LinqRequest.Query.LlmConfig) value, 0);
                    if (!llmConfigResult.isValid()) {
                        log.info("LLM config validation failed: {}", llmConfigResult.getErrors());
                        errors.addAll(llmConfigResult.getErrors());
                    }
                }
            }

            // Recursively validate nested objects
            if (value != null && !value.getClass().isPrimitive() && value.getClass().getPackageName().startsWith(OUR_PACKAGE_PREFIX)) {
                validateObject(value, errors);
            }
        }
    }
} 