package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.dto.DqlRequest;
import com.spirecentral.dfcbridge.exception.DqlException;
import com.spirecentral.dfcbridge.model.QueryResult;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.service.DqlService;
import com.spirecentral.dfcbridge.util.DfcExceptionUtils;
import com.spirecentral.dfcbridge.util.DfcQuery;
import com.spirecentral.dfcbridge.util.DfcReflection;
import com.spirecentral.dfcbridge.util.DfcTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DFC implementation of DqlService using reflection to call DFC APIs.
 *
 * <p>Requires DFC classes to be available on the classpath at runtime.
 */
@Service
public class DqlServiceImpl implements DqlService {

    private static final Logger log = LoggerFactory.getLogger(DqlServiceImpl.class);

    private static final int DM_TIME = 4;

    private static final Class<?>[] STRING_PARAM = {String.class};
    private static final Class<?>[] STRING_INT_PARAMS = {String.class, int.class};

    private final DfcSessionService sessionService;

    public DqlServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public QueryResult executeQuery(DqlRequest request) {
        log.debug("Executing DQL query: {}", request.getQuery());
        long startTime = System.currentTimeMillis();

        return sessionService.withSession(request.getSessionId(), dfSession -> {
            try {
                List<Column> columns = new ArrayList<>();
                List<Map<String, Object>> rows = new ArrayList<>();
                boolean[] hasMore = {false};
                int[] skipped = {0};

                DfcQuery.forEachRow(dfSession, request.getQuery(), row -> {
                    if (skipped[0] < request.getStartRow()) {
                        skipped[0]++;
                        return true;
                    }
                    if (columns.isEmpty()) {
                        columns.addAll(extractColumns(row));
                    }
                    if (rows.size() >= request.getMaxRows()) {
                        hasMore[0] = true;
                        return false;
                    }
                    rows.add(extractRow(row, columns));
                    return true;
                });

                long executionTime = System.currentTimeMillis() - startTime;
                log.debug("Query returned {} rows in {}ms", rows.size(), executionTime);

                List<QueryResult.ColumnInfo> columnInfos = new ArrayList<>();
                for (Column column : columns) {
                    columnInfos.add(column.info);
                }

                return QueryResult.builder()
                        .columns(columnInfos)
                        .rows(rows)
                        .rowCount(rows.size())
                        .hasMore(hasMore[0])
                        .executionTimeMs(executionTime)
                        .build();

            } catch (Exception e) {
                throw new DqlException("DQL execution failed: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    private List<Column> extractColumns(Object collection) throws Exception {
        List<Column> columns = new ArrayList<>();
        int attrCount = (Integer) DfcReflection.invoke(collection, "getAttrCount");

        for (int i = 0; i < attrCount; i++) {
            Object attr = DfcReflection.invoke(collection, "getAttr", new Class<?>[]{int.class}, i);
            int dataType = (Integer) DfcReflection.invoke(attr, "getDataType");
            boolean repeating = (Boolean) DfcReflection.invoke(attr, "isRepeating");

            QueryResult.ColumnInfo info = QueryResult.ColumnInfo.builder()
                    .name((String) DfcReflection.invoke(attr, "getName"))
                    .type(DfcTypeUtils.dataTypeToString(dataType))
                    .length((Integer) DfcReflection.invoke(attr, "getLength"))
                    .repeating(repeating)
                    .build();
            String getter = repeating ? DfcTypeUtils.repeatingGetterName(dataType) : DfcTypeUtils.getterName(dataType);
            columns.add(new Column(info, getter, dataType == DM_TIME));
        }

        return columns;
    }

    private Map<String, Object> extractRow(Object collection, List<Column> columns) throws Exception {
        Map<String, Object> row = new HashMap<>();
        for (Column column : columns) {
            String name = column.info.getName();
            Object value;
            if (column.info.isRepeating()) {
                List<Object> values = new ArrayList<>();
                int count = DfcQuery.getValueCount(collection, name);
                for (int i = 0; i < count; i++) {
                    values.add(column.toResult(
                            DfcReflection.invoke(collection, column.getter, STRING_INT_PARAMS, name, i)));
                }
                value = values;
            } else {
                value = column.toResult(DfcReflection.invoke(collection, column.getter, STRING_PARAM, name));
            }
            row.put(name, value);
        }
        return row;
    }

    private static final class Column {
        private final QueryResult.ColumnInfo info;
        private final String getter;
        private final boolean time;

        private Column(QueryResult.ColumnInfo info, String getter, boolean time) {
            this.info = info;
            this.getter = getter;
            this.time = time;
        }

        // IDfTime is returned as its default text
        private Object toResult(Object value) {
            return time && value != null ? value.toString() : value;
        }
    }
}
