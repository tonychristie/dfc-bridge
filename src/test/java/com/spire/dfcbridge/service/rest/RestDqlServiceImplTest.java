package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DqlException;
import com.spire.dfcbridge.model.QueryResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RestDqlServiceImpl.
 */
class RestDqlServiceImplTest {

    private MockWebServer mockWebServer;
    private RestDqlServiceImpl dqlService;
    private RestSessionServiceImpl mockSessionService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Create a mock session service that returns a session with WebClient
        mockSessionService = new TestableRestSessionService(mockWebServer);
        dqlService = new RestDqlServiceImpl(mockSessionService);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void executeQuery_returnsResults() {
        // Arrange
        String jsonResponse = """
            {
                "entries": [
                    {
                        "content": {
                            "properties": {
                                "r_object_id": "0900000180000001",
                                "object_name": "Test Document",
                                "r_object_type": "dm_document"
                            }
                        }
                    },
                    {
                        "content": {
                            "properties": {
                                "r_object_id": "0900000180000002",
                                "object_name": "Another Document",
                                "r_object_type": "dm_document"
                            }
                        }
                    }
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT r_object_id, object_name FROM dm_document")
                .maxRows(100)
                .build();

        // Act
        QueryResult result = dqlService.executeQuery(request);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumns().size());
        assertEquals("0900000180000001", result.getRows().get(0).get("r_object_id"));
        assertEquals("Test Document", result.getRows().get(0).get("object_name"));
    }

    @Test
    void executeQuery_handlesEmptyResults() {
        // Arrange
        String jsonResponse = """
            {
                "entries": []
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT * FROM dm_document WHERE 1=0")
                .build();

        // Act
        QueryResult result = dqlService.executeQuery(request);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getRowCount());
        assertTrue(result.getRows().isEmpty());
    }

    @Test
    void executeQuery_handlesPagination() {
        // Arrange
        String jsonResponse = """
            {
                "entries": [
                    {
                        "content": {
                            "properties": {
                                "r_object_id": "0900000180000001"
                            }
                        }
                    }
                ],
                "links": [
                    {"rel": "next", "href": "/next-page"}
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT r_object_id FROM dm_document")
                .maxRows(1)
                .build();

        // Act
        QueryResult result = dqlService.executeQuery(request);

        // Assert
        assertTrue(result.isHasMore());
    }

    @Test
    void executeQuery_handlesServerError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"message\": \"Internal Server Error\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT * FROM invalid_type")
                .build();

        // Act & Assert
        assertThrows(DqlException.class, () -> dqlService.executeQuery(request));
    }

    @Test
    void executeUpdate_throwsException() {
        // Act & Assert
        DqlException exception = assertThrows(DqlException.class,
                () -> dqlService.executeUpdate("test-session", "DELETE FROM dm_document WHERE 1=0"));

        assertTrue(exception.getMessage().contains("REST"));
    }

    /**
     * Test helper that creates a RestSessionServiceImpl with a mock WebClient
     */
    private static class TestableRestSessionService extends RestSessionServiceImpl {
        private final MockWebServer mockWebServer;

        public TestableRestSessionService(MockWebServer mockWebServer) {
            super(new com.spire.dfcbridge.config.backend.BackendProperties());
            this.mockWebServer = mockWebServer;
        }

        @Override
        public RestSessionHolder getRestSession(String sessionId) {
            RestSessionHolder holder = new RestSessionHolder();
            holder.setWebClient(WebClient.builder()
                    .baseUrl(mockWebServer.url("/").toString())
                    .build());
            holder.setRepository("TestRepo");
            holder.setUsername("testuser");
            holder.setSessionInfo(com.spire.dfcbridge.model.SessionInfo.builder()
                    .sessionId(sessionId)
                    .connected(true)
                    .repository("TestRepo")
                    .user("testuser")
                    .lastActivity(Instant.now())
                    .build());
            return holder;
        }
    }
}
