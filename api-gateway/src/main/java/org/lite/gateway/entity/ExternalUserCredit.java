package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "external_user_credits")
@CompoundIndex(name = "external_user_service_idx", def = "{'externalUserId': 1, 'serviceName': 1}", unique = true)
public class ExternalUserCredit {
    @Id
    private String id;

    private String externalUserId;

    private String serviceName;

    private String machineId; // For soft tracking

    @Builder.Default
    private Integer aiCredits = 100;

    @Builder.Default
    private Integer maxAiCredits = 100;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
