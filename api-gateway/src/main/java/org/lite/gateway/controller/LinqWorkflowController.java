package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.model.LinqWorkflowStats;
import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.service.LinqWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/linq/workflows")
@RequiredArgsConstructor
public class LinqWorkflowController {
    private final LinqWorkflowService linqWorkflowService;

    @PostMapping
    public Mono<LinqWorkflow> createWorkflow(@RequestBody LinqWorkflow workflow) {
        log.info("Creating new workflow: {}", workflow.getName());
        return linqWorkflowService.createWorkflow(workflow)
            .doOnSuccess(w -> log.info("Workflow created successfully: {}", w.getId()))
            .doOnError(error -> log.error("Error creating workflow: {}", error.getMessage()));
    }

    @PutMapping("/{workflowId}")
    public Mono<LinqWorkflow> updateWorkflow(
        @PathVariable String workflowId,
        @RequestBody LinqWorkflow workflow
    ) {
        log.info("Updating workflow: {}", workflowId);
        return linqWorkflowService.updateWorkflow(workflowId, workflow)
            .doOnSuccess(w -> log.info("Workflow updated successfully: {}", w.getId()))
            .doOnError(error -> log.error("Error updating workflow: {}", error.getMessage()));
    }

    @DeleteMapping("/{workflowId}")
    public Mono<ResponseEntity<Void>> deleteWorkflow(@PathVariable String workflowId) {
        log.info("Deleting workflow: {}", workflowId);
        return linqWorkflowService.deleteWorkflow(workflowId)
            .then(Mono.just(ResponseEntity.noContent().<Void>build()))
            .doOnSuccess(v -> log.info("Workflow deleted successfully: {}", workflowId))
            .doOnError(error -> log.error("Error deleting workflow: {}", error.getMessage()));
    }

    @GetMapping
    public Flux<LinqWorkflow> getWorkflows() {
        log.info("Fetching all workflows");
        return linqWorkflowService.getWorkflows()
            .doOnError(error -> log.error("Error fetching workflows: {}", error.getMessage()));
    }

    @GetMapping("/{workflowId}")
    public Mono<LinqWorkflow> getWorkflow(@PathVariable String workflowId) {
        log.info("Fetching workflow: {}", workflowId);
        return linqWorkflowService.getWorkflow(workflowId)
            .doOnSuccess(w -> log.info("Workflow fetched successfully: {}", w.getId()))
            .doOnError(error -> log.error("Error fetching workflow: {}", error.getMessage()));
    }

    @PostMapping("/{workflowId}/execute")
    public Mono<LinqResponse> executeWorkflow(
        @PathVariable String workflowId,
        @RequestBody(required = false) Map<String, Object> variables
    ) {
        log.info("Executing workflow: {} with variables: {}", workflowId, variables);
        return linqWorkflowService.getWorkflow(workflowId)
            .flatMap(workflow -> {
                // Use the workflow's request directly
                LinqRequest request = workflow.getRequest();
                
                // Ensure it's a workflow request
                if (!request.getLink().getTarget().equals("workflow")) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, 
                        "Invalid workflow request: target must be 'workflow'"
                    ));
                }
                
                // Set the workflowId in the request
                if (request.getQuery() == null) {
                    request.setQuery(new LinqRequest.Query());
                }
                request.getQuery().setWorkflowId(workflowId);
                
                // Fix the max.tokens issue in the workflow steps
                if (request.getQuery().getWorkflow() != null) {
                    request.getQuery().getWorkflow().forEach(step -> {
                        if (step.getToolConfig() != null && step.getToolConfig().getSettings() != null) {
                            Map<String, Object> settings = step.getToolConfig().getSettings();
                            if (settings.containsKey("max.tokens")) {
                                Object value = settings.remove("max.tokens");
                                settings.put("max_tokens", value);
                            }
                        }
                    });
                }
                
                return linqWorkflowService.executeWorkflow(request)
                    .flatMap(response -> 
                        linqWorkflowService.trackExecution(request, response)
                            .thenReturn(response)
                    );
            })
            .doOnSuccess(r -> log.info("Workflow executed successfully: {}", workflowId))
            .doOnError(error -> log.error("Error executing workflow: {}", error.getMessage()));
    }

    @GetMapping("/{workflowId}/executions")
    public Flux<LinqWorkflowExecution> getWorkflowExecutions(@PathVariable String workflowId) {
        log.info("Fetching executions for workflow: {}", workflowId);
        return linqWorkflowService.getWorkflowExecutions(workflowId)
            .doOnError(error -> log.error("Error fetching workflow executions: {}", error.getMessage()));
    }

    @GetMapping("/executions")
    public Flux<LinqWorkflowExecution> getTeamExecutions(@RequestParam String teamId) {
        log.info("Fetching executions for team: {}", teamId);
        return linqWorkflowService.getTeamExecutions(teamId)
            .doOnError(error -> log.error("Error fetching team executions: {}", error.getMessage()));
    }

    @GetMapping("/executions/{executionId}")
    public Mono<LinqWorkflowExecution> getExecution(@PathVariable String executionId) {
        log.info("Fetching execution: {}", executionId);
        return linqWorkflowService.getExecution(executionId)
            .doOnSuccess(e -> log.info("Execution fetched successfully: {}", e.getId()))
            .doOnError(error -> log.error("Error fetching execution: {}", error.getMessage()));
    }

    @GetMapping("/search")
    public Flux<LinqWorkflow> searchWorkflows(@RequestParam String query) {
        log.info("Searching workflows with query: {}", query);
        return linqWorkflowService.searchWorkflows(query)
            .doOnError(error -> log.error("Error searching workflows: {}", error.getMessage()));
    }

    @GetMapping("/{workflowId}/stats")
    public Mono<LinqWorkflowStats> getWorkflowStats(@PathVariable String workflowId) {
        log.info("Fetching stats for workflow: {}", workflowId);
        return linqWorkflowService.getWorkflowStats(workflowId)
            .doOnSuccess(s -> log.info("Stats fetched successfully for workflow: {}", workflowId))
            .doOnError(error -> log.error("Error fetching workflow stats: {}", error.getMessage()));
    }
}
