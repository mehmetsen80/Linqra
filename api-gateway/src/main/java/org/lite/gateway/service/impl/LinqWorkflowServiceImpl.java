package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowVersion;
import org.lite.gateway.repository.LinqWorkflowRepository;
import org.lite.gateway.repository.LinqWorkflowVersionRepository;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.service.LinqWorkflowService;
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
    private final LinqWorkflowVersionRepository workflowVersionRepository;
    private final LinqWorkflowExecutionRepository executionRepository;

    @Override
    public Mono<LinqWorkflow> createWorkflow(LinqWorkflow workflow) {
        String teamId = workflow.getTeamId();
        if (teamId == null) {
            return Mono.error(new RuntimeException("Team ID must be provided"));
        }
        
        workflow.setVersion(1);
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());

        // Create initial version
        LinqWorkflowVersion initialVersion = LinqWorkflowVersion.builder()
            .workflowId(null) // Will be set after workflow is saved
            .teamId(teamId)
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
    }

    @Override
    public Mono<LinqWorkflow> updateWorkflow(String workflowId, LinqWorkflow updatedWorkflow) {
        String teamId = updatedWorkflow.getTeamId();
        if (teamId == null) {
            return Mono.error(new RuntimeException("Team ID must be provided"));
        }
        
        return workflowRepository.findByIdAndTeamId(workflowId, teamId)
            .flatMap(existingWorkflow -> {
                updatedWorkflow.setId(workflowId);
                updatedWorkflow.setCreatedAt(existingWorkflow.getCreatedAt());
                updatedWorkflow.setUpdatedAt(LocalDateTime.now());
                return workflowRepository.save(updatedWorkflow)
                    .doOnSuccess(w -> log.info("Updated workflow: {}", w.getId()))
                    .doOnError(error -> log.error("Error updating workflow: {}", error.getMessage()));
            })
            .switchIfEmpty(Mono.error(new RuntimeException("Workflow not found or access denied")));
    }

    @Override
    public Mono<Void> deleteWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, 
                "Workflow not found")))
            .flatMap(workflow -> {
                log.info("Deleting workflow: {} and its versions", workflow);
                String teamId = workflow.getTeamId();
                if (teamId == null) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Workflow has no teamId"));
                }
                // Delete all versions
                return workflowVersionRepository.findByWorkflowIdAndTeamIdOrderByVersionDesc(workflowId, teamId)
                    .flatMap(workflowVersionRepository::delete)
                    .then()
                    .then(executionRepository.findByWorkflowIdAndTeamId(workflowId, teamId, Sort.by(Sort.Direction.DESC, "executedAt"))
                        .flatMap(executionRepository::delete)
                        .then())
                    .then(workflowRepository.delete(workflow))
                    .doOnSuccess(v -> log.info("Deleted workflow: {} and its versions and executions", workflowId))
                    .doOnError(error -> log.error("Error deleting workflow: {}", error.getMessage()));
            });
    }

    @Override
    public Mono<Void> deleteWorkflow(String workflowId, String teamId) {
        return workflowRepository.findByIdAndTeamId(workflowId, teamId)
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, 
                "Workflow not found or access denied")))
            .flatMap(workflow -> {
                log.info("Deleting workflow: {} and its versions", workflow);
                // Delete all versions
                return workflowVersionRepository.findByWorkflowIdAndTeamIdOrderByVersionDesc(workflowId, teamId)
                    .flatMap(workflowVersionRepository::delete)
                    .then()
                    .then(executionRepository.findByWorkflowIdAndTeamId(workflowId, teamId, Sort.by(Sort.Direction.DESC, "executedAt"))
                        .flatMap(executionRepository::delete)
                        .then())
                    .then(workflowRepository.delete(workflow))
                    .doOnSuccess(v -> log.info("Deleted workflow: {} and its versions and executions", workflowId))
                    .doOnError(error -> log.error("Error deleting workflow: {}", error.getMessage()));
            });
    }

    @Override
    public Flux<LinqWorkflow> getAllWorkflows(String teamId) {
        return workflowRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
            .doOnError(error -> log.error("Error fetching workflows: {}", error.getMessage()));
    }

    @Override
    public Mono<LinqWorkflow> getWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId)
            .doOnError(error -> log.error("Error fetching workflow: {}", error.getMessage()))
            .switchIfEmpty(Mono.error(new RuntimeException("Workflow not found")));
    }

    @Override
    public Mono<LinqWorkflow> getWorkflowByIdAndTeam(String workflowId, String teamId) {
        log.info("Fetching workflow {} for team {} (bypassing context)", workflowId, teamId);
        return workflowRepository.findByIdAndTeamId(workflowId, teamId)
            .doOnError(error -> log.error("Error fetching workflow: {}", error.getMessage()))
            .switchIfEmpty(Mono.error(new RuntimeException("Workflow not found: " + workflowId + " for team: " + teamId)));
    }

    @Override
    public Flux<LinqWorkflow> searchWorkflows(String searchTerm) {
        return workflowRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            searchTerm, searchTerm)
            .doOnError(error -> log.error("Error searching workflows: {}", error.getMessage()));
    }
    
}
