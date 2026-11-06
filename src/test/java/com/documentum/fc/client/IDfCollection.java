package com.documentum.fc.client;

import com.documentum.fc.common.DfException;

/**
 * Test stub interface that mirrors the DFC IDfCollection interface.
 * Used for unit testing the query helper, which reads a collection row by row,
 * and ObjectServiceImpl, which closes a collection returned by an API call.
 */
public interface IDfCollection {

    boolean next() throws DfException;

    void close() throws DfException;
}
