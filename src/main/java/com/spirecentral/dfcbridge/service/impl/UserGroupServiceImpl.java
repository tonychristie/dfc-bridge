package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.exception.DfcBridgeException;
import com.spirecentral.dfcbridge.exception.GroupNotFoundException;
import com.spirecentral.dfcbridge.exception.UserNotFoundException;
import com.spirecentral.dfcbridge.model.GroupInfo;
import com.spirecentral.dfcbridge.model.UserInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.service.UserGroupService;
import com.spirecentral.dfcbridge.util.DfcExceptionUtils;
import com.spirecentral.dfcbridge.util.DfcQuery;
import com.spirecentral.dfcbridge.util.DfcTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    private static final String USER_SELECT = "SELECT r_object_id, user_name, user_os_name, user_address, " +
            "user_state, default_folder, user_group_name, user_privileges FROM dm_user";

    private static final String GROUP_COLUMNS = "r_object_id, group_name, description, group_class, " +
            "group_admin, is_private, is_dynamic, owner_name, group_address, group_source";

    // 16 = superuser privilege
    private static final int SUPERUSER_PRIVILEGE = 16;

    private final DfcSessionService sessionService;

    public UserGroupServiceImpl(DfcSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public List<UserInfo> listUsers(String sessionId, String pattern) {
        log.debug("Listing users with pattern: {}", pattern);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                String dql = USER_SELECT;
                if (pattern != null && !pattern.isEmpty()) {
                    dql += " WHERE user_name LIKE '" + DfcTypeUtils.toLikePattern(pattern) + "' ESCAPE '\\'";
                }
                dql += " ORDER BY user_name";

                return DfcQuery.list(dfSession, dql, this::toUserInfo);

            } catch (Exception e) {
                throw new DfcBridgeException("USER_LIST_ERROR",
                        "Failed to list users: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public UserInfo getUser(String sessionId, String userName) {
        log.debug("Getting user: {}", userName);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                String dql = USER_SELECT + " WHERE user_name = '" + DfcTypeUtils.sanitizeDqlString(userName) + "'";

                return DfcQuery.first(dfSession, dql, this::toUserInfo)
                        .orElseThrow(() -> new UserNotFoundException(userName));

            } catch (UserNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("USER_ERROR",
                        "Failed to get user: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public List<GroupInfo> listGroups(String sessionId, String pattern) {
        log.debug("Listing groups with pattern: {}", pattern);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                String dql = "SELECT " + GROUP_COLUMNS + " FROM dm_group";
                if (pattern != null && !pattern.isEmpty()) {
                    dql += " WHERE group_name LIKE '" + DfcTypeUtils.toLikePattern(pattern) + "' ESCAPE '\\'";
                }
                dql += " ORDER BY group_name";

                return DfcQuery.list(dfSession, dql, this::toGroupInfo);

            } catch (Exception e) {
                throw new DfcBridgeException("GROUP_LIST_ERROR",
                        "Failed to list groups: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public GroupInfo getGroup(String sessionId, String groupName) {
        log.debug("Getting group: {}", groupName);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                String dql = "SELECT " + GROUP_COLUMNS + ", users_names, groups_names " +
                        "FROM dm_group WHERE group_name = '" + DfcTypeUtils.sanitizeDqlString(groupName) + "'";

                return DfcQuery.first(dfSession, dql, row -> {
                    GroupInfo group = toGroupInfo(row);
                    group.setUsersNames(nonEmptyValues(row, "users_names"));
                    group.setGroupsNames(nonEmptyValues(row, "groups_names"));
                    return group;
                }).orElseThrow(() -> new GroupNotFoundException(groupName));

            } catch (GroupNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new DfcBridgeException("GROUP_ERROR",
                        "Failed to get group: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    @Override
    public List<GroupInfo> getGroupsForUser(String sessionId, String userName) {
        log.debug("Getting groups for user: {}", userName);

        return sessionService.withSession(sessionId, dfSession -> {
            try {
                // Finds the groups the user is a direct member of
                String dql = "SELECT " + GROUP_COLUMNS + " FROM dm_group " +
                        "WHERE ANY users_names = '" + DfcTypeUtils.sanitizeDqlString(userName) + "' " +
                        "ORDER BY group_name";

                return DfcQuery.list(dfSession, dql, this::toGroupInfo);

            } catch (Exception e) {
                throw new DfcBridgeException("USER_GROUPS_ERROR",
                        "Failed to get groups for user: " + DfcExceptionUtils.unwrap(e).getMessage(), e);
            }
        });
    }

    private UserInfo toUserInfo(Object row) throws Exception {
        return UserInfo.builder()
                .objectId(DfcQuery.getString(row, "r_object_id"))
                .userName(DfcQuery.getString(row, "user_name"))
                .userOsName(DfcQuery.getString(row, "user_os_name"))
                .userAddress(DfcQuery.getString(row, "user_address"))
                .userState(DfcQuery.getString(row, "user_state"))
                .defaultFolder(DfcQuery.getString(row, "default_folder"))
                .userGroupName(DfcQuery.getString(row, "user_group_name"))
                .superUser(DfcQuery.getInt(row, "user_privileges") >= SUPERUSER_PRIVILEGE)
                .build();
    }

    private GroupInfo toGroupInfo(Object row) throws Exception {
        return GroupInfo.builder()
                .objectId(DfcQuery.getString(row, "r_object_id"))
                .groupName(DfcQuery.getString(row, "group_name"))
                .description(DfcQuery.getString(row, "description"))
                .groupClass(DfcQuery.getString(row, "group_class"))
                .groupAdmin(DfcQuery.getString(row, "group_admin"))
                .isPrivate(DfcQuery.getBoolean(row, "is_private"))
                .isDynamic(DfcQuery.getBoolean(row, "is_dynamic"))
                .ownerName(DfcQuery.getString(row, "owner_name"))
                .groupAddress(DfcQuery.getString(row, "group_address"))
                .groupSource(DfcQuery.getString(row, "group_source"))
                .usersNames(new ArrayList<>())
                .groupsNames(new ArrayList<>())
                .build();
    }

    private List<String> nonEmptyValues(Object row, String attrName) throws Exception {
        List<String> values = new ArrayList<>();
        int count = DfcQuery.getValueCount(row, attrName);
        for (int i = 0; i < count; i++) {
            String value = DfcQuery.getRepeatingString(row, attrName, i);
            if (value != null && !value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }
}
