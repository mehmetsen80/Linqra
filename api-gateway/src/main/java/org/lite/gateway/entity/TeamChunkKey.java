package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "team_chunk_keys")
@CompoundIndex(name = "team_version_idx", def = "{'teamId': 1, 'version': 1}", unique = true)
public class TeamChunkKey {
    @Id
    private String id;

    @Indexed
    private String teamId;

    private String version; // e.g., "v1", "v2"

    private String encryptedKey; // The team key encrypted with the Global Master Key

    private boolean isActive; // Only one active key per team usually

    private LocalDateTime createdAt;
}
