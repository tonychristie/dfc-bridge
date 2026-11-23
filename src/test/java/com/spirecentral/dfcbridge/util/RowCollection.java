package com.spirecentral.dfcbridge.util;

import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.common.DfTime;
import com.documentum.fc.common.IDfTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Collection of query rows for tests. A repeating value is held as a List.
 */
public class RowCollection implements IDfCollection {

    private final List<Map<String, Object>> rows;
    private final List<Column> columns = new ArrayList<>();
    private int index = -1;
    private boolean closed;

    @SafeVarargs
    public RowCollection(Map<String, Object>... rows) {
        this.rows = Arrays.asList(rows);
    }

    public RowCollection column(String name, int dataType, boolean repeating) {
        columns.add(new Column(name, dataType, repeating));
        return this;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean next() {
        index++;
        return index < rows.size();
    }

    @Override
    public void close() {
        closed = true;
    }

    public int getAttrCount() {
        return columns.size();
    }

    public Column getAttr(int attrIndex) {
        return columns.get(attrIndex);
    }

    public String getString(String attrName) {
        return (String) rows.get(index).get(attrName);
    }

    public int getInt(String attrName) {
        return (Integer) rows.get(index).get(attrName);
    }

    public boolean getBoolean(String attrName) {
        return (Boolean) rows.get(index).get(attrName);
    }

    public int getValueCount(String attrName) {
        return ((List<?>) rows.get(index).get(attrName)).size();
    }

    public String getRepeatingString(String attrName, int valueIndex) {
        return (String) ((List<?>) rows.get(index).get(attrName)).get(valueIndex);
    }

    public IDfTime getRepeatingTime(String attrName, int valueIndex) {
        return new DfTime(getRepeatingString(attrName, valueIndex));
    }

    public static class Column {
        private final String name;
        private final int dataType;
        private final boolean repeating;

        public Column(String name, int dataType, boolean repeating) {
            this.name = name;
            this.dataType = dataType;
            this.repeating = repeating;
        }

        public String getName() {
            return name;
        }

        public int getDataType() {
            return dataType;
        }

        public int getLength() {
            return 0;
        }

        public boolean isRepeating() {
            return repeating;
        }
    }
}
