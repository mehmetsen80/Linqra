package org.lite.gateway.config;

import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.filter.ApiKeyAuthenticationFilter;
import org.lite.gateway.service.DynamicRouteService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.lite.gateway.service.CacheService;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.AntPathMatcher;
import java.time.Duration;

import org.lite.gateway.entity.RoutePermission;
import org.lite.gateway.repository.ApiRouteRepository;
import org.lite.gateway.repository.TeamRouteRepository;
import org.lite.gateway.service.TeamContextService;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Slf4j
public class SecurityConfig implements BeanFactoryAware {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods}")
    private String allowedMethods;

    @Value("${cors.allowed-headers}")
    private String allowedHeaders;

    @Value("${cors.max-age}")
    private long maxAge;

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.client-id}")
    private String clientId;

    private final DynamicRouteService dynamicRouteService;
    private final ReactiveClientRegistrationRepository customClientRegistrationRepository;
    private final ReactiveOAuth2AuthorizedClientService customAuthorizedClientService;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final CacheService cacheService;
    private final ApiRouteRepository apiRouteRepository;
    private final TeamRouteRepository teamRouteRepository;
    private final Environment environment;

    private BeanFactory beanFactory;

    public SecurityConfig(DynamicRouteService dynamicRouteService,
            ReactiveClientRegistrationRepository customClientRegistrationRepository,
            ReactiveOAuth2AuthorizedClientService customAuthorizedClientService,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            CacheService cacheService,
            ApiRouteRepository apiRouteRepository,
            TeamRouteRepository teamRouteRepository,
            Environment environment) {
        this.dynamicRouteService = dynamicRouteService;
        this.customClientRegistrationRepository = customClientRegistrationRepository;
        this.customAuthorizedClientService = customAuthorizedClientService;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.cacheService = cacheService;
        this.apiRouteRepository = apiRouteRepository;
        this.teamRouteRepository = teamRouteRepository;
        this.environment = environment;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    private TeamContextService getTeamContextService() {
        return beanFactory.getBean(TeamContextService.class);
    }

    // List of public endpoints (Ant-style patterns allowed)
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/r/komunas-app/whatsapp/webhook",
            "/widget/**", // Public AI Assistant widget scripts (public API key based)
            "/api/auth/**", // Public Auth Endpoints (SSO Callback, Login, Register)
            "/api/internal/**", // Secured by X-Change-Log-Token
            "/linqra-knowledge-hub-dev/**", // MinIO Proxy (Secured by S3 Signature)
            "/backup-linqra-knowledge-hub-dev/**",
            "/linqra-audit-dev/**",
            "/backup-linqra-audit-dev/**");

    private static final Pattern SCOPE_KEY_PATTERN = Pattern.compile("^/([\\w-]+)/");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("/r/([^/]+)/");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // For Keycloak RS256 tokens
    @Bean
    @Profile("!remote-dev")
    public ReactiveJwtDecoder keycloakJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    // For user HS256 tokens
    @Bean
    public ReactiveJwtDecoder userJwtDecoder() {
        return NimbusReactiveJwtDecoder.withSecretKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build();
    }

    @Bean
    public SecurityWebFilterChain jwtSecurityFilterChain(ServerHttpSecurity serverHttpSecurity,
            AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            ReactiveJwtDecoder keycloakJwtDecoder) {
        serverHttpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .x509(x509 -> x509
                        .principalExtractor(principal -> {
                            // Extract the CN from the certificate (adjust this logic as needed)
                            String dn = principal.getSubjectX500Principal().getName();
                            // log.info("dn: {}", dn);
                            String cn = dn.split(",")[0].replace("CN=", "");
                            return cn; // Return the Common Name (CN) as the principal
                        }))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtDecoder(keycloakJwtDecoder)))
                .addFilterBefore(apiKeyAuthenticationFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                        .anyExchange()
                        .access(this::dynamicPathAuthorization));
        return serverHttpSecurity.build();
    }

    // Define a ReactiveUserDetailsService to map certificates to users
    // Do not remove this, although it might seem it's not being used
    // WebFluxSecurityConfiguration requires a bean of type
    // 'org.springframework.security.core.userdetails.ReactiveUserDetailsService'
    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        // Example: Hardcoded user with role
        UserDetails user = User.withUsername("example-cn")
                .password("{noop}password") // Password is not used in mTLS
                .roles("VIEW", "ADMIN")
                .build();

        // A Map-based user details service
        return new MapReactiveUserDetailsService(user);
    }

    // We are injecting the gateway token here
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter tokenRelayWebFilter(
            AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        return (exchange, chain) -> {
            // Check if we've already processed this request
            if (exchange.getAttribute("TOKEN_RELAY_PROCESSED") != null) {
                return chain.filter(exchange);
            }

            String path = exchange.getRequest().getPath().toString();
            // Skip token relay for certain paths
            // ADDED: /r/ to skip this filter for routed internal requests (preserves User
            // Token)
            if (path.startsWith("/actuator") ||
                    path.startsWith("/favicon")) {
                return chain.filter(exchange);
            }
            // log.info("TokenRelayWebFilter for path: {}", path);

            // Store the original user token if it exists
            String userToken = exchange.getRequest().getHeaders().getFirst("Authorization");

            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientId)
                    .principal(clientId)
                    .build();

            return authorizedClientManager.authorize(authorizeRequest)
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .doOnError(error -> log.error("Error authorizing client: {}", error.getMessage()))
                    .flatMap(authorizedClient -> {
                        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                            String gatewayToken = authorizedClient.getAccessToken().getTokenValue();

                            // Mark this request as processed
                            exchange.getAttributes().put("TOKEN_RELAY_PROCESSED", true);

                            ServerHttpRequest request = exchange.getRequest().mutate()
                                    .headers(headers -> {
                                        headers.set("Authorization", "Bearer " + gatewayToken);
                                        headers.set("Accept", "application/json");
                                        headers.set("Content-Type", "application/json");
                                        if (userToken != null) {
                                            String token = userToken.startsWith("Bearer ") ? userToken.substring(7)
                                                    : userToken;
                                            headers.set("X-User-Token", token);
                                            // log.info("User token set for path: {}", path);
                                        }
                                    })
                                    .build();

                            // Log keys of headers being sent downstream
                            log.info("Relaying request to: {}", path);
                            // log.info("Request Headers: {}", request.getHeaders());

                            return chain.filter(exchange.mutate().request(request).build());
                        }

                        ServerHttpRequest request = exchange.getRequest().mutate()
                                .headers(headers -> {
                                    headers.set("Accept", "application/json");
                                    headers.set("Content-Type", "application/json");
                                    if (userToken != null) {
                                        String token = userToken.startsWith("Bearer ") ? userToken.substring(7)
                                                : userToken;
                                        headers.set("X-User-Token", token);
                                    }
                                })
                                .build();

                        return chain.filter(exchange.mutate().request(request).build());
                    })
                    .onErrorResume(error -> {
                        log.error("Failed to authorize client for path {}: {}", path, error.getMessage());
                        return chain.filter(exchange);
                    });
        };
    }

    // Bean to handle OAuth2 client credentials
    // DO NOT DELETE THIS
    @Bean
    @Profile("!remote-dev")
    public AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager() {

        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder
                .builder()
                .authorizationCode()
                .refreshToken()
                .clientCredentials()
                .build();

        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                customClientRegistrationRepository, customAuthorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        // //This is just to test if we get the token or not, enable this to test
        // OAuth2AuthorizeRequest authorizeRequest =
        // OAuth2AuthorizeRequest.withClientRegistrationId("linqra-gateway-client")
        // .principal("linqra-gateway-client") // Use a dummy principal for
        // client_credentials
        // .build();
        // authorizedClientManager.authorize(authorizeRequest)
        // .flatMap(authorizedClient -> {
        // if (authorizedClient != null) {
        // OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        // if (accessToken != null) {
        // log.info("Access Token: {}", accessToken.getTokenValue());
        // // Log scopes (from token response)
        // String scopes = String.join(",",
        // authorizedClient.getAccessToken().getScopes());
        // log.info("Granted Scopes: {}", scopes);
        //
        // return Mono.just(accessToken);
        // } else {
        // log.warn("Access token is null!");
        // }
        // } else {
        // log.warn("Authorized client is null!");
        // }
        // return Mono.empty();
        // })
        // .doOnError(error -> log.error("Error during token retrieval", error))
        // .subscribe(token -> log.info("Token successfully retrieved at
        // authorizedClientManager: {}", token.getTokenValue()),
        // error -> log.error("Failed to retrieve token at authorizedClientManager",
        // error));

        return authorizedClientManager;
    }

    private String getScopeKey(String path) {
        // Updated regex to capture prefixes that may include hyphens
        Matcher matcher = SCOPE_KEY_PATTERN.matcher(path);

        if (matcher.find()) {
            String prefix = matcher.group(0);
            return prefix + "**";
        }

        return path + "**";
    }

    private Mono<AuthorizationDecision> dynamicPathAuthorization(Mono<Authentication> authenticationMono,
            AuthorizationContext authorizationContext) {
        String path = authorizationContext.getExchange().getRequest().getPath().toString();
        // log.info("Security check for path: {}", path);
        // log.info("Checking authorization for path: {} with method: {}", path,
        // authorizationContext.getExchange().getRequest().getMethod());

        // Allow public access to any endpoint in PUBLIC_ENDPOINTS
        AntPathMatcher matcher = new AntPathMatcher();
        boolean isPublic = PUBLIC_ENDPOINTS.stream().anyMatch(pattern -> matcher.match(pattern, path));
        if (isPublic) {
            log.info("Public access granted for endpoint: {}", path);
            return Mono.just(new AuthorizationDecision(true));
        }

        // 1st step
        // Use the path matcher from the DynamicRouteService to check if the path is
        // whitelisted.
        // whitelisted means either hard coded or read from mongodb
        boolean isWhitelisted = dynamicRouteService.isPathWhitelisted(path);
        // log.info("Is path {} whitelisted? {}", path, isWhitelisted);

        // Check if this is a health endpoint - these should be completely public
        if (path.endsWith("/health") || path.endsWith("/health/")) {
            // log.info("Health endpoint detected, allowing public access: {}", path);
            return Mono.just(new AuthorizationDecision(true));
        }

        // String prefix = "/inventory-service/";
        // String scopeKey = "";
        // if (path.startsWith(prefix)) {
        // scopeKey = prefix + "**";
        // }
        return dynamicRouteService.getClientScope(getScopeKey(path))
                .defaultIfEmpty("")
                .flatMap(scope -> {
                    // 2nd step - check the realm access role
                    // if whitelist passes, check for roles in the JWT token for secured paths
                    if (isWhitelisted) {
                        // REMOTE-DEV BYPASS: Allow open access to whitelisted routes in remote-dev
                        // profile
                        boolean isRemoteDev = java.util.Arrays.asList(environment.getActiveProfiles())
                                .contains("remote-dev");
                        if (isRemoteDev) {
                            log.warn("REMOTE-DEV MODE: Bypassing security checks for path: {}", path);
                            return Mono.just(new AuthorizationDecision(true));
                        }

                        // For /r/ paths, check route permissions first
                        if (path.startsWith("/r/") && !path.contains("/whatsapp/webhook")) {
                            // log.info("Checking route permissions for path: {}", path);
                            return checkRoutePermission(authenticationMono, path, authorizationContext.getExchange())
                                    .doOnNext(hasPermission -> log.info("Route permission result for {}: {}", path,
                                            hasPermission))
                                    .flatMap(hasPermission -> {
                                        if (!hasPermission) {
                                            log.warn(
                                                    "Route permission denied for: {}. Team does not have USE permission for this route.",
                                                    path);
                                            return Mono.just(new AuthorizationDecision(false));
                                        }
                                        // Check if identified by API Key filter
                                        return authenticationMono.flatMap(auth -> {
                                            boolean isApiKeyAuth = auth.getAuthorities().stream()
                                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API_ACCESS"));

                                            if (isApiKeyAuth) {
                                                return Mono.just(new AuthorizationDecision(true));
                                            }
                                            return continueWithJwtChecks(Mono.just(auth), path, scope);
                                        });
                                    })
                                    .onErrorResume(error -> {
                                        log.error("Error checking route permissions for {}: {}", path,
                                                error.getMessage(), error);
                                        return Mono.just(new AuthorizationDecision(false));
                                    })
                                    .defaultIfEmpty(new AuthorizationDecision(false));
                        }

                        // For non-route paths, continue with normal JWT checks
                        return authenticationMono.flatMap(auth -> {
                            boolean isApiKeyAuth = auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API_ACCESS"));

                            if (isApiKeyAuth) {
                                return Mono.just(new AuthorizationDecision(true));
                            }
                            return continueWithJwtChecks(Mono.just(auth), path, scope);
                        });
                    }

                    return Mono.just(new AuthorizationDecision(false));
                });

    }

    private Mono<AuthorizationDecision> continueWithJwtChecks(Mono<Authentication> authenticationMono, String path,
            String scope) {
        return authenticationMono
                .doOnNext(auth -> log.info("Evaluating JWT for path: {}", path))
                .doOnError(error -> log.error("Error in authenticationMono: {}", error.toString()))
                .filter(authentication -> authentication instanceof JwtAuthenticationToken) // Ensure it's a JWT auth
                                                                                            // token
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken) // Get the JWT token
                .map(jwt -> {
                    String scopes = jwt.getClaimAsString("scope");
                    // log.info("scopes: {}", scopes);
                    boolean hasGatewayReadScope = scopes != null && scopes.contains("gateway.read");// does the gateway
                                                                                                    // itself has this
                                                                                                    // scope
                    if (!hasGatewayReadScope) {
                        log.warn("JWT is missing 'gateway.read' scope");
                        return new AuthorizationDecision(false); // Deny if the hard-coded scope is missing
                    }

                    // i.e. inventory-service.read or product-service.read should be defined in the
                    // keycloak
                    // we bypass the refresh and fallback end points
                    if (!path.startsWith("/ws-linqra")
                            && !path.startsWith("/metrics/")
                            && !path.startsWith("/analysis/")
                            && !path.startsWith("/routes/")
                            && !path.startsWith("/api/")
                            && !path.startsWith("/r/")
                            && !path.startsWith("/health/")
                            && !path.startsWith("/fallback/")
                            && !path.startsWith("/linq")) {
                        boolean hasClientReadScope = scope != null && scopes.contains(scope);// does the client itself
                                                                                             // has the scope
                        if (!hasClientReadScope) {
                            if (scope == null) {
                                log.error("Client path {} is missing scope in mongodb and keycloak", path);
                            } else {
                                log.error("Client path {} is missing scope {} in keycloak", path, scope);
                            }
                            return new AuthorizationDecision(false); // Deny if the client scope is missing
                        }
                    }

                    // Extract roles from JWT claims (adjust based on your Keycloak setup)
                    // Keycloak roles are often under 'realm_access' or 'resource_access'
                    // Extract Keycloak roles
                    var realmAccess = jwt.getClaimAsMap("realm_access");
                    var rolesRealm = realmAccess != null ? realmAccess.get("roles") : null;

                    // Ensure roles are a list and check for the specific role
                    if (rolesRealm instanceof List) {
                        // Log user roles for debugging
                        log.info("User Realm Roles: {}", rolesRealm);

                        // check realm role if authorized
                        @SuppressWarnings("unchecked")
                        boolean isAuthorizedRealm = ((List<String>) rolesRealm).contains("gateway_admin_realm");
                        if (isAuthorizedRealm) {
                            // get the resource access map
                            var resourceAccess = jwt.getClaimAsMap("resource_access");
                            if (resourceAccess != null) {
                                // get the client access
                                // 3rd step - check out the client roles
                                var clientAccess = resourceAccess.get(clientId);
                                if (clientAccess instanceof Map<?, ?>) {
                                    // get the client roles
                                    @SuppressWarnings("unchecked")
                                    var clientRoles = ((Map<String, Object>) clientAccess).get("roles");
                                    if (clientRoles instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        boolean isAuthorizedClient = ((List<String>) clientRoles)
                                                .contains("gateway_admin");
                                        log.info("Access granted: Authorized via gateway_admin role for path: {}",
                                                path);
                                        return new AuthorizationDecision(isAuthorizedClient); // finally return the
                                                                                              // authorization decision
                                    }
                                }
                            }
                        }
                    }

                    log.warn("Access denied: Missing required roles for path: {}", path);
                    return new AuthorizationDecision(false);
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<Boolean> checkRoutePermission(Mono<Authentication> authenticationMono, String path,
            ServerWebExchange exchange) {
        Matcher routeMatcher = ROUTE_PATTERN.matcher(path);

        if (!routeMatcher.find()) {
            log.warn("No route identifier found in path: {}", path);
            return Mono.just(false);
        }

        String routeIdentifier = routeMatcher.group(1);
        // log.info("Route identifier found: {}", routeIdentifier);

        // Prioritize the API Key Attribute or Authentication Principal over header
        // lookup
        return authenticationMono
                .flatMap(auth -> {
                    // Check if we stashed the teamId in attributes (happens when Bearer token
                    // overwrites context)
                    String apiKeyTeamId = exchange.getAttribute("API_KEY_TEAM_ID");
                    if (apiKeyTeamId != null) {
                        return Mono.just(apiKeyTeamId);
                    }

                    if (auth.getPrincipal() instanceof String teamId) {
                        return Mono.just(teamId);
                    }
                    return getTeamContextService().getTeamFromContext(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Check attribute even if authenticationMono is empty/default
                    String apiKeyTeamId = exchange.getAttribute("API_KEY_TEAM_ID");
                    if (apiKeyTeamId != null) {
                        return Mono.just(apiKeyTeamId);
                    }
                    return getTeamContextService().getTeamFromContext(exchange);
                }))
                .doOnNext(teamId -> log.info("Checking team {} permission for route: {}", teamId, routeIdentifier))
                .flatMap(teamId -> {
                    String permissionCacheKey = String.format("permission:%s:%s", teamId, routeIdentifier);
                    // log.info("Checking Redis cache for key: {}", permissionCacheKey);

                    return cacheService.get(permissionCacheKey)
                            .map(cachedPermission -> {
                                boolean hasPermission = Boolean.parseBoolean(cachedPermission);
                                // log.info("Using cached permission for team {} route {}: {}", teamId,
                                // routeIdentifier, hasPermission);
                                return hasPermission;
                            })
                            .switchIfEmpty(
                                    apiRouteRepository.findByRouteIdentifier(routeIdentifier)
                                            .doOnNext(apiRoute -> log.info("Found API route: {} with ID: {}",
                                                    routeIdentifier, apiRoute.getId()))
                                            .flatMap(apiRoute -> teamRouteRepository
                                                    .findByTeamIdAndRouteId(teamId, apiRoute.getId())
                                                    .doOnNext(teamRoute -> log.info(
                                                            "Found team route for team {} and route {}: {}", teamId,
                                                            apiRoute.getId(), teamRoute.getPermissions()))
                                                    .map(teamRoute -> {
                                                        boolean hasUsePermission = teamRoute.getPermissions()
                                                                .contains(RoutePermission.USE);
                                                        // log.info("Team {} has USE permission for route {}: {}",
                                                        // teamId, routeIdentifier, hasUsePermission);

                                                        // Cache the result
                                                        cacheService.set(permissionCacheKey,
                                                                String.valueOf(hasUsePermission),
                                                                Duration.ofMinutes(5))
                                                                .subscribe();
                                                        // log.info("Cached permission result for team {} route {}: {}",
                                                        // teamId, routeIdentifier, hasUsePermission);

                                                        return hasUsePermission;
                                                    }))
                                            .switchIfEmpty(Mono.defer(() -> {
                                                log.warn("No API route found for identifier: {}", routeIdentifier);
                                                return Mono.just(false);
                                            })));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("No team context found for path: {}", path);
                    return Mono.just(false);
                }))
                .onErrorResume(error -> {
                    log.error("Error checking route permissions for path {}: {}", path, error.getMessage(), error);
                    return Mono.just(false);
                });
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of(allowedMethods.split(",")));

        // Add X-API-Key headers to allowed headers
        List<String> allowedHeadersList = new ArrayList<>(List.of(allowedHeaders.split(",")));
        allowedHeadersList.add("X-API-Key");
        allowedHeadersList.add("X-API-Key-Name");
        allowedHeadersList.add("X-Team-ID");
        configuration.setAllowedHeaders(allowedHeadersList);

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
