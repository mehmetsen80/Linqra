package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Represents an LLM model with its current pricing information
 */
@Document(collection = "llm_models")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmModel {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String modelName;              // e.g., "gpt-4o", "gemini-2.0-flash"
    
    private String displayName;            // e.g., "GPT-4 Optimized"
    private String provider;               // e.g., "openai", "gemini"
    private String category;               // e.g., "chat", "embedding", "vision"
    
    private double inputPricePer1M;       // Input/prompt price per 1M tokens in USD
    private double outputPricePer1M;      // Output/completion price per 1M tokens in USD
    
    private boolean active;               // Whether this model is currently active/available
    private String description;           // Optional description of the model
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;            // User who last updated the pricing
}

