package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.dto.ConnectRequest;
import com.spirecentral.dfcbridge.dto.ConnectResponse;
import com.spirecentral.dfcbridge.exception.ConnectionException;
import com.spirecentral.dfcbridge.exception.SessionNotFoundException;
import com.spirecentral.dfcbridge.model.RepositoryInfo;
import com.spirecentral.dfcbridge.model.SessionInfo;
import com.spirecentral.dfcbridge.service.DfcAvailabilityService;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.util.DfcExceptionUtils;
import com.spirecentral.dfcbridge.util.DfcReflection;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Implementation of DfcSessionService using reflection to call DFC APIs.
 * DFC classes must be available on the classpath at runtime.
 */
@Service
public class DfcSessionServiceImpl implements DfcSessionService {

    private static final Logger log = LoggerFactory.getLogger(DfcSessionServiceImpl.class);

    // DFC class names for reflection
    private static final String DFC_CLIENT_CLASS = "com.documentum.fc.client.DfClient";
    private static final String DFC_LOGIN_INFO_CLASS = "com.documentum.fc.common.DfLoginInfo";
    private static final String DFC_SESSION_MANAGER_IFACE = "com.documentum.fc.client.IDfSessionManager";
    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";
    private static final String DFC_LOGIN_INFO_IFACE = "com.documentum.fc.common.IDfLoginInfo";

    private static final Class<?>[] NO_PARAMS = new Class<?>[0];
    private static final Class<?>[] STRING_PARAM = {String.class};

    private final Map<String, SessionHolder> sessions = new ConcurrentHashMap<>();
    private final DfcAvailabilityService dfcAvailability;

    @Value("${dfc.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    public DfcSessionServiceImpl(DfcAvailabilityService dfcAvailability) {
        this.dfcAvailability = dfcAvailability;
    }

    @Override
    public ConnectResponse connect(ConnectRequest request) {
        // Check DFC availability first
        dfcAvailability.requireDfc();

        // Validate DFC-required fields
        if (request.getDocbroker() == null || request.getDocbroker().isBlank()) {
            throw new ConnectionException("Docbroker host is required for DFC backend connections",
                    HttpStatus.BAD_REQUEST);
        }

        log.info("Connecting to repository {} via {}:{}",
                request.getRepository(), request.getDocbroker(), request.getPort());

        try {
            Object dfClient = DfcReflection.invoke(DFC_CLIENT_CLASS, null, "getLocalClient", NO_PARAMS);

            Object loginInfo = DfcReflection.newInstance(DFC_LOGIN_INFO_CLASS);
            DfcReflection.invoke(loginInfo, "setUser", STRING_PARAM, request.getUsername());
            DfcReflection.invoke(loginInfo, "setPassword", STRING_PARAM, request.getPassword());
            if (request.getDomain() != null) {
                DfcReflection.invoke(loginInfo, "setDomain", STRING_PARAM, request.getDomain());
            }

            Object sessionManager = DfcReflection.invoke(dfClient, "newSessionManager");
            DfcReflection.invoke(DFC_SESSION_MANAGER_IFACE, sessionManager, "setIdentity",
                    new Class<?>[]{String.class, Class.forName(DFC_LOGIN_INFO_IFACE)},
                    request.getRepository(), loginInfo);
            Object dfSession = DfcReflection.invoke(DFC_SESSION_MANAGER_IFACE, sessionManager, "getSession",
                    STRING_PARAM, request.getRepository());

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
                    "Ensure DFC is properly installed and configured.", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            throw new ConnectionException("Failed to connect: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
        }
    }

    @Override
    public void disconnect(String sessionId) {
        SessionHolder holder = sessions.remove(sessionId);
        if (holder != null) {
            holder.lock.lock();
            try {
                holder.released = true;
                releaseDfcSession(holder);
            } finally {
                holder.lock.unlock();
            }
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
        holder.lock.lock();
        try {
            return !holder.released && isDfcSessionConnected(holder);
        } finally {
            holder.lock.unlock();
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
    public <T> T withSession(String sessionId, Function<Object, T> work) {
        SessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        holder.lock.lock();
        try {
            // disconnect or cleanup may have released the session while this request waited for the lock
            if (holder.released) {
                throw new SessionNotFoundException(sessionId);
            }
            // a released DFC session handle fails every call, for example after an /api disconnect
            if (!isDfcSessionConnected(holder)) {
                sessions.remove(sessionId, holder);
                holder.released = true;
                releaseDfcSession(holder);
                log.info("Session {} removed: DFC session is no longer connected", sessionId);
                throw new SessionNotFoundException(sessionId);
            }
            holder.sessionInfo.setLastActivity(Instant.now());
            return work.apply(holder.dfSession);
        } finally {
            holder.lock.unlock();
        }
    }

    /**
     * Clean up expired sessions periodically. A session in use by a request is skipped
     * and checked again on the next run.
     */
    @Scheduled(fixedRateString = "${dfc.session.cleanup-interval-ms:60000}")
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusSeconds(sessionTimeoutMinutes * 60L);
        sessions.entrySet().removeIf(entry -> {
            SessionHolder holder = entry.getValue();
            if (!holder.lock.tryLock()) {
                return false;
            }
            try {
                if (!holder.sessionInfo.getLastActivity().isBefore(cutoff)) {
                    return false;
                }
                log.info("Cleaning up expired session: {}", entry.getKey());
                // Release the DFC session directly instead of calling disconnect()
                // since removeIf will handle removing the entry from the map
                holder.released = true;
                releaseDfcSession(holder);
                return true;
            } finally {
                holder.lock.unlock();
            }
        });
    }

    private boolean isDfcSessionConnected(SessionHolder holder) {
        try {
            return (Boolean) DfcReflection.invoke(holder.dfSession, "isConnected");
        } catch (Exception e) {
            log.warn("Error checking session validity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Releases a DFC session back to the session manager.
     */
    private void releaseDfcSession(SessionHolder holder) {
        try {
            DfcReflection.invoke(holder.sessionManager, "release",
                    new Class<?>[]{Class.forName(DFC_SESSION_IFACE)}, holder.dfSession);
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
            String serverVersion = (String) DfcReflection.invoke(dfSession, "getServerVersion");

            // Get docbase config for more info
            Object docbaseConfig = DfcReflection.invoke(dfSession, "getDocbaseConfig");

            String docbaseId = null;
            if (docbaseConfig != null) {
                docbaseId = (String) DfcReflection.invoke(docbaseConfig, "getString", STRING_PARAM, "r_docbase_id");
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
        final ReentrantLock lock = new ReentrantLock();
        boolean released;
        Object sessionManager;
        Object dfSession;
        SessionInfo sessionInfo;
    }
}
