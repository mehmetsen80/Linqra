package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.CallerParams;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.ToolDefinition;
import org.lite.gateway.entity.ToolExecution;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.model.ExecutionStatus;
import org.lite.gateway.repository.ToolExecutionRepository;
import org.lite.gateway.service.LinqMicroService;
import org.lite.gateway.service.ToolExecutionService;
import org.lite.gateway.service.ToolRegistryService;
import org.lite.gateway.util.AuditLogHelper;
import org.lite.gateway.validation.ToolParameterValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolExecutionServiceImpl implements ToolExecutionService {

    private final ToolRegistryService toolRegistryService;
    private final LinqMicroService linqMicroService;
    private final ToolExecutionRepository toolExecutionRepository;
    private final AuditLogHelper auditLogHelper;
    private final ToolParameterValidationService parameterValidationService;

    @Override
    public Mono<LinqResponse> executeTool(LinqRequest request, CallerParams callerParams) {
        return executeTool(request, callerParams, false);
    }

    @Override
    public Mono<LinqResponse> executeTool(LinqRequest request, CallerParams callerParams, boolean forceRefresh) {
        String toolId = request.getQuery().getIntent();
        String executedBy = request.getExecutedBy();
        String executionId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Starting tool execution: toolId={}, executionId={}, refresh={}", toolId, executionId, forceRefresh);

        return toolRegistryService.getTool(toolId, forceRefresh)
                .switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Tool not found: " + toolId)))
                .flatMap(tool -> {
                    if (tool.getLinqConfig() == null || (tool.getLinqConfig() instanceof Map && ((Map)tool.getLinqConfig()).isEmpty())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Tool has no linqConfig: " + toolId));
                    }

                    Map<String, Object> linqParams = (request.getQuery() != null) ? request.getQuery().getParams()
                            : new HashMap<>();

                    return parameterValidationService.validate(linqParams, tool)
                            .flatMap(valResult -> {
                                if (!valResult.isValid()) {
                                    String errorMsg = String.join("; ", valResult.getErrors());
                                    Map<String, Object> auditCtx = buildAuditContext(tool, executionId, executedBy,
                                            linqParams, callerParams);
                                    auditCtx.put("validationErrors", valResult.getErrors());

                                    return auditLogHelper.logDetailedEvent(
                                            AuditEventType.TOOL_EXECUTION_BLOCKED,
                                            AuditActionType.READ,
                                            AuditResourceType.TOOL_EXECUTION,
                                            executionId,
                                            "Validation failed: " + errorMsg,
                                            auditCtx)
                                            .then(Mono.error(
                                                    new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMsg)));
                                }

                                ToolExecution execution = ToolExecution.builder()
                                        .executionId(executionId)
                                        .toolId(tool.getToolId())
                                        .toolName(tool.getName())
                                        .teamId(extractTeamId(linqParams, executedBy))
                                        .executedBy(executedBy)
                                        .callerParams(callerParams)
                                        .visibility(tool.getVisibility())
                                        .request(request)
                                        .status(ExecutionStatus.IN_PROGRESS)
                                        .executedAt(startTime)
                                        .build();

                                return toolExecutionRepository.save(execution)
                                        .flatMap(saved -> performExecution(tool, linqParams, executedBy, callerParams,
                                                startTime, saved));
                            });
                })
                .onErrorResume(error -> {
                    if (error instanceof ResponseStatusException) {
                        return Mono.error(error);
                    }
                    long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("toolId", toolId);
                    ctx.put("error", error.getMessage());
                    ctx.put("durationMs", durationMs);

                    return auditLogHelper.logDetailedEvent(
                            AuditEventType.TOOL_EXECUTION_FAILED,
                            AuditActionType.READ,
                            AuditResourceType.TOOL_EXECUTION,
                            executionId,
                            "Initiation failed: " + error.getMessage(),
                            ctx, null, null, AuditResultType.FAILED)
                            .then(Mono.error(error));
                });
    }

    private Mono<LinqResponse> performExecution(ToolDefinition tool, Map<String, Object> params, String executedBy,
            CallerParams cp, LocalDateTime start, ToolExecution saved) {
        Map<String, Object> auditCtx = buildAuditContext(tool, saved.getExecutionId(), executedBy, params, cp);

        return auditLogHelper.logDetailedEvent(AuditEventType.TOOL_EXECUTION_STARTED, AuditActionType.READ,
                AuditResourceType.TOOL_EXECUTION, saved.getExecutionId(), "Execution started", auditCtx)
                .onErrorResume(e -> Mono.empty())
                .then(executeViaLinqConfig(tool, params, executedBy, cp))
                .flatMap(resp -> handleSuccess(tool, saved, start, resp, cp, executedBy, params))
                .onErrorResume(err -> handleFailure(tool, saved, start, err, cp, executedBy, params));
    }

    private Mono<LinqResponse> handleSuccess(ToolDefinition tool, ToolExecution saved, LocalDateTime start,
            LinqResponse resp, CallerParams cp, String executedBy, Map<String, Object> params) {
        long duration = java.time.Duration.between(start, LocalDateTime.now()).toMillis();
        saved.setStatus(ExecutionStatus.SUCCESS);
        saved.setResponse(resp);
        saved.setDurationMs(duration);

        return toolExecutionRepository.save(saved)
                .flatMap(done -> {
                    Map<String, Object> ctx = buildAuditContext(tool, saved.getExecutionId(), executedBy, params, cp);
                    ctx.put("durationMs", duration);
                    return auditLogHelper
                            .logDetailedEvent(AuditEventType.TOOL_EXECUTION_COMPLETED, AuditActionType.READ,
                                    AuditResourceType.TOOL_EXECUTION, saved.getExecutionId(), "Success", ctx, null,
                                    null, AuditResultType.SUCCESS)
                            .thenReturn(resp);
                });
    }

    private Mono<LinqResponse> handleFailure(ToolDefinition tool, ToolExecution saved, LocalDateTime start,
            Throwable err, CallerParams cp, String executedBy, Map<String, Object> params) {
        long duration = java.time.Duration.between(start, LocalDateTime.now()).toMillis();
        saved.setStatus(ExecutionStatus.FAILED);
        saved.setErrorMessage(err.getMessage());
        saved.setDurationMs(duration);

        return toolExecutionRepository.save(saved)
                .then(Mono.defer(() -> {
                    Map<String, Object> ctx = buildAuditContext(tool, saved.getExecutionId(), executedBy, params, cp);
                    ctx.put("durationMs", duration);
                    ctx.put("error", err.getMessage());
                    return auditLogHelper.logDetailedEvent(AuditEventType.TOOL_EXECUTION_FAILED, AuditActionType.READ,
                            AuditResourceType.TOOL_EXECUTION, saved.getExecutionId(), "Failed: " + err.getMessage(),
                            ctx,
                            null, null, AuditResultType.FAILED);
                }))
                .onErrorResume(e -> Mono.empty())
                .then(Mono.error(err));
    }

    @Override
    public Mono<LinqResponse> executeToolDryRun(ToolDefinition tool, Map<String, Object> params, String executedBy) {
        if (tool.getLinqConfig() == null || tool.getLinqConfig().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No linqConfig for dry-run"));
        }
        return executeViaLinqConfig(tool, params, executedBy, null);
    }

    @SuppressWarnings("unchecked")
    private Mono<LinqResponse> executeViaLinqConfig(ToolDefinition tool, Map<String, Object> callerParamsMap,
            String executedBy, CallerParams callerParams) {
        Map<String, Object> config = tool.getLinqConfig();
        Map<String, Object> linkMap = (Map<String, Object>) config.get("link");
        Map<String, Object> queryMap = (Map<String, Object>) config.get("query");

        LinqRequest linqRequest = LinqRequest.builder()
                .link(LinqRequest.Link.builder()
                        .target((String) linkMap.get("target"))
                        .action((String) linkMap.get("action"))
                        .build())
                .query(LinqRequest.Query.builder()
                        .intent((String) queryMap.get("intent"))
                        .params(callerParamsMap)
                        .build())
                .executedBy(executedBy)
                .build();

        return linqMicroService.execute(linqRequest);
    }

    private String extractTeamId(Map<String, Object> params, String fallback) {
        if (params != null) {
            Object teamId = params.get("teamId");
            if (teamId instanceof String) {
                return (String) teamId;
            }
        }
        return fallback;
    }

    private Map<String, Object> buildAuditContext(ToolDefinition tool, String executionId, String executedBy,
            Map<String, Object> params, CallerParams callerParams) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("executionId", executionId);
        ctx.put("toolId", tool.getToolId());
        ctx.put("toolName", tool.getName());
        ctx.put("teamId", tool.getTeamId());
        ctx.put("executedBy", executedBy);

        if (callerParams != null) {
            ctx.put("triggeredBy", callerParams.getTriggeredBy());
            if (callerParams.getAgentId() != null) {
                ctx.put("agentId", callerParams.getAgentId());
                ctx.put("agentName", callerParams.getAgentName());
                ctx.put("agentTaskId", callerParams.getAgentTaskId());
                ctx.put("agentExecutionId", callerParams.getAgentExecutionId());
                ctx.put("executionSource", callerParams.getExecutionSource());
            }
        }
        ctx.put("visibility", tool.getVisibility());
        ctx.put("category", tool.getCategory());
        if (params != null && !params.isEmpty()) {
            ctx.put("paramKeys", params.keySet());
        }
        return ctx;
    }
}
