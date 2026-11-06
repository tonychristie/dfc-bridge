package com.documentum.fc.client;

/**
 * Test stub class that mirrors the DFC DfIdNotFoundException class,
 * thrown by IDfSession.getObject for an id that does not exist.
 */
public class DfIdNotFoundException extends DfObjectNotFoundException {

    public DfIdNotFoundException(String messageId) {
        super(messageId);
    }
}
