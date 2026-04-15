package org.lite.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.time.Duration;

@Configuration
@Slf4j
public class WebClientConfig {

        @Bean
        public ConnectionProvider connectionProvider() {
                return ConnectionProvider.builder("fixed")
                                .maxConnections(50)
                                .maxIdleTime(Duration.ofSeconds(20))
                                .maxLifeTime(Duration.ofSeconds(60))
                                .pendingAcquireTimeout(Duration.ofSeconds(30))
                                .evictInBackground(Duration.ofSeconds(30))
                                .build();
        }

        @Bean
        @Primary
        public HttpClient httpClient(ConnectionProvider provider) {
                return HttpClient.create(provider)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                                .responseTimeout(Duration.ofSeconds(120))
                                .option(ChannelOption.SO_REUSEADDR, true)
                                .doOnConnected(conn -> conn
                                                .addHandlerLast(new ReadTimeoutHandler(120))
                                                .addHandlerLast(new WriteTimeoutHandler(120)))
                                .secure(spec -> {
                                        try {
                                                io.netty.handler.ssl.SslContext sslContext = SslContextBuilder
                                                                .forClient()
                                                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                                .build();

                                                spec.sslContext(sslContext)
                                                                .handlerConfigurator(handler -> {
                                                                        SSLEngine engine = handler.engine();
                                                                        SSLParameters params = engine.getSSLParameters();
                                                                        // Disable hostname verification to resolve "No name matching polytechnic-backend-service found"
                                                                        params.setEndpointIdentificationAlgorithm(null);
                                                                        engine.setSSLParameters(params);
                                                                });
                                        } catch (SSLException e) {
                                                throw new RuntimeException(e);
                                        }
                                })
                                .wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG)
                                .option(ChannelOption.SO_KEEPALIVE, true)
                                .option(ChannelOption.TCP_NODELAY, true);
        }

        @Bean
        public WebClient.Builder webClientBuilder(HttpClient httpClient) {
                ObjectMapper mapper = new ObjectMapper()
                                .registerModule(new JavaTimeModule());

                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(clientCodecConfigurer -> {
                                        clientCodecConfigurer.defaultCodecs().jackson2JsonDecoder(
                                                        new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
                                        clientCodecConfigurer.defaultCodecs().jackson2JsonEncoder(
                                                        new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
                                        clientCodecConfigurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024);
                                })
                                .build();

                return WebClient.builder()
                                .exchangeStrategies(strategies)
                                .clientConnector(new ReactorClientHttpConnector(httpClient));
        }
}
