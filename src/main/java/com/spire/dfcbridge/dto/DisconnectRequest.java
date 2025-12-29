package com.spire.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for closing a DFC session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to close a DFC session")
public class DisconnectRequest {

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Session ID to disconnect")
    private String sessionId;
}
