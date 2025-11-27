package org.lite.vault.reader.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing the encrypted vault file structure
 * Matches the VaultFile in api-gateway module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultFile {
    
    private String version;
    private Map<String, EnvironmentSecrets> environments;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentSecrets {
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime updatedAt;
        
        private String updatedBy;
        
        private Map<String, String> secrets;
    }
}
