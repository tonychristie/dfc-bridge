package com.spirecentral.dfcbridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isPrivate")
    private boolean isPrivate;

    @JsonProperty("isDynamic")
    private boolean isDynamic;

    private String ownerName;
    private String groupAddress;
    private String groupSource;
    private List<String> usersNames;
    private List<String> groupsNames;
}
