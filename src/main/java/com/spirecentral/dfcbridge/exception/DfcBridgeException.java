package com.spirecentral.dfcbridge.exception;

import com.spirecentral.dfcbridge.util.DfcExceptionUtils;
import org.springframework.http.HttpStatus;

/**
 * Base exception for DFC Bridge errors. Carries the HTTP status of the error response.
 */
public class DfcBridgeException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DfcBridgeException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Takes the status from the exception that caused the error.
     */
    public DfcBridgeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = DfcExceptionUtils.statusOf(cause);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
