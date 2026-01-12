package com.spirecentral.dfcbridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for executing a dmAPI command.
 *
 * dmAPI commands are server-level API calls that are executed via
 * IDfSession.apiGet(), apiExec(), or apiSet() - not Java method invocations.
 *
 * Examples:
 * - dmAPIGet("getservermap,session")
 * - dmAPIGet("dump,session,0900000000000001")
 * - dmAPIExec("save,session,0900000000000001")
 * - dmAPISet("set,session,0900000000000001,object_name,NewName")
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to execute a dmAPI command")
public class DmApiRequest {

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Active session ID")
    private String sessionId;

    @NotBlank(message = "API type is required")
    @Schema(description = "Type of dmAPI call: get, exec, or set", example = "get")
    private String apiType;

    @NotBlank(message = "Command is required")
    @Schema(description = "The dmAPI command string (without the type prefix)",
            example = "getservermap,session")
    private String command;
}
