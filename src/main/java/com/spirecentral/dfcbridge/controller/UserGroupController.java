package com.spirecentral.dfcbridge.controller;

import com.spirecentral.dfcbridge.dto.ErrorResponse;
import com.spirecentral.dfcbridge.model.GroupInfo;
import com.spirecentral.dfcbridge.model.UserInfo;
import com.spirecentral.dfcbridge.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user and group operations.
 *
 * <p>Uses DQL queries with explicit attribute selection to properly handle
 * repeating attributes (users_names, groups_names) which are not returned
 * by SELECT * queries in Documentum.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Users and Groups", description = "User and group operations")
public class UserGroupController {

    private final UserGroupService userGroupService;

    public UserGroupController(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @GetMapping("/users")
    @Operation(
        summary = "List users",
        description = "Lists users in the repository, optionally filtered by name pattern."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Users retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserInfo.class)))
        )
    })
    public ResponseEntity<List<UserInfo>> listUsers(
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "User name pattern filter (supports * wildcard)") @RequestParam(required = false) String pattern) {
        List<UserInfo> users = userGroupService.listUsers(sessionId, pattern);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userName}")
    @Operation(
        summary = "Get user",
        description = "Gets a user by name."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "User retrieved",
            content = @Content(schema = @Schema(implementation = UserInfo.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<UserInfo> getUser(
            @Parameter(description = "User name") @PathVariable String userName,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        UserInfo user = userGroupService.getUser(sessionId, userName);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/groups")
    @Operation(
        summary = "List groups",
        description = "Lists groups in the repository, optionally filtered by name pattern."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Groups retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GroupInfo.class)))
        )
    })
    public ResponseEntity<List<GroupInfo>> listGroups(
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "Group name pattern filter (supports * wildcard)") @RequestParam(required = false) String pattern) {
        List<GroupInfo> groups = userGroupService.listGroups(sessionId, pattern);
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/groups/{groupName}")
    @Operation(
        summary = "Get group",
        description = "Gets a group by name, including member lists (usersNames and groupsNames)."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Group retrieved",
            content = @Content(schema = @Schema(implementation = GroupInfo.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Group not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<GroupInfo> getGroup(
            @Parameter(description = "Group name") @PathVariable String groupName,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        GroupInfo group = userGroupService.getGroup(sessionId, groupName);
        return ResponseEntity.ok(group);
    }

    @GetMapping("/users/{userName}/groups")
    @Operation(
        summary = "Get groups for user",
        description = "Gets the groups that contain this user as a direct member."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Groups retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GroupInfo.class)))
        )
    })
    public ResponseEntity<List<GroupInfo>> getGroupsForUser(
            @Parameter(description = "User name") @PathVariable String userName,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        List<GroupInfo> groups = userGroupService.getGroupsForUser(sessionId, userName);
        return ResponseEntity.ok(groups);
    }
}
