package com.spire.dfcbridge.service.impl;

import com.spire.dfcbridge.dto.ConnectRequest;
import com.spire.dfcbridge.dto.ConnectResponse;
import com.spire.dfcbridge.exception.ConnectionException;
import com.spire.dfcbridge.exception.SessionNotFoundException;
import com.spire.dfcbridge.model.RepositoryInfo;
import com.spire.dfcbridge.model.SessionInfo;
import com.spire.dfcbridge.service.DfcSessionService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of DfcSessionService using reflection to call DFC APIs.
 * DFC classes must be available on the classpath at runtime.
 */
@Service
public class DfcSessionServiceImpl implements DfcSessionService {

    private static final Logger log = LoggerFactory.getLogger(DfcSessionServiceImpl.class);

    // DFC class names for reflection
    private static final String DFC_CLIENT_CLASS = "com.documentum.fc.client.DfClient";
    private static final String DFC_LOGIN_INFO_CLASS = "com.documentum.fc.client.DfLoginInfo";
    private static final String DFC_SESSION_MANAGER_IFACE = "com.documentum.fc.client.IDfSessionManager";
    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";

    private final Map<String, SessionHolder> sessions = new ConcurrentHashMap<>();

    @Value("${dfc.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Override
    public ConnectResponse connect(ConnectRequest request) {
        log.info("Connecting to repository {} via {}:{}",
                request.getRepository(), request.getDocbroker(), request.getPort());

        try {
            // Get DfClient instance via reflection
            Class<?> dfClientClass = Class.forName(DFC_CLIENT_CLASS);
            Method getLocalClientMethod = dfClientClass.getMethod("getLocalClient");
            Object dfClient = getLocalClientMethod.invoke(null);

            // Create login info
            Class<?> loginInfoClass = Class.forName(DFC_LOGIN_INFO_CLASS);
            Object loginInfo = loginInfoClass.getDeclaredConstructor().newInstance();

            // Set login info properties
            Method setUser = loginInfoClass.getMethod("setUser", String.class);
            Method setPassword = loginInfoClass.getMethod("setPassword", String.class);
            Method setDomain = loginInfoClass.getMethod("setDomain", String.class);

            setUser.invoke(loginInfo, request.getUsername());
            setPassword.invoke(loginInfo, request.getPassword());
            if (request.getDomain() != null) {
                setDomain.invoke(loginInfo, request.getDomain());
            }

            // Create session manager
            Method newSessionMgrMethod = dfClient.getClass().getMethod("newSessionManager");
            Object sessionManager = newSessionMgrMethod.invoke(dfClient);

            // Set identity
            Class<?> sessionMgrClass = Class.forName(DFC_SESSION_MANAGER_IFACE);
            Method setIdentityMethod = sessionMgrClass.getMethod("setIdentity",
                    String.class, Class.forName("com.documentum.fc.client.IDfLoginInfo"));
            setIdentityMethod.invoke(sessionManager, request.getRepository(), loginInfo);

            // Get session
            Method getSessionMethod = sessionMgrClass.getMethod("getSession", String.class);
            Object dfSession = getSessionMethod.invoke(sessionManager, request.getRepository());

            // Generate session ID
            String sessionId = UUID.randomUUID().toString();

            // Get repository info
            RepositoryInfo repoInfo = extractRepositoryInfo(dfSession, request);

            // Store session
            SessionHolder holder = new SessionHolder();
            holder.sessionManager = sessionManager;
            holder.dfSession = dfSession;
            holder.sessionInfo = SessionInfo.builder()
                    .sessionId(sessionId)
                    .connected(true)
                    .repository(request.getRepository())
                    .user(request.getUsername())
                    .docbroker(request.getDocbroker())
                    .port(request.getPort())
                    .dfcProfile(request.getDfcProfile())
                    .sessionStart(Instant.now())
                    .lastActivity(Instant.now())
                    .serverVersion(repoInfo.getServerVersion())
                    .build();
            sessions.put(sessionId, holder);

            log.info("Session {} established for user {} on repository {}",
                    sessionId, request.getUsername(), request.getRepository());

            return ConnectResponse.builder()
                    .sessionId(sessionId)
                    .repositoryInfo(repoInfo)
                    .build();

        } catch (ClassNotFoundException e) {
            throw new ConnectionException("DFC libraries not found on classpath. " +
                    "Ensure DFC is properly installed and configured.", e);
        } catch (Exception e) {
            throw new ConnectionException("Failed to connect: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect(String sessionId) {
        SessionHolder holder = sessions.remove(sessionId);
        if (holder != null) {
            releaseDfcSession(holder);
            log.info("Session {} disconnected", sessionId);
        }
    }

    @Override
    public SessionInfo getSessionInfo(String sessionId) {
        SessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return holder.sessionInfo;
    }

    @Override
    public boolean isSessionValid(String sessionId) {
        SessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            return false;
        }
        try {
            // Check if session is still connected
            Method isConnectedMethod = holder.dfSession.getClass().getMethod("isConnected");
            return (Boolean) isConnectedMethod.invoke(holder.dfSession);
        } catch (Exception e) {
            log.warn("Error checking session validity: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void touchSession(String sessionId) {
        SessionHolder holder = sessions.get(sessionId);
        if (holder != null) {
            holder.sessionInfo.setLastActivity(Instant.now());
        }
    }

    @Override
    public Object getDfcSession(String sessionId) {
        SessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        touchSession(sessionId);
        return holder.dfSession;
    }

    /**
     * Clean up expired sessions periodically.
     */
    @Scheduled(fixedRateString = "${dfc.session.cleanup-interval-ms:60000}")
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusSeconds(sessionTimeoutMinutes * 60L);
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().sessionInfo.getLastActivity().isBefore(cutoff)) {
                log.info("Cleaning up expired session: {}", entry.getKey());
                // Release the DFC session directly instead of calling disconnect()
                // since removeIf will handle removing the entry from the map
                releaseDfcSession(entry.getValue());
                return true;
            }
            return false;
        });
    }

    /**
     * Releases a DFC session back to the session manager.
     */
    private void releaseDfcSession(SessionHolder holder) {
        try {
            Class<?> sessionMgrClass = Class.forName(DFC_SESSION_MANAGER_IFACE);
            Method releaseMethod = sessionMgrClass.getMethod("release",
                    Class.forName(DFC_SESSION_IFACE));
            releaseMethod.invoke(holder.sessionManager, holder.dfSession);
        } catch (Exception e) {
            log.warn("Error releasing DFC session: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down - disconnecting all sessions");
        sessions.keySet().forEach(this::disconnect);
    }

    private RepositoryInfo extractRepositoryInfo(Object dfSession, ConnectRequest request) {
        try {
            // Get server version
            Method getServerVersionMethod = dfSession.getClass().getMethod("getServerVersion");
            String serverVersion = (String) getServerVersionMethod.invoke(dfSession);

            // Get docbase config for more info
            Method getDocbaseConfigMethod = dfSession.getClass().getMethod("getDocbaseConfig");
            Object docbaseConfig = getDocbaseConfigMethod.invoke(dfSession);

            String docbaseId = null;
            if (docbaseConfig != null) {
                Method getStringMethod = docbaseConfig.getClass().getMethod("getString", String.class);
                docbaseId = (String) getStringMethod.invoke(docbaseConfig, "r_docbase_id");
            }

            return RepositoryInfo.builder()
                    .name(request.getRepository())
                    .id(docbaseId)
                    .serverVersion(serverVersion)
                    .contentServerHost(request.getDocbroker())
                    .build();

        } catch (Exception e) {
            log.warn("Could not extract full repository info: {}", e.getMessage());
            return RepositoryInfo.builder()
                    .name(request.getRepository())
                    .contentServerHost(request.getDocbroker())
                    .build();
        }
    }

    /**
     * Internal holder for session objects.
     */
    private static class SessionHolder {
        Object sessionManager;
        Object dfSession;
        SessionInfo sessionInfo;
    }
}
