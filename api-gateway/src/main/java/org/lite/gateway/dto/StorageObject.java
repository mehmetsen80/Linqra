package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageObject {
    private String key;
    private Long size;
    private Instant lastModified;
    private String eTag;
    private String storageClass;
    private String owner;
}
