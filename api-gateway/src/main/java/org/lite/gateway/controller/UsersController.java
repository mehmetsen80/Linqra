package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.User;
import org.lite.gateway.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UsersController {
    
    private final UserService userService;

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<User>> getUserById(@PathVariable String userId) {
        log.info("Fetching user by ID: {}", userId);
        return userService.findById(userId)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnSuccess(user -> log.info("User fetched successfully: {}", userId))
            .doOnError(error -> log.error("Error fetching user: {}", error.getMessage()));
    }

    @GetMapping("/by-username/{username}")
    public Mono<ResponseEntity<User>> getUserByUsername(@PathVariable String username) {
        return userService.findByUsername(username)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public Flux<User> searchUsers(@RequestParam String query) {
        return userService.searchUsers(query);
    }

    @PostMapping("/validate-password")
    public Mono<ResponseEntity<Map<String, Object>>> validatePassword(@RequestBody Map<String, String> request) {
        String password = request.get("password");
        return userService.validatePasswordStrength(password)
            .map(ResponseEntity::ok);
    }
} 