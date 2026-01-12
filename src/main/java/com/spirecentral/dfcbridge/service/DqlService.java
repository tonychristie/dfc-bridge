package com.spirecentral.dfcbridge.service;

import com.spirecentral.dfcbridge.dto.DqlRequest;
import com.spirecentral.dfcbridge.model.QueryResult;

/**
 * Service interface for executing DQL queries.
 */
public interface DqlService {

    /**
     * Execute a DQL query.
     *
     * @param request Query parameters including session ID and DQL
     * @return Query result with columns and rows
     */
    QueryResult executeQuery(DqlRequest request);

    /**
     * Execute a DQL query that modifies data (INSERT, UPDATE, DELETE).
     *
     * @param sessionId Session ID
     * @param dql       DQL statement
     * @return Number of affected rows
     */
    int executeUpdate(String sessionId, String dql);
}
