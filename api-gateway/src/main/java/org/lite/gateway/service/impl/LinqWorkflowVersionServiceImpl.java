package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowVersion;
import org.lite.gateway.repository.LinqWorkflowRepository;
import org.lite.gateway.repository.LinqWorkflowVersionRepository;
import org.lite.gateway.service.LinqWorkflowVersionService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqWorkflowVersionServiceImpl implements LinqWorkflowVersionService {
    private final LinqWorkflowRepository workflowRepository;
    private final LinqWorkflowVersionRepository workflowVersionRepository;
    private final TeamContextService teamContextService;

    @Override
    public Mono<LinqWorkflow> createNewVersion(String workflowId, LinqWorkflow updatedWorkflow) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> workflowRepository.findByIdAndTeam(workflowId, teamId)
                .flatMap(workflow -> 
                    // Get the latest version number
                    workflowVersionRepository.findFirstByWorkflowIdAndTeamOrderByVersionDesc(workflowId, teamId)
                        .map(latestVersion -> latestVersion.getVersion() + 1)
                        .defaultIfEmpty(1)
                        .flatMap(newVersionNumber -> {
                            // Create new version
                            LinqWorkflowVersion newVersion = LinqWorkflowVersion.builder()
                                .workflowId(workflowId)
                                .team(teamId)
                                .version(newVersionNumber)
                                .request(updatedWorkflow.getRequest())
                                .createdAt(System.currentTimeMillis())
                                .createdBy(updatedWorkflow.getUpdatedBy())
                                .changeDescription("Version created at " + LocalDateTime.now())
                                .build();

                            // Update workflow with new data
                            workflow.setVersion(newVersionNumber);
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
                )
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