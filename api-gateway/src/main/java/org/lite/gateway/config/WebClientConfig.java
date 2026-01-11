package org.lite.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.time.Duration;

@Configuration
@Slf4j
public class WebClientConfig {

        @Bean
        public WebClient.Builder webClientBuilder() {
                ObjectMapper mapper = new ObjectMapper()
                                .registerModule(new JavaTimeModule());

                ExchangeStrategies strategies = ExchangeStrategies
                                .builder()
                                .codecs(clientCodecConfigurer -> {
                                        clientCodecConfigurer.defaultCodecs().jackson2JsonDecoder(
                                                        new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
                                        clientCodecConfigurer.defaultCodecs().jackson2JsonEncoder(
                                                        new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
                                        clientCodecConfigurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024); // Further
                                                                                                                // reduced
                                                                                                                // for
                                                                                                                // EC2
                                })
                                .build();

                ConnectionProvider provider = ConnectionProvider
                                .builder("fixed")
                                .maxConnections(50) // Increased for better concurrency
                                .maxIdleTime(Duration.ofSeconds(20))
                                .maxLifeTime(Duration.ofSeconds(60))
                                .pendingAcquireTimeout(Duration.ofSeconds(30)) // Increased to avoid fast failures
                                .evictInBackground(Duration.ofSeconds(30))
                                .build();

                HttpClient httpClient = HttpClient.create(provider)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10s connect timeout
                                .responseTimeout(Duration.ofSeconds(120)) // Increased for LLM generation
                                .option(ChannelOption.SO_REUSEADDR, true)
                                .doOnConnected(conn -> conn
                                                .addHandlerLast(new ReadTimeoutHandler(120)) // 120s read timeout for
                                                                                             // LLMs
                                                .addHandlerLast(new WriteTimeoutHandler(120))) // 120s write timeout
                                .secure(spec -> {
                                        try {
                                                // Build SSL context that trusts all certs
                                                // This is safe for internal service-to-service calls within VPC
                                                spec.sslContext(SslContextBuilder
                                                                .forClient()
                                                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                                                .build());
                                        } catch (SSLException e) {
                                                throw new RuntimeException(e);
                                        }
                                })
                                // Disable hostname verification after SSL handler is added
                                .doOnChannelInit((observer, channel, remoteAddress) -> {
                                        channel.pipeline().addFirst("ssl-hostname-disabler",
                                                        new io.netty.channel.ChannelInboundHandlerAdapter() {
                                                                @Override
                                                                public void channelActive(
                                                                                io.netty.channel.ChannelHandlerContext ctx)
                                                                                throws Exception {
                                                                        var sslHandler = ctx.pipeline().get(
                                                                                        io.netty.handler.ssl.SslHandler.class);
                                                                        if (sslHandler != null) {
                                                                                var sslEngine = sslHandler.engine();
                                                                                var sslParams = sslEngine
                                                                                                .getSSLParameters();
                                                                                sslParams.setEndpointIdentificationAlgorithm(
                                                                                                null); // Disable
                                                                                                       // hostname check
                                                                                sslEngine.setSSLParameters(sslParams);
                                                                        }
                                                                        super.channelActive(ctx);
                                                                        ctx.pipeline().remove(this); // Remove handler
                                                                                                     // after use
                                                                }
                                                        });
                                })
                                .wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG)
                                .option(ChannelOption.SO_KEEPALIVE, true)
                                .option(ChannelOption.TCP_NODELAY, true);

                return WebClient.builder()
                                .exchangeStrategies(strategies)
                                .clientConnector(new ReactorClientHttpConnector(httpClient));
        }
}
