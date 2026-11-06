package com.spirecentral.dfcbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a Documentum user is not found.
 */
public class UserNotFoundException extends DfcBridgeException {

    public UserNotFoundException(String userName) {
        super("USER_NOT_FOUND", "User not found: " + userName, HttpStatus.NOT_FOUND);
    }
}
