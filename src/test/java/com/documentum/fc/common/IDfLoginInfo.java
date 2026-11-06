package com.documentum.fc.common;

/**
 * Test stub interface that mirrors the DFC IDfLoginInfo interface.
 * Used for unit testing DfcSessionServiceImpl, which looks up
 * IDfSessionManager.setIdentity by this parameter type.
 */
public interface IDfLoginInfo {

    void setUser(String user);

    void setPassword(String password);

    void setDomain(String domain);
}
