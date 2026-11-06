package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.dto.ApiResponse;
import com.spirecentral.dfcbridge.dto.DmApiRequest;
import com.spirecentral.dfcbridge.exception.DfcBridgeException;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.service.DmApiService;
import com.spirecentral.dfcbridge.util.DfcExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DFC implementation of DmApiService using reflection to call DFC session API methods.
 *
 * <p>This service handles dmAPI commands which are server-level API calls.
 * The actual DFC method signatures are:
 * <ul>
 *   <li>apiGet(String method, String args) - returns a String result</li>
 *   <li>apiExec(String method, String args) - returns a boolean result</li>
 *   <li>apiSet(String method, String args, String value) - returns a boolean result</li>
 * </ul>
 *
 * <p>Commands are passed as comma-separated strings (e.g., "getdocbaseconfig,session")
 * and split into method name and arguments for the DFC call.
 *
 */
@Service
public class DmApiServiceImpl implements DmApiService {

    private static final Logger log = LoggerFactory.getLogger(DmApiServiceImpl.class);

    // DFC interface name - the apiGet/apiExec/apiSet methods are defined on this interface
    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";

    // Traditional dmAPI session placeholders that should be stripped from commands
    // These include: session, c (current session), s0-s99 (numbered sessions)
    private static final Set<String> SESSION_PLACEHOLDERS = Set.of("session", "c");
    private static final Pattern SESSION_NUMBER_PATTERN = Pattern.compile("^s\\d{1,2}$", Pattern.CASE_INSENSITIVE);

    // UUID pattern for dfc-bridge session IDs
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

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
        return sessionService.withSession(request.getSessionId(), dfSession -> {
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
                        result = invokeApiSet(dfSession, command, request.getValue());
                        resultType = "Boolean";
                        break;
                    default:
                        throw new DfcBridgeException("INVALID_API_TYPE",
                                "Invalid API type: " + apiType + ". Must be 'get', 'exec', or 'set'",
                                HttpStatus.BAD_REQUEST);
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
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("DMAPI_ERROR",
                        "Failed to execute dmAPI: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
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

        Method apiGetMethod = sessionInterface.getMethod("apiGet", String.class, String.class);
        log.debug("Found apiGet method: {} (declared in {})",
                apiGetMethod, apiGetMethod.getDeclaringClass().getName());

        String result = (String) apiGetMethod.invoke(dfSession, method, args);
        log.debug("apiGet returned: '{}'", result);
        return result;
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

        Method apiExecMethod = sessionInterface.getMethod("apiExec", String.class, String.class);
        log.debug("Found apiExec method: {} (declared in {})",
                apiExecMethod, apiExecMethod.getDeclaringClass().getName());

        boolean result = (Boolean) apiExecMethod.invoke(dfSession, method, args);
        log.debug("apiExec returned: {}", result);
        return result;
    }

    /**
     * Invoke IDfSession.apiSet(method, args, value) via reflection.
     * DFC signature: boolean apiSet(String method, String args, String value)
     *
     * Command format: "method,args" with the value passed separately
     * (e.g., command "set,session,objectId,attrName" and value "Smith, John").
     * Split at first comma to get method name and arguments.
     */
    private boolean invokeApiSet(Object dfSession, String command, String value) throws Exception {
        if (value == null) {
            throw new DfcBridgeException("INVALID_SET_COMMAND",
                    "A set command needs a value",
                    HttpStatus.BAD_REQUEST);
        }

        String[] parts = splitCommand(command);
        String method = parts[0];
        String args = parts[1];

        log.debug("invokeApiSet: method='{}', args='{}', value='{}'", method, args, value);

        Class<?> sessionInterface = Class.forName(DFC_SESSION_IFACE);
        log.debug("Looking up apiSet on interface: {}", sessionInterface.getName());

        Method apiSetMethod = sessionInterface.getMethod("apiSet", String.class, String.class, String.class);
        log.debug("Found apiSet method: {} (declared in {})",
                apiSetMethod, apiSetMethod.getDeclaringClass().getName());

        boolean result = (Boolean) apiSetMethod.invoke(dfSession, method, args, value);
        log.debug("apiSet returned: {}", result);
        return result;
    }

    /**
     * Split a command string into method name and arguments.
     * The method name is everything before the first comma, arguments are everything after.
     * Session identifiers are stripped from the arguments.
     *
     * Traditional dmAPI commands include session identifiers like:
     * - "session" - placeholder for current session
     * - "c" - current session
     * - "s0", "s1", etc. - numbered session handles
     * - UUID - dfc-bridge session ID (e.g., "2923435e-94fa-4b47-9c1d-af84c781f2db")
     *
     * Since DFC's apiGet/apiExec/apiSet methods are already called on the session object,
     * these identifiers are redundant and cause DM_API_E_BADID errors if passed through.
     *
     * @param command the full command string (e.g., "dump,s0,0904719980000230")
     * @return array of [method, args] with session identifier stripped
     */
    private String[] splitCommand(String command) {
        int firstComma = command.indexOf(',');
        if (firstComma == -1) {
            // No comma - method name only, empty args
            return new String[] { command, "" };
        }

        String method = command.substring(0, firstComma);
        String args = command.substring(firstComma + 1);

        // Strip session identifier from the beginning of args if present
        args = stripSessionIdentifier(args);

        return new String[] { method, args };
    }

    /**
     * Strip a session identifier from the beginning of an arguments string.
     *
     * Session identifiers can be:
     * - "session" - placeholder keyword
     * - "c" - current session shorthand
     * - "s0" through "s99" - numbered session handles
     * - UUID - dfc-bridge session ID format
     *
     * Examples:
     * - "session,0904719980000230" -> "0904719980000230"
     * - "s0,0904719980000230" -> "0904719980000230"
     * - "2923435e-94fa-4b47-9c1d-af84c781f2db,0904719980000230" -> "0904719980000230"
     * - "0904719980000230" -> "0904719980000230" (no session identifier)
     * - "dmadmin" -> "dmadmin" (not a session identifier)
     *
     * @param args the arguments string, potentially starting with a session identifier
     * @return the arguments with any leading session identifier removed
     */
    String stripSessionIdentifier(String args) {
        if (args == null || args.isEmpty()) {
            return args;
        }

        // Find the first comma to isolate the potential session identifier
        int firstComma = args.indexOf(',');
        String potentialSessionId;
        String remainder;

        if (firstComma == -1) {
            // Single argument - check if it's a session identifier
            potentialSessionId = args;
            remainder = "";
        } else {
            potentialSessionId = args.substring(0, firstComma);
            remainder = args.substring(firstComma + 1);
        }

        // Check if the first argument is a session identifier
        if (isSessionIdentifier(potentialSessionId)) {
            log.debug("Stripped session identifier '{}' from dmAPI args", potentialSessionId);
            return remainder;
        }

        // Not a session identifier - return original args
        return args;
    }

    /**
     * Check if a string is a session identifier.
     *
     * @param value the string to check
     * @return true if the value is a session identifier
     */
    boolean isSessionIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        String lowerValue = value.toLowerCase();

        // Check for known placeholders (session, c)
        if (SESSION_PLACEHOLDERS.contains(lowerValue)) {
            return true;
        }

        // Check for numbered session handles (s0, s1, ..., s99)
        if (SESSION_NUMBER_PATTERN.matcher(value).matches()) {
            return true;
        }

        // Check for UUID format (dfc-bridge session ID)
        if (UUID_PATTERN.matcher(value).matches()) {
            return true;
        }

        return false;
    }
}
