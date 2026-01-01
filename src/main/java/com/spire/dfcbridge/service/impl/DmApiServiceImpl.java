package com.spire.dfcbridge.service.impl;

import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.DmApiRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.service.DfcSessionService;
import com.spire.dfcbridge.service.DmApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;

/**
 * Implementation of DmApiService using reflection to call DFC session API methods.
 *
 * This service handles dmAPI commands which are server-level API calls:
 * - apiGet(command) - returns a String result
 * - apiExec(command) - returns a boolean result
 * - apiSet(command, value) - returns a boolean result
 */
@Service
@ConditionalOnProperty(name = "documentum.backend", havingValue = "dfc", matchIfMissing = true)
public class DmApiServiceImpl implements DmApiService {

    private static final Logger log = LoggerFactory.getLogger(DmApiServiceImpl.class);

    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";

    private final DfcSessionService sessionService;

    public DmApiServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public ApiResponse execute(DmApiRequest request) {
        log.debug("Executing dmAPI {}: {}", request.getApiType(), request.getCommand());

        long startTime = System.currentTimeMillis();
        Object dfSession = sessionService.getDfcSession(request.getSessionId());

        try {
            Object result;
            String resultType;

            String apiType = request.getApiType().toLowerCase();
            String command = request.getCommand();

            switch (apiType) {
                case "get":
                    result = invokeApiGet(dfSession, command);
                    resultType = "String";
                    break;
                case "exec":
                    result = invokeApiExec(dfSession, command);
                    resultType = "Boolean";
                    break;
                case "set":
                    result = invokeApiSet(dfSession, command);
                    resultType = "Boolean";
                    break;
                default:
                    throw new DfcBridgeException("INVALID_API_TYPE",
                            "Invalid API type: " + apiType + ". Must be 'get', 'exec', or 'set'");
            }

            long executionTime = System.currentTimeMillis() - startTime;

            return ApiResponse.builder()
                    .result(result)
                    .resultType(resultType)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (DfcBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("DMAPI_ERROR",
                    "Failed to execute dmAPI: " + e.getMessage(), e);
        }
    }

    /**
     * Invoke IDfSession.apiGet(command) via reflection.
     */
    private String invokeApiGet(Object dfSession, String command) throws Exception {
        Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
        Method apiGetMethod = sessionClass.getMethod("apiGet", String.class);
        return (String) apiGetMethod.invoke(dfSession, command);
    }

    /**
     * Invoke IDfSession.apiExec(command) via reflection.
     */
    private boolean invokeApiExec(Object dfSession, String command) throws Exception {
        Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
        Method apiExecMethod = sessionClass.getMethod("apiExec", String.class);
        return (Boolean) apiExecMethod.invoke(dfSession, command);
    }

    /**
     * Invoke IDfSession.apiSet(command, value) via reflection.
     *
     * For apiSet, the value is typically the last comma-separated element in the command.
     * The command format is: "set,session,objectId,attrName,value"
     */
    private boolean invokeApiSet(Object dfSession, String command) throws Exception {
        // For apiSet, DFC expects the command and value as separate parameters
        // The command string typically includes the value at the end
        // Parse out the value from the command
        int lastComma = command.lastIndexOf(',');
        if (lastComma == -1) {
            throw new DfcBridgeException("INVALID_SET_COMMAND",
                    "Invalid apiSet command format. Expected: method,session,objectId,attrName,value");
        }

        String setCommand = command.substring(0, lastComma);
        String value = command.substring(lastComma + 1);

        Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
        Method apiSetMethod = sessionClass.getMethod("apiSet", String.class, String.class);
        return (Boolean) apiSetMethod.invoke(dfSession, setCommand, value);
    }
}
