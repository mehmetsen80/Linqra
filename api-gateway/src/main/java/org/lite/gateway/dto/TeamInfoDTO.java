package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamInfoDTO {
    private String teamId;
    private String teamName;
    private String organizationId;
    private String organizationName;
} 