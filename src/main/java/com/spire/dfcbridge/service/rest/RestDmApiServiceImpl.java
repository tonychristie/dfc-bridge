package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.DmApiRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.service.DmApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * REST implementation of DmApiService.
 *
 * dmAPI commands (apiGet, apiExec, apiSet) are DFC-specific and have no REST equivalent.
 * This implementation always throws an error directing users to use DFC or alternative methods.
 */
@Service
@ConditionalOnProperty(name = "documentum.backend", havingValue = "rest")
public class RestDmApiServiceImpl implements DmApiService {

    private static final Logger log = LoggerFactory.getLogger(RestDmApiServiceImpl.class);

    @Override
    public ApiResponse execute(DmApiRequest request) {
        log.warn("Attempted dmAPI call via REST backend - not supported. Command: {}",
                request.getCommand());

        throw new DfcBridgeException("NOT_SUPPORTED",
                "dmAPI commands require a DFC connection. " +
                "The REST backend does not support apiGet, apiExec, or apiSet commands. " +
                "Switch to a DFC backend or use DQL/REST endpoints instead.");
    }
}
