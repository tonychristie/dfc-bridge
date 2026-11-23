package com.spirecentral.dfcbridge.exception;

/**
 * Thrown when the bridge itself rejects a value in a request.
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
