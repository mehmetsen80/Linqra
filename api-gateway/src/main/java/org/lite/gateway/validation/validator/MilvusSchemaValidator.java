package org.lite.gateway.validation.validator;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class MilvusSchemaValidator {

    public static final Set<String> REQUIRED_VECTOR_FIELDS = Set.of("embedding");

    public static final Map<String, String> REQUIRED_FIELDS = Map.ofEntries(
            Map.entry("id", "INT64"),
            Map.entry("embedding", "FLOAT_VECTOR"),
            Map.entry("text", "VARCHAR"),
            Map.entry("chunkId", "VARCHAR"),
            Map.entry("chunkIndex", "INT32"),
            Map.entry("documentId", "VARCHAR"),
            Map.entry("collectionId", "VARCHAR"),
            Map.entry("fileName", "VARCHAR"),
            Map.entry("pageNumbers", "VARCHAR"),
            Map.entry("tokenCount", "INT32"),
            Map.entry("language", "VARCHAR"),
            Map.entry("createdAt", "INT64"),
            Map.entry("teamId", "VARCHAR"),
            Map.entry("title", "VARCHAR"),
            Map.entry("author", "VARCHAR"),
            Map.entry("subject", "VARCHAR"),
            Map.entry("qualityScore", "DOUBLE"),
            Map.entry("startPosition", "INT32"),
            Map.entry("endPosition", "INT32"),
            Map.entry("category", "VARCHAR"),
            Map.entry("metadataOnly", "BOOL"),
            Map.entry("documentType", "VARCHAR"),
            Map.entry("mimeType", "VARCHAR"),
            Map.entry("collectionType", "VARCHAR"),
            Map.entry("encryptionKeyVersion", "VARCHAR"));

    public ValidationResult validate(List<Map<String, Object>> schemaFields, Integer expectedDimension) {
        if (schemaFields == null) {
            return ValidationResult.invalid(List.of("Schema definition is missing"));
        }

        Map<String, Map<String, Object>> fieldsByName = schemaFields.stream()
                .filter(Map.class::isInstance)
                .map(field -> (Map<String, Object>) field)
                .collect(Collectors.toMap(
                        field -> String.valueOf(field.get("name")),
                        field -> field,
                        (existing, replacement) -> replacement));

        List<String> issues = new ArrayList<>();
        Integer detectedVectorDimension = null;
        String vectorFieldName = null;

        for (Map.Entry<String, String> requiredField : REQUIRED_FIELDS.entrySet()) {
            String fieldName = requiredField.getKey();
            Map<String, Object> fieldInfo = fieldsByName.get(fieldName);
            if (fieldInfo == null) {
                issues.add("Missing required field '" + fieldName + "'");
                continue;
            }

            String actualTypeRaw = String.valueOf(fieldInfo.get("dataType"));
            String normalizedType = normalizeType(actualTypeRaw);
            if (!Objects.equals(normalizedType, requiredField.getValue())) {
                issues.add("Field '" + fieldName + "' must be of type " + requiredField.getValue() + " but was "
                        + actualTypeRaw);
            }

            if (REQUIRED_VECTOR_FIELDS.contains(fieldName)) {
                vectorFieldName = fieldName;
                Map<String, Object> typeParams = null;
                Object paramsObj = fieldInfo.get("typeParams");
                if (paramsObj instanceof Map<?, ?> map) {
                    typeParams = map.entrySet().stream()
                            .collect(Collectors.toMap(
                                    entry -> String.valueOf(entry.getKey()),
                                    entry -> String.valueOf(entry.getValue())));
                }

                if (typeParams == null || !typeParams.containsKey("dim")) {
                    issues.add("Vector field '" + fieldName + "' is missing dimension configuration");
                } else {
                    try {
                        detectedVectorDimension = Integer.parseInt(String.valueOf(typeParams.get("dim")));
                        if (expectedDimension != null && !Objects.equals(detectedVectorDimension, expectedDimension)) {
                            issues.add("Vector field '" + fieldName + "' has dimension " + detectedVectorDimension +
                                    " but expected " + expectedDimension);
                        }
                    } catch (NumberFormatException ex) {
                        issues.add("Invalid dimension value for field '" + fieldName + "'");
                    }
                }
            }
        }

        return ValidationResult.builder()
                .valid(issues.isEmpty())
                .issues(issues)
                .vectorDimension(detectedVectorDimension)
                .vectorFieldName(vectorFieldName)
                .build();
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        String trimmed = type.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String alphanumeric = trimmed.replaceAll("[^A-Za-z0-9]", "");
        String upper = alphanumeric.toUpperCase(Locale.ROOT);

        return switch (upper) {
            case "FLOATVECTOR" -> "FLOAT_VECTOR";
            case "FLOAT16VECTOR" -> "FLOAT16_VECTOR";
            case "BFLOAT16VECTOR" -> "BFLOAT16_VECTOR";
            case "SPARSEFLOATVECTOR" -> "SPARSE_FLOAT_VECTOR";
            case "BINARYVECTOR" -> "BINARY_VECTOR";
            default -> upper;
        };
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ValidationResult {
        private boolean valid;
        @Builder.Default
        private List<String> issues = new ArrayList<>();
        private Integer vectorDimension;
        private String vectorFieldName;

        public static ValidationResult invalid(List<String> issues) {
            return ValidationResult.builder()
                    .valid(false)
                    .issues(new ArrayList<>(issues))
                    .build();
        }
    }
}
