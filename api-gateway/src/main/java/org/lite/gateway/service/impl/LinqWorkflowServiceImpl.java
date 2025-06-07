package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowVersion;
import org.lite.gateway.repository.LinqWorkflowRepository;
import org.lite.gateway.repository.LinqWorkflowVersionRepository;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.service.LinqWorkflowService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqWorkflowServiceImpl implements LinqWorkflowService {
    private final LinqWorkflowRepository workflowRepository;
    private final TeamContextService teamContextService;
    private final LinqWorkflowVersionRepository workflowVersionRepository;
    private final LinqWorkflowExecutionRepository executionRepository;

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
                        
                        // Set the workflowId in the request's query
                        if (savedWorkflow.getRequest() != null && 
                            savedWorkflow.getRequest().getQuery() != null) {
                            savedWorkflow.getRequest().getQuery().setWorkflowId(savedWorkflow.getId());
                        }
                        
                        return workflowVersionRepository.save(initialVersion)
                            .then(workflowRepository.save(savedWorkflow));
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
                    // Delete all versions
                    return workflowVersionRepository.findByWorkflowIdAndTeamOrderByVersionDesc(workflowId, teamId)
                        .flatMap(workflowVersionRepository::delete)
                        .then()
                        .then(executionRepository.findByWorkflowIdAndTeam(workflowId, teamId, Sort.by(Sort.Direction.DESC, "executedAt"))
                            .flatMap(executionRepository::delete)
                            .then())
                        .then(workflowRepository.delete(workflow))
                        .doOnSuccess(v -> log.info("Deleted workflow: {} and its versions and executions", workflowId))
                        .doOnError(error -> log.error("Error deleting workflow: {}", error.getMessage()));
                }));
    }

    @Override
    public Flux<LinqWorkflow> getAllWorkflows() {
        return teamContextService.getTeamFromContext()
            .flatMapMany(teamId -> workflowRepository.findByTeamOrderByCreatedAtDesc(teamId)
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
    public Flux<LinqWorkflow> searchWorkflows(String searchTerm) {
        return workflowRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            searchTerm, searchTerm)
            .doOnError(error -> log.error("Error searching workflows: {}", error.getMessage()));
    }
    
}
