package org.lite.gateway.service;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;

import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ApiKeyContextService {
    public Mono<String> getApiKeyFromContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof UsernamePasswordAuthenticationToken)
                .map(auth -> {
                    Object credentials = auth.getCredentials();
                    if (credentials instanceof String) {
                        log.debug("Found API key in security context");
                        return (String) credentials;
                    }
                    log.error("Invalid credentials type in security context: {}",
                            credentials != null ? credentials.getClass() : "null");
                    throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Invalid API key format");
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "No valid API key authentication found")));
    }
    
}
