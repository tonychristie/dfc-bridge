package com.documentum.fc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Test stub class that mirrors the DFC DfQuery class.
 *
 * <p>Tests set {@link #results} to the collection each DQL string returns.
 * {@link #executed} records each DQL string in the order it ran.
 */
public class DfQuery implements IDfQuery {

    public static Function<String, IDfCollection> results;
    public static final List<String> executed = new ArrayList<>();

    private String dql;

    public static void reset(Function<String, IDfCollection> queryResults) {
        results = queryResults;
        executed.clear();
    }

    @Override
    public void setDQL(String dql) {
        this.dql = dql;
    }

    @Override
    public IDfCollection execute(IDfSession session, int queryType) {
        executed.add(dql);
        return results.apply(dql);
    }
}
