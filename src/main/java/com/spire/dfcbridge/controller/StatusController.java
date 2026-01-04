package com.spire.dfcbridge.controller;

import com.spire.dfcbridge.config.backend.BackendProperties;
import com.spire.dfcbridge.config.backend.BackendType;
import com.spire.dfcbridge.service.DfcAvailabilityService;
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
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Status", description = "Bridge status and capability information")
public class StatusController {

    private final DfcAvailabilityService dfcAvailability;
    private final BackendProperties backendProperties;

    public StatusController(DfcAvailabilityService dfcAvailability, BackendProperties backendProperties) {
        this.dfcAvailability = dfcAvailability;
        this.backendProperties = backendProperties;
    }

    @GetMapping("/status")
    @Operation(
        summary = "Get bridge status",
        description = "Returns the current status of the bridge including backend mode and DFC availability"
    )
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "dfc-bridge");

        BackendType backend = backendProperties.getBackend();
        status.put("backend", backend.name().toLowerCase());

        if (backend == BackendType.REST) {
            // REST backend mode
            status.put("mode", "rest");
            status.put("restEndpoint", backendProperties.getRest().getEndpoint());
            status.put("dfcAvailable", dfcAvailability.isDfcAvailable());
        } else {
            // DFC backend mode
            status.put("dfcAvailable", dfcAvailability.isDfcAvailable());
            if (dfcAvailability.isDfcAvailable()) {
                status.put("mode", "dfc");
            } else {
                status.put("mode", "degraded");
                status.put("dfcUnavailableReason", dfcAvailability.getUnavailableReason());
            }
        }

        return ResponseEntity.ok(status);
    }
}
