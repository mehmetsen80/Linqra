package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
// Removed PreAuthorize import as it is no longer used
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/teams/{teamId}/encryption")
@RequiredArgsConstructor
public class TeamEncryptionController {

    private final ChunkEncryptionService chunkEncryptionService;
    private final TeamContextService teamContextService;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;

    @GetMapping("/keys/active")
    public Mono<ResponseEntity<Map<String, String>>> getActiveKeyVersion(
            @PathVariable String teamId,
            ServerWebExchange exchange) {

        log.info("Fetching active encryption key version for team: {}", teamId);

        return Mono.zip(
                teamContextService.getTeamFromContext(exchange),
                userContextService.getCurrentUsername(exchange))
                .flatMap(tuple -> {
                    String contextTeamId = tuple.getT1();
                    String username = tuple.getT2();

                    // Require ADMIN role or SUPER_ADMIN
                    return userService.findByUsername(username)
                            .flatMap(user -> teamService.hasRole(contextTeamId, user.getId(), "ADMIN")
                                    .filter(isAdmin -> isAdmin || user.getRoles().contains("SUPER_ADMIN"))
                                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                                            "Admin access required for team " + contextTeamId)))
                                    .then(chunkEncryptionService.getCurrentKeyVersion(contextTeamId)))
                            .map(version -> ResponseEntity.ok(Map.of("version", version)));
                });
    }

    @PostMapping("/keys/rotate")
    public Mono<ResponseEntity<Map<String, String>>> rotateKey(
            @PathVariable String teamId,
            ServerWebExchange exchange) {

        log.info("Rotating encryption key for team: {}", teamId);

        return Mono.zip(
                teamContextService.getTeamFromContext(exchange),
                userContextService.getCurrentUsername(exchange))
                .flatMap(tuple -> {
                    String contextTeamId = tuple.getT1();
                    String username = tuple.getT2();

                    // Require ADMIN role or SUPER_ADMIN
                    return userService.findByUsername(username)
                            .flatMap(user -> teamService.hasRole(contextTeamId, user.getId(), "ADMIN")
                                    .filter(isAdmin -> isAdmin || user.getRoles().contains("SUPER_ADMIN"))
                                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                                            "Admin access required for team " + contextTeamId)))
                                    .then(chunkEncryptionService.rotateKey(contextTeamId)))
                            .map(newVersion -> ResponseEntity.ok(Map.of(
                                    "message", "Key rotation initiated successfully",
                                    "version", newVersion)));
                });
    }
}
