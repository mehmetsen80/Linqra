package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.model.LinqWorkflowStats;
import org.lite.gateway.model.ExecutionStatus;
import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.entity.LinqWorkflowVersion;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.repository.LinqWorkflowRepository;
import org.lite.gateway.repository.LinqToolRepository;
import org.lite.gateway.repository.LinqWorkflowVersionRepository;
import org.lite.gateway.service.LinqWorkflowService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.LinqToolService;
import org.lite.gateway.service.LinqMicroService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqWorkflowServiceImpl implements LinqWorkflowService {
    private final LinqWorkflowRepository workflowRepository;
    private final LinqWorkflowExecutionRepository executionRepository;
    private final TeamContextService teamContextService;
    private final LinqToolRepository linqToolRepository;
    private final LinqToolService linqToolService;
    private final LinqMicroService linqMicroService;
    private final LinqWorkflowVersionRepository workflowVersionRepository;

    @Override
    public Mono<LinqWorkflow> createWorkflow(LinqWorkflow workflow) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> {
                workflow.setTeam(teamId);
                workflow.setVersion(1);
                workflow.setCreatedAt(LocalDateTime.now());
                workflow.setUpdatedAt(LocalDateTime.now());

                // Create initial version
                LinqWorkflowVersion initialVersion = LinqWorkflowVersion.builder()
                    .workflowId(null) // Will be set after workflow is saved
                    .team(teamId)
                    .version(1)
                    .request(workflow.getRequest())
                    .createdAt(System.currentTimeMillis())
                    .createdBy(workflow.getCreatedBy())
                    .changeDescription("Initial version")
                    .build();

                return workflowRepository.save(workflow)
                    .flatMap(savedWorkflow -> {
                        // Set the workflowId in the version
                        initialVersion.setWorkflowId(savedWorkflow.getId());
                        return workflowVersionRepository.save(initialVersion)
                            .thenReturn(savedWorkflow);
                    })
                    .doOnSuccess(w -> log.info("Created workflow: {} with initial version", w.getId()))
                    .doOnError(error -> log.error("Error creating workflow: {}", error.getMessage()));
            });
    }

    @Override
    public Mono<LinqWorkflow> updateWorkflow(String workflowId, LinqWorkflow updatedWorkflow) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> workflowRepository.findByIdAndTeam(workflowId, teamId)
                .flatMap(existingWorkflow -> {
                    updatedWorkflow.setId(workflowId);
                    updatedWorkflow.setTeam(teamId);
                    updatedWorkflow.setCreatedAt(existingWorkflow.getCreatedAt());
                    updatedWorkflow.setUpdatedAt(LocalDateTime.now());
                    return workflowRepository.save(updatedWorkflow)
                        .doOnSuccess(w -> log.info("Updated workflow: {}", w.getId()))
                        .doOnError(error -> log.error("Error updating workflow: {}", error.getMessage()));
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Workflow not found or access denied"))));
    }

    @Override
    public Mono<Void> deleteWorkflow(String workflowId) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> workflowRepository.findByIdAndTeam(workflowId, teamId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, 
                    "Workflow not found or access denied")))
                .flatMap(workflow -> {
                    log.info("Deleting workflow: {} and its versions", workflow);
                    // First delete all versions
                    return workflowVersionRepository.findFirstByWorkflowIdAndTeamOrderByVersionDesc(workflowId, teamId)
                        .flatMap(workflowVersionRepository::delete)
                        .then()
                        .then(executionRepository.findByWorkflowIdAndTeam(workflowId, teamId)
                            .flatMap(executionRepository::delete)
                            .then())
                        .then(workflowRepository.delete(workflow))
                        .doOnSuccess(v -> log.info("Deleted workflow: {} and its versions and executions", workflowId))
                        .doOnError(error -> log.error("Error deleting workflow: {}", error.getMessage()));
                }));
    }

    @Override
    public Flux<LinqWorkflow> getWorkflows() {
        return teamContextService.getTeamFromContext()
            .flatMapMany(teamId -> workflowRepository.findByTeam(teamId)
                .doOnError(error -> log.error("Error fetching workflows: {}", error.getMessage())));
    }

    @Override
    public Mono<LinqWorkflow> getWorkflow(String workflowId) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> workflowRepository.findByIdAndTeam(workflowId, teamId)
                .doOnError(error -> log.error("Error fetching workflow: {}", error.getMessage()))
                .switchIfEmpty(Mono.error(new RuntimeException("Workflow not found or access denied"))));
    }

    @Override
    public Mono<LinqWorkflowExecution> trackExecution(LinqRequest request, LinqResponse response) {
        LinqWorkflowExecution execution = new LinqWorkflowExecution();
        execution.setTeam(response.getMetadata().getTeam());
        execution.setRequest(request);
        execution.setResponse(response);
        execution.setExecutedAt(LocalDateTime.now());
        
        // Set status based on metadata status
        execution.setStatus("success".equals(response.getMetadata().getStatus()) ? 
            ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
        
        // Calculate duration from metadata if available
        if (response.getMetadata() != null && response.getMetadata().getWorkflowMetadata() != null) {
            long totalDuration = response.getMetadata().getWorkflowMetadata().stream()
                .mapToLong(LinqResponse.WorkflowStepMetadata::getDurationMs)
                .sum();
            execution.setDurationMs(totalDuration);
        }
        
        if (request.getQuery() != null && request.getQuery().getWorkflowId() != null) {
            execution.setWorkflowId(request.getQuery().getWorkflowId());
        }
        
        return executionRepository.save(execution)
            .doOnSuccess(e -> log.info("Tracked workflow execution: {} with status: {}", e.getId(), e.getStatus()))
            .doOnError(error -> log.error("Error tracking workflow execution: {}", error.getMessage()));
    }

    @Override
    public Flux<LinqWorkflowExecution> getWorkflowExecutions(String workflowId) {
        return executionRepository.findByWorkflowId(workflowId)
            .doOnError(error -> log.error("Error fetching workflow executions: {}", error.getMessage()));
    }

    @Override
    public Flux<LinqWorkflowExecution> getTeamExecutions() {
        return teamContextService.getTeamFromContext()
            .flatMapMany(teamId -> executionRepository.findByTeam(teamId))
            .doOnError(error -> log.error("Error fetching team executions: {}", error.getMessage()));
    }

    @Override
    public Mono<LinqWorkflowExecution> getExecution(String executionId) {
        return executionRepository.findById(executionId)
            .doOnError(error -> log.error("Error fetching execution: {}", error.getMessage()))
            .switchIfEmpty(Mono.error(new RuntimeException("Execution not found")));
    }

    @Override
    public Flux<LinqWorkflow> searchWorkflows(String searchTerm) {
        return workflowRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            searchTerm, searchTerm)
            .doOnError(error -> log.error("Error searching workflows: {}", error.getMessage()));
    }

    @Override
    public Mono<LinqWorkflowStats> getWorkflowStats(String workflowId) {
        return executionRepository.findByWorkflowId(workflowId)
            .collectList()
            .map(executions -> {
                LinqWorkflowStats stats = new LinqWorkflowStats();
                
                // Initialize maps
                stats.setStepStats(new HashMap<>());
                stats.setTargetStats(new HashMap<>());
                stats.setModelStats(new HashMap<>());
                stats.setHourlyExecutions(new HashMap<>());
                stats.setDailyExecutions(new HashMap<>());
                
                // Overall stats
                stats.setTotalExecutions(executions.size());
                stats.setSuccessfulExecutions((int) executions.stream()
                    .filter(e -> e.getStatus() == ExecutionStatus.SUCCESS)
                    .count());
                stats.setFailedExecutions((int) executions.stream()
                    .filter(e -> e.getStatus() == ExecutionStatus.FAILED)
                    .count());
                stats.setAverageExecutionTime(executions.stream()
                    .mapToLong(LinqWorkflowExecution::getDurationMs)
                    .average()
                    .orElse(0.0));
                
                // Process each execution
                executions.forEach(execution -> {
                    // Time-based stats
                    String hour = execution.getExecutedAt().format(DateTimeFormatter.ofPattern("HH:00"));
                    String day = execution.getExecutedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    stats.getHourlyExecutions().merge(hour, 1, Integer::sum);
                    stats.getDailyExecutions().merge(day, 1, Integer::sum);
                    
                    // Process steps
                    if (execution.getResponse() != null && 
                        execution.getResponse().getMetadata() != null && 
                        execution.getResponse().getMetadata().getWorkflowMetadata() != null) {
                        
                        execution.getResponse().getMetadata().getWorkflowMetadata().forEach(stepMetadata -> {
                            // Step stats
                            LinqWorkflowStats.StepStats stepStats = stats.getStepStats().computeIfAbsent(stepMetadata.getStep(), k -> new LinqWorkflowStats.StepStats());
                            stepStats.setTotalExecutions(stepStats.getTotalExecutions() + 1);
                            if ("success".equals(stepMetadata.getStatus())) {
                                stepStats.setSuccessfulExecutions(stepStats.getSuccessfulExecutions() + 1);
                            } else {
                                stepStats.setFailedExecutions(stepStats.getFailedExecutions() + 1);
                            }
                            stepStats.setAverageDurationMs(
                                (stepStats.getAverageDurationMs() * (stepStats.getTotalExecutions() - 1) + 
                                stepMetadata.getDurationMs()) / stepStats.getTotalExecutions()
                            );
                            
                            // Target stats
                            String target = stepMetadata.getTarget();
                            LinqWorkflowStats.TargetStats targetStats = stats.getTargetStats().computeIfAbsent(target, k -> new LinqWorkflowStats.TargetStats());
                            targetStats.setTotalExecutions(targetStats.getTotalExecutions() + 1);
                            targetStats.setAverageDurationMs(
                                (targetStats.getAverageDurationMs() * (targetStats.getTotalExecutions() - 1) + 
                                stepMetadata.getDurationMs()) / targetStats.getTotalExecutions()
                            );
                            
                            // Model stats for AI targets
                            if (target.equals("openai") || target.equals("gemini")) {
                                String model = execution.getRequest().getQuery().getWorkflow().stream()
                                    .filter(step -> step.getStep() == stepMetadata.getStep())
                                    .findFirst()
                                    .map(step -> step.getToolConfig().getModel())
                                    .orElse("unknown");
                                
                                LinqWorkflowStats.ModelStats modelStats = stats.getModelStats().computeIfAbsent(model, k -> new LinqWorkflowStats.ModelStats());
                                modelStats.setTotalExecutions(modelStats.getTotalExecutions() + 1);
                                modelStats.setAverageDurationMs(
                                    (modelStats.getAverageDurationMs() * (modelStats.getTotalExecutions() - 1) + 
                                    stepMetadata.getDurationMs()) / modelStats.getTotalExecutions()
                                );
                                
                                // Token usage for OpenAI
                                if (target.equals("openai") && execution.getResponse().getResult() instanceof LinqResponse.WorkflowResult workflowResult) {
                                    List<LinqResponse.WorkflowStep> steps = workflowResult.getSteps();
                                    if (steps != null && steps.size() > stepMetadata.getStep()) {
                                        LinqResponse.WorkflowStep step = steps.get(stepMetadata.getStep());
                                        if (step.getResult() instanceof Map) {
                                            Map<String, Object> result = (Map<String, Object>) step.getResult();
                                            if (result.containsKey("usage")) {
                                                Map<String, Object> usage = (Map<String, Object>) result.get("usage");
                                                modelStats.setTotalPromptTokens(modelStats.getTotalPromptTokens() + 
                                                    ((Number) usage.get("prompt_tokens")).longValue());
                                                modelStats.setTotalCompletionTokens(modelStats.getTotalCompletionTokens() + 
                                                    ((Number) usage.get("completion_tokens")).longValue());
                                                modelStats.setTotalTokens(modelStats.getTotalTokens() + 
                                                    ((Number) usage.get("total_tokens")).longValue());
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                });
                
                return stats;
            })
            .doOnError(error -> log.error("Error calculating workflow stats: {}", error.getMessage()));
    }

    @Override
    public Mono<LinqResponse> executeWorkflow(LinqRequest request) {
        List<LinqRequest.Query.WorkflowStep> steps = request.getQuery().getWorkflow();
        Map<Integer, Object> stepResults = new HashMap<>();
        List<LinqResponse.WorkflowStepMetadata> stepMetadata = new ArrayList<>();

        // Execute steps synchronously
        Mono<LinqResponse> workflowMono = Mono.just(new LinqResponse());
        for (LinqRequest.Query.WorkflowStep step : steps) {
            workflowMono = workflowMono.flatMap(response -> {
                Instant start = Instant.now();
                // Create a single-step LinqRequest
                LinqRequest stepRequest = new LinqRequest();
                LinqRequest.Link stepLink = new LinqRequest.Link();
                stepLink.setTarget(step.getTarget());
                stepLink.setAction(step.getAction());
                stepRequest.setLink(stepLink);

                LinqRequest.Query stepQuery = new LinqRequest.Query();
                stepQuery.setIntent(step.getIntent());
                stepQuery.setParams(resolvePlaceholdersForMap(step.getParams(), stepResults));
                stepQuery.setPayload(resolvePlaceholders(step.getPayload(), stepResults));
                stepQuery.setToolConfig(step.getToolConfig());
                stepRequest.setQuery(stepQuery);

                // Execute the step
                return teamContextService.getTeamFromContext()
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, 
                            "Team context not found. Please ensure you are authenticated with a valid team.")))
                        .flatMap(teamId -> {
                            log.info("Searching for tool with target: {} and team: {}", step.getTarget(), teamId);
                            return linqToolRepository.findByTargetAndTeam(step.getTarget(), teamId)
                                    .doOnNext(tool -> log.info("Found tool: {}", tool))
                                    .doOnError(error -> log.error("Error finding tool: {}", error.getMessage()))
                                    .doOnSuccess(tool -> {
                                        if (tool == null) {
                                            log.info("No tool found for target: {}", step.getTarget());
                                        }
                                    });
                        })
                        .doOnNext(tool -> log.info("About to execute tool request"))
                        .flatMap(tool -> linqToolService.executeToolRequest(stepRequest, tool))
                        .doOnNext(stepResponse -> log.info("Tool request executed successfully"))
                        .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                            log.info("No tool found, executing microservice request");
                            return linqMicroService.execute(stepRequest);
                        }))
                        .flatMap(stepResponse -> {
                            // Check if the result contains an error
                            if (stepResponse.getResult() instanceof Map<?, ?> resultMap && 
                                resultMap.containsKey("error")) {
                                return Mono.error(new ResponseStatusException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    String.format("Workflow step %d failed: %s", 
                                        step.getStep(), 
                                        resultMap.get("error"))
                                ));
                            }
                            
                            stepResults.put(step.getStep(), stepResponse.getResult());
                            long durationMs = Duration.between(start, Instant.now()).toMillis();
                            LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                            meta.setStep(step.getStep());
                            meta.setStatus("success");
                            meta.setDurationMs(durationMs);
                            meta.setTarget(step.getTarget());
                            meta.setExecutedAt(LocalDateTime.now());
                            stepMetadata.add(meta);
                            return Mono.just(response);
                        })
                        .onErrorResume(error -> {
                            long durationMs = Duration.between(start, Instant.now()).toMillis();
                            LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                            meta.setStep(step.getStep());
                            meta.setStatus("error");
                            meta.setDurationMs(durationMs);
                            meta.setTarget(step.getTarget());
                            meta.setExecutedAt(LocalDateTime.now());
                            stepMetadata.add(meta);
                            log.error("Error in workflow step {}: {}", step.getStep(), error.getMessage());
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                String.format("Workflow step %d failed: %s", step.getStep(), error.getMessage())
                            ));
                        });
            });
        }

        return workflowMono.flatMap(response -> 
            teamContextService.getTeamFromContext().map(teamId -> {
                // Build WorkflowResult
                LinqResponse.WorkflowResult workflowResult = new LinqResponse.WorkflowResult();
                List<LinqResponse.WorkflowStep> stepResultList = steps.stream()
                        .map(step -> {
                            LinqResponse.WorkflowStep stepResult = new LinqResponse.WorkflowStep();
                            stepResult.setStep(step.getStep());
                            stepResult.setTarget(step.getTarget());
                            stepResult.setResult(stepResults.get(step.getStep()));
                            return stepResult;
                        })
                        .collect(Collectors.toList());
                workflowResult.setSteps(stepResultList);

                // Set final result (from last step)
                Object lastResult = stepResults.get(steps.getLast().getStep());
                workflowResult.setFinalResult(extractFinalResult(lastResult));

                // Set response
                response.setResult(workflowResult);
                LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                metadata.setSource("workflow");
                metadata.setStatus("success");
                metadata.setTeam(teamId);
                metadata.setCacheHit(false);
                metadata.setWorkflowMetadata(stepMetadata);
                response.setMetadata(metadata);
                return response;
            })
        ).onErrorResume(error -> {
            // Create error response
            LinqResponse errorResponse = new LinqResponse();
            LinqResponse.Metadata metadata = new LinqResponse.Metadata();
            metadata.setSource("workflow");
            metadata.setStatus("error");
            metadata.setTeam(teamContextService.getTeamFromContext().block());
            metadata.setCacheHit(false);
            metadata.setWorkflowMetadata(stepMetadata);
            errorResponse.setMetadata(metadata);
            
            // Set error result
            LinqResponse.WorkflowResult errorResult = new LinqResponse.WorkflowResult();
            errorResult.setSteps(steps.stream()
                .map(step -> {
                    LinqResponse.WorkflowStep stepResult = new LinqResponse.WorkflowStep();
                    stepResult.setStep(step.getStep());
                    stepResult.setTarget(step.getTarget());
                    stepResult.setResult(stepResults.get(step.getStep()));
                    return stepResult;
                })
                .collect(Collectors.toList()));
            errorResult.setFinalResult(error.getMessage());
            errorResponse.setResult(errorResult);
            
            return Mono.just(errorResponse);
        });
    }

    private Map<String, Object> resolvePlaceholdersForMap(Map<String, Object> input, Map<Integer, Object> stepResults) {
        if (input == null) return new HashMap<>();
        Map<String, Object> resolved = new HashMap<>();
        input.forEach((key, value) -> resolved.put(key, resolvePlaceholders(value, stepResults)));
        return resolved;
    }

    private Object resolvePlaceholders(Object input, Map<Integer, Object> stepResults) {
        if (input == null) return null;
        if (input instanceof String stringInput) {
            return resolvePlaceholder(stringInput, stepResults);
        }
        if (input instanceof List<?> list) {
            return list.stream()
                    .map(item -> resolvePlaceholders(item, stepResults))
                    .collect(Collectors.toList());
        }
        if (input instanceof Map<?, ?> map) {
            return resolvePlaceholdersForMap(map.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            Map.Entry::getValue
                    )), stepResults);
        }
        return input;
    }

    private String resolvePlaceholder(String value, Map<Integer, Object> stepResults) {
        String result = value;
        Pattern pattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([\\w.]+))?\\}\\}");
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            int stepNum = Integer.parseInt(matcher.group(1));
            String path = matcher.group(2);
            Object stepResult = stepResults.get(stepNum);
            String replacement = "";
            if (stepResult != null) {
                replacement = path != null ? extractValue(stepResult, path) : String.valueOf(stepResult);
            }
            result = result.replace(matcher.group(0), replacement);
        }
        return result;
    }

    private String extractValue(Object obj, String path) {
        String[] parts = path.split("\\.");
        Object current = obj;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list && part.matches("\\d+")) {
                current = list.get(Integer.parseInt(part));
            } else {
                return "";
            }
            if (current == null) return "";
        }
        return String.valueOf(current);
    }

    private String extractFinalResult(Object result) {
        if (result instanceof Map<?, ?> resultMap) {
            Object choices = resultMap.get("choices");
            if (choices instanceof List<?> choiceList && !choiceList.isEmpty()) {
                Object firstChoice = choiceList.getFirst();
                if (firstChoice instanceof Map<?, ?> choiceMap) {
                    Object message = choiceMap.get("message");
                    if (message instanceof Map<?, ?> messageMap) {
                        Object content = messageMap.get("content");
                        return content != null ? content.toString() : "";
                    }
                }
            }
        }
        return result != null ? result.toString() : "";
    }

    @Override
    public Mono<LinqWorkflow> createNewVersion(String workflowId, LinqWorkflow updatedWorkflow) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> workflowRepository.findByIdAndTeam(workflowId, teamId)
                .flatMap(workflow -> {
                    // Create new version
                    LinqWorkflowVersion newVersion = LinqWorkflowVersion.builder()
                        .workflowId(workflowId)
                        .team(teamId)
                        .version(workflow.getVersion() + 1)
                        .request(workflow.getRequest())
                        .createdAt(System.currentTimeMillis())
                        .createdBy(workflow.getUpdatedBy())
                        .changeDescription("Version created at " + LocalDateTime.now())
                        .build();

                    // Update workflow with new data
                    workflow.setVersion(newVersion.getVersion());
                    workflow.setName(updatedWorkflow.getName());
                    workflow.setDescription(updatedWorkflow.getDescription());
                    workflow.setRequest(updatedWorkflow.getRequest());
                    workflow.setPublic(updatedWorkflow.isPublic());
                    workflow.setUpdatedAt(LocalDateTime.now());
                    workflow.setUpdatedBy(updatedWorkflow.getUpdatedBy());

                    return workflowVersionRepository.save(newVersion)
                        .then(workflowRepository.save(workflow))
                        .doOnSuccess(w -> log.info("Created new version {} for workflow: {}", 
                            newVersion.getVersion(), w.getId()))
                        .doOnError(error -> log.error("Error creating new version: {}", error.getMessage()));
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Workflow not found or access denied"))));
    }

    @Override
    public Mono<LinqWorkflow> rollbackToVersion(String workflowId, String versionId) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> workflowVersionRepository.findById(versionId)
                .filter(version -> version.getWorkflowId().equals(workflowId) && version.getTeam().equals(teamId))
                .flatMap(version -> workflowRepository.findByIdAndTeam(workflowId, teamId)
                    .flatMap(workflow -> {
                        // Create new version with rollback
                        LinqWorkflowVersion newVersion = LinqWorkflowVersion.builder()
                            .workflowId(workflowId)
                            .team(teamId)
                            .version(workflow.getVersion() + 1)
                            .request(version.getRequest())
                            .createdAt(System.currentTimeMillis())
                            .createdBy(workflow.getUpdatedBy())
                            .changeDescription("Rollback to version " + version.getVersion())
                            .build();

                        // Update workflow
                        workflow.setVersion(newVersion.getVersion());
                        workflow.setRequest(version.getRequest());
                        workflow.setUpdatedAt(LocalDateTime.now());

                        return workflowVersionRepository.save(newVersion)
                            .then(workflowRepository.save(workflow))
                            .doOnSuccess(w -> log.info("Rolled back workflow {} to version {}", 
                                w.getId(), version.getVersion()))
                            .doOnError(error -> log.error("Error rolling back workflow: {}", error.getMessage()));
                    }))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Version not found or access denied"))));
    }

    @Override
    public Flux<LinqWorkflowVersion> getVersionHistory(String workflowId) {
        return teamContextService.getTeamFromContext()
            .flatMapMany(teamId -> workflowVersionRepository.findByWorkflowIdAndTeamOrderByVersionDesc(workflowId, teamId)
                .doOnError(error -> log.error("Error fetching version history: {}", error.getMessage()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Workflow not found or access denied"))));
    }

    @Override
    public Mono<LinqWorkflowVersion> getVersion(String workflowId, String versionId) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> workflowVersionRepository.findById(versionId)
                .filter(version -> version.getWorkflowId().equals(workflowId) && version.getTeam().equals(teamId))
                .doOnError(error -> log.error("Error fetching version: {}", error.getMessage()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Version not found or access denied"))));
    }
}
