package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.SkillDefinitionDTO;
import org.lite.gateway.dto.CallerParams;
import org.lite.gateway.entity.ToolDefinition;
import org.lite.gateway.service.ToolRegistryService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.ToolExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/tools")
@Slf4j
@RequiredArgsConstructor
public class ToolController {

    @Value("${linqra.base-url:https://linqra.com}")
    private String baseUrl;

    private final ToolRegistryService toolRegistryService;
    private final TeamContextService teamContextService;
    private final ToolExecutionService toolExecutionService;
    private final org.lite.gateway.service.UserContextService userContextService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/skills")
    public Flux<SkillDefinitionDTO> getSkills() {

        return toolRegistryService.getAllPublicTools()
                .map(this::mapToolToSkill);
    }

    @GetMapping("/skills/{toolId}")
    public Mono<SkillDefinitionDTO> getSkill(@PathVariable String toolId) {

        return toolRegistryService.getTool(toolId)
                .map(this::mapToolToSkill);
    }

    private SkillDefinitionDTO mapToolToSkill(ToolDefinition tool) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());

        if (tool.getInputSchema() != null && !tool.getInputSchema().isBlank()) {
            try {
                schema = objectMapper.readValue(tool.getInputSchema(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.error("Failed to parse input schema for tool {}", tool.getToolId(), e);
            }
        }

        String safeName = (tool.getToolId() == null ? "tool" : tool.getToolId().replace(".", "_"));
        String desc = tool.getDescription() == null ? "" : tool.getDescription();

        return SkillDefinitionDTO.builder()
                .toolId(tool.getToolId())
                .executionUrl(baseUrl + "/api/tools/" + tool.getToolId() + "/execute")
                .executionMethod("POST")
                .openai(SkillDefinitionDTO.OpenAiSkill.builder()
                        .type("function")
                        .function(SkillDefinitionDTO.OpenAiFunction.builder()
                                .name(safeName)
                                .description(desc)
                                .parameters(schema)
                                .build())
                        .build())
                .anthropic(SkillDefinitionDTO.AnthropicSkill.builder()
                        .name(safeName)
                        .description(desc)
                        .input_schema(schema)
                        .build())
                .mcp(SkillDefinitionDTO.McpSkill.builder()
                        .name(safeName)
                        .description(desc)
                        .inputSchema(schema)
                        .build())
                .build();
    }

    @GetMapping
    public Flux<ToolDefinition> getAllTools(
            @RequestParam(required = false) String teamId,
            ServerWebExchange exchange) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMapMany(auth -> {

                    Flux<ToolDefinition> tools;
                    if (teamId != null && !teamId.isEmpty()) {
                        tools = toolRegistryService.getToolsByTeam(teamId);
                    } else {
                        tools = teamContextService.getTeamFromContext(exchange)
                                .flatMapMany(contextTeamId -> toolRegistryService.getToolsByTeam(contextTeamId))
                                .switchIfEmpty(toolRegistryService.getAllTools());
                    }

                    return tools;
                })
                .switchIfEmpty(toolRegistryService.getAllPublicTools());
    }

    @GetMapping("/{toolId}")
    public Mono<ToolDefinition> getTool(@PathVariable String toolId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {

                    Mono<ToolDefinition> tool = toolRegistryService.getTool(toolId);
                    return tool;
                })
                .switchIfEmpty(toolRegistryService.getTool(toolId));
    }

    @PostMapping
    public Mono<ToolDefinition> registerTool(
            @Valid @RequestBody ToolDefinition tool,
            ServerWebExchange exchange) {

        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> {
                    log.info("Registering tool '{}' for team {}", tool.getToolId(), teamId);
                    tool.setTeamId(teamId); // Set teamId from context
                    return toolRegistryService.registerTool(tool);
                });
    }

    @PutMapping("/{toolId}")
    public Mono<ToolDefinition> updateTool(
            @PathVariable String toolId,
            @Valid @RequestBody ToolDefinition tool,
            ServerWebExchange exchange) {

        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> {
                    log.info("Updating tool '{}' for team {}", toolId, teamId);
                    // Optionally verify tool belongs to this team if needed
                    return toolRegistryService.updateTool(toolId, tool);
                });
    }

    @DeleteMapping("/{toolId}")
    public Mono<Void> deleteTool(
            @PathVariable String toolId,
            ServerWebExchange exchange) {

        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> {
                    log.info("Deleting tool '{}' for team {}", toolId, teamId);
                    return toolRegistryService.deleteTool(toolId);
                });
    }

    @PostMapping("/{toolId}/execute")
    public Mono<LinqResponse> execute(
            @PathVariable String toolId,
            @RequestParam(required = false) Boolean refresh,
            @RequestBody(required = false) Map<String, Object> params,
            ServerWebExchange exchange) {

        Map<String, Object> finalParams = (params != null) ? params : new HashMap<>();

        return userContextService.getCurrentUsername(exchange)
                .onErrorResume(e -> Mono.just("anonymous"))
                .flatMap(triggeredBy -> {
                    log.info("Attempting to execute toolId: '{}' (triggeredBy: {}, refresh: {})", toolId, triggeredBy, refresh);
                    return toolRegistryService.getTool(toolId, refresh != null && refresh)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("Execution failed: ToolId '{}' not found in registry", toolId);
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Tool not found"));
                        }))
                        .flatMap(tool -> {
                            String visibility = tool.getVisibility() != null ? tool.getVisibility() : "PRIVATE";
                            log.info("Decision: Tool '{}' visibility is {}. User: {}", tool.getToolId(), visibility, triggeredBy);
                            
                            if ("PUBLIC".equalsIgnoreCase(visibility)) {
                                String teamIdFromTool = tool.getTeamId();
                                log.info("Executing PUBLIC tool '{}'. Routing via owner teamId: {}", toolId, teamIdFromTool);
                                if (teamIdFromTool == null) {
                                    log.error("Fatal error: PUBLIC tool '{}' has no teamId in database!", toolId);
                                    return Mono.error(new RuntimeException("Configuration error: Public tool owner missing"));
                                }
                                return executeWithTeamId(toolId, teamIdFromTool, finalParams, triggeredBy, refresh != null && refresh);
                            } else {
                                log.info("Executing PRIVATE tool '{}', fetching requester team context", toolId);
                                return teamContextService.getTeamFromContext(exchange)
                                        .doOnNext(teamIdCtx -> log.info("Context found for tool execution: {}", teamIdCtx))
                                        .switchIfEmpty(Mono.defer(() -> {
                                            log.error("Access Denied: Private tool '{}' called without valid API Key or Team Context", toolId);
                                            return Mono.error(new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "API Key or Team Context required for private tool"));
                                        }))
                                        .flatMap(teamIdCtx -> {
                                            log.info("Routing PRIVATE tool execution for team: {}", teamIdCtx);
                                            return executeWithTeamId(toolId, teamIdCtx, finalParams, triggeredBy, refresh != null && refresh);
                                        });
                            }
                        });
                });
    }

    private Mono<LinqResponse> executeWithTeamId(String toolId, String teamId, Map<String, Object> params,
            String triggeredBy, boolean refresh) {
        Map<String, Object> executionParams = new HashMap<>(params);
        executionParams.put("teamId", teamId);

        // Extract agent context from params if present (populated by agent executors or
        // frontend)
        String agentId = (String) params.get("agentId");
        String agentName = (String) params.get("agentName");
        String agentTaskId = (String) params.get("agentTaskId");
        String agentTaskName = (String) params.get("agentTaskName");
        String agentExecutionId = (String) params.get("agentExecutionId");
        String executionSource = (String) params.getOrDefault("executionSource",
                agentId != null ? "agent" : "manual");

        CallerParams callerParams = CallerParams.builder()
                .triggeredBy(triggeredBy)
                .agentId(agentId)
                .agentName(agentName)
                .agentTaskId(agentTaskId)
                .agentTaskName(agentTaskName)
                .agentExecutionId(agentExecutionId)
                .executionSource(executionSource)
                .build();

        LinqRequest request = LinqRequest.builder()
                .link(LinqRequest.Link.builder()
                        .target("tool")
                        .action("execute")
                        .build())
                .query(LinqRequest.Query.builder()
                        .intent(toolId)
                        .params(executionParams)
                        .build())
                .executedBy(teamId)
                .build();

        return toolExecutionService.executeTool(request, callerParams);
    }

    @PostMapping("/{toolId}/test")
    public Mono<LinqResponse> testTool(
            @PathVariable String toolId,
            @RequestBody Map<String, Object> request,
            ServerWebExchange exchange) {

        log.info("Test execution for tool '{}' requested", toolId);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    // Check if user is an admin
                    boolean isAdmin = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_gateway_admin") ||
                                    a.getAuthority().equals("gateway_admin") ||
                                    a.getAuthority().equals("ROLE_ADMIN"));

                    if (!isAdmin) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Only administrators can execute tool test configurations"));
                    }

                    // Safe extraction of tool and params from the request map
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> toolMap = (Map<String, Object>) request.get("tool");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) request.get("params");

                        if (toolMap == null) {
                            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "Missing tool definition in test request"));
                        }

                        ToolDefinition tool = new ToolDefinition();
                        tool.setToolId(toolId);

                        @SuppressWarnings("unchecked")
                        Map<String, Object> linqConfig = (Map<String, Object>) toolMap.get("linqConfig");
                        if (linqConfig == null) {
                            linqConfig = (Map<String, Object>) toolMap.get("linq_config");
                        }
                        tool.setLinqConfig(linqConfig);

                        tool.setInputSchema((String) toolMap.get("inputSchema"));
                        tool.setOutputSchema((String) toolMap.get("outputSchema"));

                        return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> {
                                    log.info("Dry-running tool '{}' for team {}", toolId, teamId);

                                    // Inject teamId into params for dry-run (critical for Polaris/Linq services)
                                    Map<String, Object> executionParams = params != null ? new HashMap<>(params)
                                            : new HashMap<>();
                                    executionParams.put("teamId", teamId);

                                    return toolExecutionService.executeToolDryRun(tool, executionParams, teamId);
                                })
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                        "Team context required for test execution")));
                    } catch (Exception e) {
                        log.error("Failed to parse test request for tool {}", toolId, e);
                        return Mono.error(
                                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid test request format"));
                    }
                });
    }
}
