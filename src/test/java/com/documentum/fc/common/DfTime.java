package com.documentum.fc.common;

/**
 * Test stub class that mirrors the DFC DfTime class.
 * Used for unit testing ObjectServiceImpl which uses reflection
 * to create DfTime instances.
 */
public class DfTime implements IDfTime {

    public static final String INVALID_VALUE = "not-a-time";

    private final String value;

    public DfTime(String value) {
        this.value = value;
    }

    @Override
    public boolean isValid() {
        return !INVALID_VALUE.equals(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
