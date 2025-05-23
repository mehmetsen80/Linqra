package org.lite.gateway.config;

import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.filter.ApiKeyAuthenticationFilter;
import org.lite.gateway.service.DynamicRouteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //For Keycloak RS256 tokens
    @Bean
    public ReactiveJwtDecoder keycloakJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    //For user HS256 tokens
    @Bean
    public ReactiveJwtDecoder userJwtDecoder() {
        return NimbusReactiveJwtDecoder.withSecretKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
            .build();
    }

    @Bean
    public SecurityWebFilterChain jwtSecurityFilterChain(ServerHttpSecurity serverHttpSecurity, 
            AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        serverHttpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .x509(x509 -> x509
                        .principalExtractor(principal -> {
                            // Extract the CN from the certificate (adjust this logic as needed)
                            String dn = principal.getSubjectX500Principal().getName();
                            log.info("dn: {}", dn);
                            String cn = dn.split(",")[0].replace("CN=", "");
                            return cn;  // Return the Common Name (CN) as the principal
                        })
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtDecoder(keycloakJwtDecoder())))
                .addFilterBefore(apiKeyAuthenticationFilter, 
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchange -> exchange
                        .anyExchange()
                        .access(this::dynamicPathAuthorization))
                .addFilterAt(tokenRelayWebFilter(authorizedClientManager), SecurityWebFiltersOrder.SECURITY_CONTEXT_SERVER_WEB_EXCHANGE); // Dynamic authorization
        return serverHttpSecurity.build();
    }

    // Define a ReactiveUserDetailsService to map certificates to users
    // Do not remove this, although it might seem it's not being used
    // WebFluxSecurityConfiguration requires a bean of type 'org.springframework.security.core.userdetails.ReactiveUserDetailsService'
    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        // Example: Hardcoded user with role
        UserDetails user = User.withUsername("example-cn")
                .password("{noop}password")  // Password is not used in mTLS
                .roles("VIEW", "ADMIN")
                .build();

        // A Map-based user details service
        return new MapReactiveUserDetailsService(user);
    }

    //We are injecting the gateway token here
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter tokenRelayWebFilter(AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        return (exchange, chain) -> {
            // Check if we've already processed this request
            if (exchange.getAttribute("TOKEN_RELAY_PROCESSED") != null) {
                return chain.filter(exchange);
            }
            
            String path = exchange.getRequest().getPath().toString();
            // Skip token relay for certain paths
            if (path.startsWith("/actuator") || 
                path.startsWith("/favicon")) {
                return chain.filter(exchange);
            }
            log.info("TokenRelayWebFilter for path: {}", path);

            // Store the original user token if it exists
            String userToken = exchange.getRequest().getHeaders().getFirst("Authorization");
            log.info("Incoming token: {}", userToken);

            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientId)
                    .principal(clientId)
                    .build();

            return authorizedClientManager.authorize(authorizeRequest)
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
                                    String token = userToken.startsWith("Bearer ") ? 
                                        userToken.substring(7) : userToken;
                                    headers.set("X-User-Token", token);
                                    log.info("User token set for path: {}", path);
                                }
                            })
                                .build();

                            return chain.filter(exchange.mutate().request(request).build());
                        }

                        ServerHttpRequest request = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.set("Accept", "application/json");
                                headers.set("Content-Type", "application/json");
                                if (userToken != null) {
                                    String token = userToken.startsWith("Bearer ") ? 
                                        userToken.substring(7) : userToken;
                                    headers.set("X-User-Token", token);
                                    log.info("Only user token set for path: {}", path);
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
    public AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ) {

        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .clientCredentials()
                .build();

        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(customClientRegistrationRepository, customAuthorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

//        //This is just to test if we get the token or not, enable this to test
//        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("linqra-gateway-client")
//                .principal("linqra-gateway-client")  // Use a dummy principal for client_credentials
//                .build();
//        authorizedClientManager.authorize(authorizeRequest)
//                .flatMap(authorizedClient -> {
//                    if (authorizedClient != null) {
//                        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
//                        if (accessToken != null) {
//                            log.info("Access Token: {}", accessToken.getTokenValue());
//                            // Log scopes (from token response)
//                            String scopes = String.join(",", authorizedClient.getAccessToken().getScopes());
//                            log.info("Granted Scopes: {}", scopes);
//
//                            return Mono.just(accessToken);
//                        } else {
//                            log.warn("Access token is null!");
//                        }
//                    } else {
//                        log.warn("Authorized client is null!");
//                    }
//                    return Mono.empty();
//                })
//                .doOnError(error -> log.error("Error during token retrieval", error))
//                .subscribe(token -> log.info("Token successfully retrieved at authorizedClientManager: {}", token.getTokenValue()),
//                        error -> log.error("Failed to retrieve token at authorizedClientManager", error));

        return authorizedClientManager;
    }

    private String getScopeKey(String path) {
        // Updated regex to capture prefixes that may include hyphens
        Pattern pattern = Pattern.compile("^/([\\w-]+)/");
        Matcher matcher = pattern.matcher(path);

        if (matcher.find()) {
            String prefix = matcher.group(0);
            return prefix + "**";
        }

        return path + "**";
    }

    private Mono<AuthorizationDecision> dynamicPathAuthorization(Mono<Authentication> authenticationMono, AuthorizationContext authorizationContext) {
        String path = authorizationContext.getExchange().getRequest().getPath().toString();
        log.info("Security check for path: {}", path);
        log.info("Checking authorization for path: {} with method: {}", path, 
            authorizationContext.getExchange().getRequest().getMethod());

        //1st step
        // Use the path matcher from the DynamicRouteService to check if the path is whitelisted.
        // whitelisted means either hard coded or read from mongodb
        boolean isWhitelisted = dynamicRouteService.isPathWhitelisted(path);
        log.info("Is path {} whitelisted? {}", path, isWhitelisted);

//        String prefix = "/inventory/";
//        String scopeKey = "";
//        if (path.startsWith(prefix)) {
//            scopeKey = prefix + "**";
//        }
        String scope = dynamicRouteService.getClientScope(getScopeKey(path));//i.e. inventory/** -> inventory-service.read

        //2nd step - check the realm access role
        //if whitelist passes, check for roles in the JWT token for secured paths
        if (isWhitelisted) {
            return authenticationMono
                    .doOnNext(auth -> log.info("Evaluating JWT for path: {}", path))
                    .doOnError(error -> log.error("Error in authenticationMono: {}", error.toString()))
                    .filter(authentication -> authentication instanceof JwtAuthenticationToken) // Ensure it's a JWT auth token
                    .cast(JwtAuthenticationToken.class)
                    .map(JwtAuthenticationToken::getToken)  // Get the JWT token
                    .map(jwt -> {
                        String scopes = jwt.getClaimAsString("scope");
                        log.info("scopes: {}", scopes);
                        boolean hasGatewayReadScope = scopes != null && scopes.contains("gateway.read");//does the gateway itself has this scope
                        if (!hasGatewayReadScope) {
                            log.warn("JWT is missing 'gateway.read' scope");
                            return new AuthorizationDecision(false); // Deny if the hard-coded scope is missing
                        }

                        //i.e. inventory-service.read or product-service.read should be defined in the keycloak
                        //we bypass the refresh and fallback end points
                        if(!path.startsWith("/ws-linqra")
                                && !path.startsWith("/metrics/")
                                && !path.startsWith("/analysis/")
                                && !path.startsWith("/routes/")
                                && !path.startsWith("/api/")
                                && !path.startsWith("/r/")
                                && !path.startsWith("/health/")
                                && !path.startsWith("/fallback/")
                                && !path.startsWith("/linq")){
                            boolean hasClientReadScope = scope != null && scopes.contains(scope);//does the client itself has the scope
                            if (!hasClientReadScope){
                                if(scope == null) {
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
                            // check realm role if authorized
                            boolean isAuthorizedRealm = ((List<String>) rolesRealm).contains("gateway_admin_realm");
                            if(isAuthorizedRealm){
                                // get the resource access map
                                var resourceAccess = jwt.getClaimAsMap("resource_access");
                                if (resourceAccess != null) {
                                    // get the client access
                                    // 3rd step - check out the client roles
                                    var clientAccess = resourceAccess.get(clientId);
                                    if (clientAccess instanceof Map<?,?>) {
                                        // get the client roles
                                        var clientRoles = ((Map<String, Object>) clientAccess).get("roles");
                                        if (clientRoles instanceof List) {
                                            boolean isAuthorizedClient = ((List<String>) clientRoles).contains("gateway_admin");
                                            return new AuthorizationDecision(isAuthorizedClient); //finally return the authorization decision
                                        }
                                    }
                                }
                            }
                        }
                        return new AuthorizationDecision(false);
                    })
                    .defaultIfEmpty(new AuthorizationDecision(false)); // Default to unauthorized if no valid authentication
        }

        return Mono.just(new AuthorizationDecision(false));
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
        configuration.setAllowedHeaders(allowedHeadersList);
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

