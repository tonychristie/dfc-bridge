package com.spirecentral.dfcbridge.service.impl;

import com.documentum.fc.client.DfQuery;
import com.spirecentral.dfcbridge.exception.GroupNotFoundException;
import com.spirecentral.dfcbridge.exception.UserNotFoundException;
import com.spirecentral.dfcbridge.model.GroupInfo;
import com.spirecentral.dfcbridge.model.UserInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.util.RowCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGroupServiceImplTest {

    @Mock
    private DfcSessionService sessionService;

    private UserGroupServiceImpl userGroupService;

    @BeforeEach
    void setUp() {
        userGroupService = new UserGroupServiceImpl(sessionService);
        when(sessionService.withSession(eq("test-session"), any())).thenAnswer(
                invocation -> invocation.<Function<Object, Object>>getArgument(1).apply(null));
    }

    @AfterEach
    void tearDown() {
        DfQuery.reset(null);
    }

    @Test
    void listUsers_dmPattern_escapesUnderscore() {
        DfQuery.reset(dql -> new RowCollection());

        userGroupService.listUsers("test-session", "dm_*");

        assertTrue(DfQuery.executed.get(0).contains(
                "WHERE r_is_group = false AND user_name LIKE 'dm\\_%' ESCAPE '\\'"));
    }

    @Test
    void listUsers_noPattern_excludesGroups() {
        DfQuery.reset(dql -> new RowCollection());

        userGroupService.listUsers("test-session", null);

        assertTrue(DfQuery.executed.get(0).endsWith("FROM dm_user WHERE r_is_group = false ORDER BY user_name"));
    }

    @Test
    void getUser_excludesGroups() {
        DfQuery.reset(dql -> new RowCollection(userRow("dmadmin", 16)));

        userGroupService.getUser("test-session", "dmadmin");

        assertTrue(DfQuery.executed.get(0).endsWith("WHERE user_name = 'dmadmin' AND r_is_group = false"));
    }

    @Test
    void getUser_noRow_throwsUserNotFound() {
        DfQuery.reset(dql -> new RowCollection());

        assertThrows(UserNotFoundException.class, () -> userGroupService.getUser("test-session", "admingroup"));
    }

    @Test
    void listGroups_dmPattern_escapesUnderscore() {
        DfQuery.reset(dql -> new RowCollection());

        userGroupService.listGroups("test-session", "dm_*");

        assertTrue(DfQuery.executed.get(0).contains("WHERE group_name LIKE 'dm\\_%' ESCAPE '\\'"));
    }

    @Test
    void listUsers_mapsRows_setsSuperUserFromPrivileges() {
        DfQuery.reset(dql -> new RowCollection(userRow("dmadmin", 16), userRow("test01", 8)));

        List<UserInfo> users = userGroupService.listUsers("test-session", null);

        assertEquals("dmadmin", users.get(0).getUserName());
        assertEquals("1100000180000001", users.get(0).getObjectId());
        assertTrue(users.get(0).isSuperUser());
        assertFalse(users.get(1).isSuperUser());
    }

    @Test
    void listGroups_mapsRows_readsPrivateAndDynamic() {
        DfQuery.reset(dql -> new RowCollection(groupRow("docu", true, false), groupRow("admingroup", false, true)));

        List<GroupInfo> groups = userGroupService.listGroups("test-session", null);

        assertEquals("docu", groups.get(0).getGroupName());
        assertTrue(groups.get(0).isPrivate());
        assertFalse(groups.get(0).isDynamic());
        assertFalse(groups.get(1).isPrivate());
        assertTrue(groups.get(1).isDynamic());
        assertEquals(List.of(), groups.get(0).getUsersNames());
    }

    @Test
    void getGroup_addsMembersAndSubgroups() {
        Map<String, Object> row = groupRow("docu", false, false);
        row.put("users_names", List.of("test01", "", "test02"));
        row.put("groups_names", List.of("admingroup"));
        DfQuery.reset(dql -> new RowCollection(row));

        GroupInfo group = userGroupService.getGroup("test-session", "docu");

        assertEquals("docu", group.getGroupName());
        assertEquals(List.of("test01", "test02"), group.getUsersNames());
        assertEquals(List.of("admingroup"), group.getGroupsNames());
    }

    @Test
    void getGroup_noRow_throwsGroupNotFound() {
        DfQuery.reset(dql -> new RowCollection());

        assertThrows(GroupNotFoundException.class, () -> userGroupService.getGroup("test-session", "nogroup"));
    }

    @Test
    void getGroupsForUser_queriesUsersNamesWithQuoteDoubled_mapsEachRow() {
        DfQuery.reset(dql -> new RowCollection(groupRow("docu", false, false), groupRow("admingroup", false, false)));

        List<GroupInfo> groups = userGroupService.getGroupsForUser("test-session", "o'brien");

        assertTrue(DfQuery.executed.get(0).contains("WHERE ANY users_names = 'o''brien'"));
        assertEquals(2, groups.size());
        assertEquals("docu", groups.get(0).getGroupName());
        assertEquals("admingroup", groups.get(1).getGroupName());
    }

    private Map<String, Object> userRow(String userName, int privileges) {
        Map<String, Object> row = new HashMap<>();
        row.put("r_object_id", "1100000180000001");
        row.put("user_name", userName);
        row.put("user_os_name", userName);
        row.put("user_address", "");
        row.put("user_state", "0");
        row.put("default_folder", "/" + userName);
        row.put("user_group_name", "");
        row.put("user_privileges", privileges);
        return row;
    }

    private Map<String, Object> groupRow(String groupName, boolean isPrivate, boolean isDynamic) {
        Map<String, Object> row = new HashMap<>();
        row.put("r_object_id", "1200000180000001");
        row.put("group_name", groupName);
        row.put("description", "");
        row.put("group_class", "group");
        row.put("group_admin", "");
        row.put("is_private", isPrivate);
        row.put("is_dynamic", isDynamic);
        row.put("owner_name", "dmadmin");
        row.put("group_address", "");
        row.put("group_source", "");
        return row;
    }
}
