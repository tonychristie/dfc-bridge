package com.documentum.fc.client;

/**
 * Test stub class that mirrors the DFC DfClient class.
 * Used for unit testing DfcSessionServiceImpl, which obtains the
 * local client and a new session manager via reflection.
 *
 * Tests set {@link #sessionManager} to the session manager that
 * newSessionManager returns.
 */
public class DfClient {

    public static IDfSessionManager sessionManager;

    public static DfClient getLocalClient() {
        return new DfClient();
    }

    public IDfSessionManager newSessionManager() {
        return sessionManager;
    }
}
