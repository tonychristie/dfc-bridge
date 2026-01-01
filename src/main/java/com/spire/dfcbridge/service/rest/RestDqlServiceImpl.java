package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.DqlRequest;
import com.spire.dfcbridge.exception.DqlException;
import com.spire.dfcbridge.model.QueryResult;
import com.spire.dfcbridge.service.DqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST implementation of DqlService.
 * Executes DQL queries via Documentum REST Services.
 *
 * REST endpoint: POST /repositories/{repo}/dql
 * Content-Type: application/vnd.emc.documentum+json
 *
 * Request body:
 * {
 *   "dql-query": "SELECT r_object_id, object_name FROM dm_document"
 * }
 *
 * Auto-fetches all pages for complete results.
 */
@Service
@ConditionalOnProperty(name = "documentum.backend", havingValue = "rest")
public class RestDqlServiceImpl implements DqlService {

    private static final Logger log = LoggerFactory.getLogger(RestDqlServiceImpl.class);
    private static final MediaType DOCUMENTUM_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.emc.documentum+json");
    private static final int DEFAULT_ITEMS_PER_PAGE = 100;
    private static final int MAX_PAGES = 1000; // Safety limit to prevent infinite loops

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
            List<QueryResult.ColumnInfo> columns = new ArrayList<>();
            List<Map<String, Object>> allRows = new ArrayList<>();
            int itemsPerPage = request.getMaxRows() > 0 ? request.getMaxRows() : DEFAULT_ITEMS_PER_PAGE;
            int page = 1;
            boolean hasMore = true;
            int pagesFetched = 0;

            while (hasMore && pagesFetched < MAX_PAGES) {
                Map<String, Object> response = executeDqlRequest(
                        session, request.getQuery(), itemsPerPage, page);

                if (response == null) {
                    break;
                }

                // Extract columns from first response only
                if (columns.isEmpty()) {
                    columns = extractColumns(response);
                }

                // Add rows from this page
                List<Map<String, Object>> pageRows = extractRows(response);
                allRows.addAll(pageRows);

                // Check for more pages
                hasMore = hasNextPage(response);
                page++;
                pagesFetched++;

                log.debug("Fetched page {}, rows so far: {}, hasMore: {}",
                        pagesFetched, allRows.size(), hasMore);
            }

            if (pagesFetched >= MAX_PAGES) {
                log.warn("Reached maximum page limit ({}) for query: {}",
                        MAX_PAGES, request.getQuery());
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("DQL query returned {} rows in {}ms (fetched {} pages)",
                    allRows.size(), executionTime, pagesFetched);

            return QueryResult.builder()
                    .columns(columns)
                    .rows(allRows)
                    .rowCount(allRows.size())
                    .hasMore(hasMore) // True if we hit the page limit
                    .executionTimeMs(executionTime)
                    .build();

        } catch (WebClientResponseException e) {
            throw new DqlException("DQL execution failed: " + e.getResponseBodyAsString(), e);
        } catch (DqlException e) {
            throw e;
        } catch (Exception e) {
            throw new DqlException("DQL execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a single DQL request to the REST API.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeDqlRequest(
            RestSessionHolder session,
            String query,
            int itemsPerPage,
            int page) {

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("dql-query", query);

        WebClient webClient = session.getWebClient();

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/repositories/{repo}/dql")
                        .queryParam("items-per-page", itemsPerPage)
                        .queryParam("page", page)
                        .build(session.getRepository()))
                .contentType(DOCUMENTUM_MEDIA_TYPE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(30));
    }

    @Override
    public int executeUpdate(String sessionId, String dql) {
        // REST API doesn't support direct DML operations in the same way
        // Updates are done via object operations
        throw new DqlException("DML operations (INSERT/UPDATE/DELETE) via REST require " +
                "object-specific endpoints. Use the Object API for modifications.");
    }

    /**
     * Extract column information from the REST response.
     */
    @SuppressWarnings("unchecked")
    private List<QueryResult.ColumnInfo> extractColumns(Map<String, Object> response) {
        List<QueryResult.ColumnInfo> columns = new ArrayList<>();

        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
        if (entries == null || entries.isEmpty()) {
            return columns;
        }

        // Extract columns from first entry's properties
        Map<String, Object> firstEntry = entries.get(0);
        Map<String, Object> content = (Map<String, Object>) firstEntry.get("content");
        if (content == null) {
            return columns;
        }

        Map<String, Object> properties = (Map<String, Object>) content.get("properties");
        if (properties == null) {
            return columns;
        }

        for (String key : properties.keySet()) {
            Object value = properties.get(key);
            columns.add(QueryResult.ColumnInfo.builder()
                    .name(key)
                    .type(inferType(value))
                    .length(0)
                    .repeating(value instanceof List)
                    .build());
        }

        return columns;
    }

    /**
     * Extract rows from the REST response.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> response) {
        List<Map<String, Object>> rows = new ArrayList<>();

        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
        if (entries == null) {
            return rows;
        }

        for (Map<String, Object> entry : entries) {
            Map<String, Object> content = (Map<String, Object>) entry.get("content");
            if (content != null) {
                Map<String, Object> properties = (Map<String, Object>) content.get("properties");
                if (properties != null) {
                    // Use LinkedHashMap to preserve column order
                    Map<String, Object> row = new LinkedHashMap<>(properties);
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    /**
     * Check if there's a next page in the response.
     */
    @SuppressWarnings("unchecked")
    private boolean hasNextPage(Map<String, Object> response) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) response.get("links");
        if (links == null) {
            return false;
        }
        return links.stream()
                .anyMatch(link -> "next".equals(link.get("rel")));
    }

    /**
     * Infer the DQL data type from a Java value.
     */
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
