package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiKey;
import org.lite.gateway.repository.ApiKeyRepository;
import org.lite.gateway.service.ApiKeyService;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String API_KEY_CACHE_PREFIX = "api_key:";
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);

    @Override
    public Mono<ApiKey> createApiKey(String name, String teamId, String createdBy, Long expiresInDays) {
        return apiKeyRepository.findByTeamId(teamId)
            .flatMap(existingKey -> Mono.<ApiKey>error(new IllegalStateException("Team already has an active API key")))
            .switchIfEmpty(Mono.<ApiKey>defer(() -> {
                ApiKey apiKey = new ApiKey();
                apiKey.setKey(generateApiKey());
                apiKey.setName(name);
                apiKey.setTeamId(teamId);
                apiKey.setCreatedBy(createdBy);
                apiKey.setCreatedAt(Instant.now());
                
                if (expiresInDays != null) {
                    apiKey.setExpiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS));
                }

                return apiKeyRepository.save(apiKey);
            }));
    }

    private String generateApiKey() {
        return "lm_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public Mono<ApiKey> validateApiKey(String apiKey) {
        String cacheKey = API_KEY_CACHE_PREFIX + apiKey;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedValue != null) {
            try {
                ApiKey cached = objectMapper.readValue(cachedValue, ApiKey.class);
                if (cached.isEnabled() && (cached.getExpiresAt() == null || 
                    cached.getExpiresAt().isAfter(Instant.now()))) {
                    return Mono.just(cached);
                }
                // If key is expired or disabled, remove from cache
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.error("Error deserializing cached API key", e);
            }
        }
        
        return apiKeyRepository.findByKey(apiKey)
            .flatMap(key -> {
                if (!key.isEnabled()) {
                    log.warn("API key is disabled: {}", apiKey);
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "API key is disabled"));
                }
                if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
                    String errorMessage = String.format("API key expired at %s", key.getExpiresAt());
                    log.warn("{}: {}", errorMessage, apiKey);
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, errorMessage));
                }
                return Mono.just(key);
            })
            .doOnNext(validKey -> {
                try {
                    String value = objectMapper.writeValueAsString(validKey);
                    redisTemplate.opsForValue().set(cacheKey, value, CACHE_DURATION);
                } catch (Exception e) {
                    log.error("Error caching API key", e);
                }
            });
    }

    @Override
    public Mono<Void> revokeApiKey(String id) {
        return apiKeyRepository.findById(id)
            .flatMap(apiKey -> {
                apiKey.setEnabled(false);
                invalidateKeyCache(apiKey.getKey());
                return apiKeyRepository.save(apiKey);
            })
            .then();
    }

    // Method to invalidate cache when key is revoked/deleted
    public void invalidateKeyCache(String apiKey) {
        redisTemplate.delete(API_KEY_CACHE_PREFIX + apiKey);
    }
} 