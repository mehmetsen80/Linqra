package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "doc_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "team_user_idx", def = "{'teamId': 1, 'userId': 1}"),
        @CompoundIndex(name = "team_status_idx", def = "{'teamId': 1, 'status': 1}")
})
public class DocReviewAssistant {
    @Id
    private String id;

    @Indexed
    private String teamId;
    @Indexed
    private String userId; // Created by username

    @Indexed
    private String assistantId;

    @Indexed
    private String conversationId; // Link to the chat conversation

    @Indexed
    private String documentId; // Link to Knowledge Hub Document
    private String documentName;

    private ReviewStatus status;

    private List<ReviewPoint> reviewPoints;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum ReviewStatus {
        IN_PROGRESS,
        COMPLETED,
        ARCHIVED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewPoint {
        private String id; // Unique ID for the point
        private String originalText; // The clause/sentence text
        private String verdict; // ACCEPT, REJECT, WARNING
        private String reasoning;
        private String suggestion;
        private Boolean userAccepted; // Did user accept AI suggestion?
    }
}
