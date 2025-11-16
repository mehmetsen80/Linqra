package org.lite.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "assistant_created_idx", def = "{'assistantId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "team_created_idx", def = "{'teamId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "user_created_idx", def = "{'username': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "assistant_status_idx", def = "{'assistantId': 1, 'status': 1}"),
    @CompoundIndex(name = "public_created_idx", def = "{'isPublic': 1, 'createdAt': -1}")
})
public class Conversation {
    @Id
    private String id;
    
    // Relationships
    @Indexed
    private String assistantId;            // AI Assistant ID
    @Indexed
    private String teamId;                  // Team ID
    private String username;                // Username (null for public/anonymous conversations)
    private String guestId;                 // For public conversations: browser fingerprint or session ID
    @Indexed
    private Boolean isPublic;                // true if from public assistant/widget
    private String source;                  // web_app, widget, api
    
    // Conversation Metadata
    private String title;                   // Auto-generated from first message
    @Indexed
    private String status;                  // ACTIVE, ARCHIVED, DELETED
    
    // Widget Metadata (for public conversations)
    private WidgetMetadata widgetMetadata;
    
    // Timestamps
    @Indexed
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    private Integer messageCount;
    
    // Statistics
    private ConversationMetadata metadata;
    
    // Audit Fields
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WidgetMetadata {
        private String domain;              // Source domain if from widget
        private String userAgent;
        private String ipAddress;           // Hashed IP for privacy
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConversationMetadata {
        private Long totalTokens;
        private Double totalCost;
        private Integer taskExecutions;
        private Integer successfulTasks;
        private Integer failedTasks;
        private Map<String, Object> additionalData;
    }
}

