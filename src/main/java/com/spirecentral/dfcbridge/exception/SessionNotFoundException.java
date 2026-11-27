package com.spirecentral.dfcbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a session is not found or has expired.
 */
public class SessionNotFoundException extends DfcBridgeException {

    public SessionNotFoundException(String sessionId) {
        super("SESSION_NOT_FOUND", "Session not found or expired: " + sessionId, HttpStatus.NOT_FOUND);
    }
}
