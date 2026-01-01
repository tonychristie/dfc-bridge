package com.spire.dfcbridge.config.backend;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Documentum backend.
 *
 * Example configuration:
 * <pre>
 * documentum:
 *   backend: rest
 *   rest:
 *     endpoint: http://192.168.0.110:9080/dctm-rest
 *     timeout-seconds: 30
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "documentum")
public class BackendProperties {

    /**
     * The backend type to use (dfc or rest).
     * Defaults to DFC if DFC is available, otherwise REST.
     */
    private BackendType backend = BackendType.DFC;

    /**
     * REST backend configuration.
     */
    private RestConfig rest = new RestConfig();

    @Data
    public static class RestConfig {
        /**
         * Base URL of the Documentum REST Services endpoint.
         * Example: http://192.168.0.110:9080/dctm-rest
         */
        private String endpoint;

        /**
         * Request timeout in seconds.
         */
        private int timeoutSeconds = 30;

        /**
         * Whether to verify SSL certificates.
         */
        private boolean verifySsl = true;
    }
}
