package com.spire.dfcbridge.config;

import com.spire.dfcbridge.service.DfcAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DfcHealthIndicator.
 * Tests the DFC-only health reporting after removal of REST routing.
 */
class DfcHealthIndicatorTest {

    private DfcAvailabilityService dfcAvailability;
    private DfcHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        dfcAvailability = mock(DfcAvailabilityService.class);
        healthIndicator = new DfcHealthIndicator(dfcAvailability);
    }

    @Test
    void health_dfcAvailable_returnsUp() {
        when(dfcAvailability.isDfcAvailable()).thenReturn(true);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("dfc", health.getDetails().get("backend"));
        assertEquals("available", health.getDetails().get("dfc"));
        assertEquals("full", health.getDetails().get("mode"));
    }

    @Test
    void health_dfcUnavailable_returnsDown() {
        when(dfcAvailability.isDfcAvailable()).thenReturn(false);
        when(dfcAvailability.getUnavailableReason()).thenReturn("DFC libraries not found on classpath");

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("dfc", health.getDetails().get("backend"));
        assertEquals("unavailable", health.getDetails().get("dfc"));
        assertEquals("degraded", health.getDetails().get("mode"));
        assertEquals("DFC libraries not found on classpath", health.getDetails().get("reason"));
    }

    @Test
    void health_alwaysReportsDfcBackend() {
        // After removing REST routing, backend is always DFC
        when(dfcAvailability.isDfcAvailable()).thenReturn(true);

        Health health = healthIndicator.health();

        assertEquals("dfc", health.getDetails().get("backend"));
    }

    @Test
    void health_dfcAvailable_noReasonField() {
        when(dfcAvailability.isDfcAvailable()).thenReturn(true);

        Health health = healthIndicator.health();

        assertNull(health.getDetails().get("reason"));
    }

    @Test
    void health_dfcUnavailable_includesReason() {
        when(dfcAvailability.isDfcAvailable()).thenReturn(false);
        when(dfcAvailability.getUnavailableReason()).thenReturn("Test reason");

        Health health = healthIndicator.health();

        assertEquals("Test reason", health.getDetails().get("reason"));
    }
}
