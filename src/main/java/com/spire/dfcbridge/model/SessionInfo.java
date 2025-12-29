package com.spire.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Contains information about an active DFC session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {

    /**
     * Unique session identifier
     */
    private String sessionId;

    /**
     * Whether the session is currently connected
     */
    private boolean connected;

    /**
     * Name of the connected repository
     */
    private String repository;

    /**
     * Username used for this session
     */
    private String user;

    /**
     * The docbroker host for this session
     */
    private String docbroker;

    /**
     * The docbroker port for this session
     */
    private int port;

    /**
     * DFC profile used for this session
     */
    private String dfcProfile;

    /**
     * When the session was started
     */
    private Instant sessionStart;

    /**
     * Last activity time on this session
     */
    private Instant lastActivity;

    /**
     * Server version information
     */
    private String serverVersion;
}
