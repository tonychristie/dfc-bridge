package com.spire.dfcbridge.service.impl;

import com.documentum.fc.client.IDfSession;
import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.DmApiRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.service.DfcSessionService;
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
    void apiGet_invokesMethodAndReturnsResult() {
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
        assertEquals("getdocbaseconfig,session", mockSession.getLastApiGetCommand());
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

        // Assert
        assertNull(response.getResult());
        assertEquals("String", response.getResultType());
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

    // ========== apiExec tests ==========

    @Test
    void apiExec_invokesMethodAndReturnsTrue() {
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
        assertEquals("fetch,session,0900000180000001", mockSession.getLastApiExecCommand());
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
    void apiSet_parsesCommandAndInvokesMethod() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        mockSession.setApiSetResult(true);
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        assertEquals("set,session,0900000180000001,object_name", mockSession.getLastApiSetCommand());
        assertEquals("NewName", mockSession.getLastApiSetValue());
    }

    @Test
    void apiSet_handlesValueWithCommas() {
        // Arrange - value contains commas (only last comma is separator)
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

        // Assert
        assertTrue(mockSession.isApiSetCalled());
        assertEquals("set,session,0900000180000001,title,Value with special", mockSession.getLastApiSetCommand());
        assertEquals(" chars", mockSession.getLastApiSetValue());
    }

    @Test
    void apiSet_throwsExceptionForMalformedCommand() {
        // Arrange - no comma in command
        MockDfSession mockSession = new MockDfSession();
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

        DmApiRequest request = DmApiRequest.builder()
                .sessionId("test-session")
                .apiType("set")
                .command("invalidcommandwithnocomma")
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
     */
    public static class MockDfSession implements IDfSession {
        private String apiGetResult;
        private boolean apiExecResult;
        private boolean apiSetResult;

        private boolean apiGetCalled = false;
        private boolean apiExecCalled = false;
        private boolean apiSetCalled = false;

        private String lastApiGetCommand;
        private String lastApiExecCommand;
        private String lastApiSetCommand;
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

        public String getLastApiGetCommand() {
            return lastApiGetCommand;
        }

        public String getLastApiExecCommand() {
            return lastApiExecCommand;
        }

        public String getLastApiSetCommand() {
            return lastApiSetCommand;
        }

        public String getLastApiSetValue() {
            return lastApiSetValue;
        }

        // IDfSession interface implementation methods

        @Override
        public String apiGet(String command) {
            this.apiGetCalled = true;
            this.lastApiGetCommand = command;
            return apiGetResult;
        }

        @Override
        public boolean apiExec(String command) {
            this.apiExecCalled = true;
            this.lastApiExecCommand = command;
            return apiExecResult;
        }

        @Override
        public boolean apiSet(String command, String value) {
            this.apiSetCalled = true;
            this.lastApiSetCommand = command;
            this.lastApiSetValue = value;
            return apiSetResult;
        }
    }
}
