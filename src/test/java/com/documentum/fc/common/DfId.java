package com.documentum.fc.common;

/**
 * Test stub class that mirrors the DFC DfId class.
 * Used for unit testing ObjectServiceImpl which uses reflection
 * to create DfId instances.
 */
public class DfId implements IDfId {

    private final String id;

    public DfId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNull() {
        return id == null || id.isEmpty() || "0000000000000000".equals(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
