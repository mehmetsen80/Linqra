package org.lite.gateway.dto;

import lombok.Data;
import java.util.List;

@Data
public class MilvusQueryRequest {
    private List<Float> embedding;
    private int nResults = 10;
    private String[] outputFields = new String[]{"id", "text"};
    private String teamId;
} 