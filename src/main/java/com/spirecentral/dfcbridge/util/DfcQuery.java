package com.spirecentral.dfcbridge.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs a DQL query on a DFC session and passes each row to the caller.
 *
 * <p>The collection is closed after the last row, when the caller stops reading,
 * and when the caller's code throws.
 */
public final class DfcQuery {

    private static final String DFC_QUERY_CLASS = "com.documentum.fc.client.DfQuery";
    private static final String DFC_QUERY_IFACE = "com.documentum.fc.client.IDfQuery";
    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";

    // IDfQuery.DF_READ_QUERY
    private static final int DF_READ_QUERY = 0;

    private static final Class<?>[] STRING_PARAM = {String.class};
    private static final Class<?>[] STRING_INT_PARAMS = {String.class, int.class};

    private DfcQuery() {
        // Utility class
    }

    /**
     * Reads one row of a collection.
     */
    @FunctionalInterface
    public interface RowReader {
        /**
         * @param row the collection, positioned on the row
         * @return true to read the next row, false to stop
         */
        boolean read(Object row) throws Exception;
    }

    /**
     * Maps one row of a collection to a value.
     */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(Object row) throws Exception;
    }

    /**
     * Runs the query and passes each row to the reader until the rows run out or the reader returns false.
     */
    public static void forEachRow(Object dfSession, String dql, RowReader reader) throws Exception {
        Object query = DfcReflection.newInstance(DFC_QUERY_CLASS);
        DfcReflection.invoke(query, "setDQL", STRING_PARAM, dql);
        Object collection = DfcReflection.invoke(DFC_QUERY_IFACE, query, "execute",
                new Class<?>[]{DfcReflection.dfcClass(DFC_SESSION_IFACE), int.class}, dfSession, DF_READ_QUERY);
        try {
            while ((Boolean) DfcReflection.invoke(collection, "next")) {
                if (!reader.read(collection)) {
                    break;
                }
            }
        } finally {
            DfcReflection.invoke(collection, "close");
        }
    }

    /**
     * Runs the query and maps every row.
     */
    public static <T> List<T> list(Object dfSession, String dql, RowMapper<T> mapper) throws Exception {
        List<T> results = new ArrayList<>();
        forEachRow(dfSession, dql, row -> results.add(mapper.map(row)));
        return results;
    }

    /**
     * Runs the query and maps the first row, if there is one.
     */
    public static <T> Optional<T> first(Object dfSession, String dql, RowMapper<T> mapper) throws Exception {
        List<T> results = new ArrayList<>();
        forEachRow(dfSession, dql, row -> {
            results.add(mapper.map(row));
            return false;
        });
        return results.stream().findFirst();
    }

    public static String getString(Object row, String attrName) throws Exception {
        return (String) DfcReflection.invoke(row, "getString", STRING_PARAM, attrName);
    }

    public static int getInt(Object row, String attrName) throws Exception {
        return (Integer) DfcReflection.invoke(row, "getInt", STRING_PARAM, attrName);
    }

    public static boolean getBoolean(Object row, String attrName) throws Exception {
        return (Boolean) DfcReflection.invoke(row, "getBoolean", STRING_PARAM, attrName);
    }

    public static int getValueCount(Object row, String attrName) throws Exception {
        return (Integer) DfcReflection.invoke(row, "getValueCount", STRING_PARAM, attrName);
    }

    public static String getRepeatingString(Object row, String attrName, int index) throws Exception {
        return (String) DfcReflection.invoke(row, "getRepeatingString", STRING_INT_PARAMS, attrName, index);
    }
}
