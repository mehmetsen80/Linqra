package org.lite.gateway.validation;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ValidationResult {
    private boolean valid = true;
    private List<String> errors = new ArrayList<>();

    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }
} 