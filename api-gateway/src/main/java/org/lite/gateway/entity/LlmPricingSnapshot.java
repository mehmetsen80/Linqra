package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Stores historical pricing snapshots for LLM models
 * This allows accurate cost calculation even when prices change over time
 */
@Document(collection = "llm_pricing_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "month_model_idx", def = "{'yearMonth': 1, 'model': 1}", unique = true)
})
public class LlmPricingSnapshot {
    @Id
    private String id;
    
    private String yearMonth;                 // e.g., "2025-10" (stored as string in MongoDB)
    private String model;                      // e.g., "gpt-4o", "gemini-2.0-flash"
    private String provider;                   // e.g., "openai", "gemini"
    private double inputPricePer1M;           // Input/prompt price per 1M tokens in USD
    private double outputPricePer1M;          // Output/completion price per 1M tokens in USD
    private LocalDateTime snapshotDate;        // When this pricing was recorded
    private String source;                     // "manual", "api", "imported"
    private Map<String, Object> metadata;     // Additional info (currency, region, etc.)
    
    // Helper methods for YearMonth conversion
    @JsonIgnore
    public YearMonth getYearMonthAsObject() {
        return yearMonth != null ? YearMonth.parse(yearMonth) : null;
    }
    
    @JsonIgnore
    public void setYearMonthFromObject(YearMonth yearMonth) {
        this.yearMonth = yearMonth != null ? yearMonth.toString() : null;
    }
}

