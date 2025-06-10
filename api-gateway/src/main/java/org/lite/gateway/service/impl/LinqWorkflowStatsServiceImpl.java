package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.TeamWorkflowStats;
import org.lite.gateway.model.LinqWorkflowStats;
import org.lite.gateway.model.ExecutionStatus;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.repository.LinqWorkflowRepository;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.service.LinqWorkflowStatsService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqWorkflowStatsServiceImpl implements LinqWorkflowStatsService {
    private final LinqWorkflowRepository workflowRepository;
    private final TeamContextService teamContextService;
    private final LinqWorkflowExecutionRepository executionRepository;

    @Override
    public Mono<LinqWorkflowStats> getWorkflowStats(String workflowId) {
        return executionRepository.findByWorkflowId(workflowId, Sort.by(Sort.Direction.DESC, "executedAt"))
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
    public Mono<TeamWorkflowStats> getTeamStats() {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> executionRepository.findByTeam(teamId, Sort.by(Sort.Direction.DESC, "executedAt"))
                .collectList()
                .flatMap(executions -> {
                    TeamWorkflowStats stats = new TeamWorkflowStats();
                    
                    // Initialize maps
                    stats.setWorkflowStats(new HashMap<>());
                    stats.setStepStats(new HashMap<>());
                    stats.setTargetStats(new HashMap<>());
                    stats.setModelStats(new HashMap<>());
                    stats.setHourlyExecutions(new HashMap<>());
                    stats.setDailyExecutions(new HashMap<>());
                    
                    // Calculate overall execution stats
                    stats.setTotalExecutions(executions.size());
                    stats.setSuccessfulExecutions((int) executions.stream()
                        .filter(e -> e.getStatus() == ExecutionStatus.SUCCESS)
                        .count());
                    stats.setFailedExecutions((int) executions.stream()
                        .filter(e -> e.getStatus() == ExecutionStatus.FAILED)
                        .count());
                    
                    // Calculate average execution time
                    double avgTime = executions.stream()
                        .mapToLong(LinqWorkflowExecution::getDurationMs)
                        .average()
                        .orElse(0.0);
                    stats.setAverageExecutionTime(avgTime);
                    
                    // Get unique workflow IDs
                    Set<String> workflowIds = executions.stream()
                        .map(LinqWorkflowExecution::getWorkflowId)
                        .collect(Collectors.toSet());
                    
                    // Fetch workflow names
                    return Flux.fromIterable(workflowIds)
                        .flatMap(workflowId -> workflowRepository.findById(workflowId)
                            .map(workflow -> new AbstractMap.SimpleEntry<>(workflowId, workflow.getName())))
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .map(workflowNames -> {
                            // Process each execution
                            for (LinqWorkflowExecution execution : executions) {
                                // Time-based stats
                                String hour = execution.getExecutedAt().format(DateTimeFormatter.ofPattern("HH:00"));
                                String day = execution.getExecutedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                stats.getHourlyExecutions().merge(hour, 1, Integer::sum);
                                stats.getDailyExecutions().merge(day, 1, Integer::sum);
                                
                                // Update workflow stats
                                TeamWorkflowStats.WorkflowStats workflowStats = stats.getWorkflowStats()
                                    .computeIfAbsent(execution.getWorkflowId(), k -> {
                                        TeamWorkflowStats.WorkflowStats ws = new TeamWorkflowStats.WorkflowStats();
                                        ws.setWorkflowId(k);
                                        ws.setWorkflowName(workflowNames.get(k));
                                        ws.setStepStats(new HashMap<>());
                                        return ws;
                                    });
                                workflowStats.incrementExecutions(execution.getStatus() == ExecutionStatus.SUCCESS);
                                
                                // Update step stats from workflow metadata
                                if (execution.getResponse() != null && 
                                    execution.getResponse().getMetadata() != null && 
                                    execution.getResponse().getMetadata().getWorkflowMetadata() != null) {
                                    
                                    execution.getResponse().getMetadata().getWorkflowMetadata().forEach(stepMetadata -> {
                                        // Step stats
                                        TeamWorkflowStats.StepStats stepStats = stats.getStepStats()
                                            .computeIfAbsent(String.valueOf(stepMetadata.getStep()), k -> {
                                                TeamWorkflowStats.StepStats ss = new TeamWorkflowStats.StepStats();
                                                ss.setStepNumber(stepMetadata.getStep());
                                                return ss;
                                            });
                                        stepStats.incrementExecutions("success".equals(stepMetadata.getStatus()));
                                        
                                        // Update most common target and intent
                                        stepStats.setMostCommonTarget(stepMetadata.getTarget());
                                        String intent = execution.getRequest().getQuery().getWorkflow().stream()
                                            .filter(step -> step.getStep() == stepMetadata.getStep())
                                            .findFirst()
                                            .map(step -> step.getIntent())
                                            .orElse("unknown");
                                        stepStats.setMostCommonIntent(intent);
                                        
                                        // Update workflow's step stats
                                        TeamWorkflowStats.StepStats workflowStepStats = workflowStats.getStepStats()
                                            .computeIfAbsent(String.valueOf(stepMetadata.getStep()), k -> {
                                                TeamWorkflowStats.StepStats ss = new TeamWorkflowStats.StepStats();
                                                ss.setStepNumber(stepMetadata.getStep());
                                                return ss;
                                            });
                                        workflowStepStats.incrementExecutions("success".equals(stepMetadata.getStatus()));
                                        workflowStepStats.setMostCommonTarget(stepMetadata.getTarget());
                                        workflowStepStats.setMostCommonIntent(intent);
                                        
                                        // Target stats
                                        TeamWorkflowStats.TargetStats targetStats = stats.getTargetStats()
                                            .computeIfAbsent(stepMetadata.getTarget(), k -> {
                                                TeamWorkflowStats.TargetStats ts = new TeamWorkflowStats.TargetStats();
                                                ts.setTarget(k);
                                                ts.setIntentCounts(new HashMap<>());
                                                return ts;
                                            });
                                        targetStats.incrementExecutions("success".equals(stepMetadata.getStatus()));
                                        
                                        // Update intent counts
                                        targetStats.getIntentCounts().merge(intent, 1, Integer::sum);
                                        
                                        // Model stats for AI targets
                                        if (stepMetadata.getTarget().equals("openai") || stepMetadata.getTarget().equals("gemini")) {
                                            String model = execution.getRequest().getQuery().getWorkflow().stream()
                                                .filter(step -> step.getStep() == stepMetadata.getStep())
                                                .findFirst()
                                                .map(step -> step.getToolConfig().getModel())
                                                .orElse("unknown");
                                            
                                            TeamWorkflowStats.ModelStats modelStats = stats.getModelStats()
                                                .computeIfAbsent(model, k -> {
                                                    TeamWorkflowStats.ModelStats ms = new TeamWorkflowStats.ModelStats();
                                                    ms.setModel(k);
                                                    return ms;
                                                });
                                            modelStats.incrementExecutions("success".equals(stepMetadata.getStatus()));
                                            
                                            // Update token counts if available
                                            if (stepMetadata.getTokenUsage() != null) {
                                                modelStats.setTotalPromptTokens(modelStats.getTotalPromptTokens() + stepMetadata.getTokenUsage().getPromptTokens());
                                                modelStats.setTotalCompletionTokens(modelStats.getTotalCompletionTokens() + stepMetadata.getTokenUsage().getCompletionTokens());
                                                modelStats.setTotalTokens(modelStats.getTotalTokens() + stepMetadata.getTokenUsage().getTotalTokens());
                                            }
                                        }
                                    });
                                }
                            }
                            
                            return stats;
                        });
                }));
    }
} 