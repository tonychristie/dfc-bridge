package com.documentum.fc.client;

import com.documentum.fc.common.DfException;
import com.documentum.fc.common.IDfId;

/**
 * Test stub interface that mirrors the DFC IDfSession interface.
 * This is used for unit testing DmApiServiceImpl and ObjectServiceImpl
 * which use reflection to look up methods on the IDfSession interface.
 *
 * Only the methods needed for testing are defined here.
 * Signatures match the actual DFC interface.
 */
public interface IDfSession {

    /**
     * Get an object by its ID.
     *
     * @param objectId the object ID
     * @return the persistent object
     * @throws DfException DfIdNotFoundException when no object has the id
     */
    Object getObject(IDfId objectId) throws DfException;

    /**
     * Get a type definition by name.
     *
     * @param typeName the type name
     * @return the type, or null when no type has the name
     */
    Object getType(String typeName);

    /**
     * Get a folder by its path.
     *
     * @param folderPath the folder path
     * @return the folder, or null when no folder has the path
     */
    Object getFolderByPath(String folderPath);

    /**
     * Check whether the session is connected.
     *
     * @return false once the session has been released
     */
    boolean isConnected();

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
