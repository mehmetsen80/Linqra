package org.lite.gateway.validation.validator;

import org.lite.gateway.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class ActionValidator {
    private static final Set<String> VALID_ACTIONS = new HashSet<>(Arrays.asList(
        "delete", "fetch", "create", "update", "patch", "options", "head", "generate", "execute"
    ));

    public ValidationResult validateAction(String action) {
        ValidationResult result = new ValidationResult();
        
        if (action == null || action.isEmpty()) {
            result.addError("Action is required");
            return result;
        }

        if (!VALID_ACTIONS.contains(action.toLowerCase())) {
            result.addError("Invalid action: " + action + ". Must be one of: " + String.join(", ", VALID_ACTIONS));
        }

        return result;
    }
} 