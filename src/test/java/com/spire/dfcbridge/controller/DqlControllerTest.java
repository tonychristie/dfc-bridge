package com.spire.dfcbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DqlException;
import com.spire.dfcbridge.exception.SessionNotFoundException;
import com.spire.dfcbridge.model.QueryResult;
import com.spire.dfcbridge.service.DqlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DqlController.class)
class DqlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DqlService dqlService;

    @Test
    void testExecuteQuery_Success() throws Exception {
        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session-123")
                .query("SELECT r_object_id, object_name FROM dm_document")
                .maxRows(100)
                .build();

        QueryResult result = QueryResult.builder()
                .columns(List.of(
                        QueryResult.ColumnInfo.builder()
                                .name("r_object_id")
                                .type("ID")
                                .length(16)
                                .repeating(false)
                                .build(),
                        QueryResult.ColumnInfo.builder()
                                .name("object_name")
                                .type("STRING")
                                .length(255)
                                .repeating(false)
                                .build()
                ))
                .rows(List.of(
                        Map.of("r_object_id", "0901234567890123", "object_name", "test.txt")
                ))
                .rowCount(1)
                .hasMore(false)
                .executionTimeMs(50)
                .build();

        when(dqlService.executeQuery(any(DqlRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.columns[0].name").value("r_object_id"))
                .andExpect(jsonPath("$.rows[0].object_name").value("test.txt"));
    }

    @Test
    void testExecuteQuery_SessionNotFound() throws Exception {
        DqlRequest request = DqlRequest.builder()
                .sessionId("nonexistent")
                .query("SELECT * FROM dm_document")
                .build();

        when(dqlService.executeQuery(any(DqlRequest.class)))
                .thenThrow(new SessionNotFoundException("nonexistent"));

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void testExecuteQuery_DqlError() throws Exception {
        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session-123")
                .query("SELECT * FROM invalid_type")
                .build();

        when(dqlService.executeQuery(any(DqlRequest.class)))
                .thenThrow(new DqlException("Unknown type: invalid_type"));

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DQL_ERROR"));
    }

    @Test
    void testExecuteQuery_ValidationError() throws Exception {
        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session-123")
                // Missing required query field
                .build();

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
