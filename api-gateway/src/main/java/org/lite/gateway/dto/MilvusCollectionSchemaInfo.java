package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

@Value
@Builder
public class MilvusCollectionSchemaInfo {
    String collectionName;
    String collectionType;
    Set<String> fieldNames;
    String vectorFieldName;
    String textFieldName;
    Integer textFieldMaxLength;

    public Set<String> getFieldNames() {
        return fieldNames == null ? Collections.emptySet() : fieldNames;
    }
}

