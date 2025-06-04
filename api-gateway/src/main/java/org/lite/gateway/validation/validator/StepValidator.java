package org.lite.gateway.validation.validator;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class StepValidator {

    public ValidationResult validateSteps(List<LinqRequest.Query.WorkflowStep> steps) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        if (steps == null || steps.isEmpty()) {
            errors.add("At least one workflow step is required");
            result.setValid(false);
            result.setErrors(errors);
            return result;
        }

        // Check for duplicate step numbers
        Set<Integer> stepNumbers = new HashSet<>();
        for (LinqRequest.Query.WorkflowStep step : steps) {
            if (step.getStep() <= 0) {
                errors.add("Step number must be greater than 0");
            } else if (!stepNumbers.add(step.getStep())) {
                errors.add("Duplicate step number: " + step.getStep());
            }
        }

        // Validate each step
        for (int i = 0; i < steps.size(); i++) {
            LinqRequest.Query.WorkflowStep step = steps.get(i);
            
            if (step.getTarget() == null || step.getTarget().trim().isEmpty()) {
                errors.add("Step " + (i + 1) + ": Target is required");
            }
            
            if (step.getAction() == null || step.getAction().trim().isEmpty()) {
                errors.add("Step " + (i + 1) + ": Action is required");
            }
            
            if (step.getIntent() == null || step.getIntent().trim().isEmpty()) {
                errors.add("Step " + (i + 1) + ": Intent is required");
            }
        }

        if (!errors.isEmpty()) {
            result.setValid(false);
            result.setErrors(errors);
        }

        return result;
    }
} 