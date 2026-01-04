package com.spire.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for establishing a Documentum session.
 *
 * <p>Connection type is determined by which fields are provided:
 * <ul>
 *   <li>If {@code endpoint} is provided → REST connection via Documentum REST Services</li>
 *   <li>If {@code docbroker} is provided → DFC connection via docbroker</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to establish a Documentum session")
public class ConnectRequest {

    @Schema(description = "REST endpoint URL for Documentum REST Services (use this OR docbroker, not both)",
            example = "http://dctm-rest.example.com:8080/dctm-rest")
    private String endpoint;

    @Schema(description = "Docbroker hostname for DFC connection (use this OR endpoint, not both)",
            example = "docbroker.example.com")
    private String docbroker;

    @Positive(message = "Port must be a positive number")
    @Schema(description = "Docbroker port for DFC connection", example = "1489", defaultValue = "1489")
    @Builder.Default
    private int port = 1489;

    @NotBlank(message = "Repository name is required")
    @Schema(description = "Repository name to connect to", example = "MyRepo")
    private String repository;

    @NotBlank(message = "Username is required")
    @Schema(description = "Username for authentication", example = "dmadmin")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password for authentication")
    private String password;

    @Schema(description = "DFC profile to use (optional)", example = "default")
    private String dfcProfile;

    @Schema(description = "Domain for authentication (optional)")
    private String domain;
}
