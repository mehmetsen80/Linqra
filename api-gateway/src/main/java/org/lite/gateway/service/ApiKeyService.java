package org.lite.gateway.service;

import org.lite.gateway.entity.ApiKey;
import reactor.core.publisher.Mono;

public interface ApiKeyService {
    Mono<ApiKey> validateApiKey(String apiKey);

    Mono<ApiKey> createApiKey(String teamId, String createdBy, Long expiresInDays);

    Mono<Void> revokeApiKey(String apiKeyId);

    Mono<ApiKey> getDefaultApiKeyForTeam(String teamId);
}