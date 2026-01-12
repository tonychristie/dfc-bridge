package com.spirecentral.dfcbridge.service;

import com.spirecentral.dfcbridge.exception.DfcUnavailableException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service to check and track DFC library availability.
 * Allows the bridge to start without DFC and gracefully degrade.
 */
@Service
public class DfcAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(DfcAvailabilityService.class);

    private static final String DFC_CLIENT_CLASS = "com.documentum.fc.client.DfClient";

    private boolean dfcAvailable = false;
    private String unavailableReason = null;

    @PostConstruct
    public void checkDfcAvailability() {
        try {
            Class.forName(DFC_CLIENT_CLASS);
            dfcAvailable = true;
            log.info("DFC libraries detected - full functionality available");
        } catch (ClassNotFoundException e) {
            dfcAvailable = false;
            unavailableReason = "DFC client class not found on classpath";
            log.warn("DFC libraries not found - running in degraded mode. " +
                     "DFC-dependent operations will return errors.");
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
