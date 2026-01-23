package org.lite.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ApiKeyPair;
import org.lite.gateway.service.ApiKeyService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Base64;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter implements WebFilter {
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;
    private static final String API_KEY_HEADER = "x-api-key";
    private static final String API_KEY_NAME_HEADER = "x-api-key-name";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    // Allowed Web UI origins
    private static final java.util.List<String> WEB_UI_ORIGINS = java.util.List.of(
            "https://localhost:3000",
            "http://localhost:3000",
            "https://linqra.com",
            "https://www.linqra.com",
            "https://app.linqra.com");

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip API key for all non-linq, non-route, and non-agent-tasks paths
        // Also skip for MinIO bucket paths (knowledge-hub, audit)
        if ((!path.startsWith("/linq") || path.contains("knowledge-hub") || path.contains("audit"))
                && !path.startsWith("/api/agent-tasks/")
                && (path.contains("/whatsapp/webhook") || !path.startsWith("/r/"))) {
            return chain.filter(exchange);
        }

        // Skip API key for health endpoints - these should be completely public
        if (path.endsWith("/health") || path.endsWith("/health/")) {
            return chain.filter(exchange);
        }

        // Check if this is a Web UI request by validating Origin/Referer headers
        // Web UI (browsers) automatically send Origin/Referer, external APIs typically
        // don't
        String origin = exchange.getRequest().getHeaders().getFirst("origin");
        String referer = exchange.getRequest().getHeaders().getFirst("referer");
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);

        // If request has Authorization token AND valid Origin/Referer, it's from Web UI
        if (authHeader != null && !authHeader.isEmpty() && isFromWebUI(origin, referer)) {
            log.debug(
                    "Web UI request detected (has Authorization + valid origin/referer), skipping API key for path: {}",
                    path);
            return chain.filter(exchange);
        }

        // For external API/SDK/Postman requests, API key is required
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        String apiKeyName = exchange.getRequest().getHeaders().getFirst(API_KEY_NAME_HEADER);
        log.info("My Exchange Request Headers: ");
        log.info(exchange.getRequest().getHeaders().toString());

        if (apiKey == null) {
            log.warn("No API key provided for path: {}", path);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "API key is required"));
        }

        if (apiKeyName == null) {
            log.warn("No API key name provided for path: {}", path);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "API key name is required"));
        }

        // Check Bearer Token - Optional for pure API Key access
        // logic:
        // 1. If no Auth header -> Validate API Key only (API Key Mode)
        // 2. If Auth header exists -> Validate API Key AND Token (Hybrid Mode)

        return apiKeyService.validateApiKey(apiKey)
                .flatMap(validApiKey -> {
                    // Validate API key name
                    if (!apiKeyName.equals(validApiKey.getName())) {
                        log.warn("API key name mismatch. Expected: {}, Got: {}",
                                validApiKey.getName(), apiKeyName);
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Invalid API key name"));
                    }

                    // If Authorization header is present, validate it (Hybrid Mode)
                    if (authHeader != null && !authHeader.isEmpty()) {
                        if (!authHeader.startsWith("Bearer ")) {
                            log.warn("Invalid Authorization header format for path: {}", path);
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED,
                                    "Invalid Authorization header format. Must be 'Bearer token'"));
                        }

                        String token = authHeader.substring(7);
                        if (!validateTeamInToken(validApiKey.getTeamId(), token)) {
                            log.warn("Team ID {} not found in token teams array for path: {}",
                                    validApiKey.getTeamId(), path);
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "Team not authorized in token"));
                        }
                    }

                    // Construct Authentication object
                    // If pure API Key mode, we rely on the API Key's teamId as the principal source
                    return Mono.just(new UsernamePasswordAuthenticationToken(
                            validApiKey.getTeamId(), // Principal is Team ID
                            new ApiKeyPair(apiKey, apiKeyName),
                            List.of(new SimpleGrantedAuthority("ROLE_API_ACCESS"))));
                })
                .flatMap(authentication -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid API key")))
                .onErrorResume(ResponseStatusException.class, e -> {
                    if (!exchange.getResponse().isCommitted()) {
                        exchange.getResponse().setStatusCode(e.getStatusCode());
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        String errorBody = String.format(
                                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                                e.getStatusCode().value(),
                                e.getStatusCode().toString(),
                                e.getReason());
                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(errorBody.getBytes());
                        return exchange.getResponse().writeWith(Mono.just(buffer));
                    }
                    return Mono.empty();
                });
    }

    /**
     * Helper method to check if the request is from an allowed Web UI origin
     */
    private boolean isFromWebUI(String origin, String referer) {
        // Check Origin header
        if (origin != null) {
            for (String webOrigin : WEB_UI_ORIGINS) {
                if (origin.equalsIgnoreCase(webOrigin)) {
                    return true;
                }
            }
        }

        // Check Referer header
        if (referer != null) {
            for (String webOrigin : WEB_UI_ORIGINS) {
                if (referer.startsWith(webOrigin)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean validateTeamInToken(String teamId, String token) {
        try {
            String[] chunks = token.split("\\.");
            String payload = new String(Base64.getDecoder().decode(chunks[1]));
            JsonNode jsonNode = objectMapper.readTree(payload);

            // First check if teamId claim exists and matches
            if (jsonNode.has("teamId")) {
                String tokenTeamId = jsonNode.get("teamId").asText();
                if (teamId.equals(tokenTeamId)) {
                    return true;
                }
            }

            // Then check teams array for backward compatibility
            if (jsonNode.has("teams")) {
                JsonNode teamsNode = jsonNode.get("teams");

                // Handle case where teams can be an array
                if (teamsNode.isArray()) {
                    ArrayNode teamsArray = (ArrayNode) teamsNode;
                    for (JsonNode team : teamsArray) {
                        // Remove 'lm_' prefix from teamId if it exists
                        String normalizedTeamId = teamId.startsWith("lm_") ? teamId.substring(3) : teamId;
                        if (normalizedTeamId.equals(team.asText())) {
                            return true;
                        }
                    }
                } else if (teamsNode.isTextual()) {
                    // Handle case where teams is a single string
                    String normalizedTeamId = teamId.startsWith("lm_") ? teamId.substring(3) : teamId;
                    if (normalizedTeamId.equals(teamsNode.asText())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error validating team in token: {}", e.getMessage());
            return false;
        }
    }
}