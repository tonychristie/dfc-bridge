package com.spirecentral.dfcbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a Documentum object is not found.
 */
public class ObjectNotFoundException extends DfcBridgeException {

    public ObjectNotFoundException(String objectId) {
        this("Object not found: " + objectId, HttpStatus.NOT_FOUND);
    }

    private ObjectNotFoundException(String message, HttpStatus status) {
        super("OBJECT_NOT_FOUND", message, status);
    }

    public static ObjectNotFoundException withMessage(String message) {
        return new ObjectNotFoundException(message, HttpStatus.NOT_FOUND);
    }
}
