package com.spire.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Request body for updating an object's attributes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update a Documentum object")
public class UpdateObjectRequest {

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Active session ID")
    private String sessionId;

    @NotEmpty(message = "At least one attribute must be provided")
    @Schema(description = "Attributes to update as key-value pairs")
    private Map<String, Object> attributes;

    @Schema(description = "Whether to save the object after updating attributes", defaultValue = "true")
    @Builder.Default
    private boolean save = true;
}
