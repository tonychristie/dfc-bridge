package com.spirecentral.dfcbridge.service;

import com.spirecentral.dfcbridge.exception.DfcUnavailableException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service to check and track DFC library availability.
 * Allows the bridge to start without DFC and gracefully degrade.
 */
@Service
public class DfcAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(DfcAvailabilityService.class);

    @Value("${dfc.home:}")
    private String dfcHome;

    private boolean dfcAvailable = false;
    private String unavailableReason = null;

    @PostConstruct
    public void checkDfcAvailability() {
        unavailableReason = null;
        try {
            if (dfcHome == null || dfcHome.isBlank()) {
                unavailableReason = "dfc.home is not set";
            } else if (!Files.isRegularFile(Path.of(dfcHome, "dfc.jar"))) {
                unavailableReason = "dfc.jar not found in " + dfcHome;
            }
            dfcAvailable = unavailableReason == null;
            if (dfcAvailable) {
                log.info("DFC libraries detected in {} - full functionality available", dfcHome);
            } else {
                log.warn("{} - running in degraded mode. DFC-dependent operations will return errors.",
                        unavailableReason);
            }
        } catch (Exception e) {
            dfcAvailable = false;
            unavailableReason = e.getMessage();
            log.warn("Error checking DFC availability: {} - running in degraded mode", e.getMessage());
        }
    }

    /**
     * Check if DFC is available.
     */
    public boolean isDfcAvailable() {
        return dfcAvailable;
    }

    /**
     * Get reason why DFC is unavailable, or null if available.
     */
    public String getUnavailableReason() {
        return unavailableReason;
    }

    /**
     * Require DFC to be available, throwing exception if not.
     * Use this at the start of DFC-dependent operations.
     */
    public void requireDfc() {
        if (!dfcAvailable) {
            throw new DfcUnavailableException(unavailableReason);
        }
    }
}
