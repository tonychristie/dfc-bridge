package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.ApiRequest;
import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.UpdateObjectRequest;
import com.spire.dfcbridge.model.ObjectInfo;
import com.spire.dfcbridge.model.TypeInfo;

import java.util.List;

/**
 * Service interface for Documentum object operations.
 */
public interface ObjectService {

    /**
     * Get an object by its r_object_id.
     *
     * @param sessionId Session ID
     * @param objectId  Object ID (r_object_id)
     * @return Object information
     */
    ObjectInfo getObject(String sessionId, String objectId);

    /**
     * Update an object's attributes.
     *
     * @param sessionId Session ID
     * @param objectId  Object ID
     * @param request   Update request with attributes
     * @return Updated object information
     */
    ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request);

    /**
     * List contents of a folder.
     *
     * @param sessionId  Session ID
     * @param folderPath Folder path (e.g., "/Temp" or cabinet path)
     * @return List of objects in the folder
     */
    List<ObjectInfo> listFolderContents(String sessionId, String folderPath);

    /**
     * Get type information.
     *
     * @param sessionId Session ID
     * @param typeName  Type name (e.g., "dm_document")
     * @return Type information with attributes
     */
    TypeInfo getTypeInfo(String sessionId, String typeName);

    /**
     * List object types matching a pattern.
     *
     * @param sessionId Session ID
     * @param pattern   Pattern to match (optional, null for all types)
     * @return List of type information
     */
    List<TypeInfo> listTypes(String sessionId, String pattern);

    /**
     * Execute an arbitrary DFC API method.
     *
     * @param request API request with method and arguments
     * @return API response with result
     */
    ApiResponse executeApi(ApiRequest request);
}
