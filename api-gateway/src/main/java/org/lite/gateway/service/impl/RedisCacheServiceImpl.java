package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.CacheService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
@Profile("!remote-dev")
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements CacheService {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<String> get(String key) {
        log.debug("Redis Get: {}", key);
        return redisTemplate.opsForValue().get(key)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis Get Failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> set(String key, String value, Duration duration) {
        log.debug("Redis Set: {}", key);
        return redisTemplate.opsForValue().set(key, value, duration)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis Set Failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration duration) {
        log.debug("Redis SetIfAbsent: {}", key);
        return redisTemplate.opsForValue().setIfAbsent(key, value, duration)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis SetIfAbsent Failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Boolean> delete(String key) {
        log.debug("Redis Delete: {}", key);
        return redisTemplate.opsForValue().delete(key)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis Delete Failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Long> addToSet(String key, String value) {
        log.debug("Redis AddToSet: {} -> {}", key, value);
        return redisTemplate.opsForSet().add(key, value)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis AddToSet Failed: {}", e.getMessage());
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<Long> removeFromSet(String key, String value) {
        log.debug("Redis RemoveFromSet: {} -> {}", key, value);
        return redisTemplate.opsForSet().remove(key, value)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis RemoveFromSet Failed: {}", e.getMessage());
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<java.util.Set<String>> getSetMembers(String key) {
        log.debug("Redis GetSetMembers: {}", key);
        return redisTemplate.opsForSet().members(key)
                .collect(java.util.stream.Collectors.toSet())
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis GetSetMembers Failed: {}", e.getMessage());
                    return Mono.just(java.util.Collections.emptySet());
                });
    }

    @Override
    public Mono<Long> publish(String topic, String message) {
        log.debug("Redis Publish: {} -> {}", topic, message);
        return redisTemplate.convertAndSend(topic, message)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis Publish Failed: {}", e.getMessage());
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<Long> increment(String key) {
        log.debug("Redis Increment: {}", key);
        return redisTemplate.opsForValue().increment(key)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis Increment Failed: {}", e.getMessage());
                    return Mono.just(1L);
                });
    }

    @Override
    public Mono<Boolean> expire(String key, Duration duration) {
        log.debug("Redis Expire: {} -> {}", key, duration);
        return redisTemplate.expire(key, duration)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis Expire Failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<String> getHash(String key, String hashKey) {
        log.debug("Redis GetHash: {} -> {}", key, hashKey);
        return redisTemplate.opsForHash().get(key, hashKey)
                .map(obj -> (String) obj)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis GetHash Failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> putHash(String key, String hashKey, String value) {
        log.debug("Redis PutHash: {} -> {} = {}", key, hashKey, value);
        return redisTemplate.opsForHash().put(key, hashKey, value)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis PutHash Failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<java.util.Map<String, String>> getHashEntries(String key) {
        log.debug("Redis GetHashEntries: {}", key);
        return redisTemplate.opsForHash().entries(key)
                .collect(java.util.stream.Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> (String) e.getValue()))
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis GetHashEntries Failed: {}", e.getMessage());
                    return Mono.just(java.util.Collections.emptyMap());
                });
    }

    @Override
    public Mono<Long> rightPush(String key, String value) {
        log.debug("Redis RightPush: {} -> {}", key, value);
        return redisTemplate.opsForList().rightPush(key, value)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis RightPush Failed: {}", e.getMessage());
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<String> leftPop(String key) {
        log.debug("Redis LeftPop: {}", key);
        return redisTemplate.opsForList().leftPop(key)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis LeftPop Failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public reactor.core.publisher.Flux<String> keys(String pattern) {
        log.debug("Redis Keys: {}", pattern);
        return redisTemplate.keys(pattern)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(e -> {
                    log.error("Redis Keys Failed: {}", e.getMessage());
                    return reactor.core.publisher.Flux.empty();
                });
    }
}
