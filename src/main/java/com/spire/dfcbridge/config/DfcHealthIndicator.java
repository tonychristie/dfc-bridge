package com.spire.dfcbridge.config;

import com.spire.dfcbridge.config.backend.BackendProperties;
import com.spire.dfcbridge.config.backend.BackendType;
import com.spire.dfcbridge.service.DfcAvailabilityService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator that reports backend status.
 * For DFC backend: reports DFC library availability.
 * For REST backend: reports configured endpoint.
 */
@Component
public class DfcHealthIndicator implements HealthIndicator {

    private final DfcAvailabilityService dfcAvailability;
    private final BackendProperties backendProperties;

    public DfcHealthIndicator(DfcAvailabilityService dfcAvailability,
                              BackendProperties backendProperties) {
        this.dfcAvailability = dfcAvailability;
        this.backendProperties = backendProperties;
    }

    @Override
    public Health health() {
        BackendType backend = backendProperties.getBackend();

        if (backend == BackendType.REST) {
            // REST backend
            String endpoint = backendProperties.getRest().getEndpoint();
            if (endpoint == null || endpoint.isBlank()) {
                return Health.down()
                        .withDetail("backend", "rest")
                        .withDetail("status", "not configured")
                        .withDetail("reason", "documentum.rest.endpoint not set")
                        .build();
            }
            return Health.up()
                    .withDetail("backend", "rest")
                    .withDetail("endpoint", endpoint)
                    .build();
        } else {
            // DFC backend
            if (dfcAvailability.isDfcAvailable()) {
                return Health.up()
                        .withDetail("backend", "dfc")
                        .withDetail("dfc", "available")
                        .withDetail("mode", "full")
                        .build();
            } else {
                return Health.down()
                        .withDetail("backend", "dfc")
                        .withDetail("dfc", "unavailable")
                        .withDetail("mode", "degraded")
                        .withDetail("reason", dfcAvailability.getUnavailableReason())
                        .build();
            }
        }
    }
}
