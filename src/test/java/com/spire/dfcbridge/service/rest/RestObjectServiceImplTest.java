package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.CreateObjectRequest;
import com.spire.dfcbridge.dto.UpdateObjectRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.exception.ObjectNotFoundException;
import com.spire.dfcbridge.model.ObjectInfo;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RestObjectServiceImpl.
 * Tests object operations via Documentum REST Services.
 */
class RestObjectServiceImplTest {

    private MockWebServer mockWebServer;
    private RestObjectServiceImpl objectService;
    private RestSessionServiceImpl mockSessionService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Create a mock session service that returns a session with WebClient
        mockSessionService = new TestableRestSessionService(mockWebServer);
        objectService = new RestObjectServiceImpl(mockSessionService);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ==================== getCabinets Tests ====================

    @Test
    void getCabinets_returnsCabinetList() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "entries": [
                    {
                        "id": "http://server/repositories/TestRepo/cabinets/0c04719980000101",
                        "title": "System",
                        "summary": "dm_cabinet 0c04719980000101"
                    },
                    {
                        "id": "http://server/repositories/TestRepo/cabinets/0c04719980000102",
                        "title": "Temp",
                        "summary": "dm_cabinet 0c04719980000102"
                    }
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        List<ObjectInfo> cabinets = objectService.getCabinets("test-session");

        // Assert
        assertNotNull(cabinets);
        assertEquals(2, cabinets.size());
        assertEquals("System", cabinets.get(0).getName());
        assertEquals("0c04719980000101", cabinets.get(0).getObjectId());
        assertEquals("dm_cabinet", cabinets.get(0).getType());
        assertEquals("Temp", cabinets.get(1).getName());

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains("/repositories/TestRepo/cabinets"));
    }

    @Test
    void getCabinets_returnsEmptyListWhenNoCabinets() {
        // Arrange
        String jsonResponse = """
            {
                "entries": []
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        List<ObjectInfo> cabinets = objectService.getCabinets("test-session");

        // Assert
        assertNotNull(cabinets);
        assertTrue(cabinets.isEmpty());
    }

    // ==================== getObject Tests ====================

    @Test
    void getObject_returnsObject() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "content": {
                    "properties": {
                        "r_object_id": "0900000180000001",
                        "object_name": "Test Document",
                        "r_object_type": "dm_document",
                        "title": "My Test Doc"
                    }
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        ObjectInfo object = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(object);
        assertEquals("0900000180000001", object.getObjectId());
        assertEquals("Test Document", object.getName());
        assertEquals("dm_document", object.getType());
        assertEquals("My Test Doc", object.getAttributes().get("title"));

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains("/repositories/TestRepo/objects/0900000180000001"));
    }

    @Test
    void getObject_handles404() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\": \"Object not found\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                () -> objectService.getObject("test-session", "0900000180000001"));

        assertTrue(exception.getMessage().contains("0900000180000001"));
    }

    @Test
    void getObject_handlesServerError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"message\": \"Internal Server Error\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        DfcBridgeException exception = assertThrows(DfcBridgeException.class,
                () -> objectService.getObject("test-session", "0900000180000001"));

        assertTrue(exception.getMessage().contains("Failed to get object"));
    }

    // ==================== listFolderContentsById Tests ====================

    @Test
    void listFolderContentsById_returnsFolderContents() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "entries": [
                    {
                        "id": "http://server/repositories/TestRepo/objects/0900000180000001",
                        "title": "Document1.txt",
                        "summary": "dm_document 0900000180000001"
                    },
                    {
                        "id": "http://server/repositories/TestRepo/folders/0b00000180000001",
                        "title": "Subfolder",
                        "summary": "dm_folder 0b00000180000001"
                    }
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        List<ObjectInfo> contents = objectService.listFolderContentsById("test-session", "0c04719980000101");

        // Assert
        assertNotNull(contents);
        assertEquals(2, contents.size());
        assertEquals("Document1.txt", contents.get(0).getName());
        assertEquals("dm_document", contents.get(0).getType());
        assertEquals("Subfolder", contents.get(1).getName());
        assertEquals("dm_folder", contents.get(1).getType());

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains("/repositories/TestRepo/folders/0c04719980000101/objects"));
    }

    @Test
    void listFolderContentsById_handles404() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\": \"Folder not found\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                () -> objectService.listFolderContentsById("test-session", "invalid-folder"));

        assertTrue(exception.getMessage().contains("Folder not found"));
    }

    // ==================== Version Control Operations Tests ====================

    @Test
    void checkout_locksObject() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "content": {
                    "properties": {
                        "r_object_id": "0900000180000001",
                        "object_name": "Test Document",
                        "r_object_type": "dm_document",
                        "r_lock_owner": "testuser"
                    }
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        ObjectInfo result = objectService.checkout("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        assertEquals("0900000180000001", result.getObjectId());
        assertEquals("testuser", result.getAttributes().get("r_lock_owner"));

        // Verify request - should be PUT to lock endpoint
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("PUT", request.getMethod());
        assertTrue(request.getPath().contains("/repositories/TestRepo/objects/0900000180000001/lock"));
    }

    @Test
    void checkout_handles404() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\": \"Object not found\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                () -> objectService.checkout("test-session", "0900000180000001"));

        assertTrue(exception.getMessage().contains("0900000180000001"));
    }

    @Test
    void cancelCheckout_unlocksObject() throws InterruptedException {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204));

        // Act
        objectService.cancelCheckout("test-session", "0900000180000001");

        // Verify request - should be DELETE to lock endpoint
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertTrue(request.getPath().contains("/repositories/TestRepo/objects/0900000180000001/lock"));
    }

    @Test
    void cancelCheckout_handles404() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\": \"Object not found\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                () -> objectService.cancelCheckout("test-session", "0900000180000001"));

        assertTrue(exception.getMessage().contains("0900000180000001"));
    }

    @Test
    void checkin_createsNewVersion() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "content": {
                    "properties": {
                        "r_object_id": "0900000180000002",
                        "object_name": "Test Document",
                        "r_object_type": "dm_document",
                        "r_version_label": "1.1"
                    }
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        ObjectInfo result = objectService.checkin("test-session", "0900000180000001", "1.1");

        // Assert
        assertNotNull(result);
        assertEquals("0900000180000002", result.getObjectId());
        assertEquals("1.1", result.getAttributes().get("r_version_label"));

        // Verify request - should be POST to versions endpoint
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().contains("/repositories/TestRepo/objects/0900000180000001/versions"));
    }

    @Test
    void checkin_handles404() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\": \"Object not found\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                () -> objectService.checkin("test-session", "0900000180000001", "CURRENT"));

        assertTrue(exception.getMessage().contains("0900000180000001"));
    }

    // ==================== createObject Tests ====================

    @Test
    void createObject_createsDocument() throws InterruptedException {
        // Arrange - first call to resolve folder ID
        String folderResponse = """
            {
                "entries": [
                    {
                        "id": "http://server/repositories/TestRepo/folders/0c04719980000101"
                    }
                ]
            }
            """;

        String createResponse = """
            {
                "content": {
                    "properties": {
                        "r_object_id": "0900000180000001",
                        "object_name": "NewDocument.txt",
                        "r_object_type": "dm_document"
                    }
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(folderResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        mockWebServer.enqueue(new MockResponse()
                .setBody(createResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        CreateObjectRequest request = CreateObjectRequest.builder()
                .sessionId("test-session")
                .objectType("dm_document")
                .objectName("NewDocument.txt")
                .folderPath("/Temp")
                .build();

        // Act
        ObjectInfo result = objectService.createObject("test-session", request);

        // Assert
        assertNotNull(result);
        assertEquals("0900000180000001", result.getObjectId());
        assertEquals("NewDocument.txt", result.getName());
        assertEquals("dm_document", result.getType());

        // Verify second request was POST to documents endpoint
        mockWebServer.takeRequest(); // first request - folder resolution
        RecordedRequest createRequest = mockWebServer.takeRequest();
        assertEquals("POST", createRequest.getMethod());
        assertTrue(createRequest.getPath().contains("/documents"));
    }

    @Test
    void createObject_createsCabinet() throws InterruptedException {
        // Arrange
        String createResponse = """
            {
                "content": {
                    "properties": {
                        "r_object_id": "0c04719980000105",
                        "object_name": "NewCabinet",
                        "r_object_type": "dm_cabinet"
                    }
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(createResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        CreateObjectRequest request = CreateObjectRequest.builder()
                .sessionId("test-session")
                .objectType("dm_cabinet")
                .objectName("NewCabinet")
                .build();

        // Act
        ObjectInfo result = objectService.createObject("test-session", request);

        // Assert
        assertNotNull(result);
        assertEquals("0c04719980000105", result.getObjectId());
        assertEquals("dm_cabinet", result.getType());

        // Verify request was POST to cabinets endpoint
        RecordedRequest createRequest = mockWebServer.takeRequest();
        assertEquals("POST", createRequest.getMethod());
        assertTrue(createRequest.getPath().contains("/cabinets"));
        assertFalse(createRequest.getPath().contains("/folders"));
    }

    @Test
    void createObject_usesDirectFolderIdWhenProvided() throws InterruptedException {
        // Arrange
        String createResponse = """
            {
                "content": {
                    "properties": {
                        "r_object_id": "0900000180000001",
                        "object_name": "NewDocument.txt",
                        "r_object_type": "dm_document"
                    }
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(createResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Use a 16-char hex ID directly (no folder resolution needed)
        CreateObjectRequest request = CreateObjectRequest.builder()
                .sessionId("test-session")
                .objectType("dm_document")
                .objectName("NewDocument.txt")
                .folderPath("0c04719980000101")
                .build();

        // Act
        ObjectInfo result = objectService.createObject("test-session", request);

        // Assert
        assertNotNull(result);

        // Only one request should be made (no folder resolution)
        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest createRequest = mockWebServer.takeRequest();
        assertTrue(createRequest.getPath().contains("/folders/0c04719980000101/documents"));
    }

    @Test
    void createObject_requiresFolderPathForDocuments() {
        // Arrange
        CreateObjectRequest request = CreateObjectRequest.builder()
                .sessionId("test-session")
                .objectType("dm_document")
                .objectName("NewDocument.txt")
                .folderPath(null) // No folder path
                .build();

        // Act & Assert
        DfcBridgeException exception = assertThrows(DfcBridgeException.class,
                () -> objectService.createObject("test-session", request));

        assertTrue(exception.getMessage().contains("Folder path is required"));
    }

    // ==================== deleteObject Tests ====================

    @Test
    void deleteObject_deletesSelectedVersion() throws InterruptedException {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204));

        // Act
        objectService.deleteObject("test-session", "0900000180000001", false);

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertTrue(request.getPath().contains("/repositories/TestRepo/objects/0900000180000001"));
        assertTrue(request.getPath().contains("del-version=selected"));
    }

    @Test
    void deleteObject_deletesAllVersions() throws InterruptedException {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204));

        // Act
        objectService.deleteObject("test-session", "0900000180000001", true);

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertTrue(request.getPath().contains("del-version=all"));
    }

    @Test
    void deleteObject_handles404() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\": \"Object not found\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                () -> objectService.deleteObject("test-session", "0900000180000001", false));

        assertTrue(exception.getMessage().contains("0900000180000001"));
    }

    // ==================== updateObject Tests ====================

    @Test
    void updateObject_updatesAttributes() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "content": {
                    "properties": {
                        "r_object_id": "0900000180000001",
                        "object_name": "Updated Name",
                        "r_object_type": "dm_document",
                        "title": "New Title"
                    }
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("object_name", "Updated Name");
        attrs.put("title", "New Title");

        UpdateObjectRequest request = UpdateObjectRequest.builder()
                .sessionId("test-session")
                .attributes(attrs)
                .build();

        // Act
        ObjectInfo result = objectService.updateObject("test-session", "0900000180000001", request);

        // Assert
        assertNotNull(result);
        assertEquals("Updated Name", result.getName());
        assertEquals("New Title", result.getAttributes().get("title"));

        // Verify request
        RecordedRequest recorded = mockWebServer.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertTrue(recorded.getPath().contains("/repositories/TestRepo/objects/0900000180000001"));
    }

    // ==================== Error Handling Tests ====================

    @Test
    void handlesWebClientResponseException() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"message\": \"Bad Request\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        DfcBridgeException exception = assertThrows(DfcBridgeException.class,
                () -> objectService.getCabinets("test-session"));

        assertTrue(exception.getMessage().contains("Failed to get cabinets"));
    }

    @Test
    void executeApi_throwsNotSupported() {
        // Act & Assert
        DfcBridgeException exception = assertThrows(DfcBridgeException.class,
                () -> objectService.executeApi(null));

        // The exception message should indicate REST doesn't support direct API execution
        assertTrue(exception.getMessage().contains("DFC") ||
                   exception.getMessage().contains("REST") ||
                   exception.getCode().equals("NOT_SUPPORTED"));
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
