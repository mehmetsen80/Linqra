package org.lite.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_NAME_HEADER = "X-API-Key-Name";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip API key for all non-linq
        if (!path.startsWith("/linq")) {
            return chain.filter(exchange);
        }

        // Check API Key and API Key name
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        String apiKeyName = exchange.getRequest().getHeaders().getFirst(API_KEY_NAME_HEADER);
        
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

        // Check Bearer Token - now required
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null) {
            log.warn("No Authorization header provided for path: {}", path);
            return Mono.error(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Bearer token is required"));
        }

        if (!authHeader.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format for path: {}", path);
            return Mono.error(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid Authorization header format. Must be 'Bearer token'"));
        }

        // Continue with validation
        return apiKeyService.validateApiKey(apiKey)
            .flatMap(validApiKey -> {
                // Validate API key name
                if (!apiKeyName.equals(validApiKey.getName())) {
                    log.warn("API key name mismatch. Expected: {}, Got: {}", 
                        validApiKey.getName(), apiKeyName);
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid API key name"));
                }

                // Validate client's Bearer token
                String token = authHeader.substring(7);
                if (!validateTeamInToken(validApiKey.getTeamId(), token)) {
                    log.warn("Team ID {} not found in token teams array for path: {}", 
                        validApiKey.getTeamId(), path);
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Team not authorized in token"));
                }

                return Mono.just(new UsernamePasswordAuthenticationToken(
                    validApiKey.getTeamId(),
                    apiKey,
                    List.of(new SimpleGrantedAuthority("ROLE_API_ACCESS"))
                ));
            })
            .flatMap(authentication -> 
                chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            )
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
                        e.getReason()
                    );
                    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(errorBody.getBytes());
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                }
                return Mono.empty();
            });
    }

    private boolean validateTeamInToken(String teamId, String token) {
        try {
            String[] chunks = token.split("\\.");
            String payload = new String(Base64.getDecoder().decode(chunks[1]));
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            if (jsonNode.has("teams") && jsonNode.get("teams").isArray()) {
                ArrayNode teamsArray = (ArrayNode) jsonNode.get("teams");
                for (JsonNode team : teamsArray) {
                    // Remove 'lm_' prefix from teamId if it exists
                    String normalizedTeamId = teamId.startsWith("lm_") ? 
                        teamId.substring(3) : teamId;
                    if (normalizedTeamId.equals(team.asText())) {
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