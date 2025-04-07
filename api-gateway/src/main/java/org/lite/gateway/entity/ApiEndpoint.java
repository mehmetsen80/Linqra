package org.lite.gateway.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("apiEndpoints")
@Data
@Builder

@AllArgsConstructor
public class ApiEndpoint {
    public ApiEndpoint() {
        this.version = 1;
        this.createdAt = System.currentTimeMillis();
    }

    @Id 
    private String id;

    @NotNull(message = "Version must not be null")
    private Integer version = 1;
    
    @NotNull(message = "Creation timestamp is required")
    private Long createdAt = System.currentTimeMillis();
    
    private Long updatedAt;

    @NotBlank(groups = Create.class, message = "Route identifier is required")
    private String routeIdentifier;

    @NotBlank(groups = Create.class, message = "Swagger JSON is required")
    private String swaggerJson;  // Stores the entire Swagger/OpenAPI specification

    @NotBlank(groups = Create.class, message = "Endpoint name is required")
    private String name;        // Name of the endpoint (e.g., "List Inventory Items")

    private String description; // Optional description of the endpoint

    // Validation group marker interface
    public interface Create {}
} 