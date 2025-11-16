package org.lite.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.annotation.CreatedDate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "conversation_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "conversation_sequence_idx", def = "{'conversationId': 1, 'sequenceNumber': 1}"),
    @CompoundIndex(name = "conversation_created_idx", def = "{'conversationId': 1, 'createdAt': 1}"),
    @CompoundIndex(name = "conversation_role_idx", def = "{'conversationId': 1, 'role': 1}")
})
public class ConversationMessage {
    @Id
    private String id;
    
    // Relationships
    @Indexed
    private String conversationId;         // Conversation ID
    @Indexed
    private Integer sequenceNumber;         // Message sequence in conversation
    
    // Message Content
    private String role;                    // USER, ASSISTANT, SYSTEM
    private String content;                 // Message text content
    
    // Timestamps
    @Indexed
    private LocalDateTime timestamp;
    @CreatedDate
    private LocalDateTime createdAt;
    
    // Metadata
    private MessageMetadata metadata;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MessageMetadata {
        private Integer tokens;             // Token count for this message
        private List<String> executedTasks; // List of Agent Task IDs executed for this message
        private Map<String, Object> taskResults; // Results from executed tasks (taskId -> result)
        private String intent;              // Detected intent from user query (for USER messages)
        private String modelCategory;       // Model category used (e.g., "openai-chat") - for ASSISTANT messages
        private String modelName;           // Model name used (e.g., "gpt-4o") - for ASSISTANT messages
        private Boolean piiDetected;        // Whether PII was detected in this message
        private List<String> moderationFlags; // Moderation flags if any
        private TokenUsage tokenUsage;     // Token usage (for ASSISTANT messages)
        private Map<String, Object> additionalData; // Additional metadata
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class TokenUsage {
            private Long promptTokens;
            private Long completionTokens;
            private Long totalTokens;
            private Double costUsd;
        }
    }
}

