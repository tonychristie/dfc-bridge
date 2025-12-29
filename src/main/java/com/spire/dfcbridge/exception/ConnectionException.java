package com.spire.dfcbridge.exception;

/**
 * Exception thrown when a connection to the repository fails.
 */
public class ConnectionException extends DfcBridgeException {

    public ConnectionException(String message) {
        super("CONNECTION_FAILED", message);
    }

    public ConnectionException(String message, Throwable cause) {
        super("CONNECTION_FAILED", message, cause);
    }
}
