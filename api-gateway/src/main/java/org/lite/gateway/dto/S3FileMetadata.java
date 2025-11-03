package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3FileMetadata {
    private String s3Key;
    private String contentType;
    private Long contentLength;
    private Instant lastModified;
    private String eTag;
    private Map<String, String> metadata;
}

