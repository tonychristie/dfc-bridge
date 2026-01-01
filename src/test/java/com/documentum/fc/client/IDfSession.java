package com.documentum.fc.client;

/**
 * Test stub interface that mirrors the DFC IDfSession interface.
 * This is used for unit testing DmApiServiceImpl which uses reflection
 * to look up methods on the IDfSession interface.
 *
 * Only the methods needed for dmAPI testing are defined here.
 */
public interface IDfSession {

    /**
     * Execute a dmAPI get command.
     *
     * @param command the dmAPI command string
     * @return the result string
     */
    String apiGet(String command);

    /**
     * Execute a dmAPI exec command.
     *
     * @param command the dmAPI command string
     * @return true if successful
     */
    boolean apiExec(String command);

    /**
     * Execute a dmAPI set command.
     *
     * @param command the dmAPI command string
     * @param value the value to set
     * @return true if successful
     */
    boolean apiSet(String command, String value);
}
