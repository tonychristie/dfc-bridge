package com.spire.dfcbridge.exception;

/**
 * Exception thrown when a Documentum object is not found.
 */
public class ObjectNotFoundException extends DfcBridgeException {

    public ObjectNotFoundException(String objectId) {
        super("OBJECT_NOT_FOUND", "Object not found: " + objectId);
    }
}
