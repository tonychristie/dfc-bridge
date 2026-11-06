package com.documentum.fc.client;

import com.documentum.fc.common.DfException;

/**
 * Test stub interface that mirrors the DFC IDfQuery interface.
 * Used for unit testing the query helper, which looks up execute on this interface.
 */
public interface IDfQuery {

    void setDQL(String dql);

    IDfCollection execute(IDfSession session, int queryType) throws DfException;
}
