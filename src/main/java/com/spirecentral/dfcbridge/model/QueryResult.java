package com.spirecentral.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents the result of a DQL query execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {

    /**
     * Column metadata for the result set
     */
    private List<ColumnInfo> columns;

    /**
     * The data rows - each row is a map of column name to value
     */
    private List<Map<String, Object>> rows;

    /**
     * Total number of rows returned
     */
    private int rowCount;

    /**
     * Whether there are more rows available (pagination)
     */
    private boolean hasMore;

    /**
     * Execution time in milliseconds
     */
    private long executionTimeMs;

    /**
     * Column metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        private String name;
        private String type;
        private int length;
        private boolean repeating;
    }
}
