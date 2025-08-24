package org.lite.gateway.service;

import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.lite.gateway.exception.InvalidAuthenticationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

@Service
public class TeamContextService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public Mono<String> getTeamFromContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof Jwt jwt) {
                        // First try to get teamId from the claim
                        String teamId = jwt.getClaimAsString("teamId");
                        if (teamId != null) {
                            return Mono.just(teamId);
                        }
                        
                        // If teamId is not found, try to get it from the teams array
                        try {
                            String tokenValue = jwt.getTokenValue();
                            String[] chunks = tokenValue.split("\\.");
                            String payload = new String(Base64.getDecoder().decode(chunks[1]));
                            JsonNode jsonNode = objectMapper.readTree(payload);
                            
                            if (jsonNode.has("teams")) {
                                JsonNode teamsNode = jsonNode.get("teams");
                                
                                // Handle case where teams is an array
                                if (teamsNode.isArray() && teamsNode.size() > 0) {
                                    // Return the first team from the array, removing 'lm_' prefix if it exists
                                    String firstTeam = teamsNode.get(0).asText();
                                    String normalizedTeam = firstTeam.startsWith("lm_") ? 
                                        firstTeam.substring(3) : firstTeam;
                                    return Mono.just(normalizedTeam);
                                } else if (teamsNode.isTextual()) {
                                    // Handle case where teams is a single string, removing 'lm_' prefix if it exists
                                    String teamValue = teamsNode.asText();
                                    String normalizedTeam = teamValue.startsWith("lm_") ? 
                                        teamValue.substring(3) : teamValue;
                                    return Mono.just(normalizedTeam);
                                }
                            }
                        } catch (Exception e) {
                            // Log the error but continue to return the original error
                        }
                        
                        return Mono.error(new InvalidAuthenticationException("No team ID found in token (neither teamId claim nor teams array)"));
                    } else if (principal instanceof String teamId) {
                        if (teamId.trim().isEmpty()) {
                            return Mono.error(new InvalidAuthenticationException("No team ID found in principal"));
                        }
                        return Mono.just(teamId);
                    }
                    return Mono.error(new InvalidAuthenticationException("Invalid authentication principal"));
                });
    }
}
