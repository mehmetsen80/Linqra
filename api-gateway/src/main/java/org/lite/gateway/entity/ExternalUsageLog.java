package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "external_usage_log")
public class ExternalUsageLog {
    @Id
    private String id;
    private String serviceName;
    private String externalUserId;
    private String modelId;
    private Boolean isByok;
    private Tokens tokens;
    private Double theoreticalCostUsd;
    private Double billedCostUsd;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tokens {
        private Integer prompt;
        private Integer completion;
        private Integer total;
    }
}
