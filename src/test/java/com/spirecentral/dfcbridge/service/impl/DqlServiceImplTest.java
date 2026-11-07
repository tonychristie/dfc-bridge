package com.spirecentral.dfcbridge.service.impl;

import com.documentum.fc.client.DfQuery;
import com.spirecentral.dfcbridge.dto.DqlRequest;
import com.spirecentral.dfcbridge.model.QueryResult;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.util.RowCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DqlServiceImplTest {

    @Mock
    private DfcSessionService sessionService;

    @AfterEach
    void tearDown() {
        DfQuery.reset(null);
    }

    @Test
    void executeQuery_moreRowsThanMaxRows_returnsMaxRowsWithHasMore() {
        RowCollection collection = new RowCollection(
                Map.of("object_name", "a"),
                Map.of("object_name", "b"),
                Map.of("object_name", "c"))
                .column("object_name", 2, false);
        DfQuery.reset(dql -> collection);
        when(sessionService.withSession(eq("test-session"), any())).thenAnswer(
                invocation -> invocation.<Function<Object, Object>>getArgument(1).apply(null));
        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT object_name FROM dm_document")
                .maxRows(2)
                .build();

        QueryResult result = new DqlServiceImpl(sessionService).executeQuery(request);

        assertEquals(List.of(Map.of("object_name", "a"), Map.of("object_name", "b")), result.getRows());
        assertEquals(2, result.getRowCount());
        assertTrue(result.isHasMore());
        assertTrue(collection.isClosed());
    }

    @Test
    void executeQuery_startRowOneMaxRowsOne_returnsSecondRowWithHasMore() {
        QueryResult result = executeOverThreeRows(1, 1);

        assertEquals(List.of(Map.of("object_name", "b")), result.getRows());
        assertTrue(result.isHasMore());
    }

    @Test
    void executeQuery_rowsExactlyFillMaxRows_returnsAllRowsWithoutHasMore() {
        QueryResult result = executeOverThreeRows(0, 3);

        assertEquals(List.of(Map.of("object_name", "a"), Map.of("object_name", "b"), Map.of("object_name", "c")),
                result.getRows());
        assertFalse(result.isHasMore());
    }

    @Test
    void executeQuery_startRowPastLastRow_returnsNoRowsWithoutHasMore() {
        QueryResult result = executeOverThreeRows(5, 2);

        assertEquals(List.of(), result.getRows());
        assertFalse(result.isHasMore());
    }

    private QueryResult executeOverThreeRows(int startRow, int maxRows) {
        RowCollection collection = new RowCollection(
                Map.of("object_name", "a"),
                Map.of("object_name", "b"),
                Map.of("object_name", "c"))
                .column("object_name", 2, false);
        DfQuery.reset(dql -> collection);
        when(sessionService.withSession(eq("test-session"), any())).thenAnswer(
                invocation -> invocation.<Function<Object, Object>>getArgument(1).apply(null));
        DqlRequest request = DqlRequest.builder()
                .sessionId("test-session")
                .query("SELECT object_name FROM dm_document")
                .startRow(startRow)
                .maxRows(maxRows)
                .build();

        return new DqlServiceImpl(sessionService).executeQuery(request);
    }
}
