package org.lite.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Simple health check controller for ALB health checks.
 * This endpoint is used by the AWS ALB to verify the Gateway is running.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "api-gateway")));
    }
}
