package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiKeyPair {
    String key;
    String name;
}
