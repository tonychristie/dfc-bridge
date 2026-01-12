package com.spirecentral.dfcbridge.controller;

import com.spirecentral.dfcbridge.service.DfcAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for bridge status information.
 * Reports DFC availability status.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Status", description = "Bridge status and capability information")
public class StatusController {

    private final DfcAvailabilityService dfcAvailability;

    public StatusController(DfcAvailabilityService dfcAvailability) {
        this.dfcAvailability = dfcAvailability;
    }

    @GetMapping("/status")
    @Operation(
        summary = "Get bridge status",
        description = "Returns the current status of the bridge including DFC availability"
    )
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "dfc-bridge");
        status.put("backend", "dfc");
        status.put("dfcAvailable", dfcAvailability.isDfcAvailable());

        if (dfcAvailability.isDfcAvailable()) {
            status.put("mode", "full");
        } else {
            status.put("mode", "degraded");
            status.put("dfcUnavailableReason", dfcAvailability.getUnavailableReason());
        }

        return ResponseEntity.ok(status);
    }
}
