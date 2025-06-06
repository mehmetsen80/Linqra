package org.lite.gateway.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.KeycloakProperties;
import org.lite.gateway.dto.AuthResponse;
import org.lite.gateway.entity.User;
import org.lite.gateway.exception.InvalidAuthenticationException;
import org.lite.gateway.exception.TokenExpiredException;
import org.lite.gateway.repository.UserRepository;
import org.lite.gateway.service.CodeCacheService;
import org.lite.gateway.service.JwtService;
import org.lite.gateway.service.KeycloakService;
import org.lite.gateway.service.UserContextService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakServiceImpl implements KeycloakService {

    private final WebClient.Builder webClientBuilder;
    private final KeycloakProperties keycloakProperties;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final CodeCacheService codeCacheService;
    private final UserContextService userContextService;
    private final UserRepository userRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<AuthResponse> handleCallback(String code) {
        long start = System.currentTimeMillis();
        log.info("=== [Timing] SSO callback started at {} ===", start);

        return codeCacheService.isCodeUsed(code)
            .flatMap(isUsed -> {
                if (isUsed) {
                    log.debug("Code already used, checking for existing session");
                    return exchangeCodeForToken(code)
                        .flatMap(this::validateAndCreateSession)
                        .onErrorResume(error -> {
                            log.error("Error processing used code:", error);
                            return Mono.<AuthResponse>error(new InvalidAuthenticationException(
                                "Code already in use"
                            ));
                        });
                }
                return codeCacheService.markCodeAsUsed(code)
                    .flatMap(marked -> {
                        if (!marked) {
                            log.warn("Failed to mark code as used (race condition): {}", code);
                            return Mono.<AuthResponse>error(new InvalidAuthenticationException(
                                "Code already in use"
                            ));
                        }
                        return exchangeCodeForToken(code)
                            .flatMap(this::validateAndCreateSession);
                    });
            })
            .doOnSuccess(response -> log.info("=== [Timing] SSO callback finished in {} ms ===", System.currentTimeMillis() - start));
    }

    private Mono<KeycloakTokenResponse> exchangeCodeForToken(String code) {
        long start = System.currentTimeMillis();
        log.info("=== [Timing] Token exchange started at {} ===", start);

        return Mono.zip(
            keycloakProperties.getTokenUrl()
                .doOnError(e -> log.error("Failed to get token URL: {}", e.getMessage())),
            keycloakProperties.getClientId()
                .doOnError(e -> log.error("Failed to get client ID: {}", e.getMessage())),
            keycloakProperties.getClientSecret()
                .doOnError(e -> log.error("Failed to get client secret: {}", e.getMessage())),
            keycloakProperties.getRedirectUri()
                .doOnError(e -> log.error("Failed to get redirect URI: {}", e.getMessage()))
        )
        .doOnError(error -> log.error("Failed to get OAuth2 properties: {}", error.getMessage()))
        .flatMap(tuple -> {
            String tokenUrl = tuple.getT1();
            String clientId = tuple.getT2();
            String clientSecret = tuple.getT3();
            String redirectUri = tuple.getT4();
            
            log.info("Using redirect URI for token exchange: {}", redirectUri);

            log.info("\n=== OAuth Configuration ===\n" +
                "Token URL: {}\n" +
                "Client ID: {}\n" +
                "Client Secret (first 4 chars): {}\n" +
                "Redirect URI: {}", 
                tokenUrl,
                clientId,
                clientSecret.substring(0, Math.min(4, clientSecret.length())),
                redirectUri);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("code", code);
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            formData.add("redirect_uri", redirectUri);
            
            return webClientBuilder.build().post()
                .uri(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .bodyValue(formData)
                .exchangeToMono(response -> {
                    if (response.statusCode().is4xxClientError()) {
                        return response.bodyToMono(String.class)
                            .flatMap(error -> {
                                log.error("\n=== Token Request Failed ===\n" +
                                    "Status: {}\n" +
                                    "Error: {}\n" +
                                    "Headers: {}\n" +
                                    "Request Details:\n" +
                                    "  URL: {}\n" +
                                    "  Client ID: {}\n" +
                                    "  Redirect URI: {}\n" +
                                    "  Code Length: {}", 
                                    response.statusCode(), 
                                    error,
                                    response.headers().asHttpHeaders(),
                                    tokenUrl,
                                    clientId,
                                    redirectUri,
                                    code.length());
                                return Mono.error(new RuntimeException("Token request failed: " + error));
                            });
                    }
                    log.info("Token request successful");
                    return response.bodyToMono(String.class)
                        .doOnNext(json -> log.info("Raw token response: {}", json))
                        .flatMap(json -> {
                            try {
                                KeycloakTokenResponse tokenResponse = objectMapper.readValue(json, KeycloakTokenResponse.class);
                                log.info("Parsed token response - Has id_token: {}", tokenResponse.getIdToken() != null);
                                return Mono.just(tokenResponse);
                            } catch (Exception e) {
                                log.error("Failed to parse token response", e);
                                return Mono.error(e);
                            }
                        });
                })
                .doOnNext(response -> log.info("Successfully received token response"))
                .doOnError(error -> log.error("Error exchanging code for token", error))
                .doFinally(signal -> log.info("=== [Timing] Token exchange finished in {} ms ===", System.currentTimeMillis() - start));
        });
    }

    private Mono<AuthResponse> validateAndCreateSession(KeycloakTokenResponse tokenResponse) {
        long start = System.currentTimeMillis();
        log.info("=== [Timing] validateAndCreateSession started at {} ===", start);
        log.info("Token response contains id_token: {}", tokenResponse.getIdToken() != null);

        return jwtService.extractClaims(tokenResponse.getAccessToken())
            .flatMap(claims -> {
                String username = claims.get("preferred_username", String.class);
                String email = claims.get("email", String.class);
                
                //The roles are in the realm_access.roles claim and we
                List<String> roles = claims.get("realm_access", Map.class) != null ?
                    ((Map<String, List<String>>) claims.get("realm_access", Map.class)).get("roles") :
                    new ArrayList<>();

                Mono<AuthResponse> authResponseMono = userRepository.findByUsername(username)
                    .flatMap(existingUser -> {
                        // Case 2: User exists, create AuthResponse
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("username", existingUser.getUsername());
                        userMap.put("email", existingUser.getEmail());
                        userMap.put("roles", existingUser.getRoles());
                        userMap.put("id", existingUser.getId());
                        userMap.put("authType", "SSO");

                        AuthResponse response = AuthResponse.builder()
                            .token(tokenResponse.getAccessToken())
                            .refreshToken(tokenResponse.getRefreshToken())
                            .idToken(tokenResponse.getIdToken())
                            .user(userMap)
                            .message("Login successful")
                            .success(true)
                            .build();
                        
                        log.info("Created AuthResponse with id_token: {}", response.getIdToken() != null);
                        return Mono.just(response);
                    })
                    .switchIfEmpty(
                        // Case 1: User doesn't exist, create and save new user
                        Mono.just(new User())
                            .map(newUser -> {
                                newUser.setUsername(username);
                                newUser.setEmail(email);
                                newUser.setActive(true);
                                newUser.setRoles(Set.of("USER"));
                                newUser.setPassword(""); // Empty password for SSO users
                                return newUser;
                            })
                            .flatMap(userRepository::save)
                            .map(savedUser -> {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("username", savedUser.getUsername());
                                userMap.put("email", savedUser.getEmail());
                                userMap.put("roles", savedUser.getRoles());
                                userMap.put("id", savedUser.getId());
                                userMap.put("authType", "SSO");

                                AuthResponse response = AuthResponse.builder()
                                    .token(tokenResponse.getAccessToken())
                                    .refreshToken(tokenResponse.getRefreshToken())
                                    .idToken(tokenResponse.getIdToken())
                                    .user(userMap)
                                    .message("User created and logged in successfully")
                                    .success(true)
                                    .build();
                                
                                log.info("Created AuthResponse with id_token: {}", response.getIdToken() != null);
                                return response;
                            })
                    );

                return transactionalOperator.execute(status -> authResponseMono)
                    .single()
                    .doOnSuccess(response -> log.info("SSO transaction completed successfully"))
                    .doOnError(e -> log.error("SSO transaction failed: {}", e.getMessage()));
            })
            .doFinally(signal -> log.info("=== [Timing] validateAndCreateSession finished in {} ms ===", System.currentTimeMillis() - start));
    }

    private String extractEmailFromToken(String token) {
        try {
            String[] chunks = token.split("\\.");
            String payload = new String(Base64.getDecoder().decode(chunks[1]));
            JsonNode jsonNode = objectMapper.readTree(payload);
            return jsonNode.get("email").asText();
        } catch (Exception e) {
            log.error("Failed to extract email from token", e);
            return null;
        }
    }


    @Override
    public Mono<AuthResponse> refreshToken(String refreshToken) {
        log.info("Attempting to refresh token with Keycloak");
        
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Refresh token is required"));
        }

        // Verify it's actually a refresh token
        if (!userContextService.isRefreshToken(refreshToken)) {
            log.error("Invalid token type - expected refresh token");
            return Mono.just(AuthResponse.builder()
                .message("Invalid token type - expected refresh token")
                .success(false)
                .build());
        }

        return Mono.zip(
            keycloakProperties.getClientId(),
            keycloakProperties.getClientSecret(),
            keycloakProperties.getTokenUrl()
        ).flatMap(tuple -> {
            String clientId = tuple.getT1();
            String clientSecret = tuple.getT2();
            String tokenUrl = tuple.getT3();
            
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "refresh_token");
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            formData.add("refresh_token", refreshToken);

            return webClientBuilder.build()
                .post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .flatMap(error -> {
                            log.error("Keycloak refresh token error: {}", error);
                            if (error.contains("Token is not active")) {
                                return Mono.error(new TokenExpiredException("Refresh token has expired"));
                            }
                            return Mono.error(new RuntimeException("Token refresh failed: " + error));
                        })
                )
                .bodyToMono(KeycloakTokenResponse.class)
                .flatMap(response -> {
                    // Extract claims from the token
                    return jwtService.extractClaims(response.getAccessToken())
                        .map(claims -> {
                            String username = claims.get("preferred_username", String.class);
                            String email = claims.get("email", String.class);
                            List<String> roles = claims.get("realm_access", Map.class) != null ?
                                ((Map<String, List<String>>) claims.get("realm_access", Map.class)).get("roles") :
                                new ArrayList<>();

                            Map<String, Object> user = new HashMap<>();
                            user.put("username", username);
                            user.put("email", email);
                            user.put("roles", roles);

                            return AuthResponse.builder()
                                .token(response.getAccessToken())
                                .refreshToken(response.getRefreshToken())
                                .idToken(response.getIdToken())
                                .user(user)
                                .success(true)
                                .build();
                        });
                })
                .onErrorResume(e -> {
                    if (e instanceof TokenExpiredException) {
                        return Mono.just(AuthResponse.builder()
                            .message("Session expired. Please login again.")
                            .success(false)
                            .expired(true)
                            .build());
                    }
                    log.error("Token refresh failed", e);
                    return Mono.just(AuthResponse.builder()
                        .message(e.getMessage())
                        .success(false)
                        .build());
                });
        });
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record KeycloakTokenResponse(
    String accessToken,
    String refreshToken,
    String idToken,
    String tokenType,
    Integer expiresIn
) {
    @JsonProperty("access_token")
    public String getAccessToken() { return accessToken; }
    
    @JsonProperty("refresh_token")
    public String getRefreshToken() { return refreshToken; }
    
    @JsonProperty("id_token")
    public String getIdToken() { return idToken; }
    
    @JsonProperty("token_type")
    public String getTokenType() { return tokenType; }
    
    @JsonProperty("expires_in")
    public Integer getExpiresIn() { return expiresIn; }
} 