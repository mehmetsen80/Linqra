package org.lite.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.lite.gateway.exception.InvalidAuthenticationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamContextService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReactiveJwtDecoder userJwtDecoder;
    private final ReactiveJwtDecoder keycloakJwtDecoder;
    private final UserContextService userContextService;

    /**
     * Get team from security context (for service layer use)
     */
    public Mono<String> getTeamFromContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof Jwt jwt) {
                        // First try to get teamId from the claim
                        String teamId = jwt.getClaimAsString("teamId");
                        // log.info("Extracted teamId from JWT claim: {}", teamId);
                        if (teamId != null) {
                            return Mono.just(teamId);
                        }

                        // If teamId is not found, try to get it from the teams array
                        try {
                            String tokenValue = jwt.getTokenValue();
                            String[] chunks = tokenValue.split("\\.");
                            String payload = new String(Base64.getDecoder().decode(chunks[1]));
                            JsonNode jsonNode = objectMapper.readTree(payload);
                            // log.info("Decoded token payload: {}", payload);

                            if (jsonNode.has("teams")) {
                                JsonNode teamsNode = jsonNode.get("teams");

                                // Handle case where teams is an array
                                if (teamsNode.isArray() && !teamsNode.isEmpty()) {
                                    // Return the first team from the array, removing 'lm_' prefix if it exists
                                    String firstTeam = teamsNode.get(0).asText();
                                    String normalizedTeam = firstTeam.startsWith("lm_") ? firstTeam.substring(3)
                                            : firstTeam;
                                    // log.info("Extracted first team from teams array: {}", normalizedTeam);
                                    return Mono.just(normalizedTeam);
                                } else if (teamsNode.isTextual()) {
                                    // Handle case where teams is a single string, removing 'lm_' prefix if it
                                    // exists
                                    String teamValue = teamsNode.asText();
                                    String normalizedTeam = teamValue.startsWith("lm_") ? teamValue.substring(3)
                                            : teamValue;
                                    // log.info("Extracted team from teams string: {}", normalizedTeam);
                                    return Mono.just(normalizedTeam);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error parsing token to extract teamId: {}", e.getMessage(), e);
                        }

                        return Mono.error(new InvalidAuthenticationException(
                                "No team ID found in token (neither teamId claim nor teams array)"));
                    } else if (principal instanceof String teamId) {
                        if (teamId.trim().isEmpty()) {
                            return Mono.error(new InvalidAuthenticationException("No team ID found in principal"));
                        }
                        log.debug("Extracted teamId from String principal: {}", teamId);
                        return Mono.just(teamId);
                    }
                    log.error("Invalid authentication principal type: {}", principal.getClass().getName());
                    return Mono.error(new InvalidAuthenticationException("Invalid authentication principal"));
                });
    }

    /**
     * Get team from context with ServerWebExchange support (reads X-User-Token
     * first)
     */
    public Mono<String> getTeamFromContext(ServerWebExchange exchange) {
        String userToken = exchange.getRequest().getHeaders().getFirst("X-User-Token");
        String requestedTeamId = exchange.getRequest().getHeaders().getFirst("X-Team-ID");

        // If X-User-Token exists, decode it and extract teamId
        if (userToken != null) {
            String token = userToken.startsWith("Bearer ") ? userToken.substring(7) : userToken;
            boolean isKeycloakToken = userContextService.isKeycloakToken(token);
            ReactiveJwtDecoder decoder = isKeycloakToken ? keycloakJwtDecoder : userJwtDecoder;

            return decoder.decode(token)
                    .flatMap(jwt -> {
                        // 1. If explicit X-Team-ID header is present, try to validate and use it
                        if (requestedTeamId != null && !requestedTeamId.isBlank()) {
                            // Verify the user is actually a member of this team
                            if (isTeamInToken(jwt, requestedTeamId)) {
                                log.debug("Using requested teamId from X-Team-ID header: {}", requestedTeamId);
                                return Mono.just(requestedTeamId);
                            } else {
                                log.warn("User requested teamId {} but is not a member of it according to token",
                                        requestedTeamId);
                                return Mono.error(new InvalidAuthenticationException(
                                        "User is not authorized for the requested team"));
                            }
                        }

                        // 2. Try to get teamId from the claim (Active Team in Token)
                        String teamId = jwt.getClaimAsString("teamId");
                        // log.info("Extracted teamId from X-User-Token: {}", teamId);
                        if (teamId != null) {
                            return Mono.just(teamId);
                        }

                        // 3. Fallback: Default to first team in list
                        log.debug("No X-Team-ID header and no teamId claim, falling back to default team logic");
                        return getTeamFromContext();
                    })
                    .onErrorResume(error -> {
                        log.error("Error decoding X-User-Token: {}", error.getMessage());
                        return getTeamFromContext();
                    });
        }

        // No X-User-Token, fall back to Authorization header
        log.info("No X-User-Token found, using Authorization header");
        return getTeamFromContext();
    }

    /**
     * Helper to check if a specific teamId is in the token's authorized list
     */
    private boolean isTeamInToken(Jwt jwt, String teamId) {
        try {
            // Check direct teams claim if array
            if (jwt.hasClaim("teams")) {
                java.util.List<String> teams = jwt.getClaimAsStringList("teams");
                // Handle the "lm_" prefix logic just in case the input or token differs
                String normalizedTarget = teamId.startsWith("lm_") ? teamId.substring(3) : teamId;

                for (String t : teams) {
                    String normalizedTokenTeam = t.startsWith("lm_") ? t.substring(3) : t;
                    if (normalizedTokenTeam.equals(normalizedTarget)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse teams list from token for validation", e);
        }
        return false;
    }
}
