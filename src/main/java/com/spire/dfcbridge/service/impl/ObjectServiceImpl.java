package com.spire.dfcbridge.service.impl;

import com.spire.dfcbridge.dto.ApiRequest;
import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.CreateObjectRequest;
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
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
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
    public List<ObjectInfo> getCabinets(String sessionId) {
        log.debug("Getting cabinets via DFC");

        Object dfSession = sessionService.getDfcSession(sessionId);
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
                    "Failed to get cabinets: " + e.getMessage(), e);
        }
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

    @Override
    public List<ObjectInfo> listFolderContentsById(String sessionId, String folderId) {
        log.debug("Listing folder contents by ID: {}", folderId);

        Object dfSession = sessionService.getDfcSession(sessionId);
        List<ObjectInfo> contents = new ArrayList<>();

        try {
            Object folder = getObjectById(dfSession, folderId);
            if (folder == null) {
                throw new ObjectNotFoundException("Folder not found: " + folderId);
            }

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
    public ObjectInfo checkout(String sessionId, String objectId) {
        log.debug("Checking out object: {}", objectId);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            Object sysObject = getObjectById(dfSession, objectId);
            if (sysObject == null) {
                throw new ObjectNotFoundException(objectId);
            }

            // Call checkout
            Method checkoutMethod = sysObject.getClass().getMethod("checkout");
            checkoutMethod.invoke(sysObject);

            return extractObjectInfo(sysObject, objectId);

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("CHECKOUT_ERROR",
                    "Failed to checkout object: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelCheckout(String sessionId, String objectId) {
        log.debug("Cancelling checkout of object: {}", objectId);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            Object sysObject = getObjectById(dfSession, objectId);
            if (sysObject == null) {
                throw new ObjectNotFoundException(objectId);
            }

            // Call cancelCheckout
            Method cancelCheckoutMethod = sysObject.getClass().getMethod("cancelCheckout");
            cancelCheckoutMethod.invoke(sysObject);

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("CANCEL_CHECKOUT_ERROR",
                    "Failed to cancel checkout: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo checkin(String sessionId, String objectId, String versionLabel) {
        log.debug("Checking in object: {} with label: {}", objectId, versionLabel);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            Object sysObject = getObjectById(dfSession, objectId);
            if (sysObject == null) {
                throw new ObjectNotFoundException(objectId);
            }

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
                    "Failed to checkin object: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectInfo createObject(String sessionId, CreateObjectRequest request) {
        log.debug("Creating object of type: {}", request.getObjectType());

        Object dfSession = sessionService.getDfcSession(sessionId);

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
                for (Map.Entry<String, Object> attr : request.getAttributes().entrySet()) {
                    setObjectAttribute(newObject, attr.getKey(), attr.getValue());
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
                    "Failed to create object: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String sessionId, String objectId, boolean allVersions) {
        log.debug("Deleting object: {} (allVersions={})", objectId, allVersions);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            Object sysObject = getObjectById(dfSession, objectId);
            if (sysObject == null) {
                throw new ObjectNotFoundException(objectId);
            }

            if (allVersions) {
                // Delete all versions
                Method destroyAllVersionsMethod = sysObject.getClass().getMethod("destroyAllVersions");
                destroyAllVersionsMethod.invoke(sysObject);
            } else {
                // Delete just this version
                Method destroyMethod = sysObject.getClass().getMethod("destroy");
                destroyMethod.invoke(sysObject);
            }

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("DELETE_ERROR",
                    "Failed to delete object: " + e.getMessage(), e);
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
