package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.ApiRequest;
import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.CreateObjectRequest;
import com.spire.dfcbridge.dto.UpdateObjectRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.exception.ObjectNotFoundException;
import com.spire.dfcbridge.model.ObjectInfo;
import com.spire.dfcbridge.model.TypeInfo;
import com.spire.dfcbridge.service.ObjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST implementation of ObjectService.
 * Performs object operations via Documentum REST Services.
 */
@Service
@ConditionalOnProperty(name = "documentum.backend", havingValue = "rest")
public class RestObjectServiceImpl implements ObjectService {

    private static final Logger log = LoggerFactory.getLogger(RestObjectServiceImpl.class);
    private static final String ERROR_CODE = "REST_ERROR";

    private final RestSessionServiceImpl sessionService;

    public RestObjectServiceImpl(RestSessionServiceImpl sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public List<ObjectInfo> getCabinets(String sessionId) {
        log.debug("Getting cabinets via REST");

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/cabinets", session.getRepository())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            List<ObjectInfo> results = new ArrayList<>();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null) {
                    for (Map<String, Object> entry : entries) {
                        results.add(extractCabinetInfo(entry));
                    }
                }
            }

            return results;

        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to get cabinets: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to get cabinets: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo getObject(String sessionId, String objectId) {
        log.debug("Getting object {} via REST", objectId);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/objects/{objectId}",
                            session.getRepository(), objectId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                throw new ObjectNotFoundException(objectId);
            }

            return extractObjectInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to get object: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to get object: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request) {
        log.debug("Updating object {} via REST", objectId);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            // Build the update payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("properties", request.getAttributes());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().post()
                    .uri("/repositories/{repo}/objects/{objectId}",
                            session.getRepository(), objectId)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                throw new DfcBridgeException(ERROR_CODE, "No response from update");
            }

            return extractObjectInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to update object: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (DfcBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to update object: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ObjectInfo> listFolderContents(String sessionId, String folderPath) {
        log.debug("Listing folder {} via REST", folderPath);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            // First, get folder by path to get its ID
            @SuppressWarnings("unchecked")
            Map<String, Object> folderResponse = session.getWebClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repositories/{repo}/folders")
                            .queryParam("folder-path", folderPath)
                            .build(session.getRepository()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (folderResponse == null) {
                return new ArrayList<>();
            }

            // Then get folder contents
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries =
                    (List<Map<String, Object>>) folderResponse.get("entries");

            List<ObjectInfo> results = new ArrayList<>();
            if (entries != null) {
                for (Map<String, Object> entry : entries) {
                    results.add(extractObjectInfo(entry));
                }
            }

            return results;

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException("Folder not found: " + folderPath);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to list folder: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to list folder: " + e.getMessage(), e);
        }
    }

    @Override
    public TypeInfo getTypeInfo(String sessionId, String typeName) {
        log.debug("Getting type {} via REST", typeName);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/types/{typeName}",
                            session.getRepository(), typeName)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                throw new ObjectNotFoundException("Type not found: " + typeName);
            }

            return extractTypeInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException("Type not found: " + typeName);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to get type: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to get type: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TypeInfo> listTypes(String sessionId, String pattern) {
        log.debug("Listing types via REST, pattern: {}", pattern);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            List<TypeInfo> results = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                final int currentPage = page;
                @SuppressWarnings("unchecked")
                Map<String, Object> response = session.getWebClient().get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/repositories/{repo}/types")
                                    .queryParam("inline", "true")
                                    .queryParam("items-per-page", "100")
                                    .queryParam("page", currentPage);
                            if (pattern != null && !pattern.isEmpty()) {
                                builder.queryParam("filter", "starts-with(name,'" + pattern + "')");
                            }
                            return builder.build(session.getRepository());
                        })
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(60));

                if (response == null) {
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null && !entries.isEmpty()) {
                    for (Map<String, Object> entry : entries) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content =
                                (Map<String, Object>) entry.get("content");
                        if (content != null) {
                            results.add(extractTypeInfo(content));
                        }
                    }
                }

                // Check if there's a next page
                hasMore = hasNextPage(response);
                page++;
            }

            return results;

        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to list types: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to list types: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the REST response has a "next" link indicating more pages.
     */
    @SuppressWarnings("unchecked")
    private boolean hasNextPage(Map<String, Object> response) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) response.get("links");
        if (links != null) {
            for (Map<String, Object> link : links) {
                if ("next".equals(link.get("rel"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ApiResponse executeApi(ApiRequest request) {
        // Direct DFC API method invocation is not supported via REST
        throw new DfcBridgeException("NOT_SUPPORTED",
                "Direct API method execution requires a DFC connection. " +
                "Use REST-specific endpoints or switch to a DFC backend.");
    }

    @Override
    public List<ObjectInfo> listFolderContentsById(String sessionId, String folderId) {
        log.debug("Listing folder contents by ID {} via REST", folderId);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/folders/{folderId}/objects",
                            session.getRepository(), folderId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            List<ObjectInfo> results = new ArrayList<>();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null) {
                    for (Map<String, Object> entry : entries) {
                        results.add(extractCabinetInfo(entry));
                    }
                }
            }

            return results;

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException("Folder not found: " + folderId);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to list folder: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to list folder: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo checkout(String sessionId, String objectId) {
        log.debug("Checking out object {} via REST", objectId);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().put()
                    .uri("/repositories/{repo}/objects/{objectId}/lock",
                            session.getRepository(), objectId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                throw new DfcBridgeException(ERROR_CODE, "No response from checkout");
            }

            return extractObjectInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to checkout object: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (DfcBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to checkout object: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelCheckout(String sessionId, String objectId) {
        log.debug("Cancelling checkout of object {} via REST", objectId);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            session.getWebClient().delete()
                    .uri("/repositories/{repo}/objects/{objectId}/lock",
                            session.getRepository(), objectId)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(30));

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to cancel checkout: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to cancel checkout: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo checkin(String sessionId, String objectId, String versionLabel) {
        log.debug("Checking in object {} via REST with label {}", objectId, versionLabel);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            // Build the checkin payload
            Map<String, Object> payload = new HashMap<>();
            if (versionLabel != null && !versionLabel.isEmpty()) {
                Map<String, Object> properties = new HashMap<>();
                properties.put("r_version_label", versionLabel);
                payload.put("properties", properties);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().post()
                    .uri("/repositories/{repo}/objects/{objectId}/versions",
                            session.getRepository(), objectId)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response == null) {
                throw new DfcBridgeException(ERROR_CODE, "No response from checkin");
            }

            return extractObjectInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to checkin object: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (DfcBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to checkin object: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo createObject(String sessionId, CreateObjectRequest request) {
        log.debug("Creating object of type {} via REST", request.getObjectType());

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            // Build the create payload
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> properties = new HashMap<>();

            properties.put("r_object_type", request.getObjectType());
            if (request.getObjectName() != null) {
                properties.put("object_name", request.getObjectName());
            }
            if (request.getAttributes() != null) {
                properties.putAll(request.getAttributes());
            }
            payload.put("properties", properties);

            // Determine the endpoint based on object type
            String endpoint;
            if (request.getObjectType().equals("dm_folder") ||
                request.getObjectType().endsWith("_folder")) {
                endpoint = "/repositories/{repo}/folders/{folderId}/folders";
            } else if (request.getObjectType().equals("dm_cabinet") ||
                       request.getObjectType().endsWith("_cabinet")) {
                endpoint = "/repositories/{repo}/cabinets";
            } else {
                endpoint = "/repositories/{repo}/folders/{folderId}/documents";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response;

            if (endpoint.contains("{folderId}")) {
                // Need a folder path/ID for non-cabinet objects
                String folderId = resolveFolderId(session, request.getFolderPath());
                response = session.getWebClient().post()
                        .uri(endpoint, session.getRepository(), folderId)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(30));
            } else {
                response = session.getWebClient().post()
                        .uri(endpoint, session.getRepository())
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(30));
            }

            if (response == null) {
                throw new DfcBridgeException(ERROR_CODE, "No response from create");
            }

            return extractObjectInfo(response);

        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to create object: " + e.getResponseBodyAsString(), e);
        } catch (DfcBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to create object: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String sessionId, String objectId, boolean allVersions) {
        log.debug("Deleting object {} via REST (allVersions={})", objectId, allVersions);

        RestSessionHolder session = sessionService.getRestSession(sessionId);

        try {
            session.getWebClient().delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repositories/{repo}/objects/{objectId}")
                            .queryParam("del-version", allVersions ? "all" : "selected")
                            .build(session.getRepository(), objectId))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(30));

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to delete object: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to delete object: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a folder path to its object ID.
     */
    @SuppressWarnings("unchecked")
    private String resolveFolderId(RestSessionHolder session, String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new DfcBridgeException(ERROR_CODE, "Folder path is required for non-cabinet objects");
        }

        // If it looks like an object ID (16 hex chars), use it directly
        if (folderPath.matches("[0-9a-fA-F]{16}")) {
            return folderPath;
        }

        try {
            // Query for the folder by path
            Map<String, Object> response = session.getWebClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repositories/{repo}/folders")
                            .queryParam("folder-path", folderPath)
                            .build(session.getRepository()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            if (response != null) {
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");
                if (entries != null && !entries.isEmpty()) {
                    Map<String, Object> firstEntry = entries.get(0);
                    String id = (String) firstEntry.get("id");
                    if (id != null && id.contains("/")) {
                        // Extract the ID from the URL
                        return id.substring(id.lastIndexOf('/') + 1);
                    }
                }
            }

            throw new ObjectNotFoundException("Folder not found: " + folderPath);

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException(ERROR_CODE,
                    "Failed to resolve folder path: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ObjectInfo extractObjectInfo(Map<String, Object> response) {
        Map<String, Object> content = (Map<String, Object>) response.get("content");
        if (content == null) {
            content = response; // Response might be the content directly
        }

        Map<String, Object> properties = (Map<String, Object>) content.get("properties");
        if (properties == null) {
            properties = new HashMap<>();
        }

        String objectId = (String) properties.getOrDefault("r_object_id", "");
        String type = (String) properties.getOrDefault("r_object_type", "");
        String name = (String) properties.getOrDefault("object_name", "");

        return ObjectInfo.builder()
                .objectId(objectId)
                .type(type)
                .name(name)
                .attributes(properties)
                .build();
    }

    @SuppressWarnings("unchecked")
    private TypeInfo extractTypeInfo(Map<String, Object> response) {
        // REST API returns type info at top level, not nested in "properties"
        String name = (String) response.getOrDefault("name", "");
        String category = (String) response.getOrDefault("category", "");

        // Super type comes from "parent" URL - extract type name from URL
        // Format: http://host/dctm-rest/repositories/REPO/types/dm_sysobject
        String superType = "";
        String parentUrl = (String) response.get("parent");
        if (parentUrl != null && !parentUrl.isEmpty()) {
            int lastSlash = parentUrl.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < parentUrl.length() - 1) {
                superType = parentUrl.substring(lastSlash + 1);
            }
        }

        // Determine if system type based on category or name prefix
        boolean systemType = "standard".equals(category) ||
                            (name.startsWith("dm_") || name.startsWith("dmi_"));

        // Attributes are in "properties" array (not "attributes")
        List<TypeInfo.AttributeInfo> attributes = new ArrayList<>();
        List<Map<String, Object>> propList =
                (List<Map<String, Object>>) response.get("properties");

        if (propList != null) {
            for (Map<String, Object> prop : propList) {
                Object lengthObj = prop.get("length");
                int length = 0;
                if (lengthObj instanceof Number) {
                    length = ((Number) lengthObj).intValue();
                }

                // REST API may provide 'inherited' or 'is_inherited' field
                boolean inherited = Boolean.TRUE.equals(prop.get("inherited")) ||
                                   Boolean.TRUE.equals(prop.get("is_inherited"));

                attributes.add(TypeInfo.AttributeInfo.builder()
                        .name((String) prop.get("name"))
                        .dataType((String) prop.get("type"))
                        .length(length)
                        .repeating(Boolean.TRUE.equals(prop.get("repeating")))
                        .required(Boolean.TRUE.equals(prop.get("notnull")))
                        .defaultValue(null) // REST API doesn't provide default value inline
                        .inherited(inherited)
                        .build());
            }
        }

        return TypeInfo.builder()
                .name(name)
                .superType(superType)
                .systemType(systemType)
                .category(category)
                .attributes(attributes)
                .build();
    }

    /**
     * Extracts ObjectInfo from cabinet/folder list entry format.
     * The list entries have a different structure than full object responses.
     */
    private ObjectInfo extractCabinetInfo(Map<String, Object> entry) {
        // Entry format: {id, title, summary, ...}
        // summary format: "dm_cabinet 0c04719980000107"
        String title = (String) entry.getOrDefault("title", "");
        String summary = (String) entry.getOrDefault("summary", "");
        String id = (String) entry.getOrDefault("id", "");

        // Extract object ID from id URL or summary
        String objectId = "";
        if (id.contains("/")) {
            objectId = id.substring(id.lastIndexOf('/') + 1);
        }

        // Extract type from summary (format: "type_name object_id")
        String type = "";
        if (summary.contains(" ")) {
            type = summary.substring(0, summary.indexOf(' '));
        }

        return ObjectInfo.builder()
                .objectId(objectId)
                .type(type)
                .name(title)
                .build();
    }
}
