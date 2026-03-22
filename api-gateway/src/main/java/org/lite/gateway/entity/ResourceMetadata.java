package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "resource_metadata")
@CompoundIndexes({
        @CompoundIndex(name = "resource_lookup_idx", def = "{'category': 1, 'resourceId': 1}", unique = true)
})
public class ResourceMetadata {
    @Id
    private String id;
    private String category;
    private String resourceId;
    private String displayName;
    private String description;
}
