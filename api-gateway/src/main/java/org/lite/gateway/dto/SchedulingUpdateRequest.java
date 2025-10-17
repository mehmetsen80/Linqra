package org.lite.gateway.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingUpdateRequest {
    private String cronExpression;
    private String cronDescription;
    private Boolean scheduleOnStartup;
    private String executionTrigger;
}
