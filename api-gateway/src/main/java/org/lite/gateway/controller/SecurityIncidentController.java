package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.SecurityIncident;
import org.lite.gateway.enums.IncidentStatus;
import org.lite.gateway.repository.SecurityIncidentRepository;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/security/incidents")
@RequiredArgsConstructor
public class SecurityIncidentController {

    private final SecurityIncidentRepository incidentRepository;
    private final TeamContextService teamContextService;
    private final UserContextService userContextService;
    private final UserService userService;

    @GetMapping
    public Flux<SecurityIncident> getAllIncidents(
            @RequestParam(required = false) IncidentStatus status,
            ServerWebExchange exchange) {

        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMapMany(user -> {
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        if (status != null) {
                            return incidentRepository.findByStatus(status);
                        }
                        return incidentRepository.findAll();
                    }

                    // For regular ADMINs, filter by team
                    return teamContextService.getTeamFromContext(exchange)
                            .flatMapMany(teamId -> {
                                if (status != null) {
                                    return incidentRepository.findByAffectedTeamIdAndStatus(teamId, status);
                                }
                                return incidentRepository.findByAffectedTeamId(teamId);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error fetching incidents", e);
                    return Flux.error(e);
                });
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<SecurityIncident>> getIncident(
            @PathVariable String id,
            ServerWebExchange exchange) {

        return incidentRepository.findById(id)
                .flatMap(incident -> checkAccess(incident, exchange)
                        .map(username -> incident))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(e -> {
                    if ("Access Denied".equals(e.getMessage())) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }
                    return Mono.error(e);
                });
    }

    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<SecurityIncident>> updateStatus(
            @PathVariable String id,
            @RequestBody UpdateStatusRequest request,
            ServerWebExchange exchange) {

        return incidentRepository.findById(id)
                .flatMap(incident -> checkAccess(incident, exchange)
                        .flatMap(username -> {
                            incident.setStatus(request.getStatus());
                            incident.setResolutionNotes(request.getResolutionNotes());
                            incident.setUpdatedAt(LocalDateTime.now());
                            incident.setResolvedByUserId(username);

                            if (request.getStatus() == IncidentStatus.RESOLVED ||
                                    request.getStatus() == IncidentStatus.FALSE_POSITIVE) {
                                incident.setClosedAt(LocalDateTime.now());
                            }

                            return incidentRepository.save(incident);
                        }))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(e -> {
                    if ("Access Denied".equals(e.getMessage())) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }
                    return Mono.error(e);
                });
    }

    // Helper to check access and return username if valid
    private Mono<String> checkAccess(SecurityIncident incident, ServerWebExchange exchange) {
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    if (user.getRoles().contains("SUPER_ADMIN")) {
                        return Mono.just(user.getUsername());
                    }
                    return teamContextService.getTeamFromContext(exchange)
                            .flatMap(teamId -> {
                                if (incident.getAffectedTeamId() != null &&
                                        incident.getAffectedTeamId().equals(teamId)) {
                                    return Mono.just(user.getUsername());
                                }
                                return Mono.error(new RuntimeException("Access Denied"));
                            });
                });
    }

    @lombok.Data
    public static class UpdateStatusRequest {
        private IncidentStatus status;
        private String resolutionNotes;
    }
}
