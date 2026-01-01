package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DqlException;
import com.spire.dfcbridge.model.QueryResult;
import com.spire.dfcbridge.service.DqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST implementation of DqlService.
 * Executes DQL queries via Documentum REST Services.
 *
 * REST endpoint: GET /repositories/{repo}?dql={query}
 */
@Service
@ConditionalOnProperty(name = "documentum.backend", havingValue = "rest")
public class RestDqlServiceImpl implements DqlService {

    private static final Logger log = LoggerFactory.getLogger(RestDqlServiceImpl.class);

    private final RestSessionServiceImpl sessionService;

    public RestDqlServiceImpl(RestSessionServiceImpl sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public QueryResult executeQuery(DqlRequest request) {
        log.debug("Executing DQL via REST: {}", request.getQuery());

        RestSessionHolder session = sessionService.getRestSession(request.getSessionId());
        long startTime = System.currentTimeMillis();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repositories/{repo}")
                            .queryParam("dql", request.getQuery())
                            .queryParam("items-per-page", request.getMaxRows())
                            .queryParam("page", request.getStartRow() / Math.max(1, request.getMaxRows()) + 1)
                            .build(session.getRepository()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            long executionTime = System.currentTimeMillis() - startTime;

            if (response == null) {
                throw new DqlException("No response from REST endpoint");
            }

            return transformResponse(response, executionTime);

        } catch (WebClientResponseException e) {
            throw new DqlException("DQL execution failed: " + e.getResponseBodyAsString(), e);
        } catch (DqlException e) {
            throw e;
        } catch (Exception e) {
            throw new DqlException("DQL execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int executeUpdate(String sessionId, String dql) {
        // REST API doesn't support direct DML operations in the same way
        // Updates are done via object operations
        throw new DqlException("DML operations (INSERT/UPDATE/DELETE) via REST require " +
                "object-specific endpoints. Use the Object API for modifications.");
    }

    @SuppressWarnings("unchecked")
    private QueryResult transformResponse(Map<String, Object> response, long executionTime) {
        List<QueryResult.ColumnInfo> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        // Get entries from response
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        if (entries != null && !entries.isEmpty()) {
            // Extract columns from first entry's properties
            Map<String, Object> firstEntry = entries.get(0);
            Map<String, Object> content = (Map<String, Object>) firstEntry.get("content");
            if (content != null) {
                Map<String, Object> properties = (Map<String, Object>) content.get("properties");
                if (properties != null) {
                    for (String key : properties.keySet()) {
                        Object value = properties.get(key);
                        columns.add(QueryResult.ColumnInfo.builder()
                                .name(key)
                                .type(inferType(value))
                                .length(0)
                                .repeating(value instanceof List)
                                .build());
                    }
                }
            }

            // Transform entries to rows
            for (Map<String, Object> entry : entries) {
                Map<String, Object> entryContent = (Map<String, Object>) entry.get("content");
                if (entryContent != null) {
                    Map<String, Object> props = (Map<String, Object>) entryContent.get("properties");
                    if (props != null) {
                        // Use LinkedHashMap to preserve column order
                        Map<String, Object> row = new LinkedHashMap<>(props);
                        rows.add(row);
                    }
                }
            }
        }

        // Check for pagination info
        boolean hasMore = false;
        List<Map<String, Object>> links = (List<Map<String, Object>>) response.get("links");
        if (links != null) {
            hasMore = links.stream()
                    .anyMatch(link -> "next".equals(link.get("rel")));
        }

        return QueryResult.builder()
                .columns(columns)
                .rows(rows)
                .rowCount(rows.size())
                .hasMore(hasMore)
                .executionTimeMs(executionTime)
                .build();
    }

    private String inferType(Object value) {
        if (value == null) {
            return "STRING";
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "STRING";
            }
            return inferType(list.get(0));
        }
        if (value instanceof Integer || value instanceof Long) {
            return "INTEGER";
        }
        if (value instanceof Double || value instanceof Float) {
            return "DOUBLE";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        return "STRING";
    }
}
