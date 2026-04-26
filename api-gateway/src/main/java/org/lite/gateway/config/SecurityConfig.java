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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import reactor.core.publisher.Flux;
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

    // List of public endpoints where ALL methods (GET, POST, etc.) are permitted
    private static final List<String> PUBLIC_ANY_METHOD = List.of(
            "/r/komunas-app/whatsapp/webhook",
            "/r/*/*-ws/**", // Universal WebSocket pattern for all routed apps
            "/widget/**", // Public AI Assistant widget scripts (public API key based)
            "/api/auth/**", // Public Auth Endpoints (SSO Callback, Login, Register)
            "/r/*/auth/**", // Public Auth for routed apps
            "/api/internal/**", // Secured by X-Change-Log-Token
            "/linqra-knowledge-hub-dev/**", // MinIO Proxy (Secured by S3 Signature)
            "/backup-linqra-knowledge-hub-dev/**",
            "/linqra-audit-dev/**",
            "/backup-linqra-audit-dev/**",
            "/api/tools", // Public Tool Catalog base
            "/api/tools/**", // All tool-related endpoints (Detail, Catalog, Search)
            "/api/tools/*/execute", // Unified execution endpoint (Public/Private handled by controller)
            "/api/agent-tasks/**", // Agent Task Management & Execution
            "/ws-linqra", // Native Gateway WebSocket base
            "/ws-linqra/**", // Native Gateway WebSocket subpaths
            "/r/**/api/advising/**", // Public Advising Diagnostic for all routed apps
            "/r/**/api/intel/**", // Public Semantic Knowledge
            "/r/**/api/academic/**", // Public Academic Intelligence (AAS/BAS Mappings)
            "/r/**/api/datasets/**" // Public Dataset Analytics for Workforce Insights
    );

    // List of public endpoints where ONLY GET methods are permitted (e.g.
    // documentation)
    private static final List<String> PUBLIC_GET_ONLY = List.of(
            "/api/internal-public/**");

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
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain proxiedSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity
                .securityMatcher(new OrServerWebExchangeMatcher(
                        new PathPatternParserServerWebExchangeMatcher("/r/**"),
                        new PathPatternParserServerWebExchangeMatcher("/ws-linqra"),
                        new PathPatternParserServerWebExchangeMatcher("/ws-linqra/**")))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .addFilterBefore(apiKeyAuthenticationFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                        .anyExchange()
                        .access(this::dynamicPathAuthorization));
        return serverHttpSecurity.build();
    }

    @Bean
    public SecurityWebFilterChain internalSecurityFilterChain(ServerHttpSecurity serverHttpSecurity,
            AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            ReactiveJwtDecoder keycloakJwtDecoder) {
        serverHttpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .x509(x509 -> x509
                        .principalExtractor(principal -> {
                            // Extract the CN from the certificate (adjust this logic as needed)
                            String dn = principal.getSubjectX500Principal().getName();
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
                    path.contains("-ws") ||
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
                                        // Only swap Authorization if NOT a routed path OR if no user token exists (internal call)
                                        if (!path.startsWith("/r/") || userToken == null) {
                                            headers.set("Authorization", "Bearer " + gatewayToken);
                                        }
                                        // Removed forced Accept and Content-Type to preserve original browser headers
                                        // (e.g. multipart/form-data)
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

        // Allow public access to any endpoint in PUBLIC_ANY_METHOD
        AntPathMatcher matcher = new AntPathMatcher();
        boolean isPublicAny = PUBLIC_ANY_METHOD.stream().anyMatch(pattern -> matcher.match(pattern, path));
        if (isPublicAny) {
            log.info("Public access (any method) granted for endpoint: {}", path);
            return Mono.just(new AuthorizationDecision(true));
        }

        // Allow public GET access for endpoints in PUBLIC_GET_ONLY
        if (authorizationContext.getExchange().getRequest().getMethod() == org.springframework.http.HttpMethod.GET) {
            boolean isPublicGet = PUBLIC_GET_ONLY.stream().anyMatch(pattern -> matcher.match(pattern, path));
            if (isPublicGet) {
                log.info("Public GET access granted for endpoint: {}", path);
                return Mono.just(new AuthorizationDecision(true));
            }
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

                        // For /r/ paths, check route permissions first (exempt /auth/ and webhook)
                        if (path.startsWith("/r/") && !path.contains("/auth/") && !path.contains("/whatsapp/webhook")) {
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
                                        // Check if identified by API Key filter or Administrator bypass
                                        return authenticationMono.flatMap(auth -> {
                                            boolean isAuthorizedByRole = auth.getAuthorities().stream()
                                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API_ACCESS") ||
                                                            a.getAuthority().equals("ROLE_GATEWAY_ADMIN"));

                                            if (isAuthorizedByRole) {
                                                log.info("Access granted: Authorized via role (API/ADMIN) for path: {}",
                                                        path);
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
                            boolean isAuthorizedByRole = auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_API_ACCESS") ||
                                            a.getAuthority().equals("ROLE_GATEWAY_ADMIN"));

                            if (isAuthorizedByRole) {
                                log.info("Access granted: Authorized via role (API/ADMIN) for path: {}", path);
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

        return authenticationMono.flatMap(auth -> {
            // Check for ROLE_GATEWAY_ADMIN authority (covers both JWT and Hybrid/API-Key
            // tokens)
            boolean hasAdminRole = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_GATEWAY_ADMIN"));

            if (hasAdminRole) {
                log.info("Administrator authority detected: bypassing route permission check for path: {}", path);
                return Mono.just(true);
            }

            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                if (isAdmin(jwtAuth.getToken())) {
                    log.info("Administrator identified via JWT: bypassing route permission check for path: {}", path);
                    return Mono.just(true);
                }
            }

            return apiRouteRepository.findByRouteIdentifier(routeIdentifier)
                    .flatMap(route -> {
                        String apiKeyTeamId = (String) exchange.getAttribute("API_KEY_TEAM_ID");
                        Mono<List<String>> teamsMono = (apiKeyTeamId != null)
                                ? Mono.just(List.of(apiKeyTeamId))
                                : getTeamContextService().getAllAuthorizedTeams(exchange);

                        return teamsMono
                                .flatMapMany(Flux::fromIterable)
                                .flatMap(tid -> {
                                    String cacheKey = String.format("permission:%s:%s", tid, routeIdentifier);
                                    return cacheService.get(cacheKey)
                                            .map(Boolean::parseBoolean)
                                            .switchIfEmpty(
                                                    teamRouteRepository.findByTeamIdAndRouteId(tid, route.getId())
                                                            .map(tr -> tr.getPermissions().contains(
                                                                    org.lite.gateway.entity.RoutePermission.USE))
                                                            .doOnNext(has -> cacheService
                                                                    .set(cacheKey, String.valueOf(has),
                                                                            java.time.Duration.ofMinutes(5))
                                                                    .subscribe())
                                                            .defaultIfEmpty(false));
                                })
                                .any(hasAccess -> hasAccess)
                                .defaultIfEmpty(false);
                    })
                    .switchIfEmpty(Mono.just(false));
        })
                .onErrorResume(e -> {
                    log.error("Error checking route permissions for path {}: {}", path, e.getMessage());
                    return Mono.just(false);
                });
    }

    private boolean isAdmin(Jwt jwt) {
        // Realm Roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            if (roles.contains("gateway_admin_realm"))
                return true;
        }

        // Client Roles
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.get(clientId) instanceof Map<?, ?> clientAccess) {
            if (clientAccess.get("roles") instanceof List<?> roles) {
                if (roles.contains("gateway_admin"))
                    return true;
            }
        }
        return false;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
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
