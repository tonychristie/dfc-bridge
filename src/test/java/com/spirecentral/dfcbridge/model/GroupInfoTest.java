package com.spirecentral.dfcbridge.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
        assertEquals("dmadmin", json.get("ownerName").asText());
        assertEquals("docu@example.com", json.get("groupAddress").asText());
        assertEquals("LDAP", json.get("groupSource").asText());
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
