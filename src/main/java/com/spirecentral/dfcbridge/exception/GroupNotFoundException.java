package com.spirecentral.dfcbridge.exception;

/**
 * Exception thrown when a Documentum group is not found.
 */
public class GroupNotFoundException extends DfcBridgeException {

    public GroupNotFoundException(String groupName) {
        super("GROUP_NOT_FOUND", "Group not found: " + groupName);
    }
}
