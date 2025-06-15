package org.lite.gateway.dto;

import lombok.Data;
import java.util.Map;

@Data
public class MilvusStoreRecordRequest {
    private Map<String, Object> record;
    private String targetTool;
    private String modelType;
    private String textField;
    private String teamId;
} 