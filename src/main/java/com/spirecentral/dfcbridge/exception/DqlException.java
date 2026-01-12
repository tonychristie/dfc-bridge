package com.spirecentral.dfcbridge.exception;

/**
 * Exception thrown when a DQL query fails.
 */
public class DqlException extends DfcBridgeException {

    public DqlException(String message) {
        super("DQL_ERROR", message);
    }

    public DqlException(String message, Throwable cause) {
        super("DQL_ERROR", message, cause);
    }
}
