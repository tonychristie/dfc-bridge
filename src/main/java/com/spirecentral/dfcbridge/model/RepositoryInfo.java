package com.spirecentral.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains information about a connected Documentum repository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryInfo {

    /**
     * Repository name
     */
    private String name;

    /**
     * Repository ID
     */
    private String id;

    /**
     * Server version
     */
    private String serverVersion;

    /**
     * Content server host
     */
    private String contentServerHost;

    /**
     * Whether ACS (Accelerated Content Services) is enabled
     */
    private boolean acsEnabled;

    /**
     * Current user's default folder path
     */
    private String userDefaultFolder;
}
