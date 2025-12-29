package com.spire.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a DFC installation profile.
 * Each profile contains the configuration needed to connect to a specific DFC installation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DfcProfile {

    /**
     * Unique identifier for this profile
     */
    private String name;

    /**
     * Path to the Java home directory for this DFC installation
     */
    private String javaHome;

    /**
     * Path to the DFC installation directory
     */
    private String dfcPath;

    /**
     * Path to the dfc.properties file
     */
    private String dfcPropertiesPath;

    /**
     * Optional description of this profile
     */
    private String description;

    /**
     * Whether this profile is currently active/enabled
     */
    @Builder.Default
    private boolean enabled = true;
}
