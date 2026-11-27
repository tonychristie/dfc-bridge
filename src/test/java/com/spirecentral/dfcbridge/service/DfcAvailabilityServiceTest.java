package com.spirecentral.dfcbridge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DfcAvailabilityServiceTest {

    @TempDir
    Path dfcHome;

    @Test
    void checkDfcAvailability_dfcHomeHoldsDfcJar_fullMode() throws IOException {
        Files.createFile(dfcHome.resolve("dfc.jar"));

        DfcAvailabilityService service = check(dfcHome.toString());

        assertTrue(service.isDfcAvailable());
        assertNull(service.getUnavailableReason());
    }

    @Test
    void checkDfcAvailability_dfcHomeNotSet_degradedMode() {
        DfcAvailabilityService service = check("");

        assertFalse(service.isDfcAvailable());
        assertEquals("dfc.home is not set", service.getUnavailableReason());
    }

    @Test
    void checkDfcAvailability_noDfcJarInDfcHome_degradedMode() {
        DfcAvailabilityService service = check(dfcHome.toString());

        assertFalse(service.isDfcAvailable());
        assertEquals("dfc.jar not found in " + dfcHome, service.getUnavailableReason());
    }

    private static DfcAvailabilityService check(String dfcHome) {
        DfcAvailabilityService service = new DfcAvailabilityService();
        ReflectionTestUtils.setField(service, "dfcHome", dfcHome);
        service.checkDfcAvailability();
        return service;
    }
}
