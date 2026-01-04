package com.spire.dfcbridge.controller;

import com.spire.dfcbridge.service.DfcAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StatusController.
 * Tests the DFC-only status reporting after removal of REST routing.
 */
class StatusControllerTest {

    private DfcAvailabilityService dfcAvailability;
    private StatusController statusController;

    @BeforeEach
    void setUp() {
        dfcAvailability = mock(DfcAvailabilityService.class);
        statusController = new StatusController(dfcAvailability);
    }

    @Test
    void getStatus_dfcAvailable_returnsFullMode() {
        when(dfcAvailability.isDfcAvailable()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = statusController.getStatus();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> status = response.getBody();
        assertNotNull(status);
        assertEquals("dfc-bridge", status.get("service"));
        assertEquals("dfc", status.get("backend"));
        assertEquals(true, status.get("dfcAvailable"));
        assertEquals("full", status.get("mode"));
        assertNull(status.get("dfcUnavailableReason"));
    }

    @Test
    void getStatus_dfcUnavailable_returnsDegradedMode() {
        when(dfcAvailability.isDfcAvailable()).thenReturn(false);
        when(dfcAvailability.getUnavailableReason()).thenReturn("DFC libraries not found");

        ResponseEntity<Map<String, Object>> response = statusController.getStatus();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> status = response.getBody();
        assertNotNull(status);
        assertEquals("dfc-bridge", status.get("service"));
        assertEquals("dfc", status.get("backend"));
        assertEquals(false, status.get("dfcAvailable"));
        assertEquals("degraded", status.get("mode"));
        assertEquals("DFC libraries not found", status.get("dfcUnavailableReason"));
    }

    @Test
    void getStatus_alwaysReportsDfcBackend() {
        // After removing REST routing, backend is always DFC
        when(dfcAvailability.isDfcAvailable()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = statusController.getStatus();

        Map<String, Object> status = response.getBody();
        assertNotNull(status);
        assertEquals("dfc", status.get("backend"));
    }
}
