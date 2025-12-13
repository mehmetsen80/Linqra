package org.lite.gateway.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Model for collection export tasks queued in Redis
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class CollectionExportTask {
    
    @JsonProperty("jobId")
    private final String jobId;
    
    @JsonProperty("collectionIds")
    private final List<String> collectionIds;
    
    @JsonProperty("teamId")
    private final String teamId;
    
    @JsonProperty("exportedBy")
    private final String exportedBy;
    
    @JsonCreator
    public CollectionExportTask(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("collectionIds") List<String> collectionIds,
            @JsonProperty("teamId") String teamId,
            @JsonProperty("exportedBy") String exportedBy) {
        this.jobId = jobId;
        this.collectionIds = collectionIds;
        this.teamId = teamId;
        this.exportedBy = exportedBy;
    }
}

