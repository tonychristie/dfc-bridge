package com.spirecentral.dfcbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spirecentral.dfcbridge.dto.ConnectRequest;
import com.spirecentral.dfcbridge.dto.ConnectResponse;
import com.spirecentral.dfcbridge.dto.DisconnectRequest;
import com.spirecentral.dfcbridge.exception.SessionNotFoundException;
import com.spirecentral.dfcbridge.model.RepositoryInfo;
import com.spirecentral.dfcbridge.model.SessionInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DfcSessionService sessionService;

    @Test
    void testConnect_Success() throws Exception {
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .port(1489)
                .repository("TestRepo")
                .username("dmadmin")
                .password("password123")
                .build();

        ConnectResponse response = ConnectResponse.builder()
                .sessionId("test-session-123")
                .repositoryInfo(RepositoryInfo.builder()
                        .name("TestRepo")
                        .serverVersion("23.2.0")
                        .build())
                .build();

        when(sessionService.connect(any(ConnectRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("test-session-123"))
                .andExpect(jsonPath("$.repositoryInfo.name").value("TestRepo"));

        verify(sessionService).connect(any(ConnectRequest.class));
    }

    @Test
    void testConnect_ValidationError() throws Exception {
        ConnectRequest request = ConnectRequest.builder()
                .port(1489)
                // Missing required fields
                .build();

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDisconnect_Success() throws Exception {
        DisconnectRequest request = DisconnectRequest.builder()
                .sessionId("test-session-123")
                .build();

        doNothing().when(sessionService).disconnect("test-session-123");

        mockMvc.perform(post("/api/v1/disconnect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(sessionService).disconnect("test-session-123");
    }

    @Test
    void testGetSessionInfo_Success() throws Exception {
        SessionInfo sessionInfo = SessionInfo.builder()
                .sessionId("test-session-123")
                .connected(true)
                .repository("TestRepo")
                .user("dmadmin")
                .docbroker("docbroker.example.com")
                .port(1489)
                .sessionStart(Instant.now())
                .lastActivity(Instant.now())
                .build();

        when(sessionService.getSessionInfo("test-session-123")).thenReturn(sessionInfo);

        mockMvc.perform(get("/api/v1/session/test-session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("test-session-123"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.repository").value("TestRepo"));
    }

    @Test
    void testGetSessionInfo_NotFound() throws Exception {
        when(sessionService.getSessionInfo("nonexistent"))
                .thenThrow(new SessionNotFoundException("nonexistent"));

        mockMvc.perform(get("/api/v1/session/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void testIsSessionValid_True() throws Exception {
        when(sessionService.isSessionValid("test-session-123")).thenReturn(true);

        mockMvc.perform(get("/api/v1/session/test-session-123/valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void testIsSessionValid_False() throws Exception {
        when(sessionService.isSessionValid("expired-session")).thenReturn(false);

        mockMvc.perform(get("/api/v1/session/expired-session/valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
