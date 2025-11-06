package org.lite.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import org.lite.gateway.service.KnowledgeHubDocumentMetaDataService;
import org.lite.gateway.service.KnowledgeHubDocumentProcessingService;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import reactor.core.publisher.Mono;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired(required = false)
    private KnowledgeHubDocumentProcessingService documentProcessingService;
    
    @Autowired(required = false)
    private KnowledgeHubDocumentService documentService;
    
    @Autowired(required = false)
    private KnowledgeHubDocumentMetaDataService metadataService;

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
                """
                        MESSAGE
                        destination:%s
                        content-type:application/json
                        subscription:sub-0
                        message-id:%s
                        
                        %s
                        \u0000""",
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
                        """
                                CONNECTED
                                version:1.2
                                heart-beat:0,0
                                
                                \u0000"""
                );
            } else if (payload.startsWith("SUBSCRIBE")) {
                log.debug("Handling SUBSCRIBE frame for session: {}", session.getId());
                return session.textMessage(
                        """
                                RECEIPT
                                receipt-id:sub-0
                                
                                \u0000"""
                );
            } else if (payload.startsWith("SEND")) {
                // Handle SEND command for document processing
                log.debug("Handling SEND frame for session: {}", session.getId());
                handleSendCommand(session, payload);
                return session.textMessage(
                        """
                                RECEIPT
                                receipt-id:send-0
                                
                                \u0000"""
                );
            } else if (payload.startsWith("DISCONNECT")) {
                sessions.remove(session.getId());
                return session.textMessage(
                        """
                                RECEIPT
                                receipt-id:disconnect-0
                                
                                \u0000"""
                );
            }

            return session.textMessage(
                    """
                            ERROR
                            message:Unknown command
                            
                            \u0000"""
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
    
    private void handleSendCommand(WebSocketSession session, String payload) {
        try {
            // Parse STOMP SEND frame
            Map<String, String> headers = new HashMap<>();
            String body = "";
            
            String[] lines = payload.split("\n");
            int i = 1;
            
            // Parse headers
            while (i < lines.length && !lines[i].isEmpty()) {
                int colonIndex = lines[i].indexOf(':');
                if (colonIndex > 0) {
                    String key = lines[i].substring(0, colonIndex).trim();
                    String value = lines[i].substring(colonIndex + 1).trim();
                    headers.put(key, value);
                }
                i++;
            }
            
            // Skip empty line and get body
            i++;
            if (i < lines.length) {
                body = lines[i].replace("\u0000", "").trim();
            }
            
            String destination = headers.get("destination");
            
            if (destination != null && destination.startsWith("/app/document-processing")) {
                // Handle document processing command asynchronously
                log.info("Received document processing command: {}", body);
                processDocumentProcessingCommand(body).subscribe(
                    null,
                    error -> log.error("Error processing document command: {}", error.getMessage(), error)
                );
            } else if (destination != null && destination.startsWith("/app/metadata-extraction")) {
                // Handle metadata extraction command asynchronously
                log.info("Received metadata extraction command: {}", body);
                processMetadataExtractionCommand(body).subscribe(
                    null,
                    error -> log.error("Error processing metadata extraction command: {}", error.getMessage(), error)
                );
            }
        } catch (Exception e) {
            log.error("Error processing SEND command: {}", e.getMessage(), e);
        }
    }
    
    private Mono<Void> processDocumentProcessingCommand(String body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> command = objectMapper.readValue(body, Map.class);
            String documentId = command.get("documentId");
            String status = command.get("status");
            String teamId = command.get("teamId");
            
            if (documentId == null || status == null) {
                log.warn("Invalid document processing command: missing documentId or status");
                return Mono.empty();
            }
            
            log.info("Processing document command - documentId: {}, status: {}, teamId: {}", 
                    documentId, status, teamId);
            
            if (documentProcessingService == null || documentService == null) {
                log.warn("Document processing services not available");
                return Mono.empty();
            }
            
            if (teamId != null) {
                // Update document status first, then trigger processing
                return documentService.updateStatus(documentId, status, null)
                    .then(documentProcessingService.processDocument(documentId, teamId))
                    .doOnSuccess(v -> log.info("Document processing triggered via WebSocket for document: {}", documentId))
                    .doOnError(error -> log.error("Error processing document via WebSocket: {}", documentId, error))
                    .onErrorResume(error -> Mono.empty())
                    .then();
            }
            
            return Mono.empty();
        } catch (Exception e) {
            log.error("Error processing document processing command: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }
    
    private Mono<Void> processMetadataExtractionCommand(String body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> command = objectMapper.readValue(body, Map.class);
            String documentId = command.get("documentId");
            String status = command.get("status");
            String teamId = command.get("teamId");
            
            if (documentId == null || status == null) {
                log.warn("Invalid metadata extraction command: missing documentId or status");
                return Mono.empty();
            }
            
            log.info("Processing metadata extraction command - documentId: {}, status: {}, teamId: {}", 
                    documentId, status, teamId);
            
            if (metadataService == null || documentService == null) {
                log.warn("Metadata or document service not available");
                return Mono.empty();
            }
            
            if (teamId != null) {
                // Update document status first, then trigger metadata extraction
                return documentService.updateStatus(documentId, status, null)
                    .then(metadataService.extractMetadata(documentId, teamId))
                    .doOnSuccess(metadata -> log.info("Metadata extraction completed via WebSocket for document: {}", documentId))
                    .doOnError(error -> log.error("Error extracting metadata via WebSocket: {}", documentId, error))
                    .onErrorResume(error -> Mono.empty())
                    .then();
            }
            
            return Mono.empty();
        } catch (Exception e) {
            log.error("Error processing metadata extraction command: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}