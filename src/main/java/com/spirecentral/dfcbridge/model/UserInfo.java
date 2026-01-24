package com.spirecentral.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about a Documentum user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    private String objectId;
    private String userName;
    private String userOsName;
    private String userAddress;
    private String userState;
    private String defaultFolder;
    private String userGroupName;
    private boolean superUser;
}
