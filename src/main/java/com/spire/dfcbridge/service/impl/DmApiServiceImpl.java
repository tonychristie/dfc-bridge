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
import java.util.Arrays;
import java.util.stream.Collectors;

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
        log.debug("=== dmAPI Execute Start ===");
        log.debug("Request: apiType={}, command={}, sessionId={}",
                request.getApiType(), request.getCommand(), request.getSessionId());

        long startTime = System.currentTimeMillis();
        Object dfSession = sessionService.getDfcSession(request.getSessionId());

        // Log session object details for debugging
        logSessionDetails(dfSession);

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

            log.debug("dmAPI result: type={}, value={}, executionTime={}ms",
                    resultType, result, executionTime);
            log.debug("=== dmAPI Execute End ===");

            return ApiResponse.builder()
                    .result(result)
                    .resultType(resultType)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (DfcBridgeException e) {
            log.error("dmAPI DfcBridgeException: code={}, message={}",
                    e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("dmAPI execution failed", e);
            log.error("Exception type: {}, message: {}", e.getClass().getName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            }
            throw new DfcBridgeException("DMAPI_ERROR",
                    "Failed to execute dmAPI: " + e.getMessage(), e);
        }
    }

    /**
     * Log detailed session object information for debugging.
     * Uses reflection to understand what type of object we have and what methods are available.
     */
    private void logSessionDetails(Object dfSession) {
        if (!log.isDebugEnabled()) {
            return;
        }

        log.debug("Session object class: {}", dfSession.getClass().getName());
        log.debug("Session object superclass: {}",
                dfSession.getClass().getSuperclass() != null ?
                        dfSession.getClass().getSuperclass().getName() : "none");

        // Log implemented interfaces
        Class<?>[] interfaces = dfSession.getClass().getInterfaces();
        log.debug("Session object implements {} interfaces:", interfaces.length);
        for (Class<?> iface : interfaces) {
            log.debug("  - {}", iface.getName());
        }

        // Log available api* methods on the session object's class
        log.debug("Looking for api* methods on session object class...");
        Method[] methods = dfSession.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName().startsWith("api")) {
                String params = Arrays.stream(method.getParameterTypes())
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(", "));
                log.debug("  Found: {}({}) -> {}",
                        method.getName(), params, method.getReturnType().getSimpleName());
            }
        }

        // Also check the IDfSession interface itself
        try {
            Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
            log.debug("Looking for api* methods on IDfSession interface...");
            Method[] ifaceMethods = sessionInterface.getMethods();
            for (Method method : ifaceMethods) {
                if (method.getName().startsWith("api")) {
                    String params = Arrays.stream(method.getParameterTypes())
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining(", "));
                    log.debug("  IDfSession.{}({}) -> {}",
                            method.getName(), params, method.getReturnType().getSimpleName());
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("Could not load IDfSession interface: {}", e.getMessage());
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

        log.debug("invokeApiGet: method='{}', args='{}'", method, args);

        Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
        log.debug("Looking up apiGet on interface: {}", sessionInterface.getName());

        try {
            Method apiGetMethod = sessionInterface.getMethod("apiGet", String.class, String.class);
            log.debug("Found apiGet method: {} (declared in {})",
                    apiGetMethod, apiGetMethod.getDeclaringClass().getName());

            String result = (String) apiGetMethod.invoke(dfSession, method, args);
            log.debug("apiGet returned: '{}'", result);
            return result;
        } catch (NoSuchMethodException e) {
            log.error("NoSuchMethodException looking for apiGet(String, String) on {}",
                    sessionInterface.getName());
            log.error("Available methods on interface:");
            for (Method m : sessionInterface.getMethods()) {
                log.error("  - {}", m);
            }
            throw e;
        } catch (Exception e) {
            log.error("Error invoking apiGet: {} - {}", e.getClass().getName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            }
            throw e;
        }
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

        log.debug("invokeApiExec: method='{}', args='{}'", method, args);

        Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
        log.debug("Looking up apiExec on interface: {}", sessionInterface.getName());

        try {
            Method apiExecMethod = sessionInterface.getMethod("apiExec", String.class, String.class);
            log.debug("Found apiExec method: {} (declared in {})",
                    apiExecMethod, apiExecMethod.getDeclaringClass().getName());

            boolean result = (Boolean) apiExecMethod.invoke(dfSession, method, args);
            log.debug("apiExec returned: {}", result);
            return result;
        } catch (NoSuchMethodException e) {
            log.error("NoSuchMethodException looking for apiExec(String, String) on {}",
                    sessionInterface.getName());
            throw e;
        } catch (Exception e) {
            log.error("Error invoking apiExec: {} - {}", e.getClass().getName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            }
            throw e;
        }
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

        log.debug("invokeApiSet: method='{}', args='{}', value='{}'", method, args, value);

        Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
        log.debug("Looking up apiSet on interface: {}", sessionInterface.getName());

        try {
            Method apiSetMethod = sessionInterface.getMethod("apiSet", String.class, String.class, String.class);
            log.debug("Found apiSet method: {} (declared in {})",
                    apiSetMethod, apiSetMethod.getDeclaringClass().getName());

            boolean result = (Boolean) apiSetMethod.invoke(dfSession, method, args, value);
            log.debug("apiSet returned: {}", result);
            return result;
        } catch (NoSuchMethodException e) {
            log.error("NoSuchMethodException looking for apiSet(String, String, String) on {}",
                    sessionInterface.getName());
            throw e;
        } catch (Exception e) {
            log.error("Error invoking apiSet: {} - {}", e.getClass().getName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            }
            throw e;
        }
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
