package com.documentum.fc.client;

import com.documentum.fc.common.DfException;

/**
 * Test stub class that mirrors the DFC DfObjectNotFoundException class,
 * which extends DfException directly.
 */
public abstract class DfObjectNotFoundException extends DfException {

    protected DfObjectNotFoundException(String messageId) {
        super(messageId);
    }
}
