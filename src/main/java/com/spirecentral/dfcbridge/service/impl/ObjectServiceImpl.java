package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.dto.ApiRequest;
import com.spirecentral.dfcbridge.dto.ApiResponse;
import com.spirecentral.dfcbridge.dto.CreateObjectRequest;
import com.spirecentral.dfcbridge.dto.UpdateObjectRequest;
import com.spirecentral.dfcbridge.exception.DfcBridgeException;
import com.spirecentral.dfcbridge.exception.ObjectNotFoundException;
import com.spirecentral.dfcbridge.model.AttributeValue;
import com.spirecentral.dfcbridge.model.ObjectInfo;
import com.spirecentral.dfcbridge.model.TypeInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.service.ObjectService;
import com.spirecentral.dfcbridge.util.DfcExceptionUtils;
import com.spirecentral.dfcbridge.util.DfcTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DFC implementation of ObjectService using reflection to call DFC APIs.
 *
 * <p>Requires DFC classes to be available on the classpath at runtime.
 */
@Service
public class ObjectServiceImpl implements ObjectService {

    private static final Logger log = LoggerFactory.getLogger(ObjectServiceImpl.class);

    private static final String DFC_ID_CLASS = "com.documentum.fc.common.DfId";
    private static final String DFC_ID_IFACE = "com.documentum.fc.common.IDfId";
    private static final String DFC_TIME_CLASS = "com.documentum.fc.common.DfTime";
    private static final String DFC_TIME_IFACE = "com.documentum.fc.common.IDfTime";
    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";
    private static final String DFC_SYSOBJ_IFACE = "com.documentum.fc.client.IDfSysObject";
    private static final String DFC_FOLDER_IFACE = "com.documentum.fc.client.IDfFolder";
    private static final String DFC_TYPE_IFACE = "com.documentum.fc.client.IDfType";
    private static final String DFC_ACL_IFACE = "com.documentum.fc.client.IDfACL";
    private static final String DFC_COLLECTION_IFACE = "com.documentum.fc.client.IDfCollection";

    private static final int DM_INTEGER = 1;
    private static final int DM_DOUBLE = 5;

    private final DfcSessionService sessionService;

    public ObjectServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public List<ObjectInfo> getCabinets(String sessionId) {
        log.debug("Getting cabinets via DFC");

        return sessionService.withSession(sessionId, dfSession -> {
            List<ObjectInfo> cabinets = new ArrayList<>();

            try {
                // Query for all cabinets
                String dql = "SELECT r_object_id, object_name, r_object_type FROM dm_cabinet ORDER BY object_name";

                Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
                Class<?> dfQueryClass = Class.forName("com.documentum.fc.client.DfQuery");
                Object query = dfQueryClass.getDeclaredConstructor().newInstance();

                Method setDqlMethod = dfQueryClass.getMethod("setDQL", String.class);
                setDqlMethod.invoke(query, dql);

                Class<?> queryInterface = Class.forName("com.documentum.fc.client.IDfQuery");
                Method executeMethod = queryInterface.getMethod("execute", sessionClass, int.class);
                Object collection = executeMethod.invoke(query, dfSession, 0);

                Class<?> collectionClass = collection.getClass();
                Method nextMethod = collectionClass.getMethod("next");
                Method closeMethod = collectionClass.getMethod("close");
                Method getStringMethod = collectionClass.getMethod("getString", String.class);

                try {
                    while ((Boolean) nextMethod.invoke(collection)) {
                        String objectId = (String) getStringMethod.invoke(collection, "r_object_id");
                        String objectName = (String) getStringMethod.invoke(collection, "object_name");
                        String objectType = (String) getStringMethod.invoke(collection, "r_object_type");

                        cabinets.add(ObjectInfo.builder()
                                .objectId(objectId)
                                .name(objectName)
                                .type(objectType)
                                .build());
                    }
                } finally {
                    closeMethod.invoke(collection);
                }

                return cabinets;

            } catch (Exception e) {
                throw new DfcBridgeException("CABINET_ERROR",
                        "Failed to get cabinets: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public ObjectInfo getObject(String sessionId, String objectId) {
        log.debug("Getting object: {}", objectId);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                Object sysObject = getObjectById(dfSession, objectId);

                return extractObjectInfo(sysObject, objectId);

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("OBJECT_ERROR",
                        "Failed to get object: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request) {
        log.debug("Updating object: {} with {} attributes", objectId, request.getAttributes().size());

        return sessionService.withSession(sessionId, dfSession -> {
            Object sysObject = null;
            boolean unsaved = false;
            try {
                sysObject = getObjectById(dfSession, objectId);
                Map<String, Integer> dataTypes = buildTypeAttrDataTypes(sysObject);

                unsaved = true;
                for (Map.Entry<String, Object> attr : request.getAttributes().entrySet()) {
                    setObjectAttribute(sysObject, attr.getKey(), attr.getValue(), dataTypes);
                }

                if (request.isSave()) {
                    Method saveMethod = sysObject.getClass().getMethod("save");
                    saveMethod.invoke(sysObject);
                    unsaved = false;
                }

                ObjectInfo info = extractObjectInfo(sysObject, objectId);
                if (unsaved) {
                    unsaved = false;
                    sysObject.getClass().getMethod("revert").invoke(sysObject);
                }
                return info;

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                if (unsaved) {
                    revertAfterFailure(sysObject, objectId);
                }
                throw new DfcBridgeException("UPDATE_ERROR",
                        "Failed to update object: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public List<ObjectInfo> listFolderContents(String sessionId, String folderPath) {
        log.debug("Listing folder contents: {}", folderPath);

        return sessionService.withSession(sessionId, dfSession -> {
            List<ObjectInfo> contents = new ArrayList<>();

            try {
                // Get folder by path
                Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
                Method getFolderByPathMethod = sessionClass.getMethod("getFolderByPath", String.class);
                Object folder = getFolderByPathMethod.invoke(dfSession, folderPath);

                if (folder == null) {
                    throw new ObjectNotFoundException("Folder not found: " + folderPath);
                }

                // Get folder contents
                Class<?> folderClass = Class.forName(DFC_FOLDER_IFACE);
                Method getContentsMethod = folderClass.getMethod("getContents", String.class);
                Object collection = getContentsMethod.invoke(folder, null);

                // Iterate through collection
                Class<?> collectionClass = collection.getClass();
                Method nextMethod = collectionClass.getMethod("next");
                Method closeMethod = collectionClass.getMethod("close");

                try {
                    while ((Boolean) nextMethod.invoke(collection)) {
                        Method getIdMethod = collectionClass.getMethod("getId", String.class);
                        Object id = getIdMethod.invoke(collection, "r_object_id");
                        String objectId = id.toString();

                        // Get brief info for each object
                        Object obj = getObjectById(dfSession, objectId);
                        contents.add(extractObjectInfo(obj, objectId));
                    }
                } finally {
                    closeMethod.invoke(collection);
                }

                return contents;

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("FOLDER_ERROR",
                        "Failed to list folder contents: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public TypeInfo getTypeInfo(String sessionId, String typeName) {
        log.debug("Getting type info: {}", typeName);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
                Method getTypeMethod = sessionClass.getMethod("getType", String.class);
                Object type = getTypeMethod.invoke(dfSession, typeName);

                if (type == null) {
                    throw new ObjectNotFoundException("Type not found: " + typeName);
                }

                return extractTypeInfo(type);

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("TYPE_ERROR",
                        "Failed to get type info: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public List<TypeInfo> listTypes(String sessionId, String pattern) {
        log.debug("Listing types with pattern: {}", pattern);

        return sessionService.withSession(sessionId, dfSession -> {
            List<TypeInfo> types = new ArrayList<>();

            try {
                // Query for types
                String dql = "SELECT name FROM dm_type";
                if (pattern != null && !pattern.isEmpty()) {
                    // Escape single quotes to prevent SQL injection
                    String sanitizedPattern = DfcTypeUtils.sanitizeDqlString(pattern).replace("*", "%");
                    dql += " WHERE name LIKE '" + sanitizedPattern + "'";
                }
                dql += " ORDER BY name";

                Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);

                // Create and execute query using the same pattern as DqlService
                Class<?> dfQueryClass = Class.forName("com.documentum.fc.client.DfQuery");
                Object query = dfQueryClass.getDeclaredConstructor().newInstance();

                Method setDqlMethod = dfQueryClass.getMethod("setDQL", String.class);
                setDqlMethod.invoke(query, dql);

                Class<?> queryInterface = Class.forName("com.documentum.fc.client.IDfQuery");
                Method executeMethod = queryInterface.getMethod("execute", sessionClass, int.class);
                Object collection = executeMethod.invoke(query, dfSession, 0);

                Class<?> collectionClass = collection.getClass();
                Method nextMethod = collectionClass.getMethod("next");
                Method closeMethod = collectionClass.getMethod("close");
                Method getStringMethod = collectionClass.getMethod("getString", String.class);

                try {
                    while ((Boolean) nextMethod.invoke(collection)) {
                        String typeName = (String) getStringMethod.invoke(collection, "name");
                        try {
                            types.add(getTypeInfo(sessionId, typeName));
                        } catch (Exception e) {
                            // Skip types that can't be loaded
                            log.debug("Could not load type {}: {}", typeName, e.getMessage());
                        }
                    }
                } finally {
                    closeMethod.invoke(collection);
                }

                return types;

            } catch (Exception e) {
                throw new DfcBridgeException("TYPE_LIST_ERROR",
                        "Failed to list types: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public ApiResponse executeApi(ApiRequest request) {
        log.debug("Executing API: {} on {}", request.getMethod(),
                request.getObjectId() != null ? request.getObjectId() : request.getTypeName());

        long startTime = System.currentTimeMillis();
        return sessionService.withSession(request.getSessionId(), dfSession -> {
            try {
                Object target;
                if (request.getObjectId() != null) {
                    target = getObjectById(dfSession, request.getObjectId());
                } else if (request.getTypeName() != null) {
                    Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
                    Method getTypeMethod = sessionClass.getMethod("getType", String.class);
                    target = getTypeMethod.invoke(dfSession, request.getTypeName());
                    if (target == null) {
                        throw new ObjectNotFoundException("Type not found: " + request.getTypeName());
                    }
                } else {
                    target = dfSession;
                }

                Object result = invokeMethod(target, request.getMethod(), request.getArgs());
                Object resultValue = toResultValue(result);

                long executionTime = System.currentTimeMillis() - startTime;

                return ApiResponse.builder()
                        .result(resultValue)
                        .resultType(result != null ? result.getClass().getSimpleName() : "null")
                        .executionTimeMs(executionTime)
                        .build();

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("API_ERROR",
                        "Failed to execute API: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public List<ObjectInfo> listFolderContentsById(String sessionId, String folderId) {
        log.debug("Listing folder contents by ID: {}", folderId);

        return sessionService.withSession(sessionId, dfSession -> {
            List<ObjectInfo> contents = new ArrayList<>();

            try {
                Object folder = getObjectById(dfSession, folderId);

                // Get folder contents
                Class<?> folderClass = Class.forName(DFC_FOLDER_IFACE);
                Method getContentsMethod = folderClass.getMethod("getContents", String.class);
                Object collection = getContentsMethod.invoke(folder, (String) null);

                // Iterate through collection
                Class<?> collectionClass = collection.getClass();
                Method nextMethod = collectionClass.getMethod("next");
                Method closeMethod = collectionClass.getMethod("close");

                try {
                    while ((Boolean) nextMethod.invoke(collection)) {
                        Method getIdMethod = collectionClass.getMethod("getId", String.class);
                        Object id = getIdMethod.invoke(collection, "r_object_id");
                        String objectId = id.toString();

                        // Get brief info for each object
                        Object obj = getObjectById(dfSession, objectId);
                        contents.add(extractObjectInfo(obj, objectId));
                    }
                } finally {
                    closeMethod.invoke(collection);
                }

                return contents;

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("FOLDER_ERROR",
                        "Failed to list folder contents: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public ObjectInfo checkout(String sessionId, String objectId) {
        log.debug("Checking out object: {}", objectId);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                Object sysObject = getObjectById(dfSession, objectId);

                // Call checkout
                Method checkoutMethod = sysObject.getClass().getMethod("checkout");
                checkoutMethod.invoke(sysObject);

                return extractObjectInfo(sysObject, objectId);

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("CHECKOUT_ERROR",
                        "Failed to checkout object: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public void cancelCheckout(String sessionId, String objectId) {
        log.debug("Cancelling checkout of object: {}", objectId);

        sessionService.withSession(sessionId, dfSession -> {
            try {
                Object sysObject = getObjectById(dfSession, objectId);

                // Call cancelCheckout
                Method cancelCheckoutMethod = sysObject.getClass().getMethod("cancelCheckout");
                cancelCheckoutMethod.invoke(sysObject);
                return null;

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("CANCEL_CHECKOUT_ERROR",
                        "Failed to cancel checkout: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public ObjectInfo checkin(String sessionId, String objectId, String versionLabel) {
        log.debug("Checking in object: {} with label: {}", objectId, versionLabel);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                Object sysObject = getObjectById(dfSession, objectId);

                // Call checkin with version label
                Method checkinMethod = sysObject.getClass().getMethod("checkin", boolean.class, String.class);
                Object newId = checkinMethod.invoke(sysObject, false, versionLabel);

                // Get the new version
                String newObjectId = newId.toString();
                Object newObject = getObjectById(dfSession, newObjectId);
                return extractObjectInfo(newObject, newObjectId);

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("CHECKIN_ERROR",
                        "Failed to checkin object: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public ObjectInfo createObject(String sessionId, CreateObjectRequest request) {
        log.debug("Creating object of type: {}", request.getObjectType());

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);

                // Create new object
                Method newObjectMethod = sessionClass.getMethod("newObject", String.class);
                Object newObject = newObjectMethod.invoke(dfSession, request.getObjectType());

                // Set object name if provided
                if (request.getObjectName() != null) {
                    Method setObjectNameMethod = newObject.getClass().getMethod("setObjectName", String.class);
                    setObjectNameMethod.invoke(newObject, request.getObjectName());
                }

                // Set additional attributes if provided
                if (request.getAttributes() != null) {
                    Map<String, Integer> dataTypes = buildTypeAttrDataTypes(newObject);
                    for (Map.Entry<String, Object> attr : request.getAttributes().entrySet()) {
                        setObjectAttribute(newObject, attr.getKey(), attr.getValue(), dataTypes);
                    }
                }

                // Link to folder if path provided
                if (request.getFolderPath() != null && !request.getFolderPath().isEmpty()) {
                    Method linkMethod = newObject.getClass().getMethod("link", String.class);
                    linkMethod.invoke(newObject, request.getFolderPath());
                }

                // Save the object
                Method saveMethod = newObject.getClass().getMethod("save");
                saveMethod.invoke(newObject);

                // Get the object ID
                Method getObjectIdMethod = newObject.getClass().getMethod("getObjectId");
                Object objectId = getObjectIdMethod.invoke(newObject);
                String newObjectId = objectId.toString();

                return extractObjectInfo(newObject, newObjectId);

            } catch (Exception e) {
                throw new DfcBridgeException("CREATE_ERROR",
                        "Failed to create object: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public void deleteObject(String sessionId, String objectId, boolean allVersions) {
        log.debug("Deleting object: {} (allVersions={})", objectId, allVersions);

        sessionService.withSession(sessionId, dfSession -> {
            try {
                Object sysObject = getObjectById(dfSession, objectId);

                if (allVersions) {
                    // Delete all versions
                    Method destroyAllVersionsMethod = sysObject.getClass().getMethod("destroyAllVersions");
                    destroyAllVersionsMethod.invoke(sysObject);
                } else {
                    // Delete just this version
                    Method destroyMethod = sysObject.getClass().getMethod("destroy");
                    destroyMethod.invoke(sysObject);
                }
                return null;

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("DELETE_ERROR",
                        "Failed to delete object: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    private Object getObjectById(Object dfSession, String objectId) throws Exception {
        Class<?> dfIdClass = Class.forName(DFC_ID_CLASS);
        Object dfId = dfIdClass.getConstructor(String.class).newInstance(objectId);

        Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
        Method getObjectMethod = sessionClass.getMethod("getObject", Class.forName(DFC_ID_IFACE));

        try {
            return getObjectMethod.invoke(dfSession, dfId);
        } catch (InvocationTargetException e) {
            if (DfcExceptionUtils.isInstanceOf(e.getTargetException(), DfcExceptionUtils.DFC_ID_NOT_FOUND_EXCEPTION)) {
                throw new ObjectNotFoundException(objectId);
            }
            throw e;
        }
    }

    private ObjectInfo extractObjectInfo(Object sysObject, String objectId) throws Exception {
        String typeName = (String) invokeReflection(sysObject, "getTypeName");
        String objectName = getNameForType(sysObject, typeName);
        int permit = getPermitSafe(sysObject);

        // Get all attributes with type metadata
        Map<String, AttributeValue> attributes = extractAllAttributes(sysObject);

        ObjectInfo.ObjectInfoBuilder builder = ObjectInfo.builder()
                .objectId(objectId)
                .type(typeName)
                .name(objectName)
                .attributes(attributes);

        // Only add permission info if the object supports it (sysobjects)
        if (permit >= 0) {
            builder.permissionLevel(permit)
                   .permissionLabel(DfcTypeUtils.permitToLabel(permit));
        }

        return builder.build();
    }

    private int getPermitSafe(Object dfObject) {
        try {
            return (Integer) invokeReflection(dfObject, "getPermit");
        } catch (Exception e) {
            // Non-sysobject types don't have getPermit
            return -1;
        }
    }

    private String getNameForType(Object dfObject, String typeName) throws Exception {
        // Check for type-specific name attributes in order of preference
        // Different Documentum types use different attributes for their "name"
        String[] nameAttrs = {"object_name", "name", "group_name", "user_name", "relation_name"};

        for (String attr : nameAttrs) {
            if (hasAttribute(dfObject, attr)) {
                String value = (String) invokeReflection(dfObject, "getString",
                        new Class<?>[]{String.class}, attr);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        // Fallback: return null if no name attribute found
        return null;
    }

    private boolean hasAttribute(Object dfObject, String attrName) {
        try {
            return (Boolean) invokeReflection(dfObject, "hasAttr",
                    new Class<?>[]{String.class}, attrName);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, AttributeValue> extractAllAttributes(Object sysObject) throws Exception {
        Map<String, AttributeValue> attributes = new HashMap<>();

        // Get the type definition to retrieve accurate data types
        // The object's getAttr().getDataType() can return incorrect values (e.g., double instead of id)
        // The type definition is the authoritative source for attribute data types
        Map<String, Integer> typeAttrDataTypes = buildTypeAttrDataTypes(sysObject);

        int attrCount = (Integer) invokeReflection(sysObject, "getAttrCount");

        for (int i = 0; i < attrCount; i++) {
            Object attr = invokeReflection(sysObject, "getAttr", new Class<?>[]{int.class}, i);
            String attrName = (String) invokeReflection(attr, "getName");
            boolean isRepeating = (Boolean) invokeReflection(attr, "isRepeating");

            // Use type definition data type if available, otherwise fall back to object's attr
            int dataType = typeAttrDataTypes.getOrDefault(attrName,
                    (Integer) invokeReflection(attr, "getDataType"));
            String typeName = dataTypeToName(dataType);

            try {
                Object value;
                if (isRepeating) {
                    value = extractRepeatingAttributeValue(sysObject, attrName, dataType);
                    attributes.put(attrName, AttributeValue.repeating(typeName, value));
                } else {
                    value = extractSingleAttributeValue(sysObject, attrName, dataType);
                    if (value != null) {
                        attributes.put(attrName, AttributeValue.single(typeName, value));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract attribute '{}' (type={}): {}",
                        attrName, typeName, e.getMessage());
            }
        }

        return attributes;
    }

    /**
     * Build a map of attribute name to data type from the object's type definition.
     * The type definition provides the correct canonical data types for all attributes.
     */
    private Map<String, Integer> buildTypeAttrDataTypes(Object sysObject) {
        Map<String, Integer> dataTypes = new HashMap<>();
        try {
            // Get the session from the object
            Object session = invokeReflection(sysObject, "getSession");

            // Get the object's type name
            String objectTypeName = (String) invokeReflection(sysObject, "getTypeName");

            // Get the type definition from the session
            Object type = invokeReflection(session, "getType", new Class<?>[]{String.class}, objectTypeName);
            if (type == null) {
                log.debug("Could not get type definition for {}", objectTypeName);
                return dataTypes;
            }

            // Iterate through the type's attributes and build the data type map
            int typeAttrCount = (Integer) invokeReflection(type, "getTypeAttrCount");
            for (int i = 0; i < typeAttrCount; i++) {
                Object typeAttr = invokeReflection(type, "getTypeAttr", new Class<?>[]{int.class}, i);
                String attrName = (String) invokeReflection(typeAttr, "getName");
                int dataType = (Integer) invokeReflection(typeAttr, "getDataType");
                dataTypes.put(attrName, dataType);
            }
        } catch (Exception e) {
            log.warn("Could not build type attribute data types: {}", e.getMessage());
        }
        return dataTypes;
    }

    /**
     * Convert DFC data type constant to human-readable type name.
     */
    private String dataTypeToName(int dataType) {
        // DFC data type constants (from IDfAttr):
        // DM_BOOLEAN=0, DM_INTEGER=1, DM_STRING=2, DM_ID=3, DM_TIME=4, DM_DOUBLE=5
        return switch (dataType) {
            case 0 -> "boolean";
            case 1 -> "integer";
            case 2 -> "string";
            case 3 -> "id";
            case 4 -> "time";
            case 5 -> "double";
            default -> "unknown";
        };
    }

    private Object extractSingleAttributeValue(Object sysObject, String attrName, int dataType) throws Exception {
        // For ID attributes, use getString to get consistent string representation
        // getId returns IDfId which may lose leading zeros when serialized
        if (dataType == 3) { // DM_ID type
            return invokeReflection(sysObject, "getString", new Class<?>[]{String.class}, attrName);
        }

        String methodName = getGetterMethodName(dataType);
        Object value = invokeReflection(sysObject, methodName, new Class<?>[]{String.class}, attrName);

        // Convert IDfTime to string for JSON serialization
        if (value != null && dataType == 4) { // TIME
            value = value.toString();
        }

        return value;
    }

    private Object extractRepeatingAttributeValue(Object sysObject, String attrName, int dataType) throws Exception {
        int count = (Integer) invokeReflection(sysObject, "getValueCount", new Class<?>[]{String.class}, attrName);
        if (count == 0) {
            return new ArrayList<>();
        }

        List<Object> values = new ArrayList<>();

        // For ID attributes, use getRepeatingString to get consistent string representation
        // getRepeatingId returns IDfId which may lose leading zeros when serialized
        if (dataType == 3) { // DM_ID type
            for (int i = 0; i < count; i++) {
                Object value = invokeReflection(sysObject, "getRepeatingString",
                        new Class<?>[]{String.class, int.class}, attrName, i);
                values.add(value);
            }
            return values;
        }

        String methodName = getRepeatingGetterMethodName(dataType);

        for (int i = 0; i < count; i++) {
            Object value = invokeReflection(sysObject, methodName,
                    new Class<?>[]{String.class, int.class}, attrName, i);
            // Convert IDfTime to string for JSON serialization
            if (value != null && dataType == 4) { // TIME
                value = value.toString();
            }
            values.add(value);
        }
        return values;
    }

    private String getGetterMethodName(int dataType) {
        // DFC data type constants: DM_BOOLEAN=0, DM_INTEGER=1, DM_STRING=2, DM_ID=3, DM_TIME=4, DM_DOUBLE=5
        return switch (dataType) {
            case 0 -> "getBoolean";   // DM_BOOLEAN
            case 1 -> "getInt";       // DM_INTEGER
            case 2 -> "getString";    // DM_STRING
            case 3 -> "getId";        // DM_ID
            case 4 -> "getTime";      // DM_TIME
            case 5 -> "getDouble";    // DM_DOUBLE
            default -> "getString";
        };
    }

    private String getRepeatingGetterMethodName(int dataType) {
        // DFC data type constants: DM_BOOLEAN=0, DM_INTEGER=1, DM_STRING=2, DM_ID=3, DM_TIME=4, DM_DOUBLE=5
        return switch (dataType) {
            case 0 -> "getRepeatingBoolean";   // DM_BOOLEAN
            case 1 -> "getRepeatingInt";       // DM_INTEGER
            case 2 -> "getRepeatingString";    // DM_STRING
            case 3 -> "getRepeatingId";        // DM_ID
            case 4 -> "getRepeatingTime";      // DM_TIME
            case 5 -> "getRepeatingDouble";    // DM_DOUBLE
            default -> "getRepeatingString";
        };
    }

    private void setObjectAttribute(Object sysObject, String attrName, Object value,
                                    Map<String, Integer> dataTypes) throws Exception {
        boolean isRepeating = (Boolean) invokeReflection(sysObject, "isAttrRepeating",
                new Class<?>[]{String.class}, attrName);
        Integer dataType = dataTypes.get(attrName);
        if (dataType == null) {
            dataType = (Integer) invokeReflection(sysObject, "getAttrDataType",
                    new Class<?>[]{String.class}, attrName);
        }

        if (isRepeating) {
            setRepeatingAttribute(sysObject, attrName, value, dataType);
        } else {
            setScalarAttribute(sysObject, attrName, value, dataType);
        }
    }

    private void setScalarAttribute(Object sysObject, String attrName, Object value, int dataType) throws Exception {
        Method setStringMethod = sysObject.getClass().getMethod("setString", String.class, String.class);
        setStringMethod.invoke(sysObject, attrName, attributeText(attrName, value, dataType));
    }

    private void setRepeatingAttribute(Object sysObject, String attrName, Object value, int dataType) throws Exception {
        Method removeAllMethod = sysObject.getClass().getMethod("removeAll", String.class);
        removeAllMethod.invoke(sysObject, attrName);

        if (value == null) {
            return;
        }
        if (value instanceof List<?> valueList) {
            for (int i = 0; i < valueList.size(); i++) {
                setRepeatingValueAtIndex(sysObject, attrName, i, valueList.get(i), dataType);
            }
        } else {
            setRepeatingValueAtIndex(sysObject, attrName, 0, value, dataType);
        }
    }

    private void setRepeatingValueAtIndex(Object sysObject, String attrName, int index, Object value,
                                          int dataType) throws Exception {
        Method method = sysObject.getClass().getMethod("setRepeatingString", String.class, int.class, String.class);
        method.invoke(sysObject, attrName, index, attributeText(attrName, value, dataType));
    }

    // setString parses the text for every data type, but "" fails on int and double attributes.
    private String attributeText(String attrName, Object value, int dataType) {
        if (value == null) {
            return dataType == DM_INTEGER || dataType == DM_DOUBLE ? "0" : "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).toPlainString();
        }
        String kind = value instanceof List ? "a list" : "an object";
        throw new IllegalArgumentException("Attribute " + attrName + " does not accept " + kind + " as a value");
    }

    private void revertAfterFailure(Object sysObject, String objectId) {
        try {
            sysObject.getClass().getMethod("revert").invoke(sysObject);
        } catch (Exception e) {
            log.warn("Could not revert object {} after a failed update: {}",
                    objectId, DfcExceptionUtils.unwrap(e).getMessage());
        }
    }

    private TypeInfo extractTypeInfo(Object type) throws Exception {
        String name = (String) invokeReflection(type, "getName");
        String superName = (String) invokeReflection(type, "getSuperName");
        // DFC doesn't expose isTypeInternal; determine from naming convention
        boolean isSystem = name.startsWith("dm_") || name.startsWith("dmi_");
        int attrCount = (Integer) invokeReflection(type, "getTypeAttrCount");

        // Determine the number of inherited attributes by getting the super type's attribute count.
        // All attributes at indices 0 to (inheritedAttrCount-1) are inherited from the super type.
        int inheritedAttrCount = 0;
        try {
            Object superType = invokeReflection(type, "getSuperType");
            if (superType != null) {
                inheritedAttrCount = (Integer) invokeReflection(superType, "getTypeAttrCount");
            }
        } catch (Exception e) {
            log.debug("Could not get super type attr count for type {}, defaulting to 0: {}", name, e.getMessage());
        }

        List<TypeInfo.AttributeInfo> attributes = new ArrayList<>();
        for (int i = 0; i < attrCount; i++) {
            Object attr = invokeReflection(type, "getTypeAttr", new Class<?>[]{int.class}, i);
            boolean isInherited = i < inheritedAttrCount;
            attributes.add(extractAttributeInfo(attr, isInherited));
        }

        return TypeInfo.builder()
                .name(name)
                .superType(superName)
                .systemType(isSystem)
                .attributes(attributes)
                .build();
    }

    private TypeInfo.AttributeInfo extractAttributeInfo(Object attr, boolean isInherited) throws Exception {
        return TypeInfo.AttributeInfo.builder()
                .name((String) invokeReflection(attr, "getName"))
                .dataType(DfcTypeUtils.dataTypeToString((Integer) invokeReflection(attr, "getDataType")))
                .length((Integer) invokeReflection(attr, "getLength"))
                .repeating((Boolean) invokeReflection(attr, "isRepeating"))
                .inherited(isInherited)
                .build();
    }

    private Object invokeMethod(Object target, String methodName, List<Object> args) throws Exception {
        List<Object> argList = args != null ? args : List.of();
        Method chosen = null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != argList.size()) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (!acceptsArgs(paramTypes, argList)) {
                continue;
            }
            if (!needsConversion(paramTypes, argList)) {
                chosen = method;
                break;
            }
            if (chosen == null) {
                chosen = method;
            }
        }
        if (chosen == null) {
            throw new NoSuchMethodException("No method " + methodName + " accepts the arguments ("
                    + argTypeNames(argList) + ")");
        }
        chosen.setAccessible(true);
        return chosen.invoke(target, convertArgs(chosen.getParameterTypes(), args));
    }

    private boolean acceptsArgs(Class<?>[] paramTypes, List<Object> args) {
        for (int i = 0; i < paramTypes.length; i++) {
            if (!acceptsArg(paramTypes[i], args.get(i))) {
                return false;
            }
        }
        return true;
    }

    // Method.invoke widens Integer and Long to the primitive parameter type.
    private boolean acceptsArg(Class<?> paramType, Object arg) {
        if (arg == null) {
            return !paramType.isPrimitive();
        }
        if (arg instanceof String && isConvertedParam(paramType)) {
            return true;
        }
        if (paramType == int.class) {
            return arg instanceof Integer;
        }
        if (paramType == long.class) {
            return arg instanceof Integer || arg instanceof Long;
        }
        if (paramType == double.class) {
            return arg instanceof Integer || arg instanceof Long || arg instanceof Double;
        }
        if (paramType == boolean.class) {
            return arg instanceof Boolean;
        }
        return paramType.isInstance(arg);
    }

    private boolean needsConversion(Class<?>[] paramTypes, List<Object> args) {
        for (int i = 0; i < paramTypes.length; i++) {
            if (args.get(i) instanceof String && isConvertedParam(paramTypes[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean isConvertedParam(Class<?> paramType) {
        return DFC_TIME_IFACE.equals(paramType.getName()) || DFC_ID_IFACE.equals(paramType.getName());
    }

    private String argTypeNames(List<Object> args) {
        List<String> names = new ArrayList<>();
        for (Object arg : args) {
            names.add(arg != null ? arg.getClass().getSimpleName() : "null");
        }
        return String.join(", ", names);
    }

    private Object toResultValue(Object result) throws Exception {
        if (result == null || result instanceof String || result instanceof Boolean || result instanceof Number) {
            return result;
        }
        Class<?> collectionClass = loadDfcClass(DFC_COLLECTION_IFACE);
        try {
            return result.toString();
        } finally {
            if (collectionClass != null && collectionClass.isInstance(result)) {
                collectionClass.getMethod("close").invoke(result);
            }
        }
    }

    private Class<?> loadDfcClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Object[] convertArgs(Class<?>[] paramTypes, List<Object> args) throws Exception {
        if (args == null) {
            return new Object[0];
        }
        Object[] converted = args.toArray();
        for (int i = 0; i < converted.length; i++) {
            if (!(converted[i] instanceof String)) {
                continue;
            }
            String value = (String) converted[i];
            String paramType = paramTypes[i].getName();
            if (DFC_TIME_IFACE.equals(paramType)) {
                converted[i] = toDfTime(value);
            } else if (DFC_ID_IFACE.equals(paramType)) {
                converted[i] = Class.forName(DFC_ID_CLASS).getConstructor(String.class).newInstance(value);
            }
        }
        return converted;
    }

    // DfTime(String) does not throw on unparseable text; it returns an invalid time that saves as nulldate.
    private Object toDfTime(String value) throws Exception {
        Object time = Class.forName(DFC_TIME_CLASS).getConstructor(String.class).newInstance(value);
        if (!(Boolean) time.getClass().getMethod("isValid").invoke(time)) {
            throw new IllegalArgumentException("Invalid time value: " + value);
        }
        return time;
    }

    /**
     * Helper method to invoke a method via reflection, handling accessibility.
     * DFC implementation classes are often proxies, so we search through the class,
     * its interfaces, and superclasses to find what we need.
     */
    private Object invokeReflection(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        // First try using getMethod which searches the entire class hierarchy
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (NoSuchMethodException e) {
            // Try getting all methods and finding a match
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) &&
                    java.util.Arrays.equals(method.getParameterTypes(), paramTypes)) {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                }
            }
            // Try searching through all interfaces (DFC proxies implement interfaces)
            for (Class<?> iface : getAllInterfaces(target.getClass())) {
                try {
                    Method method = iface.getMethod(methodName, paramTypes);
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (NoSuchMethodException ignored) {
                    // Try next interface
                }
            }
            throw new NoSuchMethodException(methodName + " on " + target.getClass().getName());
        }
    }

    /**
     * Get all interfaces implemented by a class, including inherited ones.
     */
    private List<Class<?>> getAllInterfaces(Class<?> clazz) {
        List<Class<?>> interfaces = new ArrayList<>();
        while (clazz != null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                if (!interfaces.contains(iface)) {
                    interfaces.add(iface);
                    // Add super-interfaces too
                    for (Class<?> superIface : iface.getInterfaces()) {
                        if (!interfaces.contains(superIface)) {
                            interfaces.add(superIface);
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }

    /**
     * Helper method to invoke a no-arg method via reflection.
     */
    private Object invokeReflection(Object target, String methodName) throws Exception {
        return invokeReflection(target, methodName, new Class<?>[0]);
    }

}
