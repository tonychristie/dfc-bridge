package com.documentum.fc.common;

/**
 * Test stub interface that mirrors the DFC IDfTime interface.
 * Used for unit testing ObjectServiceImpl which uses reflection
 * to pass time arguments to DFC methods.
 */
public interface IDfTime {

    boolean isValid();
}
