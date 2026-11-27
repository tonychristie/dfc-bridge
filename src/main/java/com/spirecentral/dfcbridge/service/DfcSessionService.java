package com.spirecentral.dfcbridge.service;

import com.spirecentral.dfcbridge.dto.ConnectRequest;
import com.spirecentral.dfcbridge.dto.ConnectResponse;
import com.spirecentral.dfcbridge.model.SessionInfo;

import java.util.function.Function;

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
     * Run work with the DFC session of a bridge session while holding that session's lock,
     * so that only one request uses the DFC session at a time. Updates the last activity time.
     *
     * @param sessionId Session ID
     * @param work Work that receives the DFC session (implementation-specific) and returns a result
     * @return The result of the work
     */
    <T> T withSession(String sessionId, Function<Object, T> work);
}
