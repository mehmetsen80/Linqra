package org.lite.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Slf4j
public class WebSocketConfig {

    private final Sinks.Many<String> messagesSink = Sinks.many()
        .multicast()
        .onBackpressureBuffer(1024, false);
    private final Sinks.Many<String> executionMessagesSink = Sinks.many()
        .multicast()
        .onBackpressureBuffer(1024, false);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Bean("healthMessageChannel")
    public MessageChannel messageChannel(ObjectMapper objectMapper) {
        return new AbstractMessageChannel() {
            @Override
            protected boolean sendInternal(@NonNull Message<?> message, long timeout) {
                try {
                    // Check if there are any active sessions
                    if (sessions.isEmpty()) {
                        log.debug("No active WebSocket sessions, skipping message emission");
                        return true;
                    }

                    String jsonPayload = objectMapper.writeValueAsString(message.getPayload());
                    Sinks.EmitResult result;
                    int attempts = 0;
                    do {
                        result = messagesSink.tryEmitNext(jsonPayload);
                        if (result.isFailure()) {
                            attempts++;
                            log.warn("Attempt {} failed to emit message. Reason: {}. Retrying...", 
                                attempts, result.name());
                            Thread.sleep(100); // Small delay before retry
                        }
                    } while (result.isFailure() && attempts < 3);
                    
                    if (result.isFailure()) {
                        log.error("Failed to emit message after {} attempts. Reason: {}. Payload: {}", 
                            attempts, result.name(), jsonPayload);
                        return false;
                    }
                    log.debug("Successfully emitted message after {} attempts", attempts + 1);
                    return true;
                } catch (Exception e) {
                    log.error("Error converting message to JSON", e);
                    return false;
                }
            }
        };
    }

    @Bean("executionMessageChannel")
    public MessageChannel executionMessageChannel(ObjectMapper objectMapper) {
        return new AbstractMessageChannel() {
            @Override
            protected boolean sendInternal(@NonNull Message<?> message, long timeout) {
                try {
                    // Check if there are any active sessions
                    log.info("📊 WebSocket sessions count: {}", sessions.size());
                    if (sessions.isEmpty()) {
                        log.info("📊 No active WebSocket sessions, skipping execution message emission");
                        return true;
                    }

                    String jsonPayload = objectMapper.writeValueAsString(message.getPayload());
                    log.info("📊 WebSocket sending JSON payload: {}", jsonPayload);
                    Sinks.EmitResult result;
                    int attempts = 0;
                    do {
                        result = executionMessagesSink.tryEmitNext(jsonPayload);
                        if (result.isFailure()) {
                            attempts++;
                            log.warn("Attempt {} failed to emit execution message. Reason: {}. Retrying...", 
                                attempts, result.name());
                            Thread.sleep(100); // Small delay before retry
                        }
                    } while (result.isFailure() && attempts < 3);
                    
                    if (result.isFailure()) {
                        log.error("Failed to emit execution message after {} attempts. Reason: {}. Payload: {}", 
                            attempts, result.name(), jsonPayload);
                        return false;
                    }
                    log.debug("Successfully emitted execution message after {} attempts", attempts + 1);
                    return true;
                } catch (Exception e) {
                    log.error("Error converting execution message to JSON", e);
                    return false;
                }
            }
        };
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws-linqra", stompWebSocketHandler());

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1);

        Map<String, CorsConfiguration> corsConfigMap = new HashMap<>();
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.addAllowedOriginPattern("*");
        corsConfig.addAllowedMethod("*");
        corsConfig.addAllowedHeader("*");
        corsConfig.setAllowCredentials(true);
        corsConfigMap.put("/ws-linqra", corsConfig);
        mapping.setCorsConfigurations(corsConfigMap);

        return mapping;
    }

    @Bean
    public WebSocketHandler stompWebSocketHandler() {
        return session -> {
            String sessionId = session.getId();
            sessions.put(sessionId, session);
            log.info("WebSocket session connected: {}", sessionId);

            // Handle outbound messages - merge health and execution updates
            Flux<WebSocketMessage> outbound = Flux.merge(
                    messagesSink.asFlux().map(msg -> createStompFrame(msg, "/topic/health")),
                    executionMessagesSink.asFlux().map(msg -> createStompFrame(msg, "/topic/execution"))
                )
                .onBackpressureBuffer(256)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                    .maxBackoff(Duration.ofSeconds(1))
                    .doBeforeRetry(signal -> 
                        log.warn("Retrying message delivery for session {}, attempt {}", 
                            sessionId, signal.totalRetries() + 1)))
                .doOnNext(message -> log.debug("Processing outbound message for session {}: {}", 
                    sessionId, message))
                .map(message -> {
                    log.debug("Created STOMP frame for session {}: {}", sessionId, message);
                    return session.textMessage(message);
                })
                .doOnSubscribe(sub -> 
                    log.info("Session {} subscribed to message stream", sessionId))
                .doOnCancel(() -> 
                    log.info("Session {} message stream cancelled", sessionId))
                .onErrorContinue((error, obj) -> {
                    log.error("Error in message processing for session {}: {}", 
                        sessionId, error.getMessage());
                })
                .doOnError(error -> log.error("Error in outbound stream for session {}: {}", 
                    sessionId, error.getMessage()));

            // Handle inbound messages
            Flux<WebSocketMessage> inbound = session.receive()
                .doOnNext(msg -> log.debug("Received message from session {}: {}", 
                    sessionId, msg.getPayloadAsText()))
                .map(msg -> handleInboundMessage(session, msg))
                .doOnError(error -> log.error("Error in inbound stream for session {}: {}", 
                    sessionId, error.getMessage()));

            // Clean up on session close
            session.closeStatus()
                .subscribe(status -> {
                    log.info("WebSocket session {} closed with status: {}", sessionId, status);
                    sessions.remove(sessionId);
                });

            return session.send(Flux.merge(inbound, outbound))
                .doOnError(error -> {
                    log.error("Error in session {}: {}", sessionId, error.getMessage());
                    sessions.remove(sessionId);
                });
        };
    }

    private String createStompFrame(String message, String destination) {
        log.debug("Creating STOMP frame for message: {} to destination: {}", message, destination);
        return String.format(
            "MESSAGE\n" +
            "destination:%s\n" +
            "content-type:application/json\n" +
            "subscription:sub-0\n" +
            "message-id:%s\n\n" +
            "%s\n\u0000",
            destination,
            UUID.randomUUID().toString(),
            message
        );
    }

    private WebSocketMessage handleInboundMessage(WebSocketSession session, WebSocketMessage msg) {
        try {
            String payload = msg.getPayloadAsText();
            log.debug("Processing STOMP message: {}", payload);

            if (payload.startsWith("CONNECT")) {
                log.debug("Handling CONNECT frame for session: {}", session.getId());
                return session.textMessage(
                    "CONNECTED\n" +
                    "version:1.2\n" +
                    "heart-beat:0,0\n\n" +
                    "\u0000"
                );
            } else if (payload.startsWith("SUBSCRIBE")) {
                log.debug("Handling SUBSCRIBE frame for session: {}", session.getId());
                return session.textMessage(
                    "RECEIPT\n" +
                    "receipt-id:sub-0\n\n" +
                    "\u0000"
                );
            } else if (payload.startsWith("DISCONNECT")) {
                sessions.remove(session.getId());
                return session.textMessage(
                    "RECEIPT\n" +
                    "receipt-id:disconnect-0\n\n" +
                    "\u0000"
                );
            }

            return session.textMessage(
                "ERROR\n" +
                "message:Unknown command\n\n" +
                "\u0000"
            );
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
            return session.textMessage(
                "ERROR\n" +
                "message:" + e.getMessage() + "\n\n" +
                "\u0000"
            );
        }
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}