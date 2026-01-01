package com.spire.dfcbridge.service;

import com.spire.dfcbridge.dto.ApiRequest;
import com.spire.dfcbridge.dto.ApiResponse;
import com.spire.dfcbridge.dto.CreateObjectRequest;
import com.spire.dfcbridge.dto.UpdateObjectRequest;
import com.spire.dfcbridge.model.ObjectInfo;
import com.spire.dfcbridge.model.TypeInfo;

import java.util.List;

/**
 * Service interface for Documentum object operations.
 */
public interface ObjectService {

    /**
     * List all cabinets in the repository.
     *
     * @param sessionId Session ID
     * @return List of cabinet objects
     */
    List<ObjectInfo> getCabinets(String sessionId);

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

    /**
     * List contents of a folder by its object ID.
     *
     * @param sessionId Session ID
     * @param folderId  Folder object ID (r_object_id)
     * @return List of objects in the folder
     */
    List<ObjectInfo> listFolderContentsById(String sessionId, String folderId);

    /**
     * Checkout (lock) an object.
     *
     * @param sessionId Session ID
     * @param objectId  Object ID to checkout
     * @return The checked out object info
     */
    ObjectInfo checkout(String sessionId, String objectId);

    /**
     * Cancel checkout (unlock) an object.
     *
     * @param sessionId Session ID
     * @param objectId  Object ID to unlock
     */
    void cancelCheckout(String sessionId, String objectId);

    /**
     * Checkin an object, creating a new version.
     *
     * @param sessionId    Session ID
     * @param objectId     Object ID to checkin
     * @param versionLabel Version label (e.g., "CURRENT", "1.1")
     * @return The new version object info
     */
    ObjectInfo checkin(String sessionId, String objectId, String versionLabel);

    /**
     * Create a new object.
     *
     * @param sessionId Session ID
     * @param request   Create request with type, folder, and attributes
     * @return The created object info
     */
    ObjectInfo createObject(String sessionId, CreateObjectRequest request);

    /**
     * Delete an object.
     *
     * @param sessionId  Session ID
     * @param objectId   Object ID to delete
     * @param allVersions Whether to delete all versions
     */
    void deleteObject(String sessionId, String objectId, boolean allVersions);
}
