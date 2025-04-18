package org.lite.gateway.service;

import org.lite.gateway.dto.InitialSetupRequest;
import org.lite.gateway.entity.User;
import reactor.core.publisher.Mono;

public interface SetupService {
    Mono<Boolean> isSystemInitialized();
    Mono<User> createInitialSuperAdmin(InitialSetupRequest request);
} 