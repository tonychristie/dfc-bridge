package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.config.backend.BackendProperties;
import com.spire.dfcbridge.config.backend.BackendType;
import com.spire.dfcbridge.dto.ConnectRequest;
import com.spire.dfcbridge.dto.ConnectResponse;
import com.spire.dfcbridge.exception.ConnectionException;
import com.spire.dfcbridge.exception.SessionNotFoundException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RestSessionServiceImpl.
 */
class RestSessionServiceImplTest {

    private MockWebServer mockWebServer;
    private RestSessionServiceImpl sessionService;
    private BackendProperties backendProperties;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        backendProperties = new BackendProperties();
        backendProperties.setBackend(BackendType.REST);
        backendProperties.getRest().setEndpoint(mockWebServer.url("/").toString());
        backendProperties.getRest().setTimeoutSeconds(5);

        sessionService = new RestSessionServiceImpl(backendProperties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void connect_successfulConnection() {
        // Arrange
        String jsonResponse = """
            {
                "id": 1,
                "name": "TestRepo",
                "description": "Test Repository",
                "servers": [
                    {
                        "name": "cs01",
                        "host": "docbroker.example.com",
                        "version": "21.4",
                        "docbroker": "docbroker.example.com"
                    }
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConnectRequest request = ConnectRequest.builder()
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build();

        // Act
        ConnectResponse response = sessionService.connect(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getSessionId());
        assertTrue(response.getSessionId().startsWith("rest-"));
        assertNotNull(response.getRepositoryInfo());
        assertEquals("TestRepo", response.getRepositoryInfo().getName());
        assertEquals("21.4", response.getRepositoryInfo().getServerVersion());
    }

    @Test
    void connect_authenticationFailure() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"message\": \"Unauthorized\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConnectRequest request = ConnectRequest.builder()
                .repository("TestRepo")
                .username("wronguser")
                .password("wrongpassword")
                .build();

        // Act & Assert
        ConnectionException exception = assertThrows(ConnectionException.class,
                () -> sessionService.connect(request));
        assertTrue(exception.getMessage().contains("Authentication failed"));
    }

    @Test
    void connect_repositoryNotFound() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\": \"Repository not found\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConnectRequest request = ConnectRequest.builder()
                .repository("NonExistentRepo")
                .username("dmadmin")
                .password("password")
                .build();

        // Act & Assert
        ConnectionException exception = assertThrows(ConnectionException.class,
                () -> sessionService.connect(request));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void disconnect_removesSession() {
        // Arrange - first connect
        String jsonResponse = """
            {
                "id": 1,
                "name": "TestRepo"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConnectRequest request = ConnectRequest.builder()
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build();

        ConnectResponse response = sessionService.connect(request);
        String sessionId = response.getSessionId();

        // Verify session exists
        assertTrue(sessionService.isSessionValid(sessionId));

        // Act
        sessionService.disconnect(sessionId);

        // Assert
        assertFalse(sessionService.isSessionValid(sessionId));
    }

    @Test
    void getSessionInfo_returnsInfo() {
        // Arrange
        String jsonResponse = """
            {
                "id": 1,
                "name": "TestRepo",
                "servers": [{"version": "21.4"}]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConnectRequest request = ConnectRequest.builder()
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build();

        ConnectResponse response = sessionService.connect(request);

        // Act
        var sessionInfo = sessionService.getSessionInfo(response.getSessionId());

        // Assert
        assertNotNull(sessionInfo);
        assertEquals("TestRepo", sessionInfo.getRepository());
        assertEquals("dmadmin", sessionInfo.getUser());
        assertTrue(sessionInfo.isConnected());
    }

    @Test
    void getSessionInfo_throwsForInvalidSession() {
        // Act & Assert
        assertThrows(SessionNotFoundException.class,
                () -> sessionService.getSessionInfo("invalid-session-id"));
    }

    @Test
    void isSessionValid_returnsFalseForInvalidSession() {
        // Act & Assert
        assertFalse(sessionService.isSessionValid("nonexistent-session"));
    }

    @Test
    void touchSession_updatesLastActivity() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "id": 1,
                "name": "TestRepo"
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ConnectRequest request = ConnectRequest.builder()
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build();

        ConnectResponse response = sessionService.connect(request);
        var initialActivity = sessionService.getSessionInfo(response.getSessionId()).getLastActivity();

        // Wait a bit
        Thread.sleep(10);

        // Act
        sessionService.touchSession(response.getSessionId());

        // Assert
        var newActivity = sessionService.getSessionInfo(response.getSessionId()).getLastActivity();
        assertTrue(newActivity.isAfter(initialActivity));
    }
}
