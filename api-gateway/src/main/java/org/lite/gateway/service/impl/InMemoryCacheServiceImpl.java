package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.CacheService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("remote-dev")
public class InMemoryCacheServiceImpl implements CacheService {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(String value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    @Override
    public Mono<String> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (entry.isExpired()) {
                cache.remove(key);
                return Mono.empty();
            }
            return Mono.just(entry.value());
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> set(String key, String value, Duration duration) {
        cache.put(key, new CacheEntry(value, Instant.now().plus(duration)));
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration duration) {
        return Mono.fromSupplier(() -> {
            AtomicBoolean set = new AtomicBoolean(false);
            cache.compute(key, (k, v) -> {
                if (v == null || v.isExpired()) {
                    set.set(true);
                    return new CacheEntry(value, Instant.now().plus(duration));
                }
                return v;
            });
            return set.get();
        });
    }

    private final Map<String, Set<String>> setCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> hashCache = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<String>> queueCache = new ConcurrentHashMap<>();

    @Override
    public Mono<Boolean> delete(String key) {
        setCache.remove(key); // Also remove from Set cache if key matches
        hashCache.remove(key); // Also remove from Hash cache
        return Mono.just(cache.remove(key) != null);
    }

    @Override
    public Mono<String> getHash(String key, String hashKey) {
        return Mono.fromSupplier(() -> {
            java.util.Map<String, String> map = hashCache.get(key);
            if (map != null) {
                return map.get(hashKey);
            }
            return null;
        });
    }

    @Override
    public Mono<Boolean> putHash(String key, String hashKey, String value) {
        return Mono.fromSupplier(() -> {
            hashCache.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(hashKey, value);
            return true;
        });
    }

    @Override
    public Mono<Map<String, String>> getHashEntries(String key) {
        return Mono.fromSupplier(() -> {
            Map<String, String> map = hashCache.get(key);
            if (map != null) {
                return new HashMap<>(map);
            }
            return Collections.emptyMap();
        });
    }

    @Override
    public Mono<Long> addToSet(String key, String value) {
        return Mono.fromSupplier(() -> {
            setCache.computeIfAbsent(key, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(value);
            return 1L;
        });
    }

    @Override
    public Mono<Long> removeFromSet(String key, String value) {
        return Mono.fromSupplier(() -> {
            Set<String> set = setCache.get(key);
            if (set != null) {
                return set.remove(value) ? 1L : 0L;
            }
            return 0L;
        });
    }

    @Override
    public Mono<Set<String>> getSetMembers(String key) {
        return Mono.fromSupplier(() -> {
            Set<String> set = setCache.get(key);
            if (set != null) {
                return new HashSet<>(set);
            }
            return Collections.emptySet();
        });
    }

    @Override
    public Mono<Long> publish(String topic, String message) {
        log.info("Mock Pub/Sub (InMemory): Topic={}, Message={}", topic, message);
        return Mono.just(1L);
    }

    @Override
    public Mono<Long> increment(String key) {
        return Mono.fromSupplier(() -> {
            AtomicLong result = new AtomicLong(0);
            cache.compute(key, (k, v) -> {
                if (v == null || v.isExpired()) {
                    result.set(1);
                    return new CacheEntry("1", Instant.MAX);
                }
                try {
                    long val = Long.parseLong(v.value());
                    result.set(val + 1);
                    return new CacheEntry(String.valueOf(val + 1), v.expiresAt());
                } catch (NumberFormatException e) {
                    result.set(1);
                    return new CacheEntry("1", v.expiresAt());
                }
            });
            return result.get();
        });
    }

    @Override
    public Mono<Boolean> expire(String key, Duration duration) {
        return Mono.fromSupplier(() -> {
            AtomicBoolean updated = new AtomicBoolean(false);
            cache.computeIfPresent(key, (k, v) -> {
                if (v.isExpired())
                    return null;
                updated.set(true);
                return new CacheEntry(v.value(), Instant.now().plus(duration));
            });
            return updated.get();
        });
    }

    @Override
    public Mono<Long> rightPush(String key, String value) {
        return Mono.fromSupplier(() -> {
            ConcurrentLinkedDeque<String> queue = queueCache.computeIfAbsent(key,
                    k -> new ConcurrentLinkedDeque<>());
            queue.addLast(value);
            return (long) queue.size();
        });
    }

    @Override
    public Mono<String> leftPop(String key) {
        return Mono.fromSupplier(() -> {
            java.util.concurrent.ConcurrentLinkedDeque<String> queue = queueCache.get(key);
            if (queue != null) {
                return queue.pollFirst();
            }
            return null;
        });
    }

    @Override
    public Flux<String> keys(String pattern) {
        return Flux.fromIterable(() -> {
            // Convert glob pattern to regex
            String regex = pattern.replace("*", ".*").replace("?", ".");
            return cache.keySet().stream()
                    .filter(key -> key.matches(regex))
                    .collect(Collectors.toList())
                    .iterator();
        });
    }
}
