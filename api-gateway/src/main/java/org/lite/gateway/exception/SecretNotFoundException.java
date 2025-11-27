package org.lite.gateway.exception;

/**
 * Exception thrown when a secret is not found in the vault
 */
public class SecretNotFoundException extends RuntimeException {
    
    public SecretNotFoundException(String key, String environment) {
        super(String.format("Secret '%s' not found for environment: %s", key, environment));
    }
    
    public SecretNotFoundException(String message) {
        super(message);
    }
}

