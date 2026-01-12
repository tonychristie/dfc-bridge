package com.spirecentral.dfcbridge.service.impl;

import com.documentum.fc.client.IDfSession;
import com.spirecentral.dfcbridge.dto.ApiResponse;
import com.spirecentral.dfcbridge.dto.DmApiRequest;
import com.spirecentral.dfcbridge.exception.DfcBridgeException;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DmApiServiceImpl.
 * Tests the reflection-based invocation of DFC session API methods.
 *
 * Note: DmApiServiceImpl looks up methods on the IDfSession interface via reflection.
 * Since we can't depend on DFC at test time, we define a matching interface here
 * with the same fully-qualified name that DFC uses, allowing the reflection to work.
 *
 * DFC method signatures:
 * - apiGet(String method, String args)
 * - apiExec(String method, String args)
 * - apiSet(String method, String args, String value)
 */
@ExtendWith(MockitoExtension.class)
class DmApiServiceImplTest {

    @Mock
    private DfcSessionService sessionService;

    private DmApiServiceImpl dmApiService;

    @BeforeEach
    void setUp() {
        dmApiService = new DmApiServiceImpl(sessionService);
    }

    // ========== apiGet tests ==========

    @Test
    void apiGet_splitsCommandAndStripsSessionPlaceholder() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("test-result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("getdocbaseconfig,session")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertNotNull(response);
        assertEquals("test-result", response.getResult());
        assertEquals("String", response.getResultType());
        assertTrue(response.getExecutionTimeMs() >= 0);
        assertTrue(mockSession.isApiGetCalled());
        // Verify "session" placeholder was stripped
        assertEquals("getdocbaseconfig", mockSession.getLastApiGetMethod());
        assertEquals("", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_handlesNullResult() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult(null);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("get,session,invalid")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - "session" is stripped, leaving "invalid"
        assertNull(response.getResult());
        assertEquals("String", response.getResultType());
        assertEquals("get", mockSession.getLastApiGetMethod());
        assertEquals("invalid", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_handlesNoArgs() {
        // Arrange - command with no comma (method only, empty args)
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("somemethod")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertEquals("result", response.getResult());
        assertEquals("somemethod", mockSession.getLastApiGetMethod());
        assertEquals("", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_caseInsensitiveApiType() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("GET")  // uppercase
                .command("getdocbaseconfig,session")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertEquals("result", response.getResult());
        assertTrue(mockSession.isApiGetCalled());
    }

    // ========== Session identifier stripping tests ==========

    @Test
    void apiGet_stripsNumberedSessionHandle() {
        // Arrange - traditional dmAPI format with "s0" session handle
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("dump-result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("dump,s0,0904719980000230")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - "s0" should be stripped, leaving just the object ID
        assertEquals("dump-result", response.getResult());
        assertEquals("dump", mockSession.getLastApiGetMethod());
        assertEquals("0904719980000230", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_stripsCurrentSessionHandle() {
        // Arrange - traditional dmAPI format with "c" (current session) handle
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("login");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("getlogin,c,dmadmin")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - "c" should be stripped
        assertEquals("login", response.getResult());
        assertEquals("getlogin", mockSession.getLastApiGetMethod());
        assertEquals("dmadmin", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_stripsUuidSessionId() {
        // Arrange - dfc-bridge UUID included in command
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("dump-result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("dump,2923435e-94fa-4b47-9c1d-af84c781f2db,0904719980000230")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - UUID should be stripped
        assertEquals("dump-result", response.getResult());
        assertEquals("dump", mockSession.getLastApiGetMethod());
        assertEquals("0904719980000230", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_preservesNonSessionFirstArg() {
        // Arrange - first arg is not a session identifier (e.g., docbase name)
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("config-value");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("getserverconfig,myrepo,config_key")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - "myrepo" is NOT a session identifier, so should be preserved
        assertEquals("config-value", response.getResult());
        assertEquals("getserverconfig", mockSession.getLastApiGetMethod());
        assertEquals("myrepo,config_key", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_handlesCommandWithoutSessionId() {
        // Arrange - command with real args (no session identifier)
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("getlogin,dmadmin")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - "dmadmin" is not a session identifier, so preserved
        assertEquals("result", response.getResult());
        assertEquals("getlogin", mockSession.getLastApiGetMethod());
        assertEquals("dmadmin", mockSession.getLastApiGetArgs());
    }

    @Test
    void apiGet_stripsSessionFromSingleArgCommand() {
        // Arrange - command with only "session" as arg
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("getdocbasemap,session")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - "session" stripped, leaving empty args
        assertEquals("result", response.getResult());
        assertEquals("getdocbasemap", mockSession.getLastApiGetMethod());
        assertEquals("", mockSession.getLastApiGetArgs());
    }

    @Test
    void sessionIdentifier_recognizesVariousFormats() {
        // Test the isSessionIdentifier method directly
        assertTrue(dmApiService.isSessionIdentifier("session"));
        assertTrue(dmApiService.isSessionIdentifier("SESSION"));
        assertTrue(dmApiService.isSessionIdentifier("c"));
        assertTrue(dmApiService.isSessionIdentifier("C"));
        assertTrue(dmApiService.isSessionIdentifier("s0"));
        assertTrue(dmApiService.isSessionIdentifier("s1"));
        assertTrue(dmApiService.isSessionIdentifier("s99"));
        assertTrue(dmApiService.isSessionIdentifier("S0"));
        assertTrue(dmApiService.isSessionIdentifier("2923435e-94fa-4b47-9c1d-af84c781f2db"));
        assertTrue(dmApiService.isSessionIdentifier("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"));

        // Non-session identifiers
        assertFalse(dmApiService.isSessionIdentifier("dmadmin"));
        assertFalse(dmApiService.isSessionIdentifier("0904719980000230"));
        assertFalse(dmApiService.isSessionIdentifier("myrepo"));
        assertFalse(dmApiService.isSessionIdentifier("s100"));  // Only s0-s99
        assertFalse(dmApiService.isSessionIdentifier("sessions"));
        assertFalse(dmApiService.isSessionIdentifier(""));
        assertFalse(dmApiService.isSessionIdentifier(null));
    }

    // ========== apiExec tests ==========

    @Test
    void apiExec_splitsCommandAndStripsSession() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiExecResult(true);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("exec")
                .command("fetch,session,0900000180000001")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertNotNull(response);
        assertEquals(true, response.getResult());
        assertEquals("Boolean", response.getResultType());
        assertTrue(mockSession.isApiExecCalled());
        // Verify "session" was stripped
        assertEquals("fetch", mockSession.getLastApiExecMethod());
        assertEquals("0900000180000001", mockSession.getLastApiExecArgs());
    }

    @Test
    void apiExec_invokesMethodAndReturnsFalse() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiExecResult(false);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("exec")
                .command("invalid,command")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertEquals(false, response.getResult());
        assertEquals("Boolean", response.getResultType());
    }

    @Test
    void apiExec_caseInsensitiveApiType() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiExecResult(true);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("EXEC")  // uppercase
                .command("fetch,session,0900000180000001")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertEquals(true, response.getResult());
        assertTrue(mockSession.isApiExecCalled());
    }

    // ========== apiSet tests ==========

    @Test
    void apiSet_splitsCommandAndStripsSession() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiSetResult(true);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        // Command: "set,session,objectId,attrName,value"
        // After stripping "session": method="set", args="objectId,attrName", value="NewName"
        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("set")
                .command("set,session,0900000180000001,object_name,NewName")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertNotNull(response);
        assertEquals(true, response.getResult());
        assertEquals("Boolean", response.getResultType());
        assertTrue(mockSession.isApiSetCalled());
        assertEquals("set", mockSession.getLastApiSetMethod());
        // "session" should be stripped
        assertEquals("0900000180000001,object_name", mockSession.getLastApiSetArgs());
        assertEquals("NewName", mockSession.getLastApiSetValue());
    }

    @Test
    void apiSet_handlesValueWithCommas() {
        // Arrange - value is everything after the last comma in the args portion
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiSetResult(true);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("set")
                .command("set,session,0900000180000001,title,Value with special, chars")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert - "session" is stripped
        assertTrue(mockSession.isApiSetCalled());
        assertEquals("set", mockSession.getLastApiSetMethod());
        assertEquals("0900000180000001,title,Value with special", mockSession.getLastApiSetArgs());
        assertEquals(" chars", mockSession.getLastApiSetValue());
    }

    @Test
    void apiSet_throwsExceptionForMissingValue() {
        // Arrange - command with method but no value separator in args
        MockDfSession mockSession = new MockDfSession();
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("set")
                .command("set,novalue")
                .build();

        // Act & Assert
        DfcBridgeException exception = assertThrows(DfcBridgeException.class,
                () -> dmApiService.execute(request));

        assertEquals("INVALID_SET_COMMAND", exception.getCode());
        assertTrue(exception.getMessage().contains("Invalid apiSet command format"));
    }

    @Test
    void apiSet_caseInsensitiveApiType() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiSetResult(true);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("SET")  // uppercase
                .command("set,session,0900000180000001,attr,value")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertEquals(true, response.getResult());
        assertTrue(mockSession.isApiSetCalled());
    }

    // ========== Error handling tests ==========

    @Test
    void execute_throwsExceptionForInvalidApiType() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("invalid")
                .command("some,command")
                .build();

        // Act & Assert
        DfcBridgeException exception = assertThrows(DfcBridgeException.class,
                () -> dmApiService.execute(request));

        assertEquals("INVALID_API_TYPE", exception.getCode());
        assertTrue(exception.getMessage().contains("Invalid API type"));
        assertTrue(exception.getMessage().contains("invalid"));
    }

    @Test
    void execute_throwsExceptionForNullApiType() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType(null)
                .command("some,command")
                .build();

        // Act & Assert
        assertThrows(Exception.class, () -> dmApiService.execute(request));
    }

    @Test
    void execute_wrapsReflectionExceptions() {
        // Arrange - use a mock that doesn't have the required methods
        Object invalidSession = new Object();  // no apiGet method
        when(sessionService.getDfcSession("test-session")).thenReturn(invalidSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("getdocbaseconfig,session")
                .build();

        // Act & Assert
        DfcBridgeException exception = assertThrows(DfcBridgeException.class,
                () -> dmApiService.execute(request));

        assertEquals("DMAPI_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("Failed to execute dmAPI"));
    }

    @Test
    void execute_tracksExecutionTime() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiGetResult("result");
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("get")
                .command("getdocbaseconfig,session")
                .build();

        // Act
        ApiResponse response = dmApiService.execute(request);

        // Assert
        assertTrue(response.getExecutionTimeMs() >= 0);
    }

    // ========== Mock DFC Session ==========

    /**
     * Mock session object that implements the IDfSession interface stub.
     * Used to verify reflection-based invocation works correctly.
     * The interface is defined in test sources with the same package/class name as DFC.
     *
     * Implements the actual DFC method signatures:
     * - apiGet(String method, String args)
     * - apiExec(String method, String args)
     * - apiSet(String method, String args, String value)
     */
    public static class MockDfSession implements IDfSession {
        private String apiGetResult;
        private boolean apiExecResult;
        private boolean apiSetResult;

        private boolean apiGetCalled = false;
        private boolean apiExecCalled = false;
        private boolean apiSetCalled = false;

        private String lastApiGetMethod;
        private String lastApiGetArgs;
        private String lastApiExecMethod;
        private String lastApiExecArgs;
        private String lastApiSetMethod;
        private String lastApiSetArgs;
        private String lastApiSetValue;

        // Setters for test configuration
        public void setApiGetResult(String result) {
            this.apiGetResult = result;
        }

        public void setApiExecResult(boolean result) {
            this.apiExecResult = result;
        }

        public void setApiSetResult(boolean result) {
            this.apiSetResult = result;
        }

        // Getters for verification
        public boolean isApiGetCalled() {
            return apiGetCalled;
        }

        public boolean isApiExecCalled() {
            return apiExecCalled;
        }

        public boolean isApiSetCalled() {
            return apiSetCalled;
        }

        public String getLastApiGetMethod() {
            return lastApiGetMethod;
        }

        public String getLastApiGetArgs() {
            return lastApiGetArgs;
        }

        public String getLastApiExecMethod() {
            return lastApiExecMethod;
        }

        public String getLastApiExecArgs() {
            return lastApiExecArgs;
        }

        public String getLastApiSetMethod() {
            return lastApiSetMethod;
        }

        public String getLastApiSetArgs() {
            return lastApiSetArgs;
        }

        public String getLastApiSetValue() {
            return lastApiSetValue;
        }

        // IDfSession interface implementation methods

        @Override
        public String apiGet(String method, String args) {
            this.apiGetCalled = true;
            this.lastApiGetMethod = method;
            this.lastApiGetArgs = args;
            return apiGetResult;
        }

        @Override
        public boolean apiExec(String method, String args) {
            this.apiExecCalled = true;
            this.lastApiExecMethod = method;
            this.lastApiExecArgs = args;
            return apiExecResult;
        }

        @Override
        public boolean apiSet(String method, String args, String value) {
            this.apiSetCalled = true;
            this.lastApiSetMethod = method;
            this.lastApiSetArgs = args;
            this.lastApiSetValue = value;
            return apiSetResult;
        }
    }
}
