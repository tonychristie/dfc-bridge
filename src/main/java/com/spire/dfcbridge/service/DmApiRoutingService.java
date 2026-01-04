package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.DmApiRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.service.impl.DmApiServiceImpl;
import com.spire.dfcbridge.service.rest.RestDmApiServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Routes dmAPI operations to the appropriate backend (DFC or REST) based on the session type.
 *
 * <p>This service determines which dmAPI implementation to use by checking the session's
 * backend type via {@link SessionRoutingService#getSessionBackendType(String)}.
 */
@Service
@Primary
public class DmApiRoutingService implements DmApiService {

    private static final Logger log = LoggerFactory.getLogger(DmApiRoutingService.class);

    private final SessionRoutingService sessionRoutingService;
    private final DmApiServiceImpl dfcDmApiService;
    private final RestDmApiServiceImpl restDmApiService;

    public DmApiRoutingService(
            SessionRoutingService sessionRoutingService,
            @Nullable DmApiServiceImpl dfcDmApiService,
            @Nullable RestDmApiServiceImpl restDmApiService) {
        this.sessionRoutingService = sessionRoutingService;
        this.dfcDmApiService = dfcDmApiService;
        this.restDmApiService = restDmApiService;

        log.info("dmAPI routing initialized - DFC: {}, REST: {}",
                dfcDmApiService != null ? "available" : "unavailable",
                restDmApiService != null ? "available" : "unavailable");
    }

    @Override
    public ApiResponse execute(DmApiRequest request) {
        String backendType = sessionRoutingService.getSessionBackendType(request.getSessionId());

        if ("rest".equals(backendType)) {
            if (restDmApiService == null) {
                throw new DfcBridgeException("DMAPI_ERROR",
                        "REST dmAPI service is not available");
            }
            log.debug("Routing dmAPI command to REST backend for session {}", request.getSessionId());
            return restDmApiService.execute(request);
        } else if ("dfc".equals(backendType)) {
            if (dfcDmApiService == null) {
                throw new DfcBridgeException("DMAPI_ERROR",
                        "DFC dmAPI service is not available");
            }
            log.debug("Routing dmAPI command to DFC backend for session {}", request.getSessionId());
            return dfcDmApiService.execute(request);
        } else {
            throw new DfcBridgeException("DMAPI_ERROR",
                    "Unknown session backend type: " + backendType);
        }
    }
}
