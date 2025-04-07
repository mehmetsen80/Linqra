package org.lite.gateway.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinqProtocolExample {
    private String summary;
    private LinqRequest request;
    private LinqResponse response;
} 