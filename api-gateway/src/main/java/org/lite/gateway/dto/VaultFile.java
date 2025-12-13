package org.lite.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO representing the encrypted vault file structure
 * Contains secrets organized by environment (dev/staging/prod)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VaultFile {
    
    private String version;
    private Map<String, EnvironmentSecrets> environments;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EnvironmentSecrets {
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime updatedAt;
        
        private String updatedBy;
        
        private Map<String, String> secrets;
    }
    
    /**
     * Create empty vault structure
     */
    public static VaultFile createEmpty() {
        Map<String, EnvironmentSecrets> environments = new HashMap<>();
        environments.put("dev", EnvironmentSecrets.builder()
            .updatedAt(LocalDateTime.now())
            .updatedBy("system")
            .secrets(new HashMap<>())
            .build());
        
        return VaultFile.builder()
            .version("1.0")
            .environments(environments)
            .build();
    }
}

