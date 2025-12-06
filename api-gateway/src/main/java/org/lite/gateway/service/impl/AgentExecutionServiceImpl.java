package org.lite.gateway.service.impl;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.ExecutionTrigger;

import org.lite.gateway.executor.WorkflowEmbeddedAgentTaskExecutor;
import org.lite.gateway.executor.WorkflowTriggerAgentTaskExecutor;
import org.lite.gateway.executor.WorkflowAdHocAgentTaskExecutor;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.ExecutionMonitoringService;
import org.lite.gateway.service.ExecutionQueueService;
import org.lite.gateway.dto.ExecutionProgressUpdate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.lite.gateway.enums.ExecutionType;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.enums.ExecutionResult;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.util.AuditLogHelper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentExecutionServiceImpl implements AgentExecutionService {

    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final WorkflowTriggerAgentTaskExecutor workflowTriggerExecutor;
    private final WorkflowEmbeddedAgentTaskExecutor workflowEmbeddedExecutor;
    private final WorkflowAdHocAgentTaskExecutor workflowAdhocExecutor;
    private final ExecutionMonitoringService executionMonitoringService;
    private final ExecutionQueueService executionQueueService;
    private final AuditLogHelper auditLogHelper;

    // ==================== EXECUTION MANAGEMENT ====================

    @Override
    public Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy,
            Map<String, Object> inputOverrides) {
        log.info("Starting execution of task {} for agent {} in team {}", taskId, agentId, teamId);
        LocalDateTime startTime = LocalDateTime.now();

        Map<String, Object> overrides = inputOverrides != null ? new HashMap<>(inputOverrides) : new HashMap<>();
        overrides.entrySet().removeIf(entry -> entry.getValue() == null);
        Object questionValue = overrides.get("question");
        if (questionValue instanceof String) {
            String trimmed = ((String) questionValue).trim();
            if (trimmed.isEmpty()) {
                overrides.remove("question");
            } else {
                overrides.put("question", trimmed);
            }
        } else if (questionValue != null) {
            overrides.put("question", questionValue.toString());
        }

        return Mono.zip(
                agentRepository.findById(agentId),
                agentTaskRepository.findById(taskId)).flatMap(tuple -> {
                    Agent agent = tuple.getT1();
                    AgentTask task = tuple.getT2();

                    // Validate task can be executed
                    log.info("Task {} state: enabled={}, cronExpression={}, autoExecute={}, executionTrigger={}",
                            taskId, task.isEnabled(), task.getCronExpression(), task.isAutoExecute(),
                            task.getExecutionTrigger());

                    if (!task.isReadyToExecute()) {
                        log.error(
                                "Task {} is not ready to execute. enabled={}, cronExpression={}, autoExecute={}, executionTrigger={}",
                                taskId, task.isEnabled(), task.getCronExpression(), task.isAutoExecute(),
                                task.getExecutionTrigger());

                        // Log validation failure
                        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                        Map<String, Object> errorContext = new HashMap<>();
                        errorContext.put("agentId", agentId);
                        errorContext.put("agentName", agent.getName());
                        errorContext.put("taskId", taskId);
                        errorContext.put("taskName", task.getName());
                        errorContext.put("teamId", teamId);
                        errorContext.put("executedBy", executedBy);
                        errorContext.put("taskEnabled", task.isEnabled());
                        errorContext.put("cronExpression", task.getCronExpression());
                        errorContext.put("autoExecute", task.isAutoExecute());
                        errorContext.put("executionTrigger",
                                task.getExecutionTrigger() != null ? task.getExecutionTrigger().name() : null);
                        errorContext.put("error", "Task is not ready to execute");
                        errorContext.put("durationMs", durationMs);
                        errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                        return auditLogHelper.logDetailedEvent(
                                AuditEventType.AGENT_TASK_EXECUTION_FAILED,
                                AuditActionType.READ,
                                AuditResourceType.AGENT_TASK_EXECUTION,
                                taskId,
                                String.format("Task execution failed for '%s' - task is not ready to execute",
                                        task.getName()),
                                errorContext,
                                null,
                                null,
                                AuditResultType.DENIED)
                                .doOnError(auditError -> log.error("Failed to log audit event (task not ready): {}",
                                        auditError.getMessage(), auditError))
                                .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                                .then(Mono.error(new RuntimeException("Task is not ready to execute")));
                    }

                    // Validate execution trigger configuration
                    if (!task.isExecutionTriggerValid()) {
                        log.error("Task {} has invalid execution trigger configuration: {}", taskId,
                                task.getExecutionTrigger());

                        // Log validation failure
                        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                        Map<String, Object> errorContext = new HashMap<>();
                        errorContext.put("agentId", agentId);
                        errorContext.put("agentName", agent.getName());
                        errorContext.put("taskId", taskId);
                        errorContext.put("taskName", task.getName());
                        errorContext.put("teamId", teamId);
                        errorContext.put("executedBy", executedBy);
                        errorContext.put("executionTrigger",
                                task.getExecutionTrigger() != null ? task.getExecutionTrigger().name() : null);
                        errorContext.put("error", "Invalid execution trigger configuration");
                        errorContext.put("durationMs", durationMs);
                        errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                        return auditLogHelper.logDetailedEvent(
                                AuditEventType.AGENT_TASK_EXECUTION_FAILED,
                                AuditActionType.READ,
                                AuditResourceType.AGENT_TASK_EXECUTION,
                                taskId,
                                String.format(
                                        "Task execution failed for '%s' - invalid execution trigger configuration",
                                        task.getName()),
                                errorContext,
                                null,
                                null,
                                AuditResultType.DENIED)
                                .doOnError(auditError -> log.error("Failed to log audit event (invalid trigger): {}",
                                        auditError.getMessage(), auditError))
                                .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                                .then(Mono.error(new RuntimeException("Invalid execution trigger configuration")));
                    }

                    // Validate that manual execution is allowed for this trigger type
                    if (task.getExecutionTrigger() == ExecutionTrigger.CRON && !task.isAutoExecute()) {
                        log.error("Task {} is configured for CRON trigger but autoExecute is false", taskId);

                        // Log validation failure
                        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                        Map<String, Object> errorContext = new HashMap<>();
                        errorContext.put("agentId", agentId);
                        errorContext.put("agentName", agent.getName());
                        errorContext.put("taskId", taskId);
                        errorContext.put("taskName", task.getName());
                        errorContext.put("teamId", teamId);
                        errorContext.put("executedBy", executedBy);
                        errorContext.put("executionTrigger", ExecutionTrigger.CRON.name());
                        errorContext.put("autoExecute", task.isAutoExecute());
                        errorContext.put("error", "CRON tasks must have autoExecute enabled");
                        errorContext.put("durationMs", durationMs);
                        errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                        return auditLogHelper.logDetailedEvent(
                                AuditEventType.AGENT_TASK_EXECUTION_FAILED,
                                AuditActionType.READ,
                                AuditResourceType.AGENT_TASK_EXECUTION,
                                taskId,
                                String.format(
                                        "Task execution failed for '%s' - CRON tasks must have autoExecute enabled",
                                        task.getName()),
                                errorContext,
                                null,
                                null,
                                AuditResultType.DENIED)
                                .doOnError(auditError -> log.error("Failed to log audit event (CRON autoExecute): {}",
                                        auditError.getMessage(), auditError))
                                .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                                .then(Mono.error(new RuntimeException("CRON tasks must have autoExecute enabled")));
                    }

                    if (!agent.canExecute()) {
                        // Log validation failure
                        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                        Map<String, Object> errorContext = new HashMap<>();
                        errorContext.put("agentId", agentId);
                        errorContext.put("agentName", agent.getName());
                        errorContext.put("taskId", taskId);
                        errorContext.put("taskName", task.getName());
                        errorContext.put("teamId", teamId);
                        errorContext.put("executedBy", executedBy);
                        errorContext.put("error", "Agent is not ready to execute");
                        errorContext.put("durationMs", durationMs);
                        errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                        return auditLogHelper.logDetailedEvent(
                                AuditEventType.AGENT_TASK_EXECUTION_FAILED,
                                AuditActionType.READ,
                                AuditResourceType.AGENT_TASK_EXECUTION,
                                taskId,
                                String.format("Task execution failed for '%s' - agent '%s' is not ready to execute",
                                        task.getName(), agent.getName()),
                                errorContext,
                                null,
                                null,
                                AuditResultType.DENIED)
                                .doOnError(auditError -> log.error("Failed to log audit event (agent not ready): {}",
                                        auditError.getMessage(), auditError))
                                .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                                .then(Mono.error(new RuntimeException("Agent is not ready to execute")));
                    }

                    // Create execution record
                    String executionId = UUID.randomUUID().toString();
                    AgentExecution execution = AgentExecution.builder()
                            .executionId(executionId)
                            .agentId(agentId)
                            .agentName(agent.getName()) // Set the agent name
                            .taskId(taskId)
                            .taskName(task.getName())
                            .teamId(teamId)
                            .executionType(ExecutionType.MANUAL)
                            .scheduledAt(LocalDateTime.now())
                            .startedAt(LocalDateTime.now())
                            .executedBy(executedBy)
                            .executionEnvironment("production")
                            .maxRetries(task.getMaxRetries())
                            .inputData(overrides.isEmpty() ? null : overrides)
                            .build();

                    // Capture execution in a final variable for use in error handlers
                    final AgentExecution finalExecution = execution;

                    // Add to queue first, then execute after a delay
                    return agentExecutionRepository.save(execution)
                            .flatMap(savedExecution -> {
                                // Use saved execution from now on to ensure we have the persisted version
                                return executionQueueService
                                        .addToQueue(executionId, agentId, agent.getName(), taskId, task.getName(),
                                                teamId, executedBy)
                                        .then(Mono.delay(java.time.Duration.ofSeconds(2))) // Wait 2 seconds to show
                                                                                           // queue
                                        .then(executionQueueService.markAsStarting(executionId))
                                        .then(Mono.delay(java.time.Duration.ofSeconds(1))) // Wait 1 more second for
                                                                                           // starting animation
                                        .then(executionQueueService.markAsStartedAndRemove(executionId))
                                        .then(sendExecutionStartedUpdate(savedExecution, agent, task))
                                        .doOnError(
                                                updateError -> log.error("Failed to send execution started update: {}",
                                                        updateError.getMessage(), updateError))
                                        .onErrorResume(updateError -> Mono.empty()) // Don't fail if update sending
                                                                                    // fails
                                        .then(executeWorkflow(savedExecution, task, agent))
                                        .then(Mono.defer(() -> {
                                            log.info(
                                                    "Task execution workflow triggered, now logging audit event for execution: {}",
                                                    executionId);

                                            // Build audit context
                                            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now())
                                                    .toMillis();
                                            Map<String, Object> auditContext = new HashMap<>();
                                            auditContext.put("executionId", executionId);
                                            auditContext.put("agentId", agentId);
                                            auditContext.put("agentName", agent.getName());
                                            auditContext.put("taskId", taskId);
                                            auditContext.put("taskName", task.getName());
                                            auditContext.put("teamId", teamId);
                                            auditContext.put("executedBy", executedBy);
                                            auditContext.put("executionType", ExecutionType.MANUAL.name());
                                            auditContext.put("taskType",
                                                    task.getTaskType() != null ? task.getTaskType().name() : null);
                                            auditContext.put("executionTrigger",
                                                    task.getExecutionTrigger() != null
                                                            ? task.getExecutionTrigger().name()
                                                            : null);
                                            auditContext.put("maxRetries", task.getMaxRetries());
                                            auditContext.put("hasInputOverrides", !overrides.isEmpty());
                                            if (!overrides.isEmpty()) {
                                                auditContext.put("inputOverrideKeys", overrides.keySet());
                                            }
                                            auditContext.put("durationMs", durationMs);
                                            auditContext.put("startTimestamp",
                                                    savedExecution.getStartedAt().toString());

                                            // Log audit event as part of the reactive chain (preserves context)
                                            // Use doOnSuccess/doOnError to log audit as side effect, but always return
                                            // execution
                                            return auditLogHelper.logDetailedEvent(
                                                    AuditEventType.AGENT_TASK_EXECUTION_STARTED,
                                                    AuditActionType.READ,
                                                    AuditResourceType.AGENT_TASK_EXECUTION,
                                                    executionId,
                                                    String.format(
                                                            "Task '%s' execution started successfully for agent '%s' by user '%s'",
                                                            task.getName(), agent.getName(), executedBy),
                                                    auditContext,
                                                    null,
                                                    null)
                                                    .doOnSuccess(v -> log.info(
                                                            "✅ Audit log saved successfully for execution: {}",
                                                            executionId))
                                                    .doOnError(auditError -> log.error(
                                                            "❌ Failed to log audit event for task execution start (executionId: {}): {}",
                                                            executionId, auditError.getMessage(), auditError))
                                                    .thenReturn(savedExecution) // Return execution after audit log
                                                                                // completes successfully
                                                    .onErrorResume(auditError -> {
                                                        log.error(
                                                                "Audit logging failed for execution {}, returning execution anyway: {}",
                                                                executionId, auditError.getMessage());
                                                        return Mono.just(savedExecution); // Always return execution
                                                                                          // even if audit logging fails
                                                    });
                                        }))
                                        .onErrorResume(error -> {
                                            // ERROR CASE - Only update execution status
                                            log.error("Task execution failed: {}", error.getMessage());

                                            // Reload execution from database to ensure we have the latest version
                                            return agentExecutionRepository.findById(executionId)
                                                    .switchIfEmpty(Mono.defer(() -> {
                                                        log.warn(
                                                                "Execution {} not found in database, using original execution object",
                                                                executionId);
                                                        return Mono.just(finalExecution);
                                                    }))
                                                    .flatMap(executionToUpdate -> {
                                                        executionToUpdate.markAsFailed(error.getMessage(),
                                                                "Workflow execution failed");

                                                        // Log execution failure
                                                        long durationMs = java.time.Duration
                                                                .between(startTime, LocalDateTime.now()).toMillis();
                                                        Map<String, Object> errorContext = new HashMap<>();
                                                        errorContext.put("executionId", executionId);
                                                        errorContext.put("agentId", agentId);
                                                        errorContext.put("agentName", agent.getName());
                                                        errorContext.put("taskId", taskId);
                                                        errorContext.put("taskName", task.getName());
                                                        errorContext.put("teamId", teamId);
                                                        errorContext.put("executedBy", executedBy);
                                                        errorContext.put("executionType", ExecutionType.MANUAL.name());
                                                        errorContext.put("taskType",
                                                                task.getTaskType() != null ? task.getTaskType().name()
                                                                        : null);
                                                        errorContext.put("error", error.getMessage());
                                                        errorContext.put("errorType", error.getClass().getSimpleName());
                                                        errorContext.put("durationMs", durationMs);
                                                        errorContext.put("failureTimestamp",
                                                                LocalDateTime.now().toString());

                                                        // Chain audit logging before saving and returning error
                                                        return auditLogHelper.logDetailedEvent(
                                                                AuditEventType.AGENT_TASK_EXECUTION_FAILED,
                                                                AuditActionType.READ,
                                                                AuditResourceType.AGENT_TASK_EXECUTION,
                                                                executionId,
                                                                String.format(
                                                                        "Task '%s' execution failed for agent '%s': %s",
                                                                        task.getName(), agent.getName(),
                                                                        error.getMessage()),
                                                                errorContext,
                                                                null,
                                                                null,
                                                                AuditResultType.FAILED)
                                                                .doOnError(auditError -> log.error(
                                                                        "Failed to log audit event (execution failed): {}",
                                                                        auditError.getMessage(), auditError))
                                                                .onErrorResume(auditError -> Mono.empty()) // Don't fail
                                                                                                           // if audit
                                                                                                           // logging
                                                                                                           // fails
                                                                .then(agentExecutionRepository.save(executionToUpdate))
                                                                .flatMap(saved -> {
                                                                    try {
                                                                        return sendExecutionFailedUpdate(saved, agent,
                                                                                task, error.getMessage());
                                                                    } catch (Exception e) {
                                                                        log.error(
                                                                                "Failed to send execution failed update: {}",
                                                                                e.getMessage(), e);
                                                                        return Mono.empty(); // Don't fail if update
                                                                                             // sending fails
                                                                    }
                                                                })
                                                                .then(Mono.error(error));
                                                    });
                                        });
                            });
                })
                .doOnSuccess(execution -> {
                    if (execution != null) {
                        log.info("Task execution started: {}", execution.getExecutionId());
                    } else {
                        log.warn("Task execution completed but execution object is null");
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to start task execution: {}", error.getMessage());

                    // Log error for agent/task not found or other failures before execution
                    // creation
                    long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    Map<String, Object> errorContext = new HashMap<>();
                    errorContext.put("agentId", agentId);
                    errorContext.put("taskId", taskId);
                    errorContext.put("teamId", teamId);
                    errorContext.put("executedBy", executedBy);
                    errorContext.put("error", error.getMessage());
                    errorContext.put("errorType", error.getClass().getSimpleName());
                    errorContext.put("durationMs", durationMs);
                    errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                    // Chain audit logging before returning error
                    return auditLogHelper.logDetailedEvent(
                            AuditEventType.AGENT_TASK_EXECUTION_FAILED,
                            AuditActionType.READ,
                            AuditResourceType.AGENT_TASK_EXECUTION,
                            taskId,
                            String.format("Task execution initiation failed: %s", error.getMessage()),
                            errorContext,
                            null,
                            null,
                            AuditResultType.FAILED)
                            .doOnError(auditError -> log.error("Failed to log audit event (initiation failed): {}",
                                    auditError.getMessage(), auditError))
                            .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                            .then(Mono.error(error)); // Return the original error after logging
                });
    }

    @Override
    public Mono<Boolean> cancelExecution(String executionId, String teamId, String cancelledBy) {
        log.info("Cancelling execution {} for team {} by {}", executionId, teamId, cancelledBy);

        return agentExecutionRepository.findById(executionId)
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Execution not found or access denied")))
                .flatMap(execution -> {
                    execution.setStatus(ExecutionStatus.CANCELLED);
                    execution.setResult(ExecutionResult.FAILURE);
                    execution.setErrorMessage("Execution cancelled by: " + cancelledBy);
                    execution.setCompletedAt(LocalDateTime.now());

                    return agentExecutionRepository.save(execution)
                            .thenReturn(true);
                })
                .doOnSuccess(
                        cancelled -> log.info("Execution {} cancelled successfully by {}", executionId, cancelledBy))
                .doOnError(error -> log.error("Failed to cancel execution {}: {}", executionId, error.getMessage()));
    }

    @Override
    public Flux<AgentExecution> getExecutionHistory(String agentId, int limit) {
        return agentRepository.findById(agentId)
                .thenMany(agentExecutionRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                        .take(limit));
    }

    @Override
    public Flux<AgentExecution> getTaskExecutionHistory(String taskId, int limit) {
        return agentTaskRepository.findById(taskId)
                .thenMany(agentExecutionRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                        .take(limit));
    }

    @Override
    public Flux<AgentExecution> getExecutionsByTeamAndStatus(String teamId, String status, int limit) {
        return agentExecutionRepository.findByStatus(status)
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .take(limit);
    }

    @Override
    public Mono<AgentExecution> getExecutionById(String executionId) {
        return agentExecutionRepository.findById(executionId)
                .doOnSuccess(execution -> log.debug("Found execution: {} for executionId: {}",
                        execution != null ? execution.getExecutionId() : "null", executionId))
                .doOnError(error -> log.error("Error fetching execution by id {}: {}", executionId, error.getMessage()))
                .switchIfEmpty(Mono.error(new RuntimeException("Execution not found with id: " + executionId)));
    }

    @Override
    public Flux<AgentExecution> getRecentExecutions(String teamId, int limit) {
        return agentExecutionRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
                .take(limit)
                .doOnNext(execution -> log.debug("Found recent execution: {} for team: {}",
                        execution.getExecutionId(), teamId))
                .doOnError(error -> log.error("Error fetching recent executions for team {}: {}", teamId,
                        error.getMessage()));
    }

    // ==================== WORKFLOW INTEGRATION ====================

    /**
     * Execute the task based on its type using dedicated executors
     */
    private Mono<Void> executeWorkflow(AgentExecution execution, AgentTask task, Agent agent) {
        if (execution == null) {
            log.error("Cannot execute workflow: execution is null");
            return Mono.error(new IllegalStateException("Execution is null when trying to execute workflow"));
        }

        log.info("Executing task: {} (type: {}, execution: {})", task.getName(), task.getTaskType(),
                execution.getExecutionId());

        return switch (task.getTaskType()) {
            case WORKFLOW_EMBEDDED -> workflowEmbeddedExecutor.executeTask(execution, task, agent);
            case WORKFLOW_TRIGGER -> workflowTriggerExecutor.executeTask(execution, task, agent);
            default -> Mono.error(new IllegalArgumentException("Unsupported task type: " + task.getTaskType()));
        };
    }

    @Override
    public Mono<Object> executeAdhocTask(AgentTask agentTask, String teamId, String executedBy) {
        log.info("Executing ad-hoc task: {} for team: {}", agentTask.getName(), teamId);
        if (agentTask.getTaskType() == null) {
            return Mono.error(new IllegalArgumentException("taskType is required for ad-hoc execution"));
        }
        if (agentTask.getTaskType() != org.lite.gateway.enums.AgentTaskType.WORKFLOW_EMBEDDED_ADHOC) {
            return Mono.error(new IllegalArgumentException(
                    "Only WORKFLOW_EMBEDDED_ADHOC tasks are supported for ad-hoc execution"));
        }
        return workflowAdhocExecutor.executeAdhocTask(agentTask, teamId, executedBy, null);
    }

    // ==================== EXECUTION MONITORING ====================

    private Mono<Void> sendExecutionStartedUpdate(AgentExecution execution, Agent agent, AgentTask task) {
        if (execution == null) {
            log.error("Cannot send execution started update: execution is null");
            return Mono.error(new IllegalStateException("Execution is null when trying to send started update"));
        }

        // Calculate duration from start time to now (both in UTC)
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        long durationMs = java.time.Duration.between(execution.getStartedAt(), now).toMillis();

        log.info("📊 STARTED message - startedAt: {}, now: {}, durationMs: {}",
                execution.getStartedAt(), now, durationMs);

        ExecutionProgressUpdate update = ExecutionProgressUpdate.builder()
                .executionId(execution.getExecutionId())
                .agentId(agent.getId())
                .agentName(agent.getName())
                .taskId(task.getId())
                .taskName(task.getName())
                .teamId(execution.getTeamId())
                .status("STARTED")
                .currentStep(0)
                .totalSteps(getTotalSteps(task))
                .startedAt(execution.getStartedAt())
                .lastUpdatedAt(now)
                .executionDurationMs(durationMs)
                .build();

        return executionMonitoringService.sendExecutionStarted(update);
    }

    private Mono<Void> sendExecutionFailedUpdate(AgentExecution execution, Agent agent, AgentTask task,
            String errorMessage) {
        if (execution == null) {
            log.error("Cannot send execution failed update: execution is null");
            return Mono.error(new IllegalStateException("Execution is null when trying to send failed update"));
        }

        // Calculate duration from start time to now (both in UTC)
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        long durationMs = java.time.Duration.between(execution.getStartedAt(), now).toMillis();

        ExecutionProgressUpdate update = ExecutionProgressUpdate.builder()
                .executionId(execution.getExecutionId())
                .agentId(agent.getId())
                .agentName(agent.getName())
                .taskId(task.getId())
                .taskName(task.getName())
                .teamId(execution.getTeamId())
                .status("FAILED")
                .currentStep(0)
                .totalSteps(getTotalSteps(task))
                .startedAt(execution.getStartedAt())
                .lastUpdatedAt(now)
                .executionDurationMs(durationMs)
                .errorMessage(errorMessage)
                .build();

        return executionMonitoringService.sendExecutionFailed(update, errorMessage);
    }

    private int getTotalSteps(AgentTask task) {
        try {
            // Extract workflow steps from the task's linq_config
            Map<String, Object> linqConfig = task.getLinqConfig();
            if (linqConfig != null && linqConfig.containsKey("query")) {
                Object queryObj = linqConfig.get("query");
                if (queryObj instanceof Map) {
                    Map<?, ?> queryMap = (Map<?, ?>) queryObj;
                    Object workflowObj = queryMap.get("workflow");
                    if (workflowObj instanceof List) {
                        List<?> workflow = (List<?>) workflowObj;
                        return workflow.size();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract step count from task {}, using default: {}", task.getId(), e.getMessage());
        }
        return 1; // Default fallback
    }
}
