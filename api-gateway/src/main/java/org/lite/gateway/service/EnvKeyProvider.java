package org.lite.gateway.service;

import reactor.core.publisher.Mono;

public interface EnvKeyProvider {
    Mono<String> getApiKey(String target, String teamId);
}
