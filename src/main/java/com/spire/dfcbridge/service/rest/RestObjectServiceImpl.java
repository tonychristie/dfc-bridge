package com.spire.dfcbridge.service.rest;

import com.spire.dfcbridge.dto.ApiRequest;
import com.spire.dfcbridge.dto.ApiResponse;
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
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/repositories/{repo}/types");
                        if (pattern != null && !pattern.isEmpty()) {
                            builder.queryParam("filter", "starts-with(name,'" + pattern + "')");
                        }
                        return builder.build(session.getRepository());
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));

            List<TypeInfo> results = new ArrayList<>();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null) {
                    for (Map<String, Object> entry : entries) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content =
                                (Map<String, Object>) entry.get("content");
                        if (content != null) {
                            results.add(extractTypeInfo(content));
                        }
                    }
                }
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

    @Override
    public ApiResponse executeApi(ApiRequest request) {
        // Direct DFC API method invocation is not supported via REST
        throw new DfcBridgeException("NOT_SUPPORTED",
                "Direct API method execution requires a DFC connection. " +
                "Use REST-specific endpoints or switch to a DFC backend.");
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
        Map<String, Object> properties = (Map<String, Object>) response.get("properties");
        if (properties == null) {
            properties = response;
        }

        String name = (String) properties.getOrDefault("name", "");
        String superType = (String) properties.getOrDefault("super_name", "");

        List<TypeInfo.AttributeInfo> attributes = new ArrayList<>();
        List<Map<String, Object>> attrList =
                (List<Map<String, Object>>) properties.get("attributes");

        if (attrList != null) {
            for (Map<String, Object> attr : attrList) {
                attributes.add(TypeInfo.AttributeInfo.builder()
                        .name((String) attr.get("name"))
                        .dataType((String) attr.get("type"))
                        .length(((Number) attr.getOrDefault("length", 0)).intValue())
                        .repeating(Boolean.TRUE.equals(attr.get("repeating")))
                        .required(Boolean.TRUE.equals(attr.get("not_null")))
                        .build());
            }
        }

        return TypeInfo.builder()
                .name(name)
                .superType(superType)
                .attributes(attributes)
                .build();
    }
}
