package com.spire.dfcbridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.dfcbridge.dto.ConnectRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for REST backend connections.
 * Tests the full stack from controller through REST service implementations.
 *
 * <p>Key tests:
 * <ul>
 *   <li>Application starts correctly with both backends available</li>
 *   <li>Connect requests with endpoint use REST backend</li>
 *   <li>Connect requests with docbroker are routed to DFC backend (which will fail in test since no DFC libs)</li>
 *   <li>Required fields (repository, username, password) still validated</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RestBackendIntegrationTest {

    // MockWebServer must be started before Spring context, hence static initializer
    private static final MockWebServer mockDocumentumRest;
    private static final int mockServerPort;

    static {
        mockDocumentumRest = new MockWebServer();
        try {
            mockDocumentumRest.start();
            mockServerPort = mockDocumentumRest.getPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MockWebServer", e);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    static void stopMockServer() throws IOException {
        if (mockDocumentumRest != null) {
            mockDocumentumRest.shutdown();
        }
    }

    @DynamicPropertySource
    static void configureRestBackend(DynamicPropertyRegistry registry) {
        registry.add("documentum.backend", () -> "rest");
        registry.add("documentum.rest.endpoint", () -> "http://localhost:" + mockServerPort);
    }

    @Test
    @Order(1)
    void statusEndpoint_reportsRestMode() throws Exception {
        // This verifies the StatusController fix - should report "rest" mode, not "degraded"
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("dfc-bridge"))
                .andExpect(jsonPath("$.backend").value("rest"))
                .andExpect(jsonPath("$.mode").value("rest"));
    }

    @Test
    @Order(2)
    void connect_worksWithEndpoint() throws Exception {
        // REST connections use 'endpoint' field to specify REST service URL
        String repositoryResponse = """
            {
                "name": "TestRepo",
                "id": "0c00000012345678"
            }
            """;

        mockDocumentumRest.enqueue(new MockResponse()
                .setBody(repositoryResponse)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Connect with endpoint - uses REST backend
        ConnectRequest request = ConnectRequest.builder()
                .endpoint("http://localhost:" + mockServerPort)
                .repository("TestRepo")
                .username("dmadmin")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.repositoryInfo.name").value("TestRepo"));

        // Verify the request was made to the mock server
        RecordedRequest recordedRequest = mockDocumentumRest.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recordedRequest, "Expected request to mock Documentum REST");
        assertTrue(recordedRequest.getPath().contains("/repositories/TestRepo"));
    }

    @Test
    @Order(3)
    void connect_withDocbrokerRoutesToDfc() throws Exception {
        // Docbroker connections are routed to DFC backend
        // Since DFC libraries aren't available in test, this returns a 503 error
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .port(1489)
                .repository("TestRepo2")
                .username("dmadmin")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DFC_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("DFC")));
    }

    @Test
    @Order(4)
    void connect_requiresEndpointOrDocbroker() throws Exception {
        // Must specify either endpoint (REST) or docbroker (DFC)
        ConnectRequest request = ConnectRequest.builder()
                .repository("TestRepo")
                .username("dmadmin")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("endpoint")));
    }

    @Test
    @Order(5)
    void connect_stillRequiresRepository() throws Exception {
        // Validation: repository is still required
        ConnectRequest request = ConnectRequest.builder()
                .endpoint("http://localhost:" + mockServerPort)
                .username("dmadmin")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void connect_stillRequiresUsername() throws Exception {
        // Validation: username is still required
        ConnectRequest request = ConnectRequest.builder()
                .endpoint("http://localhost:" + mockServerPort)
                .repository("TestRepo")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void connect_stillRequiresPassword() throws Exception {
        // Validation: password is still required
        ConnectRequest request = ConnectRequest.builder()
                .endpoint("http://localhost:" + mockServerPort)
                .repository("TestRepo")
                .username("dmadmin")
                .build();

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
