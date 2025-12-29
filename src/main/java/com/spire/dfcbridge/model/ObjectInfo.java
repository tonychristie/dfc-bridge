package com.spire.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a Documentum object with its attributes and permissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectInfo {

    /**
     * The r_object_id of the object
     */
    private String objectId;

    /**
     * The object type (e.g., dm_document, dm_folder)
     */
    private String type;

    /**
     * Object name (object_name attribute)
     */
    private String name;

    /**
     * All object attributes as key-value pairs
     */
    private Map<String, Object> attributes;

    /**
     * User's permission level on this object
     */
    private int permissionLevel;

    /**
     * Human-readable permission label
     */
    private String permissionLabel;

    /**
     * Extended permissions (if any)
     */
    private List<String> extendedPermissions;
}
