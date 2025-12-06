package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.annotation.AuditLog;
import org.lite.gateway.dto.*;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.service.JwtService;
import org.lite.gateway.service.KeycloakService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final KeycloakService keycloakService;
    private final JwtService jwtService;
    private final UserContextService userContextService;

    @PostMapping("/login")
    @AuditLog(
        eventType = AuditEventType.USER_LOGIN,
        action = AuditActionType.READ,
        resourceType = AuditResourceType.AUTH,
        reason = "User login attempt",
        logOnSuccessOnly = false  // Log both success and failure
    )
    public Mono<ResponseEntity<AuthResponse>> login(
            @RequestBody LoginRequest request,
            ServerWebExchange exchange) {
        return userService.login(request)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest()
                .body(AuthResponse.builder()
                    .message(e.getMessage())
                    .build())));
    }

    @PostMapping("/register")
    @AuditLog(
        eventType = AuditEventType.USER_CREATED,
        action = AuditActionType.CREATE,
        resourceType = AuditResourceType.USER,
        reason = "User registration",
        logOnSuccessOnly = false  // Log both success and failure
    )
    public Mono<ResponseEntity<AuthResponse>> register(
            @RequestBody RegisterRequest request,
            ServerWebExchange exchange) {
        return userService.register(request)
            .doOnSuccess(response -> log.info("Registration successful for user: {}", request.getUsername()))
            .doOnError(e -> log.error("Registration failed: {}", e.getMessage()))
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                log.error("Error during registration", e);
                return Mono.just(ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                        .success(false)
                        .message(e.getMessage())
                        .build()));
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/sso/callback")
    public Mono<ResponseEntity<Object>> handleCallback(@RequestBody KeycloakCallbackRequest request) {
        log.info("=== SSO Callback Request Received ===");
        log.info("Request code: {}", request.code());
        
        return keycloakService.handleCallback(request.code())
                .doOnSubscribe(s -> log.info("Starting SSO callback processing"))
                .doOnSuccess(response -> {
                    log.info("SSO callback successful");
                    log.info("Response contains id_token: {}", response.getIdToken() != null);
                    log.info("Response user: {}", response.getUser());
                })
                .doOnError(error -> {
                    log.error("SSO callback failed", error);
                    log.error("Error details: {}", error.getMessage());
                })
                .<ResponseEntity<Object>>map(response -> {
                    log.info("Mapping response to ResponseEntity");
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Error processing SSO callback", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ErrorResponse.builder()
                                    .code("AUTHENTICATION_ERROR")
                                    .message(e.getMessage())
                                    .details(Map.of("status", HttpStatus.BAD_REQUEST.value()))
                                    .build()));
                });
    }

    @PostMapping("/refresh")
    @AuditLog(
        eventType = AuditEventType.TOKEN_REFRESHED,
        action = AuditActionType.READ,
        resourceType = AuditResourceType.AUTH,
        reason = "Token refresh requested"
    )
    public Mono<ResponseEntity<AuthResponse>> refreshToken(
            @RequestBody RefreshTokenRequest request,
            ServerWebExchange exchange) {
        boolean isKeycloakToken = userContextService.isKeycloakToken(request.getRefresh_token());
        log.info("Token type: {}", isKeycloakToken ? "Keycloak" : "Standard");

        Mono<AuthResponse> refreshMono = isKeycloakToken ?
            keycloakService.refreshToken(request.getRefresh_token()) :
            userService.refreshToken(request.getRefresh_token());

        return refreshMono
            .doOnNext(response -> log.info("Refresh response: {}", response))
            .flatMap(authResponse -> {
                if (authResponse.isExpired()) {
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(authResponse));
                }
                
                if (authResponse.getToken() == null) {
                    return Mono.just(ResponseEntity.badRequest()
                        .body(AuthResponse.builder()
                            .message("Failed to refresh token")
                            .success(false)
                            .build()));
                }

                return jwtService.extractClaims(authResponse.getToken())
                    .flatMap(claims -> {
                        String username = claims.get("preferred_username", String.class);
                        String email = claims.get("email", String.class);
                        
                        // Get roles from claims, handling both Keycloak and standard format
                        List<String> roles;
                        if (claims.get("realm_access", Map.class) != null) {
                            // Keycloak format
                            @SuppressWarnings("unchecked")
                            Map<String, List<String>> realmAccess = (Map<String, List<String>>) claims.get("realm_access", Map.class);
                            roles = realmAccess.get("roles");
                        } else {
                            // Standard format
                            @SuppressWarnings("unchecked")
                            List<String> standardRoles = (List<String>) claims.get("roles");
                            roles = standardRoles;
                        }
                        
                        if (roles == null) {
                            roles = new ArrayList<>();
                        }
                        
                        Map<String, Object> user = new HashMap<>();
                        user.put("username", username);
                        user.put("email", email);
                        user.put("roles", roles);

                        return Mono.just(ResponseEntity.ok(AuthResponse.builder()
                            .token(authResponse.getToken())
                            .refreshToken(authResponse.getRefreshToken())
                            .user(user)
                            .success(true)
                            .build()));
                    });
            })
            .doOnError(e -> log.error("Token refresh error", e));
    }

    @PostMapping("/test-refresh")
    public Mono<ResponseEntity<AuthResponse>> testRefresh(
            @RequestBody RefreshTokenRequest request,
            ServerWebExchange exchange) {
        log.info("Testing token refresh with token: {}", 
            request.getRefresh_token() != null ? request.getRefresh_token().substring(0, 10) + "..." : "null");
        return refreshToken(request, exchange);
    }

    @PostMapping("/switch-team")
    @AuditLog(
        eventType = AuditEventType.TEAM_SWITCHED,
        action = AuditActionType.UPDATE,
        resourceType = AuditResourceType.TEAM,
        reason = "User switched teams"
    )
    public Mono<ResponseEntity<AuthResponse>> switchTeam(
            @RequestBody SwitchTeamRequest request,
            ServerWebExchange exchange) {
        log.info("Switching team for user: {} to team: {}", request.getUsername(), request.getTeamId());
        
        return userService.findByUsername(request.getUsername())
            .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
            .flatMap(user -> {
                // Generate new token with team information
                String newToken = jwtService.generateTokenWithTeam(request.getUsername(), request.getTeamId());
                String refreshToken = jwtService.generateRefreshToken(request.getUsername());
                
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("username", user.getUsername());
                userMap.put("email", user.getEmail());
                userMap.put("roles", user.getRoles());
                
                AuthResponse response = AuthResponse.builder()
                    .token(newToken)
                    .refreshToken(refreshToken)
                    .user(userMap)
                    .success(true)
                    .message("Team switched successfully")
                    .build();
                
                log.info("Successfully switched team for user: {} to team: {}", request.getUsername(), request.getTeamId());
                return Mono.just(ResponseEntity.ok(response));
            })
            .onErrorResume(e -> {
                log.error("Error switching team: {}", e.getMessage());
                return Mono.just(ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                        .success(false)
                        .message("Failed to switch team: " + e.getMessage())
                        .build()));
            });
    }
} 