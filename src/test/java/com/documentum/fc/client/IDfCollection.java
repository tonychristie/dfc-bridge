package com.documentum.fc.client;

import com.documentum.fc.common.DfException;

/**
 * Test stub interface that mirrors the DFC IDfCollection interface.
 * Used for unit testing ObjectServiceImpl, which closes a collection
 * returned by an API call.
 */
public interface IDfCollection {

    void close() throws DfException;
}
