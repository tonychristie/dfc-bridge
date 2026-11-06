package com.documentum.fc.client;

import com.documentum.fc.common.IDfLoginInfo;

/**
 * Test stub interface that mirrors the DFC IDfSessionManager interface.
 * Used for unit testing DfcSessionServiceImpl, which looks up
 * these methods on the interface via reflection.
 *
 * Only the methods needed for testing are defined here.
 * Signatures match the actual DFC interface.
 */
public interface IDfSessionManager {

    void setIdentity(String docbase, IDfLoginInfo loginInfo);

    IDfSession getSession(String docbase);

    void release(IDfSession session);
}
