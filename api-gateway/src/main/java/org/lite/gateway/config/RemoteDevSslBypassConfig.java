package org.lite.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.reactive.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ClientCredentialsReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.RefreshTokenReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.AuthorizationCodeReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient;

@Configuration
@Profile("remote-dev")
@Slf4j
public class RemoteDevSslBypassConfig {

    @PostConstruct
    public void turnOffSslChecking() throws NoSuchAlgorithmException, KeyManagementException {
        log.warn("!!! REMOTE-DEV MODE: DISABLING SSL CERTIFICATE VALIDATION GLOBALLY !!!");

        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        // Apply to HttpsURLConnection
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        // Also set the global default SSLContext for other clients (like Apache
        // HttpClient used by Eureka)
        SSLContext.setDefault(sslContext);

        // Apply host name verifier to allow any hostname
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        log.info("Global SSL Verification disabled for HttpsURLConnection.");
    }

    @Bean
    public org.springframework.cloud.netflix.eureka.http.RestTemplateDiscoveryClientOptionalArgs discoveryClientOptionalArgs()
            throws NoSuchAlgorithmException, KeyManagementException {
        log.warn("!!! REMOTE-DEV MODE: Configuring Eureka Client (RestTemplate) to bypass SSL validation !!!");
        // Use the constructor requiring factory supplier and rest template builder
        // supplier
        org.springframework.cloud.netflix.eureka.http.RestTemplateDiscoveryClientOptionalArgs args = new org.springframework.cloud.netflix.eureka.http.RestTemplateDiscoveryClientOptionalArgs(
                new org.springframework.cloud.netflix.eureka.http.DefaultEurekaClientHttpRequestFactorySupplier(),
                org.springframework.boot.web.client.RestTemplateBuilder::new);
        args.setSSLContext(getInsecureSslContext());
        args.setHostnameVerifier((s, sslSession) -> true);
        return args;
    }

    private SSLContext getInsecureSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public ReactorResourceFactory reactorResourceFactory() {
        return new ReactorResourceFactory();
    }

    @Bean
    @org.springframework.context.annotation.Primary
    @SuppressWarnings("deprecation")
    public reactor.netty.http.client.HttpClient reactiveHttpClient(ReactorResourceFactory factory) {
        log.warn("!!! REMOTE-DEV MODE: Configuring Netty HttpClient to bypass SSL validation !!!");
        try {
            io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
                    .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                    .build();

            return reactor.netty.http.client.HttpClient.create(factory.getConnectionProvider())
                    .runOn(factory.getLoopResources())
                    .secure(sslProvider -> sslProvider.sslContext(sslContext));
        } catch (javax.net.ssl.SSLException e) {
            throw new RuntimeException("Failed to configure Netty SSL context", e);
        }
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public ReactiveJwtDecoder insecureKeycloakJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            WebClient.Builder webClientBuilder,
            reactor.netty.http.client.HttpClient insecureHttpClient) {
        log.warn("!!! REMOTE-DEV MODE: Configuring ReactiveJwtDecoder to use insecure WebClient !!!");

        WebClient webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(insecureHttpClient))
                .build();

        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .webClient(webClient)
                .build();
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager insecureAuthorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ReactiveOAuth2AuthorizedClientService authorizedClientService,
            WebClient.Builder webClientBuilder,
            reactor.netty.http.client.HttpClient insecureHttpClient) {
        log.warn("!!! REMOTE-DEV MODE: Configuring OAuth2 AuthorizedClientManager to use insecure WebClient !!!");

        WebClient webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(insecureHttpClient))
                .build();

        // Client Credentials
        WebClientReactiveClientCredentialsTokenResponseClient clientCredentialsClient = new WebClientReactiveClientCredentialsTokenResponseClient();
        clientCredentialsClient.setWebClient(webClient);

        ClientCredentialsReactiveOAuth2AuthorizedClientProvider clientCredentials = new ClientCredentialsReactiveOAuth2AuthorizedClientProvider();
        clientCredentials.setAccessTokenResponseClient(clientCredentialsClient);

        // Refresh Token
        WebClientReactiveRefreshTokenTokenResponseClient refreshTokenClient = new WebClientReactiveRefreshTokenTokenResponseClient();
        refreshTokenClient.setWebClient(webClient);

        RefreshTokenReactiveOAuth2AuthorizedClientProvider refreshToken = new RefreshTokenReactiveOAuth2AuthorizedClientProvider();
        refreshToken.setAccessTokenResponseClient(refreshTokenClient);

        // Authorization Code - default
        AuthorizationCodeReactiveOAuth2AuthorizedClientProvider authCode = new AuthorizationCodeReactiveOAuth2AuthorizedClientProvider();

        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder
                .builder()
                .provider(authCode)
                .provider(refreshToken)
                .provider(clientCredentials)
                .build();

        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }
}
