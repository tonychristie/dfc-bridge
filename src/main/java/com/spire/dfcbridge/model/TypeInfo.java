package com.spire.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a Documentum object type definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypeInfo {

    /**
     * The type name
     */
    private String name;

    /**
     * The super type (parent type)
     */
    private String superType;

    /**
     * Whether this is a system type
     */
    private boolean systemType;

    /**
     * The type category (e.g., DOCUMENT, FOLDER, CUSTOM)
     */
    private String category;

    /**
     * List of attributes defined on this type
     */
    private List<AttributeInfo> attributes;

    /**
     * Attribute definition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeInfo {
        private String name;
        private String dataType;
        private int length;
        private boolean repeating;
        private boolean required;
        private String defaultValue;
    }
}
