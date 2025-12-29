package com.spire.dfcbridge.dto;

import com.spire.dfcbridge.model.RepositoryInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response from a successful connection request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from a successful DFC connection")
public class ConnectResponse {

    @Schema(description = "Unique session identifier for subsequent requests")
    private String sessionId;

    @Schema(description = "Information about the connected repository")
    private RepositoryInfo repositoryInfo;
}
