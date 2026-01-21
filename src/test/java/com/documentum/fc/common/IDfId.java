package com.documentum.fc.common;

/**
 * Test stub interface that mirrors the DFC IDfId interface.
 * Used for unit testing ObjectServiceImpl which uses reflection
 * to look up objects by ID.
 */
public interface IDfId {

    /**
     * Get the string representation of this ID.
     *
     * @return the object ID as a string
     */
    String getId();

    /**
     * Check if this ID is null.
     *
     * @return true if the ID is null
     */
    boolean isNull();
}
