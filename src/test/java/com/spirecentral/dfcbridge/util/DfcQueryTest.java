package com.spirecentral.dfcbridge.util;

import com.documentum.fc.client.DfQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DfcQueryTest {

    private RowCollection collection;

    @BeforeEach
    void setUp() {
        collection = new RowCollection(
                Map.of("name", "a"),
                Map.of("name", "b"),
                Map.of("name", "c"));
        DfQuery.reset(dql -> collection);
    }

    @AfterEach
    void tearDown() {
        DfQuery.reset(null);
    }

    @Test
    void forEachRow_readsEveryRow_closesCollection() throws Exception {
        List<String> names = new ArrayList<>();

        DfcQuery.forEachRow(null, "SELECT name FROM dm_type", row -> names.add(DfcQuery.getString(row, "name")));

        assertEquals(List.of("a", "b", "c"), names);
        assertEquals(List.of("SELECT name FROM dm_type"), DfQuery.executed);
        assertTrue(collection.isClosed());
    }

    @Test
    void forEachRow_readerStops_closesCollection() throws Exception {
        List<String> names = new ArrayList<>();

        DfcQuery.forEachRow(null, "SELECT name FROM dm_type", row -> {
            names.add(DfcQuery.getString(row, "name"));
            return false;
        });

        assertEquals(List.of("a"), names);
        assertTrue(collection.isClosed());
    }

    @Test
    void forEachRow_readerThrows_closesCollection() {
        IllegalStateException failure = new IllegalStateException("row failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> DfcQuery.forEachRow(null, "SELECT name FROM dm_type", row -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertTrue(collection.isClosed());
    }
}
