package com.spirecentral.dfcbridge.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryResultTest {

    @Test
    void testBuilderAndGetters() {
        QueryResult.ColumnInfo col1 = QueryResult.ColumnInfo.builder()
                .name("r_object_id")
                .type("ID")
                .length(16)
                .repeating(false)
                .build();

        QueryResult.ColumnInfo col2 = QueryResult.ColumnInfo.builder()
                .name("object_name")
                .type("STRING")
                .length(255)
                .repeating(false)
                .build();

        Map<String, Object> row1 = Map.of("r_object_id", "0901234567890123", "object_name", "test.txt");
        Map<String, Object> row2 = Map.of("r_object_id", "0901234567890124", "object_name", "test2.txt");

        QueryResult result = QueryResult.builder()
                .columns(List.of(col1, col2))
                .rows(List.of(row1, row2))
                .rowCount(2)
                .hasMore(false)
                .executionTimeMs(150)
                .build();

        assertEquals(2, result.getColumns().size());
        assertEquals(2, result.getRows().size());
        assertEquals(2, result.getRowCount());
        assertFalse(result.isHasMore());
        assertEquals(150, result.getExecutionTimeMs());
    }

    @Test
    void testColumnInfo() {
        QueryResult.ColumnInfo col = QueryResult.ColumnInfo.builder()
                .name("keywords")
                .type("STRING")
                .length(64)
                .repeating(true)
                .build();

        assertEquals("keywords", col.getName());
        assertEquals("STRING", col.getType());
        assertEquals(64, col.getLength());
        assertTrue(col.isRepeating());
    }

    @Test
    void testEmptyResult() {
        QueryResult result = QueryResult.builder()
                .columns(List.of())
                .rows(List.of())
                .rowCount(0)
                .hasMore(false)
                .executionTimeMs(5)
                .build();

        assertTrue(result.getColumns().isEmpty());
        assertTrue(result.getRows().isEmpty());
        assertEquals(0, result.getRowCount());
    }
}
