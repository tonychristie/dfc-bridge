package com.spirecentral.dfcbridge;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocumentTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private String document;

    @BeforeEach
    void readDocument() throws Exception {
        document = get("/api-docs").body();
    }

    @Test
    void connect_listsItsOwnAndTheCommonStatuses() {
        assertEquals(Set.of("200", "400", "401", "406", "415", "500", "502", "503"),
                statuses("/api/v1/connect", "post"));
    }

    @Test
    void disconnect_listsNo404() {
        assertEquals(Set.of("204", "400", "406", "415", "500"), statuses("/api/v1/disconnect", "post"));
    }

    @Test
    void api_listsTheSessionStatuses() {
        assertEquals(Set.of("200", "400", "401", "403", "404", "406", "415", "500", "502", "503"),
                statuses("/api/v1/api", "post"));
    }

    @Test
    void statusRedirect_listsOnly302() {
        assertEquals(Set.of("302"), statuses("/status", "get"));
    }

    @Test
    void components_holdTheErrorResponseSchema() {
        Map<String, Object> schemas = JsonPath.read(document, "$.components.schemas");
        assertTrue(schemas.containsKey("ErrorResponse"));
    }

    @Test
    void missingSwaggerFile_returns404() throws Exception {
        HttpResponse<String> response = get("/swagger-ui/missing.css");

        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", JsonPath.read(response.body(), "$.code"));
    }

    private Set<String> statuses(String path, String method) {
        Map<String, Object> responses = JsonPath.read(document,
                "$.paths['" + path + "']." + method + ".responses");
        return responses.keySet();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
