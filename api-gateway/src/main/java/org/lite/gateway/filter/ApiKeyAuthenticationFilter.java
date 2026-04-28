package org.lite.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ApiKeyPair;
import org.lite.gateway.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter implements WebFilter {
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Value("${linqra.base-url:https://linqra.com}")
    private String baseUrl;

    private static final String API_KEY_HEADER = "x-api-key";
    private static final String API_KEY_NAME_HEADER = "x-api-key-name";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    // Allowed Web UI origins
    private static final java.util.List<String> WEB_UI_ORIGINS = java.util.List.of(
            "https://localhost:3000",
            "http://localhost:3000",
            "https://localhost:4000",
            "http://localhost:4000",
            "http://localhost:5001",
            "https://linqra.com",
            "https://www.linqra.com",
            "https://app.linqra.com",
            "https://advising.linqra.com");

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        // [REQUIRED FOR CORS] Skip API key for OPTIONS pre-flight requests
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();

        // [HEALTH CHECK BYPASS] Skip API key for health endpoints IMMEDIATELY
        // This ensures the LoadBalancer doesn't mark services as DOWN
        if (path.endsWith("/health") || path.endsWith("/health/")) {
            return chain.filter(exchange);
        }

        log.info("ApiKey Filter checking path: {}", path);

        // [ADMIN EARLY BYPASS] If an administrator token is present, skip all API
        // key/WebUI checks
        // AND populate the SecurityContext so that subsequent DynamicPathAuthorization
        // can see it
        String authHeaderValue = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeaderValue != null && authHeaderValue.startsWith("Bearer ")) {
            JsonNode payload = decodeTokenPayload(authHeaderValue.substring(7));
            if (isAdmin(payload)) {
                log.info("Administrator identified via early bypass for path: {}", path);

                // Create a synthetic authentication object with ROLE_GATEWAY_ADMIN
                UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                        payload.path("sub").asText("admin"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_GATEWAY_ADMIN")));

                // Set the security context and continue the chain
                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(adminAuth));
            }
        }

        // [PUBLIC PATH BYPASS] Skip API key for public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check if this is a Web UI request by validating Origin/Referer headers
        // Web UI (browsers) automatically send Origin/Referer, external APIs typically
        // don't
        String origin = exchange.getRequest().getHeaders().getFirst("origin");
        String referer = exchange.getRequest().getHeaders().getFirst("referer");
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);

        // [INTERNAL SERVICE BYPASS] Trust internal calls from other backend services
        String xServiceName = exchange.getRequest().getHeaders().getFirst("X-Service-Name");
        if (xServiceName != null && !xServiceName.isEmpty()) {
            log.info("Internal service-to-service request detected from: {}, bypassing API key check", xServiceName);
            return chain.filter(exchange);
        }

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

        // If no keys provided, check if it's a mandatory path
        // Paths starting with /api/tools/ are NOT mandatory here because the controller
        // handles visibility
        if (apiKey == null) {
            if (path.startsWith("/api/tools/")) {
                return chain.filter(exchange);
            }
            log.warn("No API key provided for path: {}", path);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "API key is required"));
        }

        if (apiKeyName == null) {
            if (path.startsWith("/api/tools/")) {
                return chain.filter(exchange);
            }
            log.warn("No API key name provided for path: {}", path);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "API key name is required"));
        }

        // Check Bearer Token - Optional for pure API Key access
        // logic:
        // 1. If no Auth header -> Validate API Key only (API Key Mode)
        // 2. If Auth header exists -> Validate API Key AND Token (Hybrid Mode)

        final boolean[] isAdminFlag = { false };

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
                        JsonNode payload = decodeTokenPayload(token);

                        if (payload != null && isAdmin(payload)) {
                            log.info("Administrator identified: bypassing team validation for API key for path: {}",
                                    path);
                            isAdminFlag[0] = true;
                        } else if (!validateTeamInToken(validApiKey.getTeamId(), payload)) {
                            log.warn("Team ID {} not found in token teams array for path: {}",
                                    validApiKey.getTeamId(), path);
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.FORBIDDEN, "Team not authorized in token"));
                        }
                    }

                    // Construct Authentication object
                    // If pure API Key mode, we rely on the API Key's teamId as the principal source
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_API_ACCESS"));
                    if (isAdminFlag[0]) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_GATEWAY_ADMIN"));
                    }

                    return Mono.just(new UsernamePasswordAuthenticationToken(
                            validApiKey.getTeamId(), // Principal is Team ID
                            new ApiKeyPair(apiKey, apiKeyName),
                            authorities));
                })
                .flatMap(authentication -> {
                    // Stash the Team ID in attributes in case SecurityContext is overwritten by
                    // OAuth2 filter later
                    exchange.getAttributes().put("API_KEY_TEAM_ID", authentication.getPrincipal());
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
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
            if (origin.equalsIgnoreCase(baseUrl)) {
                return true;
            }
            for (String webOrigin : WEB_UI_ORIGINS) {
                if (origin.equalsIgnoreCase(webOrigin)) {
                    return true;
                }
            }
        }

        // Check Referer header
        if (referer != null) {
            if (referer.startsWith(baseUrl)) {
                return true;
            }
            for (String webOrigin : WEB_UI_ORIGINS) {
                if (referer.startsWith(webOrigin)) {
                    return true;
                }
            }
        }

        return false;
    }

    private JsonNode decodeTokenPayload(String token) {
        try {
            String[] chunks = token.split("\\.");
            if (chunks.length < 2)
                return null;
            String payload = new String(Base64.getDecoder().decode(chunks[1]));
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Error decoding token payload: {}", e.getMessage());
            return null;
        }
    }

    private boolean isAdmin(JsonNode jsonNode) {
        if (jsonNode == null)
            return false;

        // Check realm roles
        if (jsonNode.has("realm_access")) {
            JsonNode roles = jsonNode.get("realm_access").get("roles");
            if (roles != null && roles.isArray()) {
                for (JsonNode role : roles) {
                    if ("gateway_admin_realm".equals(role.asText())) {
                        return true;
                    }
                }
            }
        }

        // Check resource roles
        if (jsonNode.has("resource_access")) {
            JsonNode clientAccess = jsonNode.get("resource_access").get("linqra-gateway-client");
            if (clientAccess != null && clientAccess.has("roles")) {
                JsonNode roles = clientAccess.get("roles");
                if (roles != null && roles.isArray()) {
                    for (JsonNode role : roles) {
                        if ("gateway_admin".equals(role.asText())) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean validateTeamInToken(String teamId, JsonNode jsonNode) {
        if (jsonNode == null)
            return false;
        try {
            // First check if teamId claim exists and matches
            if (jsonNode.has("teamId")) {
                String tokenTeamIdValue = jsonNode.get("teamId").asText();
                if (teamId.equals(tokenTeamIdValue)) {
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
            log.error("Error validating team in token node: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPublicPath(String path) {
        // Core public paths
        if (path.contains("/whatsapp/webhook") || path.contains("/auth/") || path.contains("-ws")) {
            return true;
        }

        // Public Intelligence/Advising fragments
        if (path.contains("/api/advising/") || path.contains("/api/datasets/") ||
                path.contains("/api/intel/") || path.contains("/api/academic/")) {
            return true;
        }

        // IMPORTANT: Allow all health checks (routed and native)
        if (path.endsWith("/health") || path.endsWith("/health/")) {
            return true;
        }

        // If it's a routed path but matches the public advising/intel patterns above,
        // it's already caught.
        // For everything else NOT starting with /r/ (native gateway endpoints), we
        // allow them by default
        // to avoid 401s on core infrastructure.
        if (!path.startsWith("/r/") && !path.startsWith("/linq")) {
            return true;
        }

        return false;
    }
}
