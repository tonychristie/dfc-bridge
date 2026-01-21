package com.spirecentral.dfcbridge.service;

import com.spirecentral.dfcbridge.model.GroupInfo;
import com.spirecentral.dfcbridge.model.UserInfo;

import java.util.List;

/**
 * Service interface for Documentum user and group operations.
 */
public interface UserGroupService {

    /**
     * List all users in the repository.
     *
     * @param sessionId Session ID
     * @param pattern   Optional user name pattern filter
     * @return List of users
     */
    List<UserInfo> listUsers(String sessionId, String pattern);

    /**
     * Get a user by name.
     *
     * @param sessionId Session ID
     * @param userName  User name
     * @return User information
     */
    UserInfo getUser(String sessionId, String userName);

    /**
     * List all groups in the repository.
     *
     * @param sessionId Session ID
     * @param pattern   Optional group name pattern filter
     * @return List of groups
     */
    List<GroupInfo> listGroups(String sessionId, String pattern);

    /**
     * Get a group by name.
     *
     * @param sessionId Session ID
     * @param groupName Group name
     * @return Group information including members
     */
    GroupInfo getGroup(String sessionId, String groupName);

    /**
     * Get the groups that contain a user.
     *
     * @param sessionId Session ID
     * @param userName  User name
     * @return List of groups containing the user
     */
    List<GroupInfo> getGroupsForUser(String sessionId, String userName);
}
