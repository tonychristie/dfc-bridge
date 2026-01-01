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
 * This service handles dmAPI commands which are server-level API calls.
 * The actual DFC method signatures are:
 * - apiGet(String method, String args) - returns a String result
 * - apiExec(String method, String args) - returns a boolean result
 * - apiSet(String method, String args, String value) - returns a boolean result
 *
 * Commands are passed as comma-separated strings (e.g., "getdocbaseconfig,session")
 * and split into method name and arguments for the DFC call.
 */
@Service
@ConditionalOnProperty(name = "documentum.backend", havingValue = "dfc", matchIfMissing = true)
public class DmApiServiceImpl implements DmApiService {

    private static final Logger log = LoggerFactory.getLogger(DmApiServiceImpl.class);

    // DFC interface name - the apiGet/apiExec/apiSet methods are defined on this interface
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
     * Invoke IDfSession.apiGet(method, args) via reflection.
     * DFC signature: String apiGet(String method, String args)
     *
     * Command format: "method,args" (e.g., "getdocbaseconfig,session")
     * Split at first comma to get method name and arguments.
     */
    private String invokeApiGet(Object dfSession, String command) throws Exception {
        String[] parts = splitCommand(command);
        String method = parts[0];
        String args = parts[1];

        Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
        Method apiGetMethod = sessionInterface.getMethod("apiGet", String.class, String.class);
        return (String) apiGetMethod.invoke(dfSession, method, args);
    }

    /**
     * Invoke IDfSession.apiExec(method, args) via reflection.
     * DFC signature: boolean apiExec(String method, String args)
     *
     * Command format: "method,args" (e.g., "fetch,session,objectId")
     * Split at first comma to get method name and arguments.
     */
    private boolean invokeApiExec(Object dfSession, String command) throws Exception {
        String[] parts = splitCommand(command);
        String method = parts[0];
        String args = parts[1];

        Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
        Method apiExecMethod = sessionInterface.getMethod("apiExec", String.class, String.class);
        return (Boolean) apiExecMethod.invoke(dfSession, method, args);
    }

    /**
     * Invoke IDfSession.apiSet(method, args, value) via reflection.
     * DFC signature: boolean apiSet(String method, String args, String value)
     *
     * Command format: "method,args,value" (e.g., "set,session,objectId,attrName,value")
     * Split at first comma to get method name, then at last comma to separate value from args.
     */
    private boolean invokeApiSet(Object dfSession, String command) throws Exception {
        // First split to get method name
        String[] parts = splitCommand(command);
        String method = parts[0];
        String argsAndValue = parts[1];

        // Then split args from value (value is after the last comma)
        int lastComma = argsAndValue.lastIndexOf(',');
        if (lastComma == -1) {
            throw new DfcBridgeException("INVALID_SET_COMMAND",
                    "Invalid apiSet command format. Expected: method,args,value (e.g., set,session,objectId,attrName,value)");
        }

        String args = argsAndValue.substring(0, lastComma);
        String value = argsAndValue.substring(lastComma + 1);

        Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
        Method apiSetMethod = sessionInterface.getMethod("apiSet", String.class, String.class, String.class);
        return (Boolean) apiSetMethod.invoke(dfSession, method, args, value);
    }

    /**
     * Split a command string into method name and arguments.
     * The method name is everything before the first comma, arguments are everything after.
     *
     * @param command the full command string (e.g., "getdocbaseconfig,session")
     * @return array of [method, args]
     */
    private String[] splitCommand(String command) {
        int firstComma = command.indexOf(',');
        if (firstComma == -1) {
            // No comma - method name only, empty args
            return new String[] { command, "" };
        }
        return new String[] {
            command.substring(0, firstComma),
            command.substring(firstComma + 1)
        };
    }
}
