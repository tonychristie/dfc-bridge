package com.spire.dfcbridge.service;

import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.model.ObjectInfo;
import com.spire.dfcbridge.model.TypeInfo;
import com.spire.dfcbridge.service.impl.ObjectServiceImpl;
import com.spire.dfcbridge.service.rest.RestObjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ObjectRoutingService.
 *
 * Verifies that object operations are correctly routed to the appropriate backend
 * (DFC or REST) based on the session type.
 */
@ExtendWith(MockitoExtension.class)
class ObjectRoutingServiceTest {

    @Mock
    private SessionRoutingService sessionRoutingService;

    @Mock
    private ObjectServiceImpl dfcObjectService;

    @Mock
    private RestObjectServiceImpl restObjectService;

    private ObjectRoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new ObjectRoutingService(
                sessionRoutingService, dfcObjectService, restObjectService);
    }

    @Test
    void getCabinets_routesToDfcForDfcSession() {
        // Arrange
        String sessionId = "dfc-session-123";
        List<ObjectInfo> expectedCabinets = Arrays.asList(
                ObjectInfo.builder().objectId("0c01").name("Cabinet1").type("dm_cabinet").build(),
                ObjectInfo.builder().objectId("0c02").name("Cabinet2").type("dm_cabinet").build()
        );

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");
        when(dfcObjectService.getCabinets(sessionId)).thenReturn(expectedCabinets);

        // Act
        List<ObjectInfo> result = routingService.getCabinets(sessionId);

        // Assert
        assertSame(expectedCabinets, result);
        verify(dfcObjectService).getCabinets(sessionId);
        verifyNoInteractions(restObjectService);
    }

    @Test
    void getCabinets_routesToRestForRestSession() {
        // Arrange
        String sessionId = "rest-session-456";
        List<ObjectInfo> expectedCabinets = Arrays.asList(
                ObjectInfo.builder().objectId("0c01").name("Cabinet1").type("dm_cabinet").build()
        );

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");
        when(restObjectService.getCabinets(sessionId)).thenReturn(expectedCabinets);

        // Act
        List<ObjectInfo> result = routingService.getCabinets(sessionId);

        // Assert
        assertSame(expectedCabinets, result);
        verify(restObjectService).getCabinets(sessionId);
        verifyNoInteractions(dfcObjectService);
    }

    @Test
    void listTypes_routesToDfcForDfcSession() {
        // Arrange
        String sessionId = "dfc-session-123";
        String pattern = "dm_*";
        List<TypeInfo> expectedTypes = Arrays.asList(
                TypeInfo.builder().name("dm_document").superType("dm_sysobject").build(),
                TypeInfo.builder().name("dm_folder").superType("dm_sysobject").build()
        );

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");
        when(dfcObjectService.listTypes(sessionId, pattern)).thenReturn(expectedTypes);

        // Act
        List<TypeInfo> result = routingService.listTypes(sessionId, pattern);

        // Assert
        assertSame(expectedTypes, result);
        verify(dfcObjectService).listTypes(sessionId, pattern);
        verifyNoInteractions(restObjectService);
    }

    @Test
    void listTypes_routesToRestForRestSession() {
        // Arrange
        String sessionId = "rest-session-456";
        String pattern = "dm_*";
        List<TypeInfo> expectedTypes = Arrays.asList(
                TypeInfo.builder().name("dm_document").superType("dm_sysobject").build()
        );

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");
        when(restObjectService.listTypes(sessionId, pattern)).thenReturn(expectedTypes);

        // Act
        List<TypeInfo> result = routingService.listTypes(sessionId, pattern);

        // Assert
        assertSame(expectedTypes, result);
        verify(restObjectService).listTypes(sessionId, pattern);
        verifyNoInteractions(dfcObjectService);
    }

    @Test
    void getObject_routesToDfcForDfcSession() {
        // Arrange
        String sessionId = "dfc-session-123";
        String objectId = "0900000180000001";
        ObjectInfo expectedInfo = ObjectInfo.builder()
                .objectId(objectId)
                .name("test_doc.pdf")
                .type("dm_document")
                .build();

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");
        when(dfcObjectService.getObject(sessionId, objectId)).thenReturn(expectedInfo);

        // Act
        ObjectInfo result = routingService.getObject(sessionId, objectId);

        // Assert
        assertSame(expectedInfo, result);
        verify(dfcObjectService).getObject(sessionId, objectId);
        verifyNoInteractions(restObjectService);
    }

    @Test
    void getObject_routesToRestForRestSession() {
        // Arrange
        String sessionId = "rest-session-456";
        String objectId = "0900000180000001";
        ObjectInfo expectedInfo = ObjectInfo.builder()
                .objectId(objectId)
                .name("test_doc.pdf")
                .type("dm_document")
                .build();

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");
        when(restObjectService.getObject(sessionId, objectId)).thenReturn(expectedInfo);

        // Act
        ObjectInfo result = routingService.getObject(sessionId, objectId);

        // Assert
        assertSame(expectedInfo, result);
        verify(restObjectService).getObject(sessionId, objectId);
        verifyNoInteractions(dfcObjectService);
    }

    @Test
    void getCabinets_throwsForUnknownBackendType() {
        // Arrange
        String sessionId = "unknown-session";

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("unknown");

        // Act & Assert
        DfcBridgeException exception = assertThrows(
                DfcBridgeException.class,
                () -> routingService.getCabinets(sessionId));

        assertEquals("OBJECT_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("Unknown session backend type"));
    }

    @Test
    void getCabinets_throwsWhenDfcServiceUnavailable() {
        // Arrange - Create routing service with no DFC service
        routingService = new ObjectRoutingService(sessionRoutingService, null, restObjectService);

        String sessionId = "dfc-session-123";

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");

        // Act & Assert
        DfcBridgeException exception = assertThrows(
                DfcBridgeException.class,
                () -> routingService.getCabinets(sessionId));

        assertEquals("OBJECT_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("DFC Object service is not available"));
    }

    @Test
    void getCabinets_throwsWhenRestServiceUnavailable() {
        // Arrange - Create routing service with no REST service
        routingService = new ObjectRoutingService(sessionRoutingService, dfcObjectService, null);

        String sessionId = "rest-session-456";

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");

        // Act & Assert
        DfcBridgeException exception = assertThrows(
                DfcBridgeException.class,
                () -> routingService.getCabinets(sessionId));

        assertEquals("OBJECT_ERROR", exception.getCode());
        assertTrue(exception.getMessage().contains("REST Object service is not available"));
    }

    @Test
    void checkout_routesToCorrectBackend() {
        // Arrange
        String sessionId = "rest-session-456";
        String objectId = "0900000180000001";
        ObjectInfo expectedInfo = ObjectInfo.builder()
                .objectId(objectId)
                .name("test_doc.pdf")
                .type("dm_document")
                .build();

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("rest");
        when(restObjectService.checkout(sessionId, objectId)).thenReturn(expectedInfo);

        // Act
        ObjectInfo result = routingService.checkout(sessionId, objectId);

        // Assert
        assertSame(expectedInfo, result);
        verify(restObjectService).checkout(sessionId, objectId);
        verifyNoInteractions(dfcObjectService);
    }

    @Test
    void deleteObject_routesToCorrectBackend() {
        // Arrange
        String sessionId = "dfc-session-123";
        String objectId = "0900000180000001";
        boolean allVersions = true;

        when(sessionRoutingService.getSessionBackendType(sessionId)).thenReturn("dfc");
        doNothing().when(dfcObjectService).deleteObject(sessionId, objectId, allVersions);

        // Act
        routingService.deleteObject(sessionId, objectId, allVersions);

        // Assert
        verify(dfcObjectService).deleteObject(sessionId, objectId, allVersions);
        verifyNoInteractions(restObjectService);
    }
}
