package com.spirecentral.dfcbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

/**
 * REST controller providing convenience redirects and simple endpoints.
 */
@RestController
@Tag(name = "Utility", description = "Utility endpoints and redirects")
public class RedirectController {

    @GetMapping("/health")
    @Operation(
        summary = "Health check",
        description = "Returns a simple 200 OK response to indicate the service is running"
    )
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/status")
    @Operation(
        summary = "Status redirect",
        description = "Redirects to /api/v1/status for bridge status information"
    )
    public RedirectView status() {
        return new RedirectView("/api/v1/status");
    }
}
