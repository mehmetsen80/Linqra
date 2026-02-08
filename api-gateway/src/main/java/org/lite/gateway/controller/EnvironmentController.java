package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/environment")
@RequiredArgsConstructor
public class EnvironmentController {

    private final Environment environment;

    @GetMapping("/profile")
    public Mono<EnvironmentInfo> getEnvironmentInfo() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? String.join(",", activeProfiles) : "default";
        return Mono.just(new EnvironmentInfo(profile));
    }

    public record EnvironmentInfo(String profile) {
    }
}
