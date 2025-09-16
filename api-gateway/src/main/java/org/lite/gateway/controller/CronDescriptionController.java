package org.lite.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.lite.gateway.dto.CronDescriptionRequest;
import org.lite.gateway.dto.CronDescriptionResponse;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.service.CronDescriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@RequestMapping("/api/cron")
@Tag(name = "Cron Description", description = "APIs for generating human-readable descriptions from cron expressions")
@Slf4j
public class CronDescriptionController {

    @Autowired
    private CronDescriptionService cronDescriptionService;

    @Operation(
        summary = "Generate cron expression description",
        description = "Converts a cron expression into a human-readable description. Supports 6-part cron expressions with seconds, minutes, hours, day of month, month, and day of week fields.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Cron expression to describe",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CronDescriptionRequest.class),
                examples = {
                    @ExampleObject(
                        name = "Daily at 9 AM",
                        value = "{\"cronExpression\": \"0 0 9 * * *\"}",
                        summary = "Every day at 9:00 AM"
                    ),
                    @ExampleObject(
                        name = "Every 15 minutes",
                        value = "{\"cronExpression\": \"0 */15 * * * *\"}",
                        summary = "Every 15 minutes"
                    ),
                    @ExampleObject(
                        name = "Weekdays at 5 PM",
                        value = "{\"cronExpression\": \"0 0 17 * * MON-FRI\"}",
                        summary = "Every weekday at 5:00 PM"
                    ),
                    @ExampleObject(
                        name = "Last day of month",
                        value = "{\"cronExpression\": \"0 0 9 L * *\"}",
                        summary = "Last day of month at 9:00 AM"
                    )
                }
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully generated description",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CronDescriptionResponse.class),
                examples = {
                    @ExampleObject(
                        name = "Success Response",
                        value = "{\"cronExpression\": \"0 0 9 * * *\", \"description\": \"Every day at 9:00 AM\"}"
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid cron expression provided",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(
                        name = "Invalid Expression",
                        value = "{\"code\": \"INVALID_CRON_EXPRESSION\", \"message\": \"Invalid hour value: 25\", \"details\": {\"cronExpression\": \"0 0 25 * * *\"}}"
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @PostMapping("/describe")
    public ResponseEntity<Object> describeCronExpression(@RequestBody CronDescriptionRequest request) {
        try {
            log.info("Generating description for cron expression: {}", request.getCronExpression());
            
            String description = cronDescriptionService.getCronDescription(request.getCronExpression());
            
            return ResponseEntity.ok(Map.of(
                "cronExpression", request.getCronExpression(),
                "description", description
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid cron expression provided: {}", request.getCronExpression(), e);
            return ResponseEntity.badRequest().body(ErrorResponse.fromError(
                "INVALID_CRON_EXPRESSION",
                e.getMessage(),
                Map.of("cronExpression", request.getCronExpression())
            ));
        } catch (Exception e) {
            log.error("Error generating cron description for: {}", request.getCronExpression(), e);
            return ResponseEntity.internalServerError().body(ErrorResponse.fromError(
                "CRON_DESCRIPTION_ERROR",
                "Failed to generate cron description",
                Map.of("cronExpression", request.getCronExpression())
            ));
        }
    }




} 