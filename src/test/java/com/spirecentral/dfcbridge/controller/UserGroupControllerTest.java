package com.spirecentral.dfcbridge.controller;

import com.spirecentral.dfcbridge.exception.GroupNotFoundException;
import com.spirecentral.dfcbridge.exception.UserNotFoundException;
import com.spirecentral.dfcbridge.model.GroupInfo;
import com.spirecentral.dfcbridge.model.UserInfo;
import com.spirecentral.dfcbridge.service.UserGroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserGroupController.class)
class UserGroupControllerTest {

    private static final String SESSION_ID = "test-session-123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserGroupService userGroupService;

    @Test
    void listUsers_returnsUsersForPattern() throws Exception {
        when(userGroupService.listUsers(SESSION_ID, "dm*")).thenReturn(List.of(
                UserInfo.builder().userName("dmadmin").build(),
                UserInfo.builder().userName("dm_bof_registry").build()));

        mockMvc.perform(get("/api/v1/users").param("sessionId", SESSION_ID).param("pattern", "dm*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userName").value("dmadmin"))
                .andExpect(jsonPath("$[1].userName").value("dm_bof_registry"));
    }

    @Test
    void getUser_returnsUser() throws Exception {
        when(userGroupService.getUser(SESSION_ID, "dmadmin")).thenReturn(
                UserInfo.builder().userName("dmadmin").superUser(true).build());

        mockMvc.perform(get("/api/v1/users/dmadmin").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("dmadmin"))
                .andExpect(jsonPath("$.superUser").value(true));
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        when(userGroupService.getUser(SESSION_ID, "nouser")).thenThrow(new UserNotFoundException("nouser"));

        mockMvc.perform(get("/api/v1/users/nouser").param("sessionId", SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void listGroups_returnsGroupsForPattern() throws Exception {
        when(userGroupService.listGroups(SESSION_ID, "dm_*")).thenReturn(List.of(
                GroupInfo.builder().groupName("dm_browse_all").build(),
                GroupInfo.builder().groupName("dm_superusers").build()));

        mockMvc.perform(get("/api/v1/groups").param("sessionId", SESSION_ID).param("pattern", "dm_*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupName").value("dm_browse_all"))
                .andExpect(jsonPath("$[1].groupName").value("dm_superusers"));
    }

    @Test
    void getGroup_returnsGroupWithMembers() throws Exception {
        when(userGroupService.getGroup(SESSION_ID, "docu")).thenReturn(GroupInfo.builder()
                .groupName("docu")
                .usersNames(List.of("test01", "test02"))
                .groupsNames(List.of("admingroup"))
                .build());

        mockMvc.perform(get("/api/v1/groups/docu").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("docu"))
                .andExpect(jsonPath("$.usersNames[1]").value("test02"))
                .andExpect(jsonPath("$.groupsNames[0]").value("admingroup"));
    }

    @Test
    void getGroup_notFound_returns404() throws Exception {
        when(userGroupService.getGroup(SESSION_ID, "nogroup")).thenThrow(new GroupNotFoundException("nogroup"));

        mockMvc.perform(get("/api/v1/groups/nogroup").param("sessionId", SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void getGroupsForUser_returnsGroups() throws Exception {
        when(userGroupService.getGroupsForUser(SESSION_ID, "test01")).thenReturn(List.of(
                GroupInfo.builder().groupName("docu").build()));

        mockMvc.perform(get("/api/v1/users/test01/groups").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupName").value("docu"));
    }
}
