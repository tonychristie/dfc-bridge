package com.spire.dfcbridge.config;

import com.spire.dfcbridge.service.DfcAvailabilityService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator that reports DFC library availability.
 * Allows monitoring systems to detect degraded mode.
 */
@Component
public class DfcHealthIndicator implements HealthIndicator {

    private final DfcAvailabilityService dfcAvailability;

    public DfcHealthIndicator(DfcAvailabilityService dfcAvailability) {
        this.dfcAvailability = dfcAvailability;
    }

    @Override
    public Health health() {
        if (dfcAvailability.isDfcAvailable()) {
            return Health.up()
                    .withDetail("dfc", "available")
                    .withDetail("mode", "full")
                    .build();
        } else {
            return Health.down()
                    .withDetail("dfc", "unavailable")
                    .withDetail("mode", "degraded")
                    .withDetail("reason", dfcAvailability.getUnavailableReason())
                    .build();
        }
    }
}
