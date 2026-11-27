package com.documentum.fc.client;

import com.documentum.fc.common.DfException;

/**
 * Test stub interface that mirrors the DFC IDfSysObject interface.
 * Used for unit testing object creation, which sets a name or a folder only on a sysobject.
 */
public interface IDfSysObject {

    void setObjectName(String name) throws DfException;

    void link(String folderPath) throws DfException;
}
