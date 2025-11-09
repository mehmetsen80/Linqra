package org.lite.gateway.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MilvusUpdateCollectionMetadataRequest {
    private String teamId;
    private Map<String, String> metadata;
}

