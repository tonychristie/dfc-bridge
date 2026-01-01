package com.spire.dfcbridge.service.impl;

import com.spire.dfcbridge.dto.ApiRequest;
import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.UpdateObjectRequest;
import com.spire.dfcbridge.exception.DfcBridgeException;
import com.spire.dfcbridge.exception.ObjectNotFoundException;
import com.spire.dfcbridge.model.ObjectInfo;
import com.spire.dfcbridge.model.TypeInfo;
import com.spire.dfcbridge.service.DfcSessionService;
import com.spire.dfcbridge.service.ObjectService;
import com.spire.dfcbridge.util.DfcTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of ObjectService using reflection to call DFC APIs.
 */
@Service
@ConditionalOnProperty(name = "documentum.backend", havingValue = "dfc", matchIfMissing = true)
public class ObjectServiceImpl implements ObjectService {

    private static final Logger log = LoggerFactory.getLogger(ObjectServiceImpl.class);

    private static final String DFC_ID_CLASS = "com.documentum.fc.common.DfId";
    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";
    private static final String DFC_SYSOBJ_IFACE = "com.documentum.fc.client.IDfSysObject";
    private static final String DFC_FOLDER_IFACE = "com.documentum.fc.client.IDfFolder";
    private static final String DFC_TYPE_IFACE = "com.documentum.fc.client.IDfType";
    private static final String DFC_ACL_IFACE = "com.documentum.fc.client.IDfACL";

    private final DfcSessionService sessionService;

    public ObjectServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public ObjectInfo getObject(String sessionId, String objectId) {
        log.debug("Getting object: {}", objectId);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            Object sysObject = getObjectById(dfSession, objectId);
            if (sysObject == null) {
                throw new ObjectNotFoundException(objectId);
            }

            return extractObjectInfo(sysObject, objectId);

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("OBJECT_ERROR",
                    "Failed to get object: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request) {
        log.debug("Updating object: {} with {} attributes", objectId, request.getAttributes().size());

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            Object sysObject = getObjectById(dfSession, objectId);
            if (sysObject == null) {
                throw new ObjectNotFoundException(objectId);
            }

            // Set attributes
            for (Map.Entry<String, Object> attr : request.getAttributes().entrySet()) {
                setObjectAttribute(sysObject, attr.getKey(), attr.getValue());
            }

            // Save if requested
            if (request.isSave()) {
                Method saveMethod = sysObject.getClass().getMethod("save");
                saveMethod.invoke(sysObject);
            }

            return extractObjectInfo(sysObject, objectId);

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("UPDATE_ERROR",
                    "Failed to update object: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ObjectInfo> listFolderContents(String sessionId, String folderPath) {
        log.debug("Listing folder contents: {}", folderPath);

        Object dfSession = sessionService.getDfcSession(sessionId);
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
                    if (obj != null) {
                        contents.add(extractObjectInfo(obj, objectId));
                    }
                }
            } finally {
                closeMethod.invoke(collection);
            }

            return contents;

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("FOLDER_ERROR",
                    "Failed to list folder contents: " + e.getMessage(), e);
        }
    }

    @Override
    public TypeInfo getTypeInfo(String sessionId, String typeName) {
        log.debug("Getting type info: {}", typeName);

        Object dfSession = sessionService.getDfcSession(sessionId);

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
                    "Failed to get type info: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TypeInfo> listTypes(String sessionId, String pattern) {
        log.debug("Listing types with pattern: {}", pattern);

        Object dfSession = sessionService.getDfcSession(sessionId);
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
                    "Failed to list types: " + e.getMessage(), e);
        }
    }

    @Override
    public ApiResponse executeApi(ApiRequest request) {
        log.debug("Executing API: {} on {}", request.getMethod(),
                request.getObjectId() != null ? request.getObjectId() : request.getTypeName());

        long startTime = System.currentTimeMillis();
        Object dfSession = sessionService.getDfcSession(request.getSessionId());

        try {
            Object target;
            if (request.getObjectId() != null) {
                target = getObjectById(dfSession, request.getObjectId());
                if (target == null) {
                    throw new ObjectNotFoundException(request.getObjectId());
                }
            } else if (request.getTypeName() != null) {
                Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
                Method getTypeMethod = sessionClass.getMethod("getType", String.class);
                target = getTypeMethod.invoke(dfSession, request.getTypeName());
            } else {
                target = dfSession;
            }

            // Find and invoke method
            Object result = invokeMethod(target, request.getMethod(), request.getArgs());

            long executionTime = System.currentTimeMillis() - startTime;

            return ApiResponse.builder()
                    .result(result)
                    .resultType(result != null ? result.getClass().getSimpleName() : "null")
                    .executionTimeMs(executionTime)
                    .build();

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("API_ERROR",
                    "Failed to execute API: " + e.getMessage(), e);
        }
    }

    private Object getObjectById(Object dfSession, String objectId) throws Exception {
        Class<?> dfIdClass = Class.forName(DFC_ID_CLASS);
        Object dfId = dfIdClass.getConstructor(String.class).newInstance(objectId);

        Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
        Method getObjectMethod = sessionClass.getMethod("getObject",
                Class.forName("com.documentum.fc.common.IDfId"));

        return getObjectMethod.invoke(dfSession, dfId);
    }

    private ObjectInfo extractObjectInfo(Object sysObject, String objectId) throws Exception {
        String typeName = (String) invokeReflection(sysObject, "getTypeName");
        String objectName = (String) invokeReflection(sysObject, "getObjectName");
        int permit = (Integer) invokeReflection(sysObject, "getPermit");

        // Get all attributes
        Map<String, Object> attributes = extractAllAttributes(sysObject);

        return ObjectInfo.builder()
                .objectId(objectId)
                .type(typeName)
                .name(objectName)
                .attributes(attributes)
                .permissionLevel(permit)
                .permissionLabel(DfcTypeUtils.permitToLabel(permit))
                .build();
    }

    private Map<String, Object> extractAllAttributes(Object sysObject) throws Exception {
        Map<String, Object> attributes = new HashMap<>();

        int attrCount = (Integer) invokeReflection(sysObject, "getAttrCount");

        for (int i = 0; i < attrCount; i++) {
            Object attr = invokeReflection(sysObject, "getAttr", new Class<?>[]{int.class}, i);
            String attrName = (String) invokeReflection(attr, "getName");

            try {
                Object value = invokeReflection(sysObject, "getValue", new Class<?>[]{String.class}, attrName);
                if (value != null) {
                    attributes.put(attrName, value.toString());
                }
            } catch (Exception e) {
                // Skip attributes that can't be read
            }
        }

        return attributes;
    }

    private void setObjectAttribute(Object sysObject, String attrName, Object value) throws Exception {
        // Determine the setter method based on value type
        if (value instanceof String) {
            Method setStringMethod = sysObject.getClass().getMethod("setString", String.class, String.class);
            setStringMethod.invoke(sysObject, attrName, value);
        } else if (value instanceof Integer) {
            Method setIntMethod = sysObject.getClass().getMethod("setInt", String.class, int.class);
            setIntMethod.invoke(sysObject, attrName, value);
        } else if (value instanceof Boolean) {
            Method setBooleanMethod = sysObject.getClass().getMethod("setBoolean", String.class, boolean.class);
            setBooleanMethod.invoke(sysObject, attrName, value);
        } else if (value instanceof Double) {
            Method setDoubleMethod = sysObject.getClass().getMethod("setDouble", String.class, double.class);
            setDoubleMethod.invoke(sysObject, attrName, value);
        } else {
            // Default to string
            Method setStringMethod = sysObject.getClass().getMethod("setString", String.class, String.class);
            setStringMethod.invoke(sysObject, attrName, value.toString());
        }
    }

    private TypeInfo extractTypeInfo(Object type) throws Exception {
        String name = (String) invokeReflection(type, "getName");
        String superName = (String) invokeReflection(type, "getSuperName");
        // DFC doesn't expose isTypeInternal; determine from naming convention
        boolean isSystem = name.startsWith("dm_") || name.startsWith("dmi_");
        int attrCount = (Integer) invokeReflection(type, "getTypeAttrCount");

        List<TypeInfo.AttributeInfo> attributes = new ArrayList<>();
        for (int i = 0; i < attrCount; i++) {
            Object attr = invokeReflection(type, "getTypeAttr", new Class<?>[]{int.class}, i);
            attributes.add(extractAttributeInfo(attr));
        }

        return TypeInfo.builder()
                .name(name)
                .superType(superName)
                .systemType(isSystem)
                .attributes(attributes)
                .build();
    }

    private TypeInfo.AttributeInfo extractAttributeInfo(Object attr) throws Exception {
        return TypeInfo.AttributeInfo.builder()
                .name((String) invokeReflection(attr, "getName"))
                .dataType(DfcTypeUtils.dataTypeToString((Integer) invokeReflection(attr, "getDataType")))
                .length((Integer) invokeReflection(attr, "getLength"))
                .repeating((Boolean) invokeReflection(attr, "isRepeating"))
                .build();
    }

    private Object invokeMethod(Object target, String methodName, List<Object> args) throws Exception {
        // Find method with matching name
        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                int paramCount = method.getParameterCount();
                int argCount = args != null ? args.size() : 0;

                if (paramCount == argCount) {
                    method.setAccessible(true);
                    if (args == null || args.isEmpty()) {
                        return method.invoke(target);
                    } else {
                        return method.invoke(target, args.toArray());
                    }
                }
            }
        }
        throw new NoSuchMethodException("Method not found: " + methodName);
    }

    /**
     * Helper method to invoke a method via reflection, handling accessibility.
     * DFC implementation classes are often proxies, so we search through the class
     * and its declared methods to find what we need.
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
            throw new NoSuchMethodException(methodName + " on " + target.getClass().getName());
        }
    }

    /**
     * Helper method to invoke a no-arg method via reflection.
     */
    private Object invokeReflection(Object target, String methodName) throws Exception {
        return invokeReflection(target, methodName, new Class<?>[0]);
    }

}
