package com.spire.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for executing a DQL query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to execute a DQL query")
public class DqlRequest {

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Active session ID")
    private String sessionId;

    @NotBlank(message = "Query is required")
    @Schema(description = "DQL query to execute", example = "SELECT r_object_id, object_name FROM dm_document WHERE FOLDER('/Temp')")
    private String query;

    @Positive(message = "Max rows must be positive")
    @Schema(description = "Maximum number of rows to return", example = "100", defaultValue = "100")
    @Builder.Default
    private int maxRows = 100;

    @Schema(description = "Starting row for pagination (0-based)", defaultValue = "0")
    @Builder.Default
    private int startRow = 0;
}
