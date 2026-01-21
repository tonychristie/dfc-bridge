package com.spirecentral.dfcbridge.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedirectController.
 */
class RedirectControllerTest {

    private RedirectController controller;

    @BeforeEach
    void setUp() {
        controller = new RedirectController();
    }

    @Test
    void health_returns200OkWithStatusOk() {
        ResponseEntity<Map<String, String>> response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("ok", response.getBody().get("status"));
    }

    @Test
    void status_redirectsToApiV1Status() {
        RedirectView redirectView = controller.status();

        assertEquals("/api/v1/status", redirectView.getUrl());
    }
}
