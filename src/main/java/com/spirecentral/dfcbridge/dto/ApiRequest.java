package com.spirecentral.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Request body for executing an arbitrary DFC API method.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to execute an arbitrary DFC API method")
public class ApiRequest {

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Active session ID")
    private String sessionId;

    @Schema(description = "Object ID to operate on (optional for type-level operations)")
    private String objectId;

    @Schema(description = "Type name for type-level operations", example = "dm_document")
    private String typeName;

    @NotBlank(message = "Method name is required")
    @Schema(description = "DFC method to invoke", example = "getObjectName")
    private String method;

    @Schema(description = "Method arguments as a list")
    private List<Object> args;

    @Schema(description = "Named method arguments as key-value pairs")
    private Map<String, Object> namedArgs;
}
