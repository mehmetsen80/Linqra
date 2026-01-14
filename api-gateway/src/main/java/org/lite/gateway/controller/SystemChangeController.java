package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.SystemChangeLog;
import org.lite.gateway.repository.SystemChangeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/internal/system-change")
@RequiredArgsConstructor
public class SystemChangeController {

    private final SystemChangeRepository systemChangeRepository;

    @Value("${CHANGE_LOG_TOKEN:}")
    private String changeLogToken;

    @PostMapping
    public Mono<ResponseEntity<Void>> logSystemChange(
            @RequestHeader(value = "X-Change-Log-Token", required = false) String token,
            @RequestBody SystemChangeLog changeLog) {

        // Validate Token
        if (changeLogToken == null || changeLogToken.isEmpty()) {
            log.error("❌ CHANGE_LOG_TOKEN not configured in backend. Rejecting webhook.");
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
        }

        if (!changeLogToken.equals(token)) {
            log.warn("Example: Unauthorized attempt to log system change. Token mismatch.");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        // Set Timestamp if missing
        if (changeLog.getTimestamp() == null) {
            changeLog.setTimestamp(LocalDateTime.now());
        }

        log.info("📝 Logging System Change: [{}] {} by {}",
                changeLog.getChangeType(), changeLog.getDescription(), changeLog.getActor());

        return systemChangeRepository.save(changeLog)
                .map(saved -> ResponseEntity.ok().<Void>build())
                .onErrorResume(e -> {
                    log.error("Error saving system change log", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}
