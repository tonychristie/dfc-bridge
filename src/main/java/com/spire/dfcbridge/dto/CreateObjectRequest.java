package com.spire.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Request body for creating a new Documentum object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new Documentum object")
public class CreateObjectRequest {

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Active session ID")
    private String sessionId;

    @NotBlank(message = "Object type is required")
    @Schema(description = "Object type (e.g., dm_document, dm_folder)")
    private String objectType;

    @Schema(description = "Folder path or ID to link the object to")
    private String folderPath;

    @Schema(description = "Object name")
    private String objectName;

    @Schema(description = "Additional attributes to set on the object")
    private Map<String, Object> attributes;
}
