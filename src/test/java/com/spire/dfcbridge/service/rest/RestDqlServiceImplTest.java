package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DqlException;
import com.spire.dfcbridge.model.QueryResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
 * Tests DQL execution via Documentum REST Services.
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
    void executeQuery_returnsResults() throws InterruptedException {
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
        assertFalse(result.isHasMore());

        // Verify GET request was made with query parameter
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertTrue(recordedRequest.getPath().contains("/repositories/TestRepo"));
        assertTrue(recordedRequest.getPath().contains("dql="));
    }

    @Test
    void executeQuery_usesCorrectAcceptHeader() throws InterruptedException {
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
                .query("SELECT * FROM dm_document")
                .build();

        // Act
        dqlService.executeQuery(request);

        // Assert - verify Accept header for Documentum media type
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("application/vnd.emc.documentum+json",
                recordedRequest.getHeader(HttpHeaders.ACCEPT));
    }

    @Test
    void executeQuery_sendsQueryAsParameter() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "entries": []
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        String testQuery = "SELECT r_object_id FROM dm_document WHERE object_name = 'test'";
        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query(testQuery)
                .build();

        // Act
        dqlService.executeQuery(request);

        // Assert - verify query is in URL as query parameter (URL-encoded)
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        String path = recordedRequest.getPath();
        assertTrue(path.contains("dql="), "Path should contain dql parameter: " + path);
        // URL-encoded query should contain parts of our query
        assertTrue(path.contains("SELECT"), "Path should contain SELECT: " + path);
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
        assertTrue(result.getColumns().isEmpty());
    }

    @Test
    void executeQuery_autoFetchesAllPages() {
        // Arrange - first page with "next" link
        String page1Response = """
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

        // Second page without "next" link (last page)
        String page2Response = """
            {
                "entries": [
                    {
                        "content": {
                            "properties": {
                                "r_object_id": "0900000180000002"
                            }
                        }
                    }
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(page1Response)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        mockWebServer.enqueue(new MockResponse()
                .setBody(page2Response)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT r_object_id FROM dm_document")
                .maxRows(1)
                .build();

        // Act
        QueryResult result = dqlService.executeQuery(request);

        // Assert - should have fetched both pages
        assertEquals(2, result.getRowCount());
        assertEquals("0900000180000001", result.getRows().get(0).get("r_object_id"));
        assertEquals("0900000180000002", result.getRows().get(1).get("r_object_id"));
        assertFalse(result.isHasMore());
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void executeQuery_tracksExecutionTime() {
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
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT r_object_id FROM dm_document")
                .build();

        // Act
        QueryResult result = dqlService.executeQuery(request);

        // Assert - execution time should be tracked
        assertTrue(result.getExecutionTimeMs() >= 0);
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
    void executeQuery_infersColumnTypes() {
        // Arrange
        String jsonResponse = """
            {
                "entries": [
                    {
                        "content": {
                            "properties": {
                                "string_attr": "text",
                                "int_attr": 42,
                                "double_attr": 3.14,
                                "bool_attr": true,
                                "repeating_attr": ["value1", "value2"]
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
                .query("SELECT * FROM dm_document")
                .build();

        // Act
        QueryResult result = dqlService.executeQuery(request);

        // Assert - check column types
        assertEquals(5, result.getColumns().size());

        QueryResult.ColumnInfo stringCol = result.getColumns().stream()
                .filter(c -> "string_attr".equals(c.getName())).findFirst().orElseThrow();
        assertEquals("STRING", stringCol.getType());
        assertFalse(stringCol.isRepeating());

        QueryResult.ColumnInfo intCol = result.getColumns().stream()
                .filter(c -> "int_attr".equals(c.getName())).findFirst().orElseThrow();
        assertEquals("INTEGER", intCol.getType());

        QueryResult.ColumnInfo doubleCol = result.getColumns().stream()
                .filter(c -> "double_attr".equals(c.getName())).findFirst().orElseThrow();
        assertEquals("DOUBLE", doubleCol.getType());

        QueryResult.ColumnInfo boolCol = result.getColumns().stream()
                .filter(c -> "bool_attr".equals(c.getName())).findFirst().orElseThrow();
        assertEquals("BOOLEAN", boolCol.getType());

        QueryResult.ColumnInfo repeatingCol = result.getColumns().stream()
                .filter(c -> "repeating_attr".equals(c.getName())).findFirst().orElseThrow();
        assertTrue(repeatingCol.isRepeating());
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
