package com.documentum.fc.common;

/**
 * Test stub class that mirrors the DFC DfException class.
 * Used for unit testing DfcExceptionUtils, which reads the message id
 * through reflection.
 *
 * The constructor takes the message id, which is also used as the message.
 */
public class DfException extends Exception {

    private final String messageId;

    public DfException(String messageId) {
        super(messageId);
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }
}
