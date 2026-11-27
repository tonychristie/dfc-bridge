package com.spirecentral.dfcbridge.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupInfoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testJsonWithFlagsTrue() {
        GroupInfo group = GroupInfo.builder()
                .groupName("docu")
                .isPrivate(true)
                .isDynamic(true)
                .ownerName("dmadmin")
                .groupAddress("docu@example.com")
                .groupSource("LDAP")
                .build();

        JsonNode json = mapper.valueToTree(group);

        assertTrue(json.get("isPrivate").asBoolean());
        assertTrue(json.get("isDynamic").asBoolean());
        assertEquals("dmadmin", json.get("ownerName").asString());
        assertEquals("docu@example.com", json.get("groupAddress").asString());
        assertEquals("LDAP", json.get("groupSource").asString());
    }

    @Test
    void testJsonFieldNamesAreTheDeclaredProperties() {
        GroupInfo group = GroupInfo.builder()
                .groupName("docu")
                .isPrivate(true)
                .isDynamic(true)
                .build();

        JsonNode json = mapper.valueToTree(group);

        assertEquals(Set.of("objectId", "groupName", "description", "groupClass", "groupAdmin",
                        "isPrivate", "isDynamic", "ownerName", "groupAddress", "groupSource",
                        "usersNames", "groupsNames"),
                Set.copyOf(json.propertyNames()));
    }

    @Test
    void testJsonWithFlagsFalse() {
        GroupInfo group = GroupInfo.builder()
                .groupName("docu")
                .isPrivate(false)
                .isDynamic(false)
                .build();

        JsonNode json = mapper.valueToTree(group);

        assertFalse(json.get("isPrivate").asBoolean());
        assertFalse(json.get("isDynamic").asBoolean());
    }
}
