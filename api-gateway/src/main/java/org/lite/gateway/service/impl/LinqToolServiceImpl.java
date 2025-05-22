package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.LinqTool;
import org.lite.gateway.repository.LinqToolRepository;
import org.lite.gateway.service.LinqToolService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinqToolServiceImpl implements LinqToolService {

    @NonNull
    private final LinqToolRepository linqToolRepository;

    @Override
    public Mono<LinqTool> saveLinqTool(LinqTool linqTool) {
        // Validate required fields
        if (linqTool.getTarget() == null || linqTool.getTarget().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool target is required"));
        }
        if (linqTool.getTeam() == null || linqTool.getTeam().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool team ID is required"));
        }
        if (linqTool.getEndpoint() == null || linqTool.getEndpoint().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool endpoint is required"));
        }
        if (linqTool.getMethod() == null || linqTool.getMethod().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool method is required"));
        }

        log.info("Saving LinqTool with target: {} for team: {}", linqTool.getTarget(), linqTool.getTeam());
        return linqToolRepository.save(linqTool)
                .doOnSuccess(saved -> log.info("Saved LinqTool with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Failed to save LinqTool: {}", error.getMessage()));
    }
}
