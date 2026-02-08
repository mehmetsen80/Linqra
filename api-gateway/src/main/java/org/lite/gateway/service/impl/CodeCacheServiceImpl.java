package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.CodeCacheService;
import org.lite.gateway.service.CacheService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class CodeCacheServiceImpl implements CodeCacheService {
    private static final String CODE_PREFIX = "oauth:code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final CacheService cacheService;

    public CodeCacheServiceImpl(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public Mono<Boolean> isCodeUsed(String code) {
        return cacheService.get(CODE_PREFIX + code).hasElement();
    }

    @Override
    public Mono<Boolean> markCodeAsUsed(String code) {
        return cacheService.setIfAbsent(CODE_PREFIX + code, "used", CODE_TTL);
    }
}