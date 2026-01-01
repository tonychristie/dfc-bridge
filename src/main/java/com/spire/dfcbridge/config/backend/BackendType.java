package com.spire.dfcbridge.config.backend;

/**
 * Enum defining the available Documentum backend types.
 */
public enum BackendType {
    /**
     * DFC (Documentum Foundation Classes) backend.
     * Requires DFC JARs on the classpath.
     */
    DFC,

    /**
     * REST backend.
     * Connects to Documentum REST Services via HTTP.
     */
    REST
}
