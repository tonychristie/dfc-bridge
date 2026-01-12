package com.spirecentral.dfcbridge.service;

import com.spirecentral.dfcbridge.dto.ApiResponse;
import com.spirecentral.dfcbridge.dto.DmApiRequest;

/**
 * Service for executing dmAPI commands.
 *
 * dmAPI commands are server-level API calls executed via IDfSession methods:
 * - apiGet() - returns a string result
 * - apiExec() - returns a boolean result
 * - apiSet() - returns a boolean result
 *
 * These are distinct from DFC object method invocations handled by ObjectService.
 */
public interface DmApiService {

    /**
     * Execute a dmAPI command.
     *
     * @param request the dmAPI request containing type (get/exec/set) and command
     * @return the API response with result and execution time
     */
    ApiResponse execute(DmApiRequest request);
}
