package com.spire.dfcbridge.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionInfoTest {

    @Test
    void testBuilderAndGetters() {
        Instant now = Instant.now();

        SessionInfo session = SessionInfo.builder()
                .sessionId("test-session-123")
                .connected(true)
                .repository("TestRepo")
                .user("dmadmin")
                .docbroker("docbroker.example.com")
                .port(1489)
                .dfcProfile("default")
                .sessionStart(now)
                .lastActivity(now)
                .serverVersion("23.2.0")
                .build();

        assertEquals("test-session-123", session.getSessionId());
        assertTrue(session.isConnected());
        assertEquals("TestRepo", session.getRepository());
        assertEquals("dmadmin", session.getUser());
        assertEquals("docbroker.example.com", session.getDocbroker());
        assertEquals(1489, session.getPort());
        assertEquals("default", session.getDfcProfile());
        assertEquals(now, session.getSessionStart());
        assertEquals(now, session.getLastActivity());
        assertEquals("23.2.0", session.getServerVersion());
    }

    @Test
    void testSetLastActivity() {
        Instant start = Instant.now();
        SessionInfo session = SessionInfo.builder()
                .sessionId("test")
                .sessionStart(start)
                .lastActivity(start)
                .build();

        Instant later = start.plusSeconds(60);
        session.setLastActivity(later);

        assertEquals(later, session.getLastActivity());
        assertEquals(start, session.getSessionStart());
    }
}
