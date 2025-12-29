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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                dql += " WHERE name LIKE '" + pattern.replace("*", "%") + "'";
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
        Class<?> sysObjClass = Class.forName(DFC_SYSOBJ_IFACE);

        Method getTypeNameMethod = sysObject.getClass().getMethod("getTypeName");
        Method getObjectNameMethod = sysObject.getClass().getMethod("getObjectName");
        Method getPermitMethod = sysObject.getClass().getMethod("getPermit");

        String typeName = (String) getTypeNameMethod.invoke(sysObject);
        String objectName = (String) getObjectNameMethod.invoke(sysObject);
        int permit = (Integer) getPermitMethod.invoke(sysObject);

        // Get all attributes
        Map<String, Object> attributes = extractAllAttributes(sysObject);

        return ObjectInfo.builder()
                .objectId(objectId)
                .type(typeName)
                .name(objectName)
                .attributes(attributes)
                .permissionLevel(permit)
                .permissionLabel(permitToLabel(permit))
                .build();
    }

    private Map<String, Object> extractAllAttributes(Object sysObject) throws Exception {
        Map<String, Object> attributes = new HashMap<>();

        Method getAttrCountMethod = sysObject.getClass().getMethod("getAttrCount");
        Method getAttrMethod = sysObject.getClass().getMethod("getAttr", int.class);

        int attrCount = (Integer) getAttrCountMethod.invoke(sysObject);

        for (int i = 0; i < attrCount; i++) {
            Object attr = getAttrMethod.invoke(sysObject, i);
            Method getNameMethod = attr.getClass().getMethod("getName");
            String attrName = (String) getNameMethod.invoke(attr);

            try {
                Method getValueMethod = sysObject.getClass().getMethod("getValue", String.class);
                Object value = getValueMethod.invoke(sysObject, attrName);
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
        Class<?> typeClass = Class.forName(DFC_TYPE_IFACE);

        Method getNameMethod = type.getClass().getMethod("getName");
        Method getSuperNameMethod = type.getClass().getMethod("getSuperName");
        Method isTypeInternalMethod = type.getClass().getMethod("isTypeInternal");
        Method getTypeAttrCountMethod = type.getClass().getMethod("getTypeAttrCount");
        Method getTypeAttrMethod = type.getClass().getMethod("getTypeAttr", int.class);

        String name = (String) getNameMethod.invoke(type);
        String superName = (String) getSuperNameMethod.invoke(type);
        boolean isInternal = (Boolean) isTypeInternalMethod.invoke(type);
        int attrCount = (Integer) getTypeAttrCountMethod.invoke(type);

        List<TypeInfo.AttributeInfo> attributes = new ArrayList<>();
        for (int i = 0; i < attrCount; i++) {
            Object attr = getTypeAttrMethod.invoke(type, i);
            attributes.add(extractAttributeInfo(attr));
        }

        return TypeInfo.builder()
                .name(name)
                .superType(superName)
                .systemType(isInternal)
                .attributes(attributes)
                .build();
    }

    private TypeInfo.AttributeInfo extractAttributeInfo(Object attr) throws Exception {
        Method getNameMethod = attr.getClass().getMethod("getName");
        Method getDataTypeMethod = attr.getClass().getMethod("getDataType");
        Method getLengthMethod = attr.getClass().getMethod("getLength");
        Method isRepeatingMethod = attr.getClass().getMethod("isRepeating");

        return TypeInfo.AttributeInfo.builder()
                .name((String) getNameMethod.invoke(attr))
                .dataType(dataTypeToString((Integer) getDataTypeMethod.invoke(attr)))
                .length((Integer) getLengthMethod.invoke(attr))
                .repeating((Boolean) isRepeatingMethod.invoke(attr))
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

    private String dataTypeToString(int dataType) {
        return switch (dataType) {
            case 0 -> "BOOLEAN";
            case 1 -> "INTEGER";
            case 2 -> "STRING";
            case 3 -> "ID";
            case 4 -> "TIME";
            case 5 -> "DOUBLE";
            default -> "UNDEFINED";
        };
    }

    private String permitToLabel(int permit) {
        return switch (permit) {
            case 1 -> "NONE";
            case 2 -> "BROWSE";
            case 3 -> "READ";
            case 4 -> "RELATE";
            case 5 -> "VERSION";
            case 6 -> "WRITE";
            case 7 -> "DELETE";
            default -> "UNKNOWN";
        };
    }
}
