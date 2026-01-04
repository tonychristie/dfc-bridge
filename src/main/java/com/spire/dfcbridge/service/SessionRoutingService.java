package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.ConnectRequest;
import com.spire.dfcbridge.dto.ConnectResponse;
import com.spire.dfcbridge.exception.ConnectionException;
import com.spire.dfcbridge.model.SessionInfo;
import com.spire.dfcbridge.service.impl.DfcSessionServiceImpl;
import com.spire.dfcbridge.service.rest.RestSessionServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes session operations to the appropriate backend (DFC or REST) based on the connection type.
 *
 * <p>Connection type is determined by the connect request:
 * <ul>
 *   <li>If {@code endpoint} is provided → REST connection</li>
 *   <li>If {@code docbroker} is provided → DFC connection</li>
 * </ul>
 *
 * <p>After connection, this service tracks which backend each session uses and routes
 * subsequent operations (disconnect, getSessionInfo, etc.) to the correct backend.
 */
@Service
public class SessionRoutingService implements DfcSessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionRoutingService.class);

    private final DfcSessionServiceImpl dfcService;
    private final RestSessionServiceImpl restService;

    // Track which backend each session uses
    private final Map<String, BackendType> sessionBackends = new ConcurrentHashMap<>();

    private enum BackendType { DFC, REST }

    public SessionRoutingService(
            @Nullable DfcSessionServiceImpl dfcService,
            @Nullable RestSessionServiceImpl restService) {
        this.dfcService = dfcService;
        this.restService = restService;

        log.info("Session routing initialized - DFC: {}, REST: {}",
                dfcService != null ? "available" : "unavailable",
                restService != null ? "available" : "unavailable");
    }

    @Override
    public ConnectResponse connect(ConnectRequest request) {
        BackendType backend = determineBackend(request);

        log.info("Routing connect request to {} backend for repository {}",
                backend, request.getRepository());

        ConnectResponse response;
        if (backend == BackendType.REST) {
            if (restService == null) {
                throw new ConnectionException("REST backend is not available");
            }
            response = restService.connect(request);
        } else {
            if (dfcService == null) {
                throw new ConnectionException("DFC backend is not available");
            }
            response = dfcService.connect(request);
        }

        // Track which backend this session uses
        sessionBackends.put(response.getSessionId(), backend);
        return response;
    }

    @Override
    public void disconnect(String sessionId) {
        BackendType backend = sessionBackends.get(sessionId);
        if (backend == null) {
            // Try both backends
            if (dfcService != null) {
                try {
                    dfcService.disconnect(sessionId);
                    return;
                } catch (Exception ignored) {}
            }
            if (restService != null) {
                restService.disconnect(sessionId);
            }
            return;
        }

        if (backend == BackendType.REST) {
            restService.disconnect(sessionId);
        } else {
            dfcService.disconnect(sessionId);
        }
        sessionBackends.remove(sessionId);
    }

    @Override
    public SessionInfo getSessionInfo(String sessionId) {
        BackendType backend = sessionBackends.get(sessionId);
        if (backend == null) {
            // Try both backends
            if (dfcService != null) {
                try {
                    return dfcService.getSessionInfo(sessionId);
                } catch (Exception ignored) {}
            }
            if (restService != null) {
                return restService.getSessionInfo(sessionId);
            }
            throw new ConnectionException("Session not found: " + sessionId);
        }

        return backend == BackendType.REST
                ? restService.getSessionInfo(sessionId)
                : dfcService.getSessionInfo(sessionId);
    }

    @Override
    public boolean isSessionValid(String sessionId) {
        BackendType backend = sessionBackends.get(sessionId);
        if (backend == null) {
            // Check both backends
            if (dfcService != null && dfcService.isSessionValid(sessionId)) {
                return true;
            }
            return restService != null && restService.isSessionValid(sessionId);
        }

        return backend == BackendType.REST
                ? restService.isSessionValid(sessionId)
                : dfcService.isSessionValid(sessionId);
    }

    @Override
    public void touchSession(String sessionId) {
        BackendType backend = sessionBackends.get(sessionId);
        if (backend == BackendType.REST) {
            restService.touchSession(sessionId);
        } else if (backend == BackendType.DFC) {
            dfcService.touchSession(sessionId);
        }
    }

    @Override
    public Object getDfcSession(String sessionId) {
        BackendType backend = sessionBackends.get(sessionId);
        if (backend == null) {
            // Try both backends
            if (dfcService != null) {
                try {
                    return dfcService.getDfcSession(sessionId);
                } catch (Exception ignored) {}
            }
            if (restService != null) {
                return restService.getDfcSession(sessionId);
            }
            throw new ConnectionException("Session not found: " + sessionId);
        }

        return backend == BackendType.REST
                ? restService.getDfcSession(sessionId)
                : dfcService.getDfcSession(sessionId);
    }

    /**
     * Determine which backend to use based on the connect request.
     */
    private BackendType determineBackend(ConnectRequest request) {
        boolean hasEndpoint = request.getEndpoint() != null && !request.getEndpoint().isBlank();
        boolean hasDocbroker = request.getDocbroker() != null && !request.getDocbroker().isBlank();

        if (hasEndpoint && hasDocbroker) {
            throw new ConnectionException(
                    "Cannot specify both 'endpoint' (REST) and 'docbroker' (DFC). Choose one connection type.");
        }

        if (hasEndpoint) {
            return BackendType.REST;
        }

        if (hasDocbroker) {
            return BackendType.DFC;
        }

        throw new ConnectionException(
                "Either 'endpoint' (for REST connection) or 'docbroker' (for DFC connection) must be provided.");
    }

    /**
     * Get the backend type for a session.
     */
    public String getSessionBackendType(String sessionId) {
        BackendType backend = sessionBackends.get(sessionId);
        return backend != null ? backend.name().toLowerCase() : "unknown";
    }
}
