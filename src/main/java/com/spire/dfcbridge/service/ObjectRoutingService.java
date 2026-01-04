package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.ApiRequest;
import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.CreateObjectRequest;
import com.spire.dfcbridge.dto.UpdateObjectRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.model.ObjectInfo;
import com.spire.dfcbridge.model.TypeInfo;
import com.spire.dfcbridge.service.impl.ObjectServiceImpl;
import com.spire.dfcbridge.service.rest.RestObjectServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Routes object operations to the appropriate backend (DFC or REST) based on the session type.
 *
 * <p>This service determines which Object implementation to use by checking the session's
 * backend type via {@link SessionRoutingService#getSessionBackendType(String)}.
 */
@Service
@Primary
public class ObjectRoutingService implements ObjectService {

    private static final Logger log = LoggerFactory.getLogger(ObjectRoutingService.class);

    private final SessionRoutingService sessionRoutingService;
    private final ObjectServiceImpl dfcObjectService;
    private final RestObjectServiceImpl restObjectService;

    public ObjectRoutingService(
            SessionRoutingService sessionRoutingService,
            @Nullable ObjectServiceImpl dfcObjectService,
            @Nullable RestObjectServiceImpl restObjectService) {
        this.sessionRoutingService = sessionRoutingService;
        this.dfcObjectService = dfcObjectService;
        this.restObjectService = restObjectService;

        log.info("Object routing initialized - DFC: {}, REST: {}",
                dfcObjectService != null ? "available" : "unavailable",
                restObjectService != null ? "available" : "unavailable");
    }

    private ObjectService getServiceForSession(String sessionId) {
        String backendType = sessionRoutingService.getSessionBackendType(sessionId);

        if ("rest".equals(backendType)) {
            if (restObjectService == null) {
                throw new DfcBridgeException("OBJECT_ERROR",
                        "REST Object service is not available");
            }
            return restObjectService;
        } else if ("dfc".equals(backendType)) {
            if (dfcObjectService == null) {
                throw new DfcBridgeException("OBJECT_ERROR",
                        "DFC Object service is not available");
            }
            return dfcObjectService;
        } else {
            throw new DfcBridgeException("OBJECT_ERROR",
                    "Unknown session backend type: " + backendType);
        }
    }

    @Override
    public List<ObjectInfo> getCabinets(String sessionId) {
        log.debug("Routing getCabinets to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).getCabinets(sessionId);
    }

    @Override
    public ObjectInfo getObject(String sessionId, String objectId) {
        log.debug("Routing getObject to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).getObject(sessionId, objectId);
    }

    @Override
    public ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request) {
        log.debug("Routing updateObject to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).updateObject(sessionId, objectId, request);
    }

    @Override
    public List<ObjectInfo> listFolderContents(String sessionId, String folderPath) {
        log.debug("Routing listFolderContents to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).listFolderContents(sessionId, folderPath);
    }

    @Override
    public TypeInfo getTypeInfo(String sessionId, String typeName) {
        log.debug("Routing getTypeInfo to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).getTypeInfo(sessionId, typeName);
    }

    @Override
    public List<TypeInfo> listTypes(String sessionId, String pattern) {
        log.debug("Routing listTypes to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).listTypes(sessionId, pattern);
    }

    @Override
    public ApiResponse executeApi(ApiRequest request) {
        log.debug("Routing executeApi to {} backend", sessionRoutingService.getSessionBackendType(request.getSessionId()));
        return getServiceForSession(request.getSessionId()).executeApi(request);
    }

    @Override
    public List<ObjectInfo> listFolderContentsById(String sessionId, String folderId) {
        log.debug("Routing listFolderContentsById to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).listFolderContentsById(sessionId, folderId);
    }

    @Override
    public ObjectInfo checkout(String sessionId, String objectId) {
        log.debug("Routing checkout to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).checkout(sessionId, objectId);
    }

    @Override
    public void cancelCheckout(String sessionId, String objectId) {
        log.debug("Routing cancelCheckout to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        getServiceForSession(sessionId).cancelCheckout(sessionId, objectId);
    }

    @Override
    public ObjectInfo checkin(String sessionId, String objectId, String versionLabel) {
        log.debug("Routing checkin to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).checkin(sessionId, objectId, versionLabel);
    }

    @Override
    public ObjectInfo createObject(String sessionId, CreateObjectRequest request) {
        log.debug("Routing createObject to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        return getServiceForSession(sessionId).createObject(sessionId, request);
    }

    @Override
    public void deleteObject(String sessionId, String objectId, boolean allVersions) {
        log.debug("Routing deleteObject to {} backend", sessionRoutingService.getSessionBackendType(sessionId));
        getServiceForSession(sessionId).deleteObject(sessionId, objectId, allVersions);
    }
}
