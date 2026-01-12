package com.spirecentral.dfcbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * REST controller providing convenience redirects for common endpoints.
 */
@RestController
@Tag(name = "Redirects", description = "Convenience redirects for common paths")
public class RedirectController {

    @GetMapping("/health")
    @Operation(
        summary = "Health check redirect",
        description = "Redirects to /actuator/health for Spring Boot health information"
    )
    public RedirectView health() {
        return new RedirectView("/actuator/health");
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
