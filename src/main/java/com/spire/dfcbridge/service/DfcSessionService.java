package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.ConnectRequest;
import com.spire.dfcbridge.dto.ConnectResponse;
import com.spire.dfcbridge.model.SessionInfo;

/**
 * Service interface for managing DFC sessions.
 */
public interface DfcSessionService {

    /**
     * Establish a new DFC session.
     *
     * @param request Connection parameters
     * @return Connection response with session ID and repository info
     */
    ConnectResponse connect(ConnectRequest request);

    /**
     * Close a DFC session.
     *
     * @param sessionId Session to close
     */
    void disconnect(String sessionId);

    /**
     * Get information about an active session.
     *
     * @param sessionId Session ID
     * @return Session information
     */
    SessionInfo getSessionInfo(String sessionId);

    /**
     * Check if a session is valid and connected.
     *
     * @param sessionId Session ID to check
     * @return true if session is valid and connected
     */
    boolean isSessionValid(String sessionId);

    /**
     * Update the last activity time for a session.
     *
     * @param sessionId Session ID
     */
    void touchSession(String sessionId);

    /**
     * Get the underlying DFC session manager for a session.
     * This returns the raw DFC session object for use by other services.
     *
     * @param sessionId Session ID
     * @return The DFC session manager (implementation-specific)
     */
    Object getDfcSession(String sessionId);
}
