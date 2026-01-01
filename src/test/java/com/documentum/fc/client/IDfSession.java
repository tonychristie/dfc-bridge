package com.documentum.fc.client;

/**
 * Test stub interface that mirrors the DFC IDfSession interface.
 * This is used for unit testing DmApiServiceImpl which uses reflection
 * to look up methods on the IDfSession interface.
 *
 * Only the methods needed for dmAPI testing are defined here.
 * Signatures match the actual DFC interface.
 */
public interface IDfSession {

    /**
     * Execute a dmAPI get command.
     *
     * @param method the dmAPI method name (e.g., "getdocbaseconfig")
     * @param args the method arguments (e.g., "session")
     * @return the result string
     */
    String apiGet(String method, String args);

    /**
     * Execute a dmAPI exec command.
     *
     * @param method the dmAPI method name (e.g., "fetch")
     * @param args the method arguments (e.g., "session,objectId")
     * @return true if successful
     */
    boolean apiExec(String method, String args);

    /**
     * Execute a dmAPI set command.
     *
     * @param method the dmAPI method name (e.g., "set")
     * @param args the method arguments (e.g., "session,objectId,attrName")
     * @param value the value to set
     * @return true if successful
     */
    boolean apiSet(String method, String args, String value);
}
