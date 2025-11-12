package org.lite.gateway.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.enums.ExecutionType;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.LinqWorkflowService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Executes ad-hoc WORKFLOW_EMBEDDED tasks with timeout + retry and persists execution lifecycle.
 */
@Slf4j
@Component
public class WorkflowAdHocAgentTaskExecutor extends AgentTaskExecutor {

    public WorkflowAdHocAgentTaskExecutor(
            LinqWorkflowService linqWorkflowService,
            LinqWorkflowExecutionService workflowExecutionService,
            AgentRepository agentRepository,
            AgentTaskRepository agentTaskRepository,
            AgentExecutionRepository agentExecutionRepository,
            ObjectMapper objectMapper
    ) {
        super(linqWorkflowService, workflowExecutionService, agentRepository, agentTaskRepository, agentExecutionRepository, objectMapper);
    }

    @Override
    public Mono<Void> executeTask(AgentExecution execution, AgentTask task, Agent agent) {
        return Mono.error(new UnsupportedOperationException("Use executeAdhocTask(...) for ad-hoc execution"));
    }

    public Mono<Object> executeAdhocTask(AgentTask agentTask, String teamId, String executedBy, ServerWebExchange exchange) {
        if (agentTask == null) {
            return Mono.error(new IllegalArgumentException("AgentTask must not be null"));
        }
        AgentTaskType type = agentTask.getTaskType();
        if (type != AgentTaskType.WORKFLOW_EMBEDDED && type != AgentTaskType.WORKFLOW_EMBEDDED_ADHOC) {
            return Mono.error(new IllegalArgumentException("Only WORKFLOW_EMBEDDED(_ADHOC) tasks are supported for ad-hoc execution"));
        }
        if (agentTask.getLinqConfig() == null) {
            return Mono.error(new IllegalArgumentException("Invalid task configuration: missing linq_config"));
        }

        int retries = Math.max(0, agentTask.getMaxRetries());
        Duration timeout = getTimeoutForTaskType(agentTask);

        // create execution (no persisted taskId in ad-hoc, generate a synthetic one if null)
        String taskId = agentTask.getId() != null ? agentTask.getId() : "adhoc-" + java.util.UUID.randomUUID();
        AgentExecution execution = AgentExecution.builder()
                .executionId(java.util.UUID.randomUUID().toString())
                .taskId(taskId)
                .taskName(agentTask.getName())
                .teamId(teamId)
                .executedBy(executedBy)
                .executionType(ExecutionType.MANUAL)
                .scheduledAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .maxRetries(agentTask.getMaxRetries())
                .build();

        return agentExecutionRepository.save(execution)
                .flatMap(saved -> buildLinqRequest(agentTask, teamId, executedBy)
                        .flatMap(request -> {
                            Map<String, Object> agentContext = Map.of(
                                    "agentId", "adhoc",
                                    "agentName", "AdHoc",
                                    "agentTaskId", taskId,
                                    "agentTaskName", agentTask.getName(),
                                    "executionSource", "agent_embedded_adhoc",
                                    "agentExecutionId", saved.getExecutionId()
                            );

                            return workflowExecutionService.initializeExecutionWithAgentContext(request, agentContext)
                                    .then(workflowExecutionService.executeWorkflow(request)
                                            .timeout(timeout)
                                            .retryWhen(Retry.backoff(retries, Duration.ofSeconds(2))
                                                    .filter(this::isRetryable)
                                                    .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                                            .flatMap(response -> workflowExecutionService.trackExecutionWithAgentContext(request, response, agentContext)
                                                    .map(LinqWorkflowExecution::getId)
                                                    .doOnNext(saved::setWorkflowExecutionId)
                                                    .thenReturn(response))
                                            .flatMap(response -> {
                                                saved.markAsCompleted();
                                                return agentExecutionRepository.save(saved).thenReturn((Object) response);
                                            })
                                            .onErrorResume(TimeoutException.class, tex -> {
                                                log.error("Ad-hoc execution timed out for task {}: {}", agentTask.getName(), tex.getMessage());
                                                saved.markAsTimeout();
                                                saved.setErrorMessage("Timeout after " + timeout.toMinutes() + " minutes");
                                                return agentExecutionRepository.save(saved).then(Mono.error(tex));
                                            })
                                            .onErrorResume(err -> {
                                                log.error("Ad-hoc execution failed for task {}: {}", agentTask.getName(), err.getMessage());
                                                saved.markAsFailed(err.getMessage() != null ? err.getMessage() : "Execution failed", "WORKFLOW_EXECUTION_FAILED");
                                                return agentExecutionRepository.save(saved)
                                                        .then(Mono.error(new RuntimeException("Workflow execution failed: " + saved.getErrorMessage())));
                                            })
                                    );
                        })
                );
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof IllegalArgumentException) return false;
        if (t instanceof TimeoutException) return false;
        return true;
    }

    private Mono<LinqRequest> buildLinqRequest(AgentTask agentTask, String teamId, String executedBy) {
        return Mono.fromCallable(() -> {
            LinqRequest request = objectMapper.convertValue(agentTask.getLinqConfig(), LinqRequest.class);
            if (request.getQuery() != null) {
                Map<String, Object> params = request.getQuery().getParams();
                if (params != null) {
                    params.put("teamId", teamId);
                    params.put("userId", executedBy);
                }
            }
            request.setExecutedBy(executedBy);
            return request;
        });
    }
} 