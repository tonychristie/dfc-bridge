package com.spire.dfcbridge.service.impl;

import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DqlException;
import com.spire.dfcbridge.model.QueryResult;
import com.spire.dfcbridge.service.DfcSessionService;
import com.spire.dfcbridge.service.DqlService;
import com.spire.dfcbridge.util.DfcTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DFC implementation of DqlService using reflection to call DFC APIs.
 *
 * <p>This service is conditionally loaded when DFC classes are available on the classpath.
 * It is used by {@link com.spire.dfcbridge.service.DqlRoutingService} to route DQL operations
 * to the appropriate backend based on the session type.
 */
@Service
public class DqlServiceImpl implements DqlService {

    private static final Logger log = LoggerFactory.getLogger(DqlServiceImpl.class);

    private static final String DFC_QUERY_CLASS = "com.documentum.fc.client.DfQuery";
    private static final String DFC_QUERY_IFACE = "com.documentum.fc.client.IDfQuery";
    private static final String DFC_COLLECTION_IFACE = "com.documentum.fc.client.IDfCollection";
    private static final String DFC_TYPED_OBJECT_IFACE = "com.documentum.fc.client.IDfTypedObject";

    private final DfcSessionService sessionService;

    public DqlServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public QueryResult executeQuery(DqlRequest request) {
        log.debug("Executing DQL query: {}", request.getQuery());
        long startTime = System.currentTimeMillis();

        Object dfSession = sessionService.getDfcSession(request.getSessionId());

        try {
            // Create query object
            Class<?> dfQueryClass = Class.forName(DFC_QUERY_CLASS);
            Object query = dfQueryClass.getDeclaredConstructor().newInstance();

            // Set DQL
            Method setDqlMethod = dfQueryClass.getMethod("setDQL", String.class);
            setDqlMethod.invoke(query, request.getQuery());

            // Execute query
            Class<?> sessionClass = dfSession.getClass();
            Class<?> queryInterface = Class.forName(DFC_QUERY_IFACE);

            // IDfQuery.DF_READ_QUERY = 0
            Method executeMethod = queryInterface.getMethod("execute",
                    Class.forName("com.documentum.fc.client.IDfSession"), int.class);
            Object collection = executeMethod.invoke(query, dfSession, 0);

            // Process results
            List<QueryResult.ColumnInfo> columns = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean columnsExtracted = false;
            int rowCount = 0;
            int skipped = 0;

            Class<?> collectionClass = Class.forName(DFC_COLLECTION_IFACE);
            Method nextMethod = collectionClass.getMethod("next");
            Method closeMethod = collectionClass.getMethod("close");

            try {
                while ((Boolean) nextMethod.invoke(collection)) {
                    // Skip rows for pagination
                    if (skipped < request.getStartRow()) {
                        skipped++;
                        continue;
                    }

                    // Extract columns on first row
                    if (!columnsExtracted) {
                        columns = extractColumns(collection);
                        columnsExtracted = true;
                    }

                    // Check max rows
                    if (rowCount >= request.getMaxRows()) {
                        // There are more rows
                        long executionTime = System.currentTimeMillis() - startTime;
                        return QueryResult.builder()
                                .columns(columns)
                                .rows(rows)
                                .rowCount(rowCount)
                                .hasMore(true)
                                .executionTimeMs(executionTime)
                                .build();
                    }

                    // Extract row data
                    Map<String, Object> row = extractRow(collection, columns);
                    rows.add(row);
                    rowCount++;
                }
            } finally {
                closeMethod.invoke(collection);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("Query returned {} rows in {}ms", rowCount, executionTime);

            return QueryResult.builder()
                    .columns(columns)
                    .rows(rows)
                    .rowCount(rowCount)
                    .hasMore(false)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            throw new DqlException("DQL execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int executeUpdate(String sessionId, String dql) {
        log.debug("Executing DQL update: {}", dql);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            // Create query object
            Class<?> dfQueryClass = Class.forName(DFC_QUERY_CLASS);
            Object query = dfQueryClass.getDeclaredConstructor().newInstance();

            // Set DQL
            Method setDqlMethod = dfQueryClass.getMethod("setDQL", String.class);
            setDqlMethod.invoke(query, dql);

            // Execute as update query
            // IDfQuery.DF_EXEC_QUERY = 3
            Class<?> queryInterface = Class.forName(DFC_QUERY_IFACE);
            Method executeMethod = queryInterface.getMethod("execute",
                    Class.forName("com.documentum.fc.client.IDfSession"), int.class);
            Object collection = executeMethod.invoke(query, dfSession, 3);

            // Close collection
            Class<?> collectionClass = Class.forName(DFC_COLLECTION_IFACE);
            Method closeMethod = collectionClass.getMethod("close");
            closeMethod.invoke(collection);

            // DFC doesn't easily return affected row count for updates
            // Return 1 to indicate success
            return 1;

        } catch (Exception e) {
            throw new DqlException("DQL update failed: " + e.getMessage(), e);
        }
    }

    private List<QueryResult.ColumnInfo> extractColumns(Object collection) throws Exception {
        List<QueryResult.ColumnInfo> columns = new ArrayList<>();

        Class<?> typedObjectClass = Class.forName(DFC_TYPED_OBJECT_IFACE);
        Method getAttrCountMethod = typedObjectClass.getMethod("getAttrCount");
        Method getAttrMethod = typedObjectClass.getMethod("getAttr", int.class);

        int attrCount = (Integer) getAttrCountMethod.invoke(collection);

        for (int i = 0; i < attrCount; i++) {
            Object attr = getAttrMethod.invoke(collection, i);

            Method getNameMethod = attr.getClass().getMethod("getName");
            Method getDataTypeMethod = attr.getClass().getMethod("getDataType");
            Method getLengthMethod = attr.getClass().getMethod("getLength");
            Method isRepeatingMethod = attr.getClass().getMethod("isRepeating");

            String name = (String) getNameMethod.invoke(attr);
            int dataType = (Integer) getDataTypeMethod.invoke(attr);
            int length = (Integer) getLengthMethod.invoke(attr);
            boolean repeating = (Boolean) isRepeatingMethod.invoke(attr);

            columns.add(QueryResult.ColumnInfo.builder()
                    .name(name)
                    .type(DfcTypeUtils.dataTypeToString(dataType))
                    .length(length)
                    .repeating(repeating)
                    .build());
        }

        return columns;
    }

    private Map<String, Object> extractRow(Object collection, List<QueryResult.ColumnInfo> columns)
            throws Exception {
        Map<String, Object> row = new HashMap<>();

        Class<?> typedObjectClass = Class.forName(DFC_TYPED_OBJECT_IFACE);

        for (QueryResult.ColumnInfo col : columns) {
            Object value;
            if (col.isRepeating()) {
                value = extractRepeatingValue(collection, typedObjectClass, col);
            } else {
                value = extractSingleValue(collection, typedObjectClass, col);
            }
            row.put(col.getName(), value);
        }

        return row;
    }

    private Object extractSingleValue(Object collection, Class<?> typedObjectClass,
                                       QueryResult.ColumnInfo col) throws Exception {
        String methodName = getGetterMethodName(col.getType());
        Method getValueMethod = typedObjectClass.getMethod(methodName, String.class);
        Object value = getValueMethod.invoke(collection, col.getName());

        // Convert IDfTime to string using default toString()
        if ("TIME".equals(col.getType()) && value != null) {
            value = value.toString();
        }

        return value;
    }

    private Object extractRepeatingValue(Object collection, Class<?> typedObjectClass,
                                          QueryResult.ColumnInfo col) throws Exception {
        List<Object> values = new ArrayList<>();
        Method getValueCountMethod = typedObjectClass.getMethod("getValueCount", String.class);
        int count = (Integer) getValueCountMethod.invoke(collection, col.getName());

        String methodName = getRepeatingGetterMethodName(col.getType());
        Method getRepeatingMethod = typedObjectClass.getMethod(methodName, String.class, int.class);

        for (int i = 0; i < count; i++) {
            Object value = getRepeatingMethod.invoke(collection, col.getName(), i);
            // Convert IDfTime to string using default toString()
            if ("TIME".equals(col.getType()) && value != null) {
                value = value.toString();
            }
            values.add(value);
        }
        return values;
    }

    private String getGetterMethodName(String dataType) {
        return switch (dataType) {
            case "BOOLEAN" -> "getBoolean";
            case "INTEGER" -> "getInt";
            case "DOUBLE" -> "getDouble";
            case "TIME" -> "getTime";
            case "ID" -> "getId";
            default -> "getString";
        };
    }

    private String getRepeatingGetterMethodName(String dataType) {
        return switch (dataType) {
            case "BOOLEAN" -> "getRepeatingBoolean";
            case "INTEGER" -> "getRepeatingInt";
            case "DOUBLE" -> "getRepeatingDouble";
            case "TIME" -> "getRepeatingTime";
            case "ID" -> "getRepeatingId";
            default -> "getRepeatingString";
        };
    }
}
