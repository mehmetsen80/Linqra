package org.lite.gateway.service;

import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.lite.gateway.exception.InvalidAuthenticationException;

@Service
public class TeamContextService {
    
    public Mono<String> getTeamFromContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof Jwt) {
                        Jwt jwt = (Jwt) principal;
                        String teamId = jwt.getClaimAsString("teamId");
                        if (teamId == null) {
                            return Mono.error(new InvalidAuthenticationException("No team ID found in token"));
                        }
                        return Mono.just(teamId);
                    }
                    return Mono.error(new InvalidAuthenticationException("Invalid authentication principal"));
                });
    }
}
