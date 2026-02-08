package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.KnowledgeHubDocumentVersion;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentVersionController {

    private final KnowledgeHubDocumentService documentService;

    /**
     * Get version history for a document.
     */
    @GetMapping("/{id}/versions")
    public Flux<KnowledgeHubDocumentVersion> getDocumentVersions(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        String teamId = jwt.getClaimAsString("team_id");
        return documentService.getDocumentVersions(id, teamId);
    }

    /**
     * Restore a specific version of a document.
     */
    @PostMapping("/{id}/versions/{version}/restore")
    public Mono<ResponseEntity<Map<String, String>>> restoreVersion(
            @PathVariable String id,
            @PathVariable Integer version,
            @AuthenticationPrincipal Jwt jwt) {
        String teamId = jwt.getClaimAsString("team_id");
        return documentService.restoreVersion(id, version, teamId)
                .map(doc -> ResponseEntity.ok(Map.of(
                        "message", "Restored version " + version + " successfully",
                        "newVersion", doc.getCurrentVersion().toString())));
    }
}
