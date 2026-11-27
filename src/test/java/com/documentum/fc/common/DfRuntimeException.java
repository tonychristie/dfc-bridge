package com.documentum.fc.common;

/**
 * Test stub class that mirrors the DFC DfRuntimeException class,
 * which does not extend DfException.
 */
public class DfRuntimeException extends RuntimeException {

    public DfRuntimeException(String message) {
        super(message);
    }

    public String getMessageId() {
        return null;
    }
}
