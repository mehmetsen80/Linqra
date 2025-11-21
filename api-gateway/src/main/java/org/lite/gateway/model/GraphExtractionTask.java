package org.lite.gateway.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Model for graph extraction tasks queued in Redis
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class GraphExtractionTask {
    
    @JsonProperty("jobId")
    private final String jobId;
    
    @JsonProperty("documentId")
    private final String documentId;
    
    @JsonProperty("teamId")
    private final String teamId;
    
    @JsonProperty("extractionType")
    private final String extractionType;
    
    @JsonProperty("force")
    private final boolean force;
    
    @JsonCreator
    public GraphExtractionTask(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("teamId") String teamId,
            @JsonProperty("extractionType") String extractionType,
            @JsonProperty("force") boolean force) {
        this.jobId = jobId;
        this.documentId = documentId;
        this.teamId = teamId;
        this.extractionType = extractionType;
        this.force = force;
    }
}

