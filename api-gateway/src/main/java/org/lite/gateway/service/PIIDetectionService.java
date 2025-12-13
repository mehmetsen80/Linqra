package org.lite.gateway.service;

import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for detecting and handling Personally Identifiable Information (PII)
 * in text content, with support for redaction.
 */
public interface PIIDetectionService {
    
    /**
     * Result of PII detection on a text
     */
    class PIIDetectionResult {
        private final boolean piiDetected;
        private final List<PIIMatch> matches;
        private final String redactedContent; // Only populated if redaction is enabled
        
        public PIIDetectionResult(boolean piiDetected, List<PIIMatch> matches, String redactedContent) {
            this.piiDetected = piiDetected;
            this.matches = matches != null ? matches : Collections.emptyList();
            this.redactedContent = redactedContent;
        }
        
        public boolean isPiiDetected() {
            return piiDetected;
        }
        
        public List<PIIMatch> getMatches() {
            return matches;
        }
        
        public String getRedactedContent() {
            return redactedContent;
        }
    }
    
    /**
     * Individual PII match found in text
     */
    class PIIMatch {
        private final String type;           // e.g., "SSN", "EMAIL", "PHONE", "CREDIT_CARD"
        private final int startIndex;        // Start position in original text
        private final int endIndex;          // End position in original text
        private final String matchedText;    // The matched PII text (masked for metadata, not actual value)
        private final int count;             // Count of occurrences of this type
        
        public PIIMatch(String type, int startIndex, int endIndex, String matchedText, int count) {
            this.type = type;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.matchedText = matchedText;
            this.count = count;
        }
        
        public String getType() {
            return type;
        }
        
        public int getStartIndex() {
            return startIndex;
        }
        
        public int getEndIndex() {
            return endIndex;
        }
        
        public String getMatchedText() {
            return matchedText;
        }
        
        public int getCount() {
            return count;
        }
    }
    
    /**
     * Detect PII in the given text content
     * 
     * @param text The text to scan for PII
     * @param redact Whether to return redacted content (actual PII values replaced with placeholders)
     * @return Mono containing the detection result
     */
    Mono<PIIDetectionResult> detectPII(String text, boolean redact);
    
    /**
     * Get summary metadata about detected PII (types and counts only, no actual values)
     * This is safe to include in audit logs
     * 
     * @param result The PII detection result
     * @return Map with metadata (types detected, counts per type, total count)
     */
    Map<String, Object> getPIISummaryMetadata(PIIDetectionResult result);
}

