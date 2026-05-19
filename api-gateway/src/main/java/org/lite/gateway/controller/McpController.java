package org.lite.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.CallerParams;
import org.lite.gateway.dto.mcp.McpMessage;
import org.lite.gateway.dto.mcp.McpTool;
import org.lite.gateway.entity.ToolDefinition;
import org.lite.gateway.service.ToolRegistryService;
import org.lite.gateway.service.ToolExecutionService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/mcp")
@Slf4j
@RequiredArgsConstructor
public class McpController {

    private final ToolRegistryService toolRegistryService;
    private final ToolExecutionService toolExecutionService;
    private final TeamContextService teamContextService;
    private final UserContextService userContextService;
    private final ObjectMapper objectMapper;

    // Active session mappings (can be used for tracking stream heartbeat if needed)
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    /**
     * GET /api/mcp/sse
     * Establishes the persistent Server-Sent Events channel.
     * The first event sent MUST be an 'endpoint' event informing the client where to send POST messages.
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> establishSse(ServerWebExchange exchange) {
        String sessionId = UUID.randomUUID().toString();
        log.info("Establishing persistent MCP SSE connection. SessionId: {}", sessionId);

        // Keep session recorded
        activeSessions.put(sessionId, sessionId);

        // Define base message endpoint for SSE transport
        String messageEndpoint = "/api/mcp/message?sessionId=" + sessionId;

        // Immediately send the mandatory 'endpoint' event
        ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                .event("endpoint")
                .data(messageEndpoint)
                .build();

        // Send a regular heartbeat every 15 seconds to prevent gateway timeouts
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("")
                        .build())
                .doOnCancel(() -> {
                    log.info("MCP SSE Connection closed for session: {}", sessionId);
                    activeSessions.remove(sessionId);
                });

        return Flux.just(endpointEvent).concatWith(heartbeat);
    }

    /**
     * POST /api/mcp/message
     * Main message dispatch endpoint where JSON-RPC requests are POSTed.
     */
    @PostMapping("/message")
    public Mono<McpMessage> handleMcpMessage(
            @RequestParam(required = false) String sessionId,
            @RequestBody McpMessage mcpRequest,
            ServerWebExchange exchange) {

        log.info("Received MCP JSON-RPC message (session: {}): method={}, id={}", sessionId, mcpRequest.getMethod(), mcpRequest.getId());

        if (mcpRequest.getMethod() == null) {
            return Mono.just(createJsonRpcError(mcpRequest.getId(), -32600, "Invalid Request: missing method"));
        }

        switch (mcpRequest.getMethod()) {
            case "tools/list":
                return handleToolsList(mcpRequest.getId(), exchange);

            case "tools/call":
                return handleToolsCall(mcpRequest.getId(), mcpRequest.getParams(), exchange);

            default:
                log.warn("Unsupported MCP method: {}", mcpRequest.getMethod());
                return Mono.just(createJsonRpcError(mcpRequest.getId(), -32601, "Method not found: " + mcpRequest.getMethod()));
        }
    }

    /**
     * Handles the "tools/list" JSON-RPC method call.
     * Exposes public tools from the Linqra ToolRegistry mapped to the MCP DTO representation.
     */
    private Mono<McpMessage> handleToolsList(String requestId, ServerWebExchange exchange) {
        log.info("Processing tools/list query");
        return toolRegistryService.getAllPublicTools()
                .map(this::mapToMcpTool)
                .collectList()
                .map(toolsList -> {
                    Map<String, Object> resultPayload = new HashMap<>();
                    resultPayload.put("tools", toolsList);

                    return McpMessage.builder()
                            .id(requestId)
                            .result(resultPayload)
                            .build();
                });
    }

    /**
     * Handles the "tools/call" JSON-RPC method call.
     * Resolves the target Linqra tool, maps arguments, executes it via ToolExecutionService,
     * and formats results back to standard MCP content structures.
     */
    @SuppressWarnings("unchecked")
    private Mono<McpMessage> handleToolsCall(String requestId, Object paramsObj, ServerWebExchange exchange) {
        if (paramsObj == null) {
            return Mono.just(createJsonRpcError(requestId, -32602, "Invalid params: params object is missing"));
        }

        try {
            JsonNode paramsNode = objectMapper.valueToTree(paramsObj);
            String name = paramsNode.path("name").asText();
            JsonNode argumentsNode = paramsNode.path("arguments");

            if (name == null || name.isBlank()) {
                return Mono.just(createJsonRpcError(requestId, -32602, "Invalid params: 'name' is required"));
            }

            log.info("Routing MCP tool call '{}'", name);

            Map<String, Object> arguments = new HashMap<>();
            if (argumentsNode != null && !argumentsNode.isMissingNode() && argumentsNode.isObject()) {
                arguments = objectMapper.convertValue(argumentsNode, Map.class);
            }

            final Map<String, Object> finalArgs = arguments;

            return userContextService.getCurrentUsername(exchange)
                    .onErrorResume(e -> Mono.just("anonymous"))
                    .flatMap(username -> toolRegistryService.getTool(name, true)
                            .switchIfEmpty(Mono.defer(() -> toolRegistryService.getTool(name.replace("_", "."), true)))
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Tool not found in registry: " + name)))
                            .flatMap(tool -> {
                                String resolvedToolId = tool.getToolId();
                                String visibility = tool.getVisibility() != null ? tool.getVisibility() : "PRIVATE";
                                log.info("MCP Decision: Tool '{}' visibility is {}. Requester: {}", resolvedToolId, visibility, username);

                                Mono<String> teamIdResolver;
                                if ("PUBLIC".equalsIgnoreCase(visibility)) {
                                    String teamIdFromTool = tool.getTeamId();
                                    log.info("MCP Executing PUBLIC tool '{}'. Routing via owner teamId: {}", resolvedToolId, teamIdFromTool);
                                    if (teamIdFromTool == null) {
                                        return Mono.error(new RuntimeException("Configuration error: Public tool owner missing"));
                                    }
                                    teamIdResolver = Mono.just(teamIdFromTool);
                                } else {
                                    log.info("MCP Executing PRIVATE tool '{}', fetching requester team context", resolvedToolId);
                                    teamIdResolver = teamContextService.getTeamFromContext(exchange)
                                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key or Team Context required for private tool")));
                                }

                                return teamIdResolver.flatMap(teamId -> {
                                    log.info("MCP Routing execution with resolved teamId: {}", teamId);
                                    Map<String, Object> executionParams = new HashMap<>(finalArgs);
                                    executionParams.put("teamId", teamId);

                                    CallerParams callerParams = CallerParams.builder()
                                            .triggeredBy(username)
                                            .executionSource("mcp")
                                            .build();

                                    LinqRequest linqRequest = LinqRequest.builder()
                                            .link(LinqRequest.Link.builder()
                                                    .target("tool")
                                                    .action("execute")
                                                    .build())
                                            .query(LinqRequest.Query.builder()
                                                    .intent(resolvedToolId)
                                                    .params(executionParams)
                                                    .build())
                                            .executedBy(teamId)
                                            .build();

                                    return toolExecutionService.executeTool(linqRequest, callerParams)
                                            .map(linqResponse -> formatLinqResponseToMcp(requestId, linqResponse))
                                            .onErrorResume(err -> {
                                                log.error("Internal MCP tool execution failed: {}", err.getMessage());
                                                return Mono.just(createJsonRpcError(requestId, -32000, "Internal Execution Error: " + err.getMessage()));
                                            });
                                });
                            }));

        } catch (Exception e) {
            log.error("Failed to parse tools/call params", e);
            return Mono.just(createJsonRpcError(requestId, -32602, "Invalid params parsing error: " + e.getMessage()));
        }
    }

    /**
     * Formats internal ToolDefinition schema to MCP Tool metadata.
     */
    private McpTool mapToMcpTool(ToolDefinition tool) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());

        if (tool.getInputSchema() != null && !tool.getInputSchema().isBlank()) {
            try {
                schema = objectMapper.readValue(tool.getInputSchema(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("Failed to parse input schema for MCP tool mapping: {}", tool.getToolId(), e);
            }
        }

        // Map dotted name to underscore for MCP compatibility
        String mcpName = tool.getToolId().replace(".", "_");

        return McpTool.builder()
                .name(mcpName)
                .description(tool.getDescription() != null ? tool.getDescription() : "")
                .inputSchema(schema)
                .build();
    }

    private McpMessage formatLinqResponseToMcp(String requestId, LinqResponse linqResponse) {
        List<Map<String, Object>> contentList = new ArrayList<>();

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");

        // Format LinqResponse nicely into a readable Markdown response block
        StringBuilder sb = new StringBuilder();
        sb.append("### Linqra Execution Success\n");
        if (linqResponse.getMetadata() != null) {
            sb.append("- **Status:** ").append(linqResponse.getMetadata().getStatus()).append("\n");
            if (linqResponse.getMetadata().getSource() != null) {
                sb.append("- **Source:** ").append(linqResponse.getMetadata().getSource()).append("\n");
            }
        }

        if (linqResponse.getResult() != null) {
            sb.append("\n#### Output Results:\n");
            try {
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(linqResponse.getResult());
                sb.append("```json\n").append(prettyJson).append("\n```");
            } catch (Exception e) {
                sb.append(linqResponse.getResult().toString());
            }
        }

        textContent.put("text", sb.toString());
        contentList.add(textContent);

        Map<String, Object> resultPayload = new HashMap<>();
        resultPayload.put("content", contentList);

        return McpMessage.builder()
                .id(requestId)
                .result(resultPayload)
                .build();
    }

    /**
     * Helper to build standard JSON-RPC 2.0 error payloads.
     */
    private McpMessage createJsonRpcError(String requestId, int code, String message) {
        return McpMessage.builder()
                .id(requestId)
                .error(McpMessage.McpError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }
}
