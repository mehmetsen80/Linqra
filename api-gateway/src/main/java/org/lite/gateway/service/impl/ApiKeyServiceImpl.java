package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiKey;
import org.lite.gateway.repository.ApiKeyRepository;
import org.lite.gateway.service.ApiKeyService;
import org.lite.gateway.service.CacheService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private static final String API_KEY_CACHE_PREFIX = "api_key:";
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);

    @Override
    public Mono<ApiKey> createApiKey(String teamId, String createdBy, Long expiresInDays) {
        return apiKeyRepository.findByTeamId(teamId)
                .filter(ApiKey::isEnabled)
                .hasElements()
                .flatMap(hasActiveKey -> {
                    if (hasActiveKey) {
                        return Mono.error(new IllegalStateException("Team already has an active API key"));
                    }

                    ApiKey apiKey = new ApiKey();
                    apiKey.setKey(generateApiKey());
                    apiKey.setName(generateApiKeyName());
                    apiKey.setTeamId(teamId);
                    apiKey.setCreatedBy(createdBy);
                    apiKey.setCreatedAt(Instant.now());

                    if (expiresInDays != null) {
                        apiKey.setExpiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS));
                    }

                    return apiKeyRepository.save(apiKey);
                });
    }

    private String generateApiKey() {
        return "lm_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateApiKeyName() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public Mono<ApiKey> validateApiKey(String apiKey) {
        String cacheKey = API_KEY_CACHE_PREFIX + apiKey;

        return cacheService.get(cacheKey)
                .flatMap(cachedValue -> {
                    try {
                        ApiKey cached = objectMapper.readValue(cachedValue, ApiKey.class);
                        if (isValid(cached)) {
                            return Mono.just(cached);
                        }
                        cacheService.delete(cacheKey).subscribe();
                    } catch (Exception e) {
                        log.error("Error deserializing API key", e);
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(apiKeyRepository.findByKey(apiKey)
                        .flatMap(key -> {
                            if (!key.isEnabled()) {
                                return Mono.error(
                                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key is disabled"));
                            }
                            if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
                                return Mono
                                        .error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key expired"));
                            }
                            return Mono.just(key);
                        })
                        .doOnNext(validKey -> {
                            try {
                                String value = objectMapper.writeValueAsString(validKey);
                                cacheService.set(cacheKey, value, CACHE_DURATION).subscribe();
                            } catch (Exception e) {
                                log.error("Error Caching API Key", e);
                            }
                        }));
    }

    private boolean isValid(ApiKey key) {
        return key.isEnabled() && (key.getExpiresAt() == null || key.getExpiresAt().isAfter(Instant.now()));
    }

    @Override
    public Mono<Void> revokeApiKey(String id) {
        return apiKeyRepository.findById(id)
                .flatMap(apiKey -> {
                    apiKey.setEnabled(false);
                    return apiKeyRepository.save(apiKey)
                            .then(cacheService.delete(API_KEY_CACHE_PREFIX + apiKey.getKey()).then());
                });
    }

    public void invalidateKeyCache(String apiKey) {
        cacheService.delete(API_KEY_CACHE_PREFIX + apiKey).subscribe();
    }

    @Override
    public Mono<ApiKey> getDefaultApiKeyForTeam(String teamId) {
        return apiKeyRepository.findByTeamId(teamId)
                .filter(key -> key.isEnabled()
                        && (key.getExpiresAt() == null || key.getExpiresAt().isAfter(Instant.now())))
                .next()
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No valid API key found for team")));
    }
}