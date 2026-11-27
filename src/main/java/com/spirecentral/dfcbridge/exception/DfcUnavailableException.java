package com.spirecentral.dfcbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when DFC libraries are not available on the classpath.
 * This allows the service to start without DFC and return appropriate errors.
 */
public class DfcUnavailableException extends DfcBridgeException {

    public DfcUnavailableException() {
        super("DFC_UNAVAILABLE",
              "DFC libraries are not available. The bridge is running in degraded mode. " +
              "Ensure DFC is properly installed and on the classpath.",
              HttpStatus.SERVICE_UNAVAILABLE);
    }

    public DfcUnavailableException(String details) {
        super("DFC_UNAVAILABLE",
              "DFC libraries are not available: " + details + ". " +
              "The bridge is running in degraded mode.",
              HttpStatus.SERVICE_UNAVAILABLE);
    }
}
