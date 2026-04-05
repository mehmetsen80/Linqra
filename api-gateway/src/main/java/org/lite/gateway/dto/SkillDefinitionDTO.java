package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinitionDTO {

    private String toolId;
    private String executionUrl;
    private String executionMethod;
    private OpenAiSkill openai;
    private AnthropicSkill anthropic;
    private McpSkill mcp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAiSkill {
        @Builder.Default
        private String type = "function";
        private OpenAiFunction function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAiFunction {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnthropicSkill {
        private String name;
        private String description;
        private Map<String, Object> input_schema;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpSkill {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }
}
