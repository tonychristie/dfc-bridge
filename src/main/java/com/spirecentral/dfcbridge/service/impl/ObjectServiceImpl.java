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
import com.spirecentral.dfcbridge.util.DfcQuery;
import com.spirecentral.dfcbridge.util.DfcReflection;
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
import java.util.Locale;
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
    private static final int DM_STRING = 2;
    private static final int DM_ID = 3;
    private static final int DM_TIME = 4;
    private static final int DM_DOUBLE = 5;

    private static final Class<?>[] NO_PARAMS = new Class<?>[0];
    private static final Class<?>[] STRING_PARAM = {String.class};
    private static final Class<?>[] INT_PARAM = {int.class};
    private static final Class<?>[] STRING_INT_PARAMS = {String.class, int.class};

    private final DfcSessionService sessionService;

    public ObjectServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public List<ObjectInfo> getCabinets(String sessionId) {
        log.debug("Getting cabinets via DFC");

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                String dql = "SELECT r_object_id, object_name, r_object_type FROM dm_cabinet ORDER BY object_name";

                return DfcQuery.list(dfSession, dql, row -> ObjectInfo.builder()
                        .objectId(DfcQuery.getString(row, "r_object_id"))
                        .name(DfcQuery.getString(row, "object_name"))
                        .type(DfcQuery.getString(row, "r_object_type"))
                        .build());

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
                    DfcReflection.invoke(sysObject, "save");
                    unsaved = false;
                }

                ObjectInfo info = extractObjectInfo(sysObject, objectId);
                if (unsaved) {
                    unsaved = false;
                    DfcReflection.invoke(sysObject, "revert");
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
            try {
                Object folder = DfcReflection.invoke(DFC_SESSION_IFACE, dfSession, "getFolderByPath",
                        STRING_PARAM, folderPath);
                if (folder == null) {
                    throw new ObjectNotFoundException("Folder not found: " + folderPath);
                }

                return listFolder(dfSession, folder);

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
                Object type = DfcReflection.invoke(DFC_SESSION_IFACE, dfSession, "getType", STRING_PARAM, typeName);

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
            try {
                String dql = "SELECT name, super_name FROM dm_type";
                if (pattern != null && !pattern.isEmpty()) {
                    dql += " WHERE name LIKE '" + DfcTypeUtils.toLikePattern(pattern) + "' ESCAPE '\\'";
                }
                dql += " ORDER BY name";

                return DfcQuery.list(dfSession, dql, row -> {
                    String name = DfcQuery.getString(row, "name");
                    return TypeInfo.builder()
                            .name(name)
                            .superType(DfcQuery.getString(row, "super_name"))
                            .systemType(isSystemType(name))
                            .build();
                });

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
                    target = DfcReflection.invoke(DFC_SESSION_IFACE, dfSession, "getType", STRING_PARAM,
                            request.getTypeName());
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
            try {
                Object folder = getObjectById(dfSession, folderId);
                if (!Class.forName(DFC_FOLDER_IFACE).isInstance(folder)) {
                    throw new ObjectNotFoundException("Folder not found: " + folderId);
                }

                return listFolder(dfSession, folder);

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

                DfcReflection.invoke(sysObject, "checkout");

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

                DfcReflection.invoke(sysObject, "cancelCheckout");
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

                Object newId = DfcReflection.invoke(sysObject, "checkin",
                        new Class<?>[]{boolean.class, String.class}, false, versionLabel);

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
                Object newObject = DfcReflection.invoke(DFC_SESSION_IFACE, dfSession, "newObject", STRING_PARAM,
                        request.getObjectType());

                if (request.getObjectName() != null) {
                    DfcReflection.invoke(newObject, "setObjectName", STRING_PARAM, request.getObjectName());
                }

                // Set additional attributes if provided
                if (request.getAttributes() != null) {
                    Map<String, Integer> dataTypes = buildTypeAttrDataTypes(newObject);
                    for (Map.Entry<String, Object> attr : request.getAttributes().entrySet()) {
                        setObjectAttribute(newObject, attr.getKey(), attr.getValue(), dataTypes);
                    }
                }

                if (request.getFolderPath() != null && !request.getFolderPath().isEmpty()) {
                    DfcReflection.invoke(newObject, "link", STRING_PARAM, request.getFolderPath());
                }

                DfcReflection.invoke(newObject, "save");

                String newObjectId = DfcReflection.invoke(newObject, "getObjectId").toString();

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

                DfcReflection.invoke(sysObject, allVersions ? "destroyAllVersions" : "destroy");
                return null;

            } catch (ObjectNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("DELETE_ERROR",
                        "Failed to delete object: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    private List<ObjectInfo> listFolder(Object dfSession, Object folder) throws Exception {
        String folderId = (String) DfcReflection.invoke(folder, "getString", STRING_PARAM, "r_object_id");
        String dql = "SELECT r_object_id, object_name, r_object_type, a_content_type FROM dm_sysobject " +
                "WHERE FOLDER(ID('" + DfcTypeUtils.sanitizeDqlString(folderId) + "')) ORDER BY object_name";

        return DfcQuery.list(dfSession, dql, row -> {
            Map<String, AttributeValue> attributes = new HashMap<>();
            attributes.put("a_content_type",
                    AttributeValue.single(dataTypeToName(DM_STRING), DfcQuery.getString(row, "a_content_type")));
            return ObjectInfo.builder()
                    .objectId(DfcQuery.getString(row, "r_object_id"))
                    .type(DfcQuery.getString(row, "r_object_type"))
                    .name(DfcQuery.getString(row, "object_name"))
                    .attributes(attributes)
                    .build();
        });
    }

    private Object getObjectById(Object dfSession, String objectId) throws Exception {
        Object dfId = DfcReflection.newInstance(DFC_ID_CLASS, STRING_PARAM, objectId);

        try {
            return DfcReflection.invoke(DFC_SESSION_IFACE, dfSession, "getObject",
                    new Class<?>[]{Class.forName(DFC_ID_IFACE)}, dfId);
        } catch (InvocationTargetException e) {
            if (DfcExceptionUtils.isInstanceOf(e.getTargetException(), DfcExceptionUtils.DFC_ID_NOT_FOUND_EXCEPTION)) {
                throw new ObjectNotFoundException(objectId);
            }
            throw e;
        }
    }

    private ObjectInfo extractObjectInfo(Object sysObject, String objectId) throws Exception {
        String typeName = (String) DfcReflection.invoke(sysObject, "getTypeName");
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
            return (Integer) DfcReflection.invoke(dfObject, "getPermit");
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
                String value = (String) DfcReflection.invoke(dfObject, "getString",
                        STRING_PARAM, attr);
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
            return (Boolean) DfcReflection.invoke(dfObject, "hasAttr",
                    STRING_PARAM, attrName);
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

        int attrCount = (Integer) DfcReflection.invoke(sysObject, "getAttrCount");

        for (int i = 0; i < attrCount; i++) {
            Object attr = DfcReflection.invoke(sysObject, "getAttr", INT_PARAM, i);
            String attrName = (String) DfcReflection.invoke(attr, "getName");
            boolean isRepeating = (Boolean) DfcReflection.invoke(attr, "isRepeating");

            // Use type definition data type if available, otherwise fall back to object's attr
            int dataType = typeAttrDataTypes.getOrDefault(attrName,
                    (Integer) DfcReflection.invoke(attr, "getDataType"));
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
            Object session = DfcReflection.invoke(sysObject, "getSession");

            // Get the object's type name
            String objectTypeName = (String) DfcReflection.invoke(sysObject, "getTypeName");

            // Get the type definition from the session
            Object type = DfcReflection.invoke(session, "getType", STRING_PARAM, objectTypeName);
            if (type == null) {
                log.debug("Could not get type definition for {}", objectTypeName);
                return dataTypes;
            }

            // Iterate through the type's attributes and build the data type map
            int typeAttrCount = (Integer) DfcReflection.invoke(type, "getTypeAttrCount");
            for (int i = 0; i < typeAttrCount; i++) {
                Object typeAttr = DfcReflection.invoke(type, "getTypeAttr", INT_PARAM, i);
                String attrName = (String) DfcReflection.invoke(typeAttr, "getName");
                int dataType = (Integer) DfcReflection.invoke(typeAttr, "getDataType");
                dataTypes.put(attrName, dataType);
            }
        } catch (Exception e) {
            log.warn("Could not build type attribute data types: {}", e.getMessage());
        }
        return dataTypes;
    }

    private String dataTypeToName(int dataType) {
        return DfcTypeUtils.dataTypeToString(dataType).toLowerCase(Locale.ROOT);
    }

    private Object extractSingleAttributeValue(Object sysObject, String attrName, int dataType) throws Exception {
        // getId returns an IDfId whose text can lose the leading zeros, so ids are read as text
        String methodName = dataType == DM_ID ? "getString" : DfcTypeUtils.getterName(dataType);
        Object value = DfcReflection.invoke(sysObject, methodName, STRING_PARAM, attrName);

        // Convert IDfTime to string for JSON serialization
        if (value != null && dataType == DM_TIME) {
            value = value.toString();
        }

        return value;
    }

    private Object extractRepeatingAttributeValue(Object sysObject, String attrName, int dataType) throws Exception {
        int count = (Integer) DfcReflection.invoke(sysObject, "getValueCount", STRING_PARAM, attrName);
        List<Object> values = new ArrayList<>();

        // getRepeatingId returns an IDfId whose text can lose the leading zeros, so ids are read as text
        String methodName = dataType == DM_ID ? "getRepeatingString" : DfcTypeUtils.repeatingGetterName(dataType);

        for (int i = 0; i < count; i++) {
            Object value = DfcReflection.invoke(sysObject, methodName, STRING_INT_PARAMS, attrName, i);
            // Convert IDfTime to string for JSON serialization
            if (value != null && dataType == DM_TIME) {
                value = value.toString();
            }
            values.add(value);
        }
        return values;
    }

    private void setObjectAttribute(Object sysObject, String attrName, Object value,
                                    Map<String, Integer> dataTypes) throws Exception {
        boolean isRepeating = (Boolean) DfcReflection.invoke(sysObject, "isAttrRepeating",
                STRING_PARAM, attrName);
        Integer dataType = dataTypes.get(attrName);
        if (dataType == null) {
            dataType = (Integer) DfcReflection.invoke(sysObject, "getAttrDataType",
                    STRING_PARAM, attrName);
        }

        if (isRepeating) {
            setRepeatingAttribute(sysObject, attrName, value, dataType);
        } else {
            setScalarAttribute(sysObject, attrName, value, dataType);
        }
    }

    private void setScalarAttribute(Object sysObject, String attrName, Object value, int dataType) throws Exception {
        DfcReflection.invoke(sysObject, "setString", new Class<?>[]{String.class, String.class},
                attrName, attributeText(attrName, value, dataType));
    }

    private void setRepeatingAttribute(Object sysObject, String attrName, Object value, int dataType) throws Exception {
        DfcReflection.invoke(sysObject, "removeAll", STRING_PARAM, attrName);

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
        DfcReflection.invoke(sysObject, "setRepeatingString", new Class<?>[]{String.class, int.class, String.class},
                attrName, index, attributeText(attrName, value, dataType));
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
            DfcReflection.invoke(sysObject, "revert");
        } catch (Exception e) {
            log.warn("Could not revert object {} after a failed update: {}",
                    objectId, DfcExceptionUtils.unwrap(e).getMessage());
        }
    }

    private TypeInfo extractTypeInfo(Object type) throws Exception {
        String name = (String) DfcReflection.invoke(type, "getName");
        String superName = (String) DfcReflection.invoke(type, "getSuperName");
        int attrCount = (Integer) DfcReflection.invoke(type, "getTypeAttrCount");

        // Determine the number of inherited attributes by getting the super type's attribute count.
        // All attributes at indices 0 to (inheritedAttrCount-1) are inherited from the super type.
        int inheritedAttrCount = 0;
        try {
            Object superType = DfcReflection.invoke(type, "getSuperType");
            if (superType != null) {
                inheritedAttrCount = (Integer) DfcReflection.invoke(superType, "getTypeAttrCount");
            }
        } catch (Exception e) {
            log.debug("Could not get super type attr count for type {}, defaulting to 0: {}", name, e.getMessage());
        }

        List<TypeInfo.AttributeInfo> attributes = new ArrayList<>();
        for (int i = 0; i < attrCount; i++) {
            Object attr = DfcReflection.invoke(type, "getTypeAttr", INT_PARAM, i);
            boolean isInherited = i < inheritedAttrCount;
            attributes.add(extractAttributeInfo(attr, isInherited));
        }

        return TypeInfo.builder()
                .name(name)
                .superType(superName)
                .systemType(isSystemType(name))
                .attributes(attributes)
                .build();
    }

    // DFC doesn't expose isTypeInternal; determine from naming convention
    private static boolean isSystemType(String typeName) {
        return typeName.startsWith("dm_") || typeName.startsWith("dmi_");
    }

    private TypeInfo.AttributeInfo extractAttributeInfo(Object attr, boolean isInherited) throws Exception {
        return TypeInfo.AttributeInfo.builder()
                .name((String) DfcReflection.invoke(attr, "getName"))
                .dataType(DfcTypeUtils.dataTypeToString((Integer) DfcReflection.invoke(attr, "getDataType")))
                .length((Integer) DfcReflection.invoke(attr, "getLength"))
                .repeating((Boolean) DfcReflection.invoke(attr, "isRepeating"))
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
                DfcReflection.invoke(collectionClass, result, "close", NO_PARAMS);
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
                converted[i] = DfcReflection.newInstance(DFC_ID_CLASS, STRING_PARAM, value);
            }
        }
        return converted;
    }

    // DfTime(String) does not throw on unparseable text; it returns an invalid time that saves as nulldate.
    private Object toDfTime(String value) throws Exception {
        Object time = DfcReflection.newInstance(DFC_TIME_CLASS, STRING_PARAM, value);
        if (!(Boolean) DfcReflection.invoke(time, "isValid")) {
            throw new IllegalArgumentException("Invalid time value: " + value);
        }
        return time;
    }
}
