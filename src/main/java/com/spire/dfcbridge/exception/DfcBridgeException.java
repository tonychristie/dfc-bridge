package com.spire.dfcbridge.exception;

/**
 * Base exception for DFC Bridge errors.
 */
public class DfcBridgeException extends RuntimeException {

    private final String code;

    public DfcBridgeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public DfcBridgeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
