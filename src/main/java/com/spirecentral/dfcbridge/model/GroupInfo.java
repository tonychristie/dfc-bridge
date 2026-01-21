package com.spirecentral.dfcbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about a Documentum group.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupInfo {

    private String objectId;
    private String groupName;
    private String description;
    private String groupClass;
    private String groupAdmin;
    private boolean isPrivate;
    private List<String> usersNames;
    private List<String> groupsNames;
}
