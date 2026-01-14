package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "system_change_logs")
public class SystemChangeLog {

    @Id
    private String id;

    private LocalDateTime timestamp;
    private String changeType; // e.g., "DEPLOYMENT", "CONFIG_CHANGE"
    private String actor; // e.g., "GitHub Actions", "admin@linqra.com"
    private String commit; // e.g., "a1b2c3d..."
    private String description;

    private Map<String, String> metadata; // e.g., commitHash, version, environment
}
