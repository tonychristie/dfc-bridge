package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.model.QueryResult;
import com.spire.dfcbridge.service.impl.DqlServiceImpl;
import com.spire.dfcbridge.service.rest.RestDqlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DqlRoutingService.
 *
 * Verifies that DQL operations are correctly routed to the appropriate backend
 * (DFC or REST) based on the session type.
 */
@ExtendWith(MockitoExtension.class)
class DqlRoutingServiceTest {

    @Mock
    private SessionRoutingService sessionRoutingService;

    @Mock
    private DqlServiceImpl dfcDqlService;

    @Mock
    private RestDqlServiceImpl restDqlService;

    private DqlRoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new DqlRoutingService(
                sessionRoutingService, dfcDqlService, restDqlService);
    }

    @Test
    void executeQuery_routesToDfcForDfcSession() {
        // Arrange
        String sessionId = "dfc-session-123";
        DqlRequest request = new DqlRequest();
        request.setSessionId(sessionId);
        request.setQuery("SELECT r_object_id FROM dm_document");

        QueryResult expectedResult = QueryResult.builder()
                .columns(Collections.emptyList())
                .rows(Collections.emptyList())
                .rowCount(0)
                .hasMore(false)
                .executionTimeMs(100)
                .build();

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");
        when(dfcDqlService.executeQuery(request)).thenReturn(expectedResult);

        // Act
        QueryResult result = routingService.executeQuery(request);

        // Assert
        assertSame(expectedResult, result);
        verify(sessionRoutingService).getSessionBackendType(sessionId);
        verify(dfcDqlService).executeQuery(request);
        verifyNoInteractions(restDqlService);
    }

    @Test
    void executeQuery_routesToRestForRestSession() {
        // Arrange
        String sessionId = "rest-session-456";
        DqlRequest request = new DqlRequest();
        request.setSessionId(sessionId);
        request.setQuery("SELECT r_object_id FROM dm_document");

        QueryResult expectedResult = QueryResult.builder()
                .columns(Collections.emptyList())
                .rows(Collections.emptyList())
                .rowCount(5)
                .hasMore(false)
                .executionTimeMs(50)
                .build();

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");
        when(restDqlService.executeQuery(request)).thenReturn(expectedResult);

        // Act
        QueryResult result = routingService.executeQuery(request);

        // Assert
        assertSame(expectedResult, result);
        verify(sessionRoutingService).getSessionBackendType(sessionId);
        verify(restDqlService).executeQuery(request);
        verifyNoInteractions(dfcDqlService);
    }

    @Test
    void executeQuery_throwsForUnknownBackendType() {
        // Arrange
        String sessionId = "unknown-session";
        DqlRequest request = new DqlRequest();
        request.setSessionId(sessionId);
        request.setQuery("SELECT r_object_id FROM dm_document");

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("unknown");

        // Act & Assert
        DfcBridgeException exception = assertThrows(
                DfcBridgeException.class,
                () -> routingService.executeQuery(request));

        assertEquals("DQL_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("Unknown session backend type"));
    }

    @Test
    void executeUpdate_routesToDfcForDfcSession() {
        // Arrange
        String sessionId = "dfc-session-123";
        String dql = "UPDATE dm_document SET title = 'Test'";

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");
        when(dfcDqlService.executeUpdate(sessionId, dql)).thenReturn(1);

        // Act
        int result = routingService.executeUpdate(sessionId, dql);

        // Assert
        assertEquals(1, result);
        verify(dfcDqlService).executeUpdate(sessionId, dql);
        verifyNoInteractions(restDqlService);
    }

    @Test
    void executeUpdate_routesToRestForRestSession() {
        // Arrange
        String sessionId = "rest-session-456";
        String dql = "UPDATE dm_document SET title = 'Test'";

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");
        when(restDqlService.executeUpdate(sessionId, dql)).thenReturn(1);

        // Act
        int result = routingService.executeUpdate(sessionId, dql);

        // Assert
        assertEquals(1, result);
        verify(restDqlService).executeUpdate(sessionId, dql);
        verifyNoInteractions(dfcDqlService);
    }

    @Test
    void executeQuery_throwsWhenDfcServiceUnavailable() {
        // Arrange - Create routing service with no DFC service
        routingService = new DqlRoutingService(sessionRoutingService, null, restDqlService);

        String sessionId = "dfc-session-123";
        DqlRequest request = new DqlRequest();
        request.setSessionId(sessionId);
        request.setQuery("SELECT r_object_id FROM dm_document");

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");

        // Act & Assert
        DfcBridgeException exception = assertThrows(
                DfcBridgeException.class,
                () -> routingService.executeQuery(request));

        assertEquals("DQL_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("DFC DQL service is not available"));
    }

    @Test
    void executeQuery_throwsWhenRestServiceUnavailable() {
        // Arrange - Create routing service with no REST service
        routingService = new DqlRoutingService(sessionRoutingService, dfcDqlService, null);

        String sessionId = "rest-session-456";
        DqlRequest request = new DqlRequest();
        request.setSessionId(sessionId);
        request.setQuery("SELECT r_object_id FROM dm_document");

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");

        // Act & Assert
        DfcBridgeException exception = assertThrows(
                DfcBridgeException.class,
                () -> routingService.executeQuery(request));

        assertEquals("DQL_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("REST DQL service is not available"));
    }
}
