package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.exception.DfcBridgeException;
import com.spirecentral.dfcbridge.exception.GroupNotFoundException;
import com.spirecentral.dfcbridge.exception.UserNotFoundException;
import com.spirecentral.dfcbridge.model.GroupInfo;
import com.spirecentral.dfcbridge.model.UserInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.service.UserGroupService;
import com.spirecentral.dfcbridge.util.DfcTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DFC implementation of UserGroupService using reflection to call DFC APIs.
 *
 * <p>Uses DQL queries with explicit attribute selection to properly handle
 * repeating attributes (users_names, groups_names) which are not returned
 * by SELECT * queries in Documentum.
 */
@Service
public class UserGroupServiceImpl implements UserGroupService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupServiceImpl.class);

    private static final String DFC_SESSION_IFACE = "com.documentum.fc.client.IDfSession";
    private static final String DFC_QUERY_CLASS = "com.documentum.fc.client.DfQuery";
    private static final String DFC_QUERY_IFACE = "com.documentum.fc.client.IDfQuery";

    private final DfcSessionService sessionService;

    public UserGroupServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public List<UserInfo> listUsers(String sessionId, String pattern) {
        log.debug("Listing users with pattern: {}", pattern);

        Object dfSession = sessionService.getDfcSession(sessionId);
        List<UserInfo> users = new ArrayList<>();

        try {
            String dql = "SELECT r_object_id, user_name, user_os_name, user_address, " +
                    "user_state, default_folder, user_group_name, user_privileges " +
                    "FROM dm_user";

            if (pattern != null && !pattern.isEmpty()) {
                String sanitizedPattern = DfcTypeUtils.sanitizeDqlString(pattern).replace("*", "%");
                dql += " WHERE user_name LIKE '" + sanitizedPattern + "'";
            }
            dql += " ORDER BY user_name";

            Object collection = executeQuery(dfSession, dql);
            Method nextMethod = collection.getClass().getMethod("next");
            Method closeMethod = collection.getClass().getMethod("close");
            Method getStringMethod = collection.getClass().getMethod("getString", String.class);
            Method getIntMethod = collection.getClass().getMethod("getInt", String.class);

            try {
                while ((Boolean) nextMethod.invoke(collection)) {
                    int privileges = (Integer) getIntMethod.invoke(collection, "user_privileges");
                    users.add(UserInfo.builder()
                            .objectId((String) getStringMethod.invoke(collection, "r_object_id"))
                            .userName((String) getStringMethod.invoke(collection, "user_name"))
                            .userOsName((String) getStringMethod.invoke(collection, "user_os_name"))
                            .userAddress((String) getStringMethod.invoke(collection, "user_address"))
                            .userState((String) getStringMethod.invoke(collection, "user_state"))
                            .defaultFolder((String) getStringMethod.invoke(collection, "default_folder"))
                            .userGroupName((String) getStringMethod.invoke(collection, "user_group_name"))
                            .superUser(privileges >= 16) // 16 = superuser privilege
                            .build());
                }
            } finally {
                closeMethod.invoke(collection);
            }

            return users;

        } catch (Exception e) {
            throw new DfcBridgeException("USER_LIST_ERROR",
                    "Failed to list users: " + e.getMessage(), e);
        }
    }

    @Override
    public UserInfo getUser(String sessionId, String userName) {
        log.debug("Getting user: {}", userName);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            String sanitizedUserName = DfcTypeUtils.sanitizeDqlString(userName);
            String dql = "SELECT r_object_id, user_name, user_os_name, user_address, " +
                    "user_state, default_folder, user_group_name, user_privileges " +
                    "FROM dm_user WHERE user_name = '" + sanitizedUserName + "'";

            Object collection = executeQuery(dfSession, dql);
            Method nextMethod = collection.getClass().getMethod("next");
            Method closeMethod = collection.getClass().getMethod("close");
            Method getStringMethod = collection.getClass().getMethod("getString", String.class);
            Method getIntMethod = collection.getClass().getMethod("getInt", String.class);

            try {
                if ((Boolean) nextMethod.invoke(collection)) {
                    int privileges = (Integer) getIntMethod.invoke(collection, "user_privileges");
                    return UserInfo.builder()
                            .objectId((String) getStringMethod.invoke(collection, "r_object_id"))
                            .userName((String) getStringMethod.invoke(collection, "user_name"))
                            .userOsName((String) getStringMethod.invoke(collection, "user_os_name"))
                            .userAddress((String) getStringMethod.invoke(collection, "user_address"))
                            .userState((String) getStringMethod.invoke(collection, "user_state"))
                            .defaultFolder((String) getStringMethod.invoke(collection, "default_folder"))
                            .userGroupName((String) getStringMethod.invoke(collection, "user_group_name"))
                            .superUser(privileges >= 16)
                            .build();
                }
            } finally {
                closeMethod.invoke(collection);
            }

            throw new UserNotFoundException(userName);

        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("USER_ERROR",
                    "Failed to get user: " + e.getMessage(), e);
        }
    }

    @Override
    public List<GroupInfo> listGroups(String sessionId, String pattern) {
        log.debug("Listing groups with pattern: {}", pattern);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            // First query: get basic group info (single-value attributes)
            String dql = "SELECT r_object_id, group_name, description, group_class, " +
                    "group_admin, is_private " +
                    "FROM dm_group";

            if (pattern != null && !pattern.isEmpty()) {
                String sanitizedPattern = DfcTypeUtils.sanitizeDqlString(pattern).replace("*", "%");
                dql += " WHERE group_name LIKE '" + sanitizedPattern + "'";
            }
            dql += " ORDER BY group_name";

            // Get basic info for all groups
            Map<String, GroupInfo.GroupInfoBuilder> groupBuilders = new LinkedHashMap<>();

            Object collection = executeQuery(dfSession, dql);
            Method nextMethod = collection.getClass().getMethod("next");
            Method closeMethod = collection.getClass().getMethod("close");
            Method getStringMethod = collection.getClass().getMethod("getString", String.class);
            Method getBooleanMethod = collection.getClass().getMethod("getBoolean", String.class);

            try {
                while ((Boolean) nextMethod.invoke(collection)) {
                    String objectId = (String) getStringMethod.invoke(collection, "r_object_id");
                    String groupName = (String) getStringMethod.invoke(collection, "group_name");

                    groupBuilders.put(objectId, GroupInfo.builder()
                            .objectId(objectId)
                            .groupName(groupName)
                            .description((String) getStringMethod.invoke(collection, "description"))
                            .groupClass((String) getStringMethod.invoke(collection, "group_class"))
                            .groupAdmin((String) getStringMethod.invoke(collection, "group_admin"))
                            .isPrivate((Boolean) getBooleanMethod.invoke(collection, "is_private"))
                            .usersNames(new ArrayList<>())
                            .groupsNames(new ArrayList<>()));
                }
            } finally {
                closeMethod.invoke(collection);
            }

            // Build and return the list (without members for list view)
            List<GroupInfo> groups = new ArrayList<>();
            for (GroupInfo.GroupInfoBuilder builder : groupBuilders.values()) {
                groups.add(builder.build());
            }

            return groups;

        } catch (Exception e) {
            throw new DfcBridgeException("GROUP_LIST_ERROR",
                    "Failed to list groups: " + e.getMessage(), e);
        }
    }

    @Override
    public GroupInfo getGroup(String sessionId, String groupName) {
        log.debug("Getting group: {}", groupName);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            String sanitizedGroupName = DfcTypeUtils.sanitizeDqlString(groupName);

            // First query: get basic group info
            String basicDql = "SELECT r_object_id, group_name, description, group_class, " +
                    "group_admin, is_private " +
                    "FROM dm_group WHERE group_name = '" + sanitizedGroupName + "'";

            Object collection = executeQuery(dfSession, basicDql);
            Method nextMethod = collection.getClass().getMethod("next");
            Method closeMethod = collection.getClass().getMethod("close");
            Method getStringMethod = collection.getClass().getMethod("getString", String.class);
            Method getBooleanMethod = collection.getClass().getMethod("getBoolean", String.class);

            String objectId;
            GroupInfo.GroupInfoBuilder builder;

            try {
                if (!(Boolean) nextMethod.invoke(collection)) {
                    throw new GroupNotFoundException(groupName);
                }

                objectId = (String) getStringMethod.invoke(collection, "r_object_id");
                builder = GroupInfo.builder()
                        .objectId(objectId)
                        .groupName((String) getStringMethod.invoke(collection, "group_name"))
                        .description((String) getStringMethod.invoke(collection, "description"))
                        .groupClass((String) getStringMethod.invoke(collection, "group_class"))
                        .groupAdmin((String) getStringMethod.invoke(collection, "group_admin"))
                        .isPrivate((Boolean) getBooleanMethod.invoke(collection, "is_private"));
            } finally {
                closeMethod.invoke(collection);
            }

            // Second query: get users_names repeating attribute
            // DQL returns one row per repeating value when r_object_id is included
            List<String> usersNames = new ArrayList<>();
            String usersDql = "SELECT r_object_id, users_names FROM dm_group " +
                    "WHERE group_name = '" + sanitizedGroupName + "'";

            collection = executeQuery(dfSession, usersDql);
            nextMethod = collection.getClass().getMethod("next");
            closeMethod = collection.getClass().getMethod("close");
            getStringMethod = collection.getClass().getMethod("getString", String.class);

            try {
                while ((Boolean) nextMethod.invoke(collection)) {
                    String userName = (String) getStringMethod.invoke(collection, "users_names");
                    if (userName != null && !userName.isEmpty()) {
                        usersNames.add(userName);
                    }
                }
            } finally {
                closeMethod.invoke(collection);
            }

            // Third query: get groups_names repeating attribute
            List<String> groupsNames = new ArrayList<>();
            String groupsDql = "SELECT r_object_id, groups_names FROM dm_group " +
                    "WHERE group_name = '" + sanitizedGroupName + "'";

            collection = executeQuery(dfSession, groupsDql);
            nextMethod = collection.getClass().getMethod("next");
            closeMethod = collection.getClass().getMethod("close");
            getStringMethod = collection.getClass().getMethod("getString", String.class);

            try {
                while ((Boolean) nextMethod.invoke(collection)) {
                    String subGroupName = (String) getStringMethod.invoke(collection, "groups_names");
                    if (subGroupName != null && !subGroupName.isEmpty()) {
                        groupsNames.add(subGroupName);
                    }
                }
            } finally {
                closeMethod.invoke(collection);
            }

            return builder
                    .usersNames(usersNames)
                    .groupsNames(groupsNames)
                    .build();

        } catch (GroupNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DfcBridgeException("GROUP_ERROR",
                    "Failed to get group: " + e.getMessage(), e);
        }
    }

    @Override
    public List<GroupInfo> getGroupsForUser(String sessionId, String userName) {
        log.debug("Getting groups for user: {}", userName);

        Object dfSession = sessionService.getDfcSession(sessionId);

        try {
            String sanitizedUserName = DfcTypeUtils.sanitizeDqlString(userName);

            // Query for groups containing this user
            // This finds groups where the user is a direct member
            String dql = "SELECT r_object_id, group_name, description, group_class, " +
                    "group_admin, is_private " +
                    "FROM dm_group WHERE ANY users_names = '" + sanitizedUserName + "' " +
                    "ORDER BY group_name";

            List<GroupInfo> groups = new ArrayList<>();

            Object collection = executeQuery(dfSession, dql);
            Method nextMethod = collection.getClass().getMethod("next");
            Method closeMethod = collection.getClass().getMethod("close");
            Method getStringMethod = collection.getClass().getMethod("getString", String.class);
            Method getBooleanMethod = collection.getClass().getMethod("getBoolean", String.class);

            try {
                while ((Boolean) nextMethod.invoke(collection)) {
                    groups.add(GroupInfo.builder()
                            .objectId((String) getStringMethod.invoke(collection, "r_object_id"))
                            .groupName((String) getStringMethod.invoke(collection, "group_name"))
                            .description((String) getStringMethod.invoke(collection, "description"))
                            .groupClass((String) getStringMethod.invoke(collection, "group_class"))
                            .groupAdmin((String) getStringMethod.invoke(collection, "group_admin"))
                            .isPrivate((Boolean) getBooleanMethod.invoke(collection, "is_private"))
                            .usersNames(new ArrayList<>())
                            .groupsNames(new ArrayList<>())
                            .build());
                }
            } finally {
                closeMethod.invoke(collection);
            }

            return groups;

        } catch (Exception e) {
            throw new DfcBridgeException("USER_GROUPS_ERROR",
                    "Failed to get groups for user: " + e.getMessage(), e);
        }
    }

    private Object executeQuery(Object dfSession, String dql) throws Exception {
        Class<?> dfQueryClass = Class.forName(DFC_QUERY_CLASS);
        Object query = dfQueryClass.getDeclaredConstructor().newInstance();

        Method setDqlMethod = dfQueryClass.getMethod("setDQL", String.class);
        setDqlMethod.invoke(query, dql);

        Class<?> sessionClass = Class.forName(DFC_SESSION_IFACE);
        Class<?> queryInterface = Class.forName(DFC_QUERY_IFACE);
        Method executeMethod = queryInterface.getMethod("execute", sessionClass, int.class);

        // IDfQuery.DF_READ_QUERY = 0
        return executeMethod.invoke(query, dfSession, 0);
    }
}
