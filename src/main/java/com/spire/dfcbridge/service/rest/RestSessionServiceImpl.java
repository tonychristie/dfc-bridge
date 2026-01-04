package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.config.backend.BackendProperties;
import com.spire.dfcbridge.dto.ConnectRequest;
import com.spire.dfcbridge.dto.ConnectResponse;
import com.spire.dfcbridge.exception.ConnectionException;
import com.spire.dfcbridge.exception.SessionNotFoundException;
import com.spire.dfcbridge.model.RepositoryInfo;
import com.spire.dfcbridge.model.SessionInfo;
import com.spire.dfcbridge.service.DfcSessionService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST implementation of DfcSessionService.
 * Connects to Documentum via Documentum REST Services instead of DFC.
 *
 * Key differences from DFC:
 * - Uses Basic Auth (credentials sent with each request)
 * - No server-side session - stateless HTTP requests
 * - Session ID is just a local identifier for the cached WebClient
 */
@Service
public class RestSessionServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(RestSessionServiceImpl.class);

    private final Map<String, RestSessionHolder> sessions = new ConcurrentHashMap<>();
    private final BackendProperties backendProperties;

    public RestSessionServiceImpl(BackendProperties backendProperties) {
        this.backendProperties = backendProperties;
        log.info("REST backend initialized with endpoint: {}", backendProperties.getRest().getEndpoint());
    }

    public ConnectResponse connect(ConnectRequest request) {
        log.info("Connecting to repository {} via REST", request.getRepository());

        // Use endpoint from request (required for per-connection routing)
        String endpoint = request.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            // Fall back to config if not in request (for backwards compatibility)
            endpoint = backendProperties.getRest().getEndpoint();
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new ConnectionException("REST endpoint not provided. Specify 'endpoint' in connect request.");
        }

        // Remove trailing slash
        endpoint = endpoint.replaceAll("/+$", "");

        // Create Basic Auth header
        String credentials = request.getUsername() + ":" + request.getPassword();
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        // Create WebClient for this session
        int timeoutSeconds = backendProperties.getRest().getTimeoutSeconds();
        WebClient webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // Validate connection by fetching repository info
        try {
            Map<String, Object> repoResponse = webClient.get()
                    .uri("/repositories/{repo}", request.getRepository())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (repoResponse == null) {
                throw new ConnectionException("No response from REST endpoint");
            }

            // Generate session ID
            String sessionId = "rest-" + UUID.randomUUID().toString();

            // Build repository info from response
            RepositoryInfo repoInfo = extractRepositoryInfo(repoResponse, request);

            // Create session holder
            RestSessionHolder holder = new RestSessionHolder();
            holder.setWebClient(webClient);
            holder.setRepository(request.getRepository());
            holder.setUsername(request.getUsername());
            holder.setPassword(request.getPassword());
            holder.setSessionInfo(SessionInfo.builder()
                    .sessionId(sessionId)
                    .connected(true)
                    .repository(request.getRepository())
                    .user(request.getUsername())
                    .docbroker(endpoint) // Store endpoint in docbroker field for display
                    .port(0)
                    .sessionStart(Instant.now())
                    .lastActivity(Instant.now())
                    .serverVersion(repoInfo.getServerVersion())
                    .build());

            sessions.put(sessionId, holder);

            log.info("REST session {} established for user {} on repository {}",
                    sessionId, request.getUsername(), request.getRepository());

            return ConnectResponse.builder()
                    .sessionId(sessionId)
                    .repositoryInfo(repoInfo)
                    .build();

        } catch (WebClientResponseException.Unauthorized e) {
            throw new ConnectionException("Authentication failed. Check your credentials.");
        } catch (WebClientResponseException.NotFound e) {
            throw new ConnectionException("Repository \"" + request.getRepository() + "\" not found.");
        } catch (WebClientResponseException e) {
            throw new ConnectionException("REST connection failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            if (e.getCause() instanceof java.net.ConnectException) {
                throw new ConnectionException("Cannot connect to REST endpoint: " + endpoint);
            }
            throw new ConnectionException("REST connection failed: " + e.getMessage(), e);
        }
    }

    public void disconnect(String sessionId) {
        RestSessionHolder holder = sessions.remove(sessionId);
        if (holder != null) {
            log.info("REST session {} disconnected", sessionId);
        }
    }

    public SessionInfo getSessionInfo(String sessionId) {
        RestSessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return holder.getSessionInfo();
    }

    public boolean isSessionValid(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public void touchSession(String sessionId) {
        RestSessionHolder holder = sessions.get(sessionId);
        if (holder != null) {
            holder.getSessionInfo().setLastActivity(Instant.now());
        }
    }

    public Object getDfcSession(String sessionId) {
        // For REST, return the WebClient (callers should check backend type)
        RestSessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        touchSession(sessionId);
        return holder.getWebClient();
    }

    /**
     * Get the REST session holder for use by other REST services.
     */
    public RestSessionHolder getRestSession(String sessionId) {
        RestSessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        touchSession(sessionId);
        return holder;
    }

    @Scheduled(fixedRateString = "${dfc.session.cleanup-interval-ms:60000}")
    public void cleanupExpiredSessions() {
        int timeoutMinutes = 30; // Could make configurable
        Instant cutoff = Instant.now().minusSeconds(timeoutMinutes * 60L);
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getSessionInfo().getLastActivity().isBefore(cutoff)) {
                log.info("Cleaning up expired REST session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down REST backend - clearing all sessions");
        sessions.clear();
    }

    @SuppressWarnings("unchecked")
    private RepositoryInfo extractRepositoryInfo(Map<String, Object> response, ConnectRequest request) {
        String serverVersion = null;
        String repoId = null;

        // Try to extract server info from REST response
        List<Map<String, Object>> servers = (List<Map<String, Object>>) response.get("servers");
        if (servers != null && !servers.isEmpty()) {
            Map<String, Object> server = servers.get(0);
            serverVersion = (String) server.get("version");
        }

        repoId = String.valueOf(response.get("id"));

        return RepositoryInfo.builder()
                .name(request.getRepository())
                .id(repoId)
                .serverVersion(serverVersion)
                .contentServerHost(backendProperties.getRest().getEndpoint())
                .build();
    }
}
