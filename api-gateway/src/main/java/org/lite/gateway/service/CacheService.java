package org.lite.gateway.service;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;

public interface CacheService {
    Mono<String> get(String key);

    Mono<Void> set(String key, String value, Duration duration);

    Mono<Boolean> setIfAbsent(String key, String value, Duration duration);

    Mono<Boolean> delete(String key);

    // Set Operations
    Mono<Long> addToSet(String key, String value);

    Mono<Long> removeFromSet(String key, String value);

    Mono<java.util.Set<String>> getSetMembers(String key);

    // Pub/Sub
    Mono<Long> publish(String topic, String message);

    Mono<Long> increment(String key);

    Mono<Boolean> expire(String key, Duration duration);

    // Hash Operations
    Mono<String> getHash(String key, String hashKey);

    Mono<Boolean> putHash(String key, String hashKey, String value);

    Mono<java.util.Map<String, String>> getHashEntries(String key);

    // List Operations (Queue)
    Mono<Long> rightPush(String key, String value);

    Mono<String> leftPop(String key);

    // Key Operations
    Flux<String> keys(String pattern);
}
