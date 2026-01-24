package com.spirecentral.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an attribute value with type metadata.
 * Used for object attributes in API responses to provide type information
 * alongside values, enabling downstream consumers to handle values correctly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeValue {

    /**
     * The data type of the attribute.
     * Values: boolean, integer, string, double, time, id
     */
    private String type;

    /**
     * The attribute value.
     * For single-value attributes: the scalar value
     * For repeating attributes: a List of values
     */
    private Object value;

    /**
     * Whether this is a repeating attribute.
     */
    private boolean repeating;

    /**
     * Factory method for single-value attributes.
     */
    public static AttributeValue single(String type, Object value) {
        return AttributeValue.builder()
                .type(type)
                .value(value)
                .repeating(false)
                .build();
    }

    /**
     * Factory method for repeating attributes.
     */
    public static AttributeValue repeating(String type, Object value) {
        return AttributeValue.builder()
                .type(type)
                .value(value)
                .repeating(true)
                .build();
    }
}
