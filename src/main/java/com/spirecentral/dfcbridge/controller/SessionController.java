package com.spirecentral.dfcbridge.controller;

import com.spirecentral.dfcbridge.dto.ConnectRequest;
import com.spirecentral.dfcbridge.dto.ConnectResponse;
import com.spirecentral.dfcbridge.dto.DisconnectRequest;
import com.spirecentral.dfcbridge.dto.ErrorResponse;
import com.spirecentral.dfcbridge.model.SessionInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for DFC session management.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Session", description = "DFC session management operations")
public class SessionController {

    private final DfcSessionService sessionService;

    public SessionController(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/connect")
    @Operation(
        summary = "Establish DFC session",
        description = "Creates a new DFC session with the specified repository using the provided credentials"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Session established successfully",
            content = @Content(schema = @Schema(implementation = ConnectResponse.class))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Connection failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ConnectResponse> connect(@Valid @RequestBody ConnectRequest request) {
        ConnectResponse response = sessionService.connect(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/disconnect")
    @Operation(
        summary = "Close DFC session",
        description = "Closes an existing DFC session and releases all associated resources"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session closed successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Void> disconnect(@Valid @RequestBody DisconnectRequest request) {
        sessionService.disconnect(request.getSessionId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session/{sessionId}")
    @Operation(
        summary = "Get session info",
        description = "Returns information about an active DFC session"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Session info retrieved",
            content = @Content(schema = @Schema(implementation = SessionInfo.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<SessionInfo> getSessionInfo(
            @Parameter(description = "Session ID") @PathVariable String sessionId) {
        SessionInfo info = sessionService.getSessionInfo(sessionId);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/session/{sessionId}/valid")
    @Operation(
        summary = "Check session validity",
        description = "Checks if a session is still valid and connected"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Session validity check result")
    })
    public ResponseEntity<Boolean> isSessionValid(
            @Parameter(description = "Session ID") @PathVariable String sessionId) {
        boolean valid = sessionService.isSessionValid(sessionId);
        return ResponseEntity.ok(valid);
    }
}
