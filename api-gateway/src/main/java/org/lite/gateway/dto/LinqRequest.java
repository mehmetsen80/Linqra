package org.lite.gateway.dto;

import lombok.Data;

import java.util.Map;

@Data
public class LinqRequest {
    private Link link;
    private Query query;

    @Data
    public static class Link {
        private String target; // e.g., "inventory-service"
        private String action; // e.g., "fetch"
    }

    @Data
    public static class Query {
        private String intent; // e.g., "greet"
        private Map<String, Object> params; // e.g., {}
        private Object payload;  // Add this field for POST/PUT/PATCH request bodies
    }
}
