package com.spirecentral.dfcbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a Documentum object is not found.
 */
public class ObjectNotFoundException extends DfcBridgeException {

    public ObjectNotFoundException(String objectId) {
        super("OBJECT_NOT_FOUND", "Object not found: " + objectId, HttpStatus.NOT_FOUND);
    }
}
