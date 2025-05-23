package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.EnvKeyProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

//TODO: NOT USED RIGHT NOW, IF STILL NOT USED IN THE FUTURE DELETE THIS
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvKeyProviderImpl implements EnvKeyProvider {

    private final Map<String, String> keyMappings = Map.of(
            "openai", "OPENAI_API_KEY",
            "huggingface", "HF_API_KEY",
            "gemini", "GEMINI_API_KEY"
    );

    @Override
    public Mono<String> getApiKey(String target, String teamId) {
        String envVar = keyMappings.get(target);
        if (envVar == null) {
            return Mono.error(new IllegalArgumentException("No API key configured for target: " + target));
        }
        String key = System.getenv(envVar);
        if (key == null || key.isEmpty()) {
            return Mono.error(new IllegalStateException("API key not found for target: " + target));
        }
        return Mono.just(key);
    }
}
