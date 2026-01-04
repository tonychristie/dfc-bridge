package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.model.QueryResult;
import com.spire.dfcbridge.service.impl.DqlServiceImpl;
import com.spire.dfcbridge.service.rest.RestDqlServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Routes DQL operations to the appropriate backend (DFC or REST) based on the session type.
 *
 * <p>This service determines which DQL implementation to use by checking the session's
 * backend type via {@link SessionRoutingService#getSessionBackendType(String)}.
 */
@Service
@Primary
public class DqlRoutingService implements DqlService {

    private static final Logger log = LoggerFactory.getLogger(DqlRoutingService.class);

    private final SessionRoutingService sessionRoutingService;
    private final DqlServiceImpl dfcDqlService;
    private final RestDqlServiceImpl restDqlService;

    public DqlRoutingService(
            SessionRoutingService sessionRoutingService,
            @Nullable DqlServiceImpl dfcDqlService,
            @Nullable RestDqlServiceImpl restDqlService) {
        this.sessionRoutingService = sessionRoutingService;
        this.dfcDqlService = dfcDqlService;
        this.restDqlService = restDqlService;

        log.info("DQL routing initialized - DFC: {}, REST: {}",
                dfcDqlService != null ? "available" : "unavailable",
                restDqlService != null ? "available" : "unavailable");
    }

    @Override
    public QueryResult executeQuery(DqlRequest request) {
        String backendType = sessionRoutingService.getSessionBackendType(request.getSessionId());

        if ("rest".equals(backendType)) {
            if (restDqlService == null) {
                throw new DfcBridgeException("DQL_ERROR",
                        "REST DQL service is not available");
            }
            log.debug("Routing DQL query to REST backend for session {}", request.getSessionId());
            return restDqlService.executeQuery(request);
        } else if ("dfc".equals(backendType)) {
            if (dfcDqlService == null) {
                throw new DfcBridgeException("DQL_ERROR",
                        "DFC DQL service is not available");
            }
            log.debug("Routing DQL query to DFC backend for session {}", request.getSessionId());
            return dfcDqlService.executeQuery(request);
        } else {
            throw new DfcBridgeException("DQL_ERROR",
                    "Unknown session backend type: " + backendType);
        }
    }

    @Override
    public int executeUpdate(String sessionId, String dql) {
        String backendType = sessionRoutingService.getSessionBackendType(sessionId);

        if ("rest".equals(backendType)) {
            if (restDqlService == null) {
                throw new DfcBridgeException("DQL_ERROR",
                        "REST DQL service is not available");
            }
            log.debug("Routing DQL update to REST backend for session {}", sessionId);
            return restDqlService.executeUpdate(sessionId, dql);
        } else if ("dfc".equals(backendType)) {
            if (dfcDqlService == null) {
                throw new DfcBridgeException("DQL_ERROR",
                        "DFC DQL service is not available");
            }
            log.debug("Routing DQL update to DFC backend for session {}", sessionId);
            return dfcDqlService.executeUpdate(sessionId, dql);
        } else {
            throw new DfcBridgeException("DQL_ERROR",
                    "Unknown session backend type: " + backendType);
        }
    }
}
