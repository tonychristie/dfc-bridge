package com.spirecentral.dfcbridge.exception;

/**
 * Exception thrown when a session is not found or has expired.
 */
public class SessionNotFoundException extends DfcBridgeException {

    public SessionNotFoundException(String sessionId) {
        super("SESSION_NOT_FOUND", "Session not found or expired: " + sessionId);
    }
}
