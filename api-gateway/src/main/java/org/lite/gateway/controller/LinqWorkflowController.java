package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.dto.ErrorCode;
import org.lite.gateway.model.LinqWorkflowStats;
import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.entity.LinqWorkflowVersion;
import org.lite.gateway.service.LinqWorkflowService;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.LinqWorkflowVersionService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.validation.LinqRequestValidationService;
import org.lite.gateway.validation.ValidationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import org.lite.gateway.dto.TeamWorkflowStats;
import org.lite.gateway.service.LinqWorkflowStatsService;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/linq/workflows")
@RequiredArgsConstructor
public class LinqWorkflowController {
    private final LinqWorkflowService linqWorkflowService;
    private final LinqWorkflowVersionService linqWorkflowVersionService;
    private final LinqWorkflowExecutionService workflowExecutionService;
    private final TeamService teamService;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamContextService teamContextService;
    private final LinqRequestValidationService linqRequestValidationService;
    private final ObjectMapper objectMapper;
    private final LinqWorkflowStatsService linqWorkflowStatsService;

    @PostMapping
    public Mono<ResponseEntity<?>> createWorkflow(
        @RequestBody String rawRequest,
        ServerWebExchange exchange
    ) {
        log.info("Creating new workflow with request: {}", rawRequest);
        
        try {
            // First validate the raw JSON
            ValidationResult validationResult = linqRequestValidationService.validate(rawRequest);
            if (!validationResult.isValid()) {
                return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.VALIDATION_ERROR,
                        "Workflow validation failed: " + String.join(", ", validationResult.getErrors()),
                        HttpStatus.BAD_REQUEST.value()
                    )));
            }

            // Then check if it's a LinqRequest structure
            JsonNode rootNode = objectMapper.readTree(rawRequest);
            LinqWorkflow workflow = new LinqWorkflow();
            
            if (rootNode.has("link") && rootNode.has("query")) {
                // It's a LinqRequest structure
                LinqRequest request = objectMapper.readValue(rawRequest, LinqRequest.class);
                workflow.setRequest(request);
            } else if (rootNode.has("request")) {
                // It's a LinqWorkflow structure
                workflow = objectMapper.readValue(rawRequest, LinqWorkflow.class);
            } else {
                return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.VALIDATION_ERROR,
                        "Invalid request format: must contain either 'link' and 'query' fields (LinqRequest) or 'request' field (LinqWorkflow)",
                        HttpStatus.BAD_REQUEST.value()
                    )));
            }
            
            return validateAndCreateWorkflow(workflow, exchange);
        } catch (Exception e) {
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromErrorCode(
                    ErrorCode.VALIDATION_ERROR,
                    "Error processing request: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value()
                )));
        }
    }

    private Mono<ResponseEntity<?>> validateAndCreateWorkflow(LinqWorkflow workflow, ServerWebExchange exchange) {
        try {
            // Validate the request part
            ValidationResult validationResult = linqRequestValidationService.validate(workflow.getRequest());
            if (!validationResult.isValid()) {
                return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.VALIDATION_ERROR,
                        "Workflow validation failed: " + String.join(", ", validationResult.getErrors()),
                        HttpStatus.BAD_REQUEST.value()
                    )));
            }

            return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // Set createdBy and updatedBy
                    workflow.setCreatedBy(user.getUsername());
                    workflow.setUpdatedBy(user.getUsername());
                    
                    // Get team from context and handle authorization
                    return teamContextService.getTeamFromContext()
                        .flatMap(teamId -> {
                            // Set the teamId in the workflow entity
                            workflow.setTeamId(teamId);
                            
                            // For SUPER_ADMIN, proceed directly
                            if (user.getRoles().contains("SUPER_ADMIN")) {
                                return addTeamIdToWorkflowParams(workflow)
                                    .flatMap(updatedWorkflow -> linqWorkflowService.createWorkflow(updatedWorkflow))
                                    .map(ResponseEntity::ok);
                            }
                            
                            // For non-SUPER_ADMIN users, check team role
                            return teamService.hasRole(teamId, user.getId(), "ADMIN")
                                .flatMap(isAdmin -> {
                                    if (!isAdmin) {
                                        return Mono.just(ResponseEntity
                                            .status(HttpStatus.FORBIDDEN)
                                            .body(ErrorResponse.fromErrorCode(
                                                ErrorCode.FORBIDDEN,
                                                "Only team administrators can create workflows",
                                                HttpStatus.FORBIDDEN.value()
                                            )));
                                    }
                                    return addTeamIdToWorkflowParams(workflow)
                                        .flatMap(linqWorkflowService::createWorkflow)
                                        .map(ResponseEntity::ok);
                                });
                        });
                })
                .doOnSuccess(w -> Optional.ofNullable(w.getBody())
                    .map(body -> (LinqWorkflow) body)
                    .ifPresent(savedWorkflow -> log.info("Workflow created successfully: {}", savedWorkflow.getId())))
                .doOnError(error -> log.error("Error creating workflow: {}", error.getMessage()));
        } catch (Exception e) {
            log.error("Error processing request: ", e);
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromErrorCode(
                    ErrorCode.VALIDATION_ERROR,
                    "Error processing request: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value()
                )));
        }
    }

    @PutMapping("/{workflowId}")
    public Mono<ResponseEntity<?>> updateWorkflow(
        @PathVariable String workflowId,
        @RequestBody LinqWorkflow workflow,
        ServerWebExchange exchange
    ) {
        log.info("Updating workflow: {}", workflowId);
        return linqWorkflowService.getWorkflow(workflowId)
            .flatMap(existingWorkflow -> userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // Set updatedBy
                    workflow.setUpdatedBy(user.getUsername());
                    workflow.setCreatedBy(existingWorkflow.getCreatedBy()); // Preserve original creator
                    
                    // For SUPER_ADMIN, proceed directly
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return linqWorkflowService.updateWorkflow(workflowId, workflow)
                            .map(ResponseEntity::ok);
                    }
                    
                    // For non-SUPER_ADMIN users, check team role
                    return teamService.hasRole(existingWorkflow.getTeamId(), user.getId(), "ADMIN")
                        .flatMap(isAdmin -> {
                            if (!isAdmin) {
                                return Mono.just(ResponseEntity
                                    .status(HttpStatus.FORBIDDEN)
                                    .body(ErrorResponse.fromErrorCode(
                                        ErrorCode.FORBIDDEN,
                                        "Only team administrators can update workflows",
                                        HttpStatus.FORBIDDEN.value()
                                    )));
                            }
                            return linqWorkflowService.updateWorkflow(workflowId, workflow)
                                .map(ResponseEntity::ok);
                        });
                }))
            .doOnSuccess(w -> Optional.ofNullable(w.getBody())
                .map(body -> (LinqWorkflow) body)
                .ifPresent(updatedWorkflow -> log.info("Workflow updated successfully: {}", updatedWorkflow.getId())))
            .doOnError(error -> log.error("Error updating workflow: {}", error.getMessage()));
    }

    @DeleteMapping("/{workflowId}")
    public Mono<ResponseEntity<?>> deleteWorkflow(
        @PathVariable String workflowId,
        ServerWebExchange exchange
    ) {
        log.info("Deleting workflow: {}", workflowId);
        return linqWorkflowService.getWorkflow(workflowId)
            .flatMap(workflow -> userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // For SUPER_ADMIN, proceed directly
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return linqWorkflowService.deleteWorkflow(workflowId)
                            .then(Mono.just(ResponseEntity.noContent().<Void>build()));
                    }
                    
                    // For non-SUPER_ADMIN users, check team role
                    return teamService.hasRole(workflow.getTeamId(), user.getId(), "ADMIN")
                        .flatMap(isAdmin -> {
                            if (!isAdmin) {
                                return Mono.just(ResponseEntity
                                    .status(HttpStatus.FORBIDDEN)
                                    .body(ErrorResponse.fromErrorCode(
                                        ErrorCode.FORBIDDEN,
                                        "Only team administrators can delete workflows",
                                        HttpStatus.FORBIDDEN.value()
                                    )));
                            }
                            return linqWorkflowService.deleteWorkflow(workflowId)
                                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
                        });
                }))
            .doOnSuccess(v -> log.info("Workflow deleted successfully: {}", workflowId))
            .doOnError(error -> log.error("Error deleting workflow: {}", error.getMessage()));
    }

    @GetMapping
    public Flux<LinqWorkflow> getAllWorkflows() {
        log.info("Fetching all workflows");
        return linqWorkflowService.getAllWorkflows()
            .doOnError(error -> log.error("Error getting all workflows: {}", error.getMessage()));
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
        @RequestBody(required = false) Map<String, Object> variables,
        ServerWebExchange exchange
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
                
                // Store the workflow steps for each individual request
                List<LinqRequest.Query.WorkflowStep> workflowSteps = request.getQuery().getWorkflow();
                
                // Fix the max.tokens issue in the workflow steps
                if (workflowSteps != null) {
                    workflowSteps.forEach(step -> {
                        if (step.getToolConfig() != null && step.getToolConfig().getSettings() != null) {
                            Map<String, Object> settings = step.getToolConfig().getSettings();
                            if (settings.containsKey("max.tokens")) {
                                Object value = settings.remove("max.tokens");
                                settings.put("max_tokens", value);
                            }
                        }
                    });
                }
                
                // Set the executedBy field from current user context using the exchange
                return userContextService.getCurrentUsername(exchange)
                    .flatMap(username -> {
                        request.setExecutedBy(username);
                        log.info("Setting executedBy to: {}", username);
                        
                        return workflowExecutionService.executeWorkflow(request)
                            .flatMap(response -> workflowExecutionService.trackExecution(request, response)
                                .thenReturn(response));
                    });
            })
            .doOnSuccess(r -> log.info("Workflow executed successfully: {}", workflowId))
            .doOnError(error -> log.error("Error executing workflow: {}", error.getMessage()));
    }

    @GetMapping("/{workflowId}/executions")
    public Flux<LinqWorkflowExecution> getWorkflowExecutions(@PathVariable String workflowId) {
        log.info("Fetching executions for workflow: {}", workflowId);
        return workflowExecutionService.getWorkflowExecutions(workflowId)
            .doOnNext(e -> log.info("Execution fetched: {}", e.getId()))
            .doOnError(error -> log.error("Error fetching executions: {}", error.getMessage()));
    }

    @GetMapping("/executions")
    public Flux<LinqWorkflowExecution> getTeamExecutions() {
        log.info("Fetching executions for current team");
        return workflowExecutionService.getTeamExecutions()
            .doOnError(error -> log.error("Error fetching team executions: {}", error.getMessage()));
    }

    @GetMapping("/executions/{executionId}")
    public Mono<LinqWorkflowExecution> getExecution(@PathVariable String executionId) {
        log.info("Fetching execution: {}", executionId);
        return workflowExecutionService.getExecution(executionId)
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
        return linqWorkflowStatsService.getWorkflowStats(workflowId)
            .doOnError(error -> log.error("Error fetching workflow stats: {}", error.getMessage()));
    }

    @PostMapping("/validate")
    public Mono<ValidationResult> validateWorkflow(@RequestBody String rawRequest) {
        log.info("Validating workflow request: {}", rawRequest);
        
        try {
            // First validate the raw JSON
            ValidationResult validationResult = linqRequestValidationService.validate(rawRequest);
            if (!validationResult.isValid()) {
                return Mono.just(validationResult);
            }

            // Then check if it's a LinqRequest structure
            JsonNode rootNode = objectMapper.readTree(rawRequest);
            if (rootNode.has("link") && rootNode.has("query")) {
                // It's a LinqRequest structure
                LinqRequest request = objectMapper.readValue(rawRequest, LinqRequest.class);
                return Mono.just(linqRequestValidationService.validate(request));
            } else if (rootNode.has("request")) {
                // It's a LinqWorkflow structure
                LinqWorkflow workflow = objectMapper.readValue(rawRequest, LinqWorkflow.class);
                return Mono.just(linqRequestValidationService.validate(workflow.getRequest()));
            } else {
                ValidationResult result = new ValidationResult();
                result.setValid(false);
                result.setErrors(List.of("Invalid request format: must contain either 'link' and 'query' fields (LinqRequest) or 'request' field (LinqWorkflow)"));
                return Mono.just(result);
            }
        } catch (Exception e) {
            ValidationResult result = new ValidationResult();
            result.setValid(false);
            result.setErrors(List.of("Error processing request: " + e.getMessage()));
            return Mono.just(result);
        }
    }

    @PostMapping("/{workflowId}/versions")
    public Mono<ResponseEntity<?>> createNewVersion(
        @PathVariable String workflowId,
        @RequestBody String rawRequest,
        ServerWebExchange exchange
    ) {
        log.info("Creating new version for workflow: {}", workflowId);
        
        try {
            // First check if it's a LinqRequest structure
            JsonNode rootNode = objectMapper.readTree(rawRequest);
            LinqWorkflow updatedWorkflow = new LinqWorkflow();
            
            if (rootNode.has("link") && rootNode.has("query")) {
                // It's a LinqRequest structure
                LinqRequest request = objectMapper.readValue(rawRequest, LinqRequest.class);
                updatedWorkflow.setRequest(request);
            } else if (rootNode.has("request")) {
                // It's a LinqWorkflow structure
                updatedWorkflow = objectMapper.readValue(rawRequest, LinqWorkflow.class);
            } else {
                return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.VALIDATION_ERROR,
                        "Invalid request format: must contain either 'link' and 'query' fields (LinqRequest) or 'request' field (LinqWorkflow)",
                        HttpStatus.BAD_REQUEST.value()
                    )));
            }
            
            return validateAndCreateNewVersion(workflowId, updatedWorkflow, exchange);
        } catch (Exception e) {
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromErrorCode(
                    ErrorCode.VALIDATION_ERROR,
                    "Error processing request: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value()
                )));
        }
    }

    private Mono<ResponseEntity<?>> validateAndCreateNewVersion(
        String workflowId,
        LinqWorkflow updatedWorkflow,
        ServerWebExchange exchange
    ) {
        try {
            // Validate the request part
            ValidationResult validationResult = linqRequestValidationService.validate(updatedWorkflow.getRequest());
            if (!validationResult.isValid()) {
                return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.VALIDATION_ERROR,
                        "Workflow validation failed: " + String.join(", ", validationResult.getErrors()),
                        HttpStatus.BAD_REQUEST.value()
                    )));
            }

            return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // Set updatedBy for the new version
                    updatedWorkflow.setUpdatedBy(user.getUsername());
                    
                    // For SUPER_ADMIN, proceed directly
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return addTeamIdToWorkflowParams(updatedWorkflow)
                            .flatMap(updatedWorkflowWithParams -> linqWorkflowVersionService.createNewVersion(workflowId, updatedWorkflowWithParams))
                            .map(ResponseEntity::ok);
                    }
                    
                    // For non-SUPER_ADMIN users, check team role
                    return teamContextService.getTeamFromContext()
                        .flatMap(teamId -> {
                            // Set the teamId in the workflow entity
                            updatedWorkflow.setTeamId(teamId);
                            
                            return teamService.hasRole(teamId, user.getId(), "ADMIN")
                                .flatMap(isAdmin -> {
                                    if (!isAdmin) {
                                        return Mono.just(ResponseEntity
                                            .status(HttpStatus.FORBIDDEN)
                                            .body(ErrorResponse.fromErrorCode(
                                                ErrorCode.FORBIDDEN,
                                                "Only team administrators can create new workflow versions",
                                                HttpStatus.FORBIDDEN.value()
                                            )));
                                    }
                                    return addTeamIdToWorkflowParams(updatedWorkflow)
                                        .flatMap(updatedWorkflowWithParams -> linqWorkflowVersionService.createNewVersion(workflowId, updatedWorkflowWithParams))
                                        .map(ResponseEntity::ok);
                                });
                        });
                })
                .doOnSuccess(w -> Optional.ofNullable(w.getBody())
                    .map(body -> (LinqWorkflow) body)
                    .ifPresent(savedWorkflow -> log.info("New workflow version created successfully: {}", savedWorkflow.getId())))
                .doOnError(error -> log.error("Error creating new workflow version: {}", error.getMessage()));
        } catch (Exception e) {
            log.error("Error processing request: ", e);
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromErrorCode(
                    ErrorCode.VALIDATION_ERROR,
                    "Error processing request: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value()
                )));
        }
    }

    @PostMapping("/{workflowId}/versions/{versionId}/rollback")
    public Mono<ResponseEntity<?>> rollbackToVersion(
        @PathVariable String workflowId,
        @PathVariable String versionId,
        ServerWebExchange exchange
    ) {
        log.info("Rolling back workflow: {} to version: {}", workflowId, versionId);
        return linqWorkflowService.getWorkflow(workflowId)
            .flatMap(workflow -> userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // For SUPER_ADMIN, proceed directly
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return linqWorkflowVersionService.rollbackToVersion(workflowId, versionId)
                            .map(w -> ResponseEntity.ok(w));
                    }
                    
                    // For non-SUPER_ADMIN users, check team role
                    return teamService.hasRole(workflow.getTeamId(), user.getId(), "ADMIN")
                        .flatMap(isAdmin -> {
                            if (!isAdmin) {
                                return Mono.just(ResponseEntity
                                    .status(HttpStatus.FORBIDDEN)
                                    .body(ErrorResponse.fromErrorCode(
                                        ErrorCode.FORBIDDEN,
                                        "Only team administrators can rollback versions",
                                        HttpStatus.FORBIDDEN.value()
                                    )));
                            }
                            return linqWorkflowVersionService.rollbackToVersion(workflowId, versionId)
                                .map(w -> ResponseEntity.ok(w));
                        });
                }))
            .doOnSuccess(w -> Optional.ofNullable(w.getBody())
                .map(body -> (LinqWorkflow) body)
                .ifPresent(rolledBackWorkflow -> log.info("Rolled back workflow: {} to version: {}", rolledBackWorkflow.getId(), versionId)))
            .doOnError(error -> log.error("Error rolling back workflow: {}", error.getMessage()));
    }

    @GetMapping("/{workflowId}/versions")
    public Flux<LinqWorkflowVersion> getVersionHistory(@PathVariable String workflowId) {
        log.info("Fetching version history for workflow: {}", workflowId);
        return linqWorkflowVersionService.getVersionHistory(workflowId)
            .doOnError(error -> log.error("Error fetching version history: {}", error.getMessage()));
    }

    @GetMapping("/{workflowId}/versions/{versionId}")
    public Mono<LinqWorkflowVersion> getVersion(
        @PathVariable String workflowId,
        @PathVariable String versionId
    ) {
        log.info("Fetching version: {} for workflow: {}", versionId, workflowId);
        return linqWorkflowVersionService.getVersion(workflowId, versionId)
            .doOnSuccess(v -> log.info("Fetched version: {} for workflow: {}", v.getId(), workflowId))
            .doOnError(error -> log.error("Error fetching version: {}", error.getMessage()));
    }

    @GetMapping("/team/stats")
    public Mono<ResponseEntity<TeamWorkflowStats>> getTeamStats() {
        log.info("Fetching team workflow statistics");
        return linqWorkflowStatsService.getTeamStats()
            .map(ResponseEntity::ok)
            .doOnError(error -> log.error("Error fetching team workflow statistics: {}", error.getMessage()));
    }

    /**
     * Helper method to add teamId to workflow query params if not already present
     */
    private Mono<LinqWorkflow> addTeamIdToWorkflowParams(LinqWorkflow workflow) {
        if (workflow.getRequest() != null && 
            workflow.getRequest().getQuery() != null && 
            workflow.getTeamId() != null) {
            
            // Ensure params map exists
            if (workflow.getRequest().getQuery().getParams() == null) {
                workflow.getRequest().getQuery().setParams(new HashMap<>());
            }
            
            // Add teamId to params if not already present
            workflow.getRequest().getQuery().getParams().putIfAbsent("teamId", workflow.getTeamId());
            
            log.info("Added teamId {} to workflow params", workflow.getTeamId());
        }
        
        return Mono.just(workflow);
    }
}
