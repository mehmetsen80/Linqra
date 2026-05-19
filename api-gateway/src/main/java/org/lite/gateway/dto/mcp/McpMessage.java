package org.lite.gateway.dto.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpMessage {
    @Builder.Default
    private String jsonrpc = "2.0";
    private String method;
    private Object params;
    private Object result;
    private McpError error;
    private String id;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpError {
        private int code;
        private String message;
        private Object data;
    }
}
