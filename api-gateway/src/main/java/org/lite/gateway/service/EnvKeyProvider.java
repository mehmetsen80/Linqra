package org.lite.gateway.service;

import reactor.core.publisher.Mono;

//TODO: NOT USED RIGHT NOW, IF STILL NOT USED IN THE FUTURE DELETE THIS
public interface EnvKeyProvider {
    Mono<String> getApiKey(String target, String teamId);
}
