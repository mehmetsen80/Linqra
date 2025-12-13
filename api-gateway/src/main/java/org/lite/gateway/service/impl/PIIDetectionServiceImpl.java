package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.PIIDetectionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service implementation for detecting and handling PII in text content.
 * Implements pattern-based detection for common PII types.
 */
@Service
@Slf4j
public class PIIDetectionServiceImpl implements PIIDetectionService {
    
    // PII patterns - compiled once for performance
    private static final Map<String, Pattern> PII_PATTERNS = new HashMap<>();
    
    static {
        // SSN: XXX-XX-XXXX or XXXXXXXXX
        PII_PATTERNS.put("SSN", Pattern.compile("\\b\\d{3}-?\\d{2}-?\\d{4}\\b"));
        
        // Phone: (XXX) XXX-XXXX, XXX-XXX-XXXX, XXXXXXXXXX, +1 XXX XXX XXXX
        PII_PATTERNS.put("PHONE", Pattern.compile("\\b(\\+?1[-.]?)?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b"));
        
        // Email: standard email format
        PII_PATTERNS.put("EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"));
        
        // Credit Card: 16 digits, with optional spaces/dashes
        // Note: This is a basic pattern and may have false positives
        PII_PATTERNS.put("CREDIT_CARD", Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"));
        
        // Driver's License: varies by state, using common patterns
        // Format: XXXXXXXXX or XXX-XXX-XXX-XXX
        PII_PATTERNS.put("DRIVER_LICENSE", Pattern.compile("\\b[A-Z]?\\d{6,9}\\b"));
        
        // Passport: typically 9 digits or alphanumeric
        PII_PATTERNS.put("PASSPORT", Pattern.compile("\\b[A-Z]?\\d{8,9}\\b"));
        
        // Bank Account: 8-17 digits
        PII_PATTERNS.put("BANK_ACCOUNT", Pattern.compile("\\b\\d{8,17}\\b"));
        
        // IP Address: IPv4
        PII_PATTERNS.put("IP_ADDRESS", Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"));
    }
    
    @Override
    public Mono<PIIDetectionResult> detectPII(String text, boolean redact) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(new PIIDetectionResult(false, Collections.emptyList(), text));
        }
        
        return Mono.fromCallable(() -> {
            List<PIIMatch> allMatches = new ArrayList<>();
            Map<String, Integer> typeCounts = new HashMap<>();
            
            // Detect all PII types
            for (Map.Entry<String, Pattern> entry : PII_PATTERNS.entrySet()) {
                String type = entry.getKey();
                Pattern pattern = entry.getValue();
                Matcher matcher = pattern.matcher(text);
                
                int count = 0;
                while (matcher.find()) {
                    count++;
                    String originalMatchedText = matcher.group();
                    
                    // For metadata: use masked version (safe to log without exposing actual PII)
                    String maskedText = maskPIIForMetadata(type, originalMatchedText);
                    
                    allMatches.add(new PIIMatch(
                            type,
                            matcher.start(),
                            matcher.end(),
                            maskedText, // Use masked version for metadata logging
                            count
                    ));
                }
                
                if (count > 0) {
                    typeCounts.put(type, count);
                }
            }
            
            boolean piiDetected = !allMatches.isEmpty();
            String redactedContent = text;
            
            // Redact if requested - re-run patterns to get original text for replacement
            if (redact && piiDetected) {
                redactedContent = redactPII(text);
            }
            
            log.debug("PII detection completed: detected={}, types={}, totalMatches={}", 
                    piiDetected, typeCounts.keySet(), allMatches.size());
            
            return new PIIDetectionResult(piiDetected, allMatches, redactedContent);
        });
    }
    
    @Override
    public Map<String, Object> getPIISummaryMetadata(PIIDetectionResult result) {
        Map<String, Object> metadata = new HashMap<>();
        
        if (result == null || !result.isPiiDetected()) {
            metadata.put("piiDetected", false);
            metadata.put("piiTypes", Collections.emptyList());
            metadata.put("totalMatches", 0);
            return metadata;
        }
        
        metadata.put("piiDetected", true);
        
        // Group matches by type and count
        Map<String, Integer> typeCounts = result.getMatches().stream()
                .collect(Collectors.groupingBy(
                        PIIMatch::getType,
                        Collectors.collectingAndThen(
                                Collectors.counting(),
                                Long::intValue
                        )
                ));
        
        List<Map<String, Object>> piiTypes = typeCounts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> typeInfo = new HashMap<>();
                    typeInfo.put("type", entry.getKey());
                    typeInfo.put("count", entry.getValue());
                    return typeInfo;
                })
                .collect(Collectors.toList());
        
        metadata.put("piiTypes", piiTypes);
        metadata.put("totalMatches", result.getMatches().size());
        
        return metadata;
    }
    
    /**
     * Mask PII value for safe logging (returns placeholder, not actual value)
     */
    private String maskPIIForMetadata(String type, String value) {
        switch (type) {
            case "SSN":
                return "[SSN_REDACTED]";
            case "EMAIL":
                return "[EMAIL_REDACTED]";
            case "PHONE":
                return "[PHONE_REDACTED]";
            case "CREDIT_CARD":
                return "[CREDIT_CARD_REDACTED]";
            case "DRIVER_LICENSE":
                return "[DRIVER_LICENSE_REDACTED]";
            case "PASSPORT":
                return "[PASSPORT_REDACTED]";
            case "BANK_ACCOUNT":
                return "[BANK_ACCOUNT_REDACTED]";
            case "IP_ADDRESS":
                return "[IP_ADDRESS_REDACTED]";
            default:
                return "[PII_REDACTED]";
        }
    }
    
    /**
     * Redact PII values in text by replacing them with placeholders
     * Re-runs patterns to ensure we have correct original text for replacement
     */
    private String redactPII(String text) {
        StringBuilder redacted = new StringBuilder(text);
        List<Replacement> replacements = new ArrayList<>();
        
        // Find all PII matches with their original text
        for (Map.Entry<String, Pattern> entry : PII_PATTERNS.entrySet()) {
            String type = entry.getKey();
            Pattern pattern = entry.getValue();
            Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                String replacement = maskPIIForMetadata(type, matcher.group());
                replacements.add(new Replacement(
                        matcher.start(),
                        matcher.end(),
                        replacement
                ));
            }
        }
        
        // Sort by start index in reverse order to avoid index shifting issues
        replacements.sort((a, b) -> Integer.compare(b.startIndex, a.startIndex));
        
        // Apply replacements from end to start
        for (Replacement replacement : replacements) {
            redacted.replace(replacement.startIndex, replacement.endIndex, replacement.text);
        }
        
        return redacted.toString();
    }
    
    /**
     * Helper class for tracking text replacements
     */
    private static class Replacement {
        final int startIndex;
        final int endIndex;
        final String text;
        
        Replacement(int startIndex, int endIndex, String text) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.text = text;
        }
    }
}

