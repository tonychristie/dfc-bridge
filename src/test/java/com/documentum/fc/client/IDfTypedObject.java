package com.documentum.fc.client;

import com.documentum.fc.common.DfException;

/**
 * Test stub interface that mirrors the DFC IDfTypedObject interface.
 * Used for unit testing DfcSessionServiceImpl, which reads the docbase
 * config and the server config of a new session.
 *
 * Only the methods needed for testing are defined here.
 * Signatures match the actual DFC interface.
 */
public interface IDfTypedObject {

    String getString(String attributeName) throws DfException;
}
