package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.CacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/metrics/maintenance")
@Slf4j
@RequiredArgsConstructor
public class MetricsMaintenanceController {

    private final CacheService cacheService;

    @DeleteMapping("/clear-all")
    public Mono<ResponseEntity<Map<String, Object>>> clearAllMetrics() {
        log.info("Manual metrics cleanup triggered");
        
        return cacheService.keys("metrics:*")
                .flatMap(key -> cacheService.delete(key))
                .collectList()
                .map(results -> {
                    long deletedCount = (long) results.size();
                    log.info("Manually deleted {} metrics keys", deletedCount);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "deletedCount", deletedCount,
                            "message", "All metrics keys cleared"
                    ));
                })
                .onErrorResume(e -> {
                    log.error("Error during manual metrics cleanup: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(Map.<String, Object>of(
                            "success", false,
                            "error", e.getMessage()
                    )));
                });
    }
}
