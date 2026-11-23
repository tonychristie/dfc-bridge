package com.spirecentral.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for establishing a Documentum session via DFC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Request to establish a Documentum session")
public class ConnectRequest {

    @Schema(description = "Docbroker host. The session connects to the repository in the environment of this docbroker",
            example = "docbroker.example.com")
    private String docbroker;

    @Positive(message = "Port must be a positive number")
    @Schema(description = "Docbroker port. Together with the docbroker host, it chooses the environment the session connects to",
            example = "1489", defaultValue = "1489")
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

    @Schema(description = "Domain for authentication (optional)")
    private String domain;
}
