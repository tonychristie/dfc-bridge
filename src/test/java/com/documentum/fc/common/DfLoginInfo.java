package com.documentum.fc.common;

/**
 * Test stub class that mirrors the DFC DfLoginInfo class.
 * Used for unit testing DfcSessionServiceImpl, which creates
 * login info instances via reflection.
 */
public class DfLoginInfo implements IDfLoginInfo {

    @Override
    public void setUser(String user) {
    }

    @Override
    public void setPassword(String password) {
    }

    @Override
    public void setDomain(String domain) {
    }
}
