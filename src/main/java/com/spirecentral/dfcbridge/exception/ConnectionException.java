package com.spirecentral.dfcbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a connection to the repository fails.
 */
public class ConnectionException extends DfcBridgeException {

    public ConnectionException(String message, HttpStatus status) {
        super("CONNECTION_FAILED", message, status);
    }

    public ConnectionException(String message, Throwable cause) {
        super("CONNECTION_FAILED", message, cause);
    }
}
