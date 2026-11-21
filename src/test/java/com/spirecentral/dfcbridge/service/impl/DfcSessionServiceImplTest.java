package com.spirecentral.dfcbridge.service.impl;

import com.documentum.fc.client.DfAuthenticationException;
import com.documentum.fc.client.DfClient;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.client.IDfTypedObject;
import com.documentum.fc.common.DfException;
import com.spirecentral.dfcbridge.dto.ConnectRequest;
import com.spirecentral.dfcbridge.dto.ConnectResponse;
import com.spirecentral.dfcbridge.exception.ConnectionException;
import com.spirecentral.dfcbridge.exception.SessionNotFoundException;
import com.spirecentral.dfcbridge.model.SessionInfo;
import com.spirecentral.dfcbridge.service.DfcAvailabilityService;
import com.spirecentral.dfcbridge.service.DfcInstanceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DfcSessionServiceImplTest {

    private IDfSessionManager sessionManager;
    private IDfSession dfSession;
    private DfcInstanceRegistry dfcInstances;
    private URLClassLoader dfcLoader;
    private DfcSessionServiceImpl sessionService;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager = mock(IDfSessionManager.class);
        dfSession = mock(IDfSession.class);
        when(sessionManager.getSession("TestRepo")).thenReturn(dfSession);
        when(dfSession.isConnected()).thenReturn(true);
        DfClient.sessionManager = sessionManager;

        // a child of the test classloader, so that it finds the DFC stubs and can be told apart from it
        dfcLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
        dfcInstances = mock(DfcInstanceRegistry.class);
        when(dfcInstances.loaderFor(anyString(), anyInt())).thenReturn(dfcLoader);

        sessionService = new DfcSessionServiceImpl(mock(DfcAvailabilityService.class), dfcInstances);
        ReflectionTestUtils.setField(sessionService, "sessionTimeoutMinutes", 30);
    }

    @AfterEach
    void tearDown() throws Exception {
        DfClient.sessionManager = null;
        dfcLoader.close();
    }

    @Test
    void connect_usesTheLoaderOfTheRequestedDocbrokerAndPort() throws Exception {
        AtomicReference<ClassLoader> loaderAtGetSession = new AtomicReference<>();
        when(sessionManager.getSession("TestRepo")).thenAnswer(invocation -> {
            loaderAtGetSession.set(Thread.currentThread().getContextClassLoader());
            return dfSession;
        });
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        sessionService.connect(ConnectRequest.builder()
                .docbroker("192.168.0.111")
                .port(1490)
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build());

        verify(dfcInstances).loaderFor("192.168.0.111", 1490);
        assertSame(dfcLoader, loaderAtGetSession.get());
        assertSame(previous, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void connect_reportsTheRequestedDocbrokerAndTheHostOfTheServer() throws Exception {
        IDfTypedObject serverConfig = mock(IDfTypedObject.class);
        when(serverConfig.getString("r_host_name")).thenReturn("cs23-tst");
        when(dfSession.getServerConfig()).thenReturn(serverConfig);

        ConnectResponse response = sessionService.connect(ConnectRequest.builder()
                .docbroker("192.168.0.111")
                .port(1490)
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build());

        SessionInfo info = sessionService.getSessionInfo(response.getSessionId());
        assertEquals("192.168.0.111", info.getDocbroker());
        assertEquals(1490, info.getPort());
        assertEquals("cs23-tst", response.getRepositoryInfo().getContentServerHost());
    }

    @Test
    void connect_repositoryInfoCannotBeRead_leavesContentServerHostEmpty() throws Exception {
        when(dfSession.getServerConfig()).thenThrow(new DfException("DM_API_E_NO_SERVER_CONFIG"));

        ConnectResponse response = sessionService.connect(ConnectRequest.builder()
                .docbroker("192.168.0.111")
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build());

        assertNull(response.getRepositoryInfo().getContentServerHost());
    }

    @Test
    void connect_blankDocbroker_throwsConnectionExceptionWith400() {
        ConnectionException e = assertThrows(ConnectionException.class, () -> sessionService.connect(
                ConnectRequest.builder()
                        .docbroker(" ")
                        .repository("TestRepo")
                        .username("dmadmin")
                        .password("password")
                        .build()));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verifyNoInteractions(dfcInstances);
    }

    @Test
    void withSession_runsWorkWithTheSessionLoaderAndRestoresThePreviousLoader() {
        String sessionId = connect();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        ClassLoader loaderInWork = sessionService.withSession(sessionId,
                s -> Thread.currentThread().getContextClassLoader());

        assertSame(dfcLoader, loaderInWork);
        assertSame(previous, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void withSession_workThrows_restoresThePreviousLoader() {
        String sessionId = connect();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        assertThrows(IllegalStateException.class, () -> sessionService.withSession(sessionId, s -> {
            throw new IllegalStateException("work failed");
        }));

        assertSame(previous, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void isSessionValid_checksTheDfcSessionWithTheSessionLoader() {
        String sessionId = connect();
        AtomicReference<ClassLoader> loaderAtIsConnected = recordLoaderAtIsConnected();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        assertTrue(sessionService.isSessionValid(sessionId));

        assertSame(dfcLoader, loaderAtIsConnected.get());
        assertSame(previous, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void disconnect_releasesTheDfcSessionWithTheSessionLoader() {
        String sessionId = connect();
        AtomicReference<ClassLoader> loaderAtRelease = recordLoaderAtRelease();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        sessionService.disconnect(sessionId);

        assertSame(dfcLoader, loaderAtRelease.get());
        assertSame(previous, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void cleanupExpiredSessions_releasesTheDfcSessionWithTheSessionLoader() {
        String sessionId = connect();
        sessionService.getSessionInfo(sessionId).setLastActivity(Instant.now().minus(Duration.ofHours(1)));
        AtomicReference<ClassLoader> loaderAtRelease = recordLoaderAtRelease();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        sessionService.cleanupExpiredSessions();

        assertSame(dfcLoader, loaderAtRelease.get());
        assertSame(previous, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void withSession_secondCallWaitsForFirstCallsWork() throws Exception {
        String sessionId = connect();
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);

        Thread first = new Thread(() -> sessionService.withSession(sessionId, s -> {
            events.add("first-start");
            firstStarted.countDown();
            await(finishFirst);
            events.add("first-end");
            return null;
        }));
        first.start();
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        Thread second = new Thread(() -> sessionService.withSession(sessionId, s -> {
            events.add("second");
            return null;
        }));
        second.start();
        waitUntilWaiting(second);

        assertEquals(List.of("first-start"), events);

        finishFirst.countDown();
        first.join(5000);
        second.join(5000);

        assertEquals(List.of("first-start", "first-end", "second"), events);
    }

    @Test
    void withSession_waitingCallThrowsWhenDisconnectReleasesTheSession() throws Exception {
        String sessionId = connect();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch disconnectNow = new CountDownLatch(1);
        AtomicBoolean secondWorkRan = new AtomicBoolean(false);
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        // disconnect runs on the thread that holds the lock, so it releases the session
        // while the second call is still waiting for the lock
        Thread first = new Thread(() -> sessionService.withSession(sessionId, s -> {
            firstStarted.countDown();
            await(disconnectNow);
            sessionService.disconnect(sessionId);
            return null;
        }));
        first.start();
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        Thread second = new Thread(() -> {
            try {
                sessionService.withSession(sessionId, s -> {
                    secondWorkRan.set(true);
                    return null;
                });
            } catch (Throwable t) {
                secondError.set(t);
            }
        });
        second.start();
        waitUntilWaiting(second);

        disconnectNow.countDown();
        first.join(5000);
        second.join(5000);

        assertInstanceOf(SessionNotFoundException.class, secondError.get());
        assertFalse(secondWorkRan.get());
        verify(sessionManager).release(dfSession);
    }

    @Test
    void cleanupExpiredSessions_skipsSessionInUseAndReleasesItOnALaterRun() throws Exception {
        String sessionId = connect();
        Instant expired = Instant.now().minus(Duration.ofHours(1));
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch finishWork = new CountDownLatch(1);

        Thread request = new Thread(() -> sessionService.withSession(sessionId, s -> {
            sessionService.getSessionInfo(sessionId).setLastActivity(expired);
            workStarted.countDown();
            await(finishWork);
            return null;
        }));
        request.start();
        assertTrue(workStarted.await(5, TimeUnit.SECONDS));

        sessionService.cleanupExpiredSessions();

        assertNotNull(sessionService.getSessionInfo(sessionId));
        verify(sessionManager, never()).release(any());

        finishWork.countDown();
        request.join(5000);

        sessionService.cleanupExpiredSessions();

        verify(sessionManager).release(dfSession);
        assertThrows(SessionNotFoundException.class, () -> sessionService.getSessionInfo(sessionId));
    }

    @Test
    void withSession_unknownSessionId_throwsSessionNotFound() {
        AtomicBoolean workRan = new AtomicBoolean(false);

        assertThrows(SessionNotFoundException.class, () -> sessionService.withSession("unknown", s -> {
            workRan.set(true);
            return null;
        }));
        assertFalse(workRan.get());
    }

    @Test
    void withSession_dfcSessionNotConnected_throwsSessionNotFoundAndRemovesTheSession() {
        String sessionId = connect();
        when(dfSession.isConnected()).thenReturn(false);
        AtomicBoolean workRan = new AtomicBoolean(false);

        assertThrows(SessionNotFoundException.class, () -> sessionService.withSession(sessionId, s -> {
            workRan.set(true);
            return null;
        }));
        assertThrows(SessionNotFoundException.class, () -> sessionService.withSession(sessionId, s -> {
            workRan.set(true);
            return null;
        }));

        assertFalse(workRan.get());
        assertThrows(SessionNotFoundException.class, () -> sessionService.getSessionInfo(sessionId));
        verify(sessionManager).release(dfSession);
    }

    @Test
    void connect_returnsSessionIdForTheRequestedRepositoryAndUser() {
        String sessionId = connect();

        SessionInfo info = sessionService.getSessionInfo(sessionId);
        assertEquals(sessionId, info.getSessionId());
        assertEquals("TestRepo", info.getRepository());
        assertEquals("dmadmin", info.getUser());
    }

    @Test
    void connect_authenticationFails_throwsConnectionExceptionWith401() throws Exception {
        when(sessionManager.getSession("TestRepo"))
                .thenThrow(new DfAuthenticationException("DM_SESSION_E_AUTHENTICATION"));

        ConnectionException e = assertThrows(ConnectionException.class, () -> connect());

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
    }

    @Test
    void cleanupExpiredSessions_releasesIdleSessionAndKeepsActiveSession() throws Exception {
        IDfSession activeDfSession = mock(IDfSession.class);
        when(activeDfSession.isConnected()).thenReturn(true);
        when(sessionManager.getSession("TestRepo")).thenReturn(dfSession, activeDfSession);
        String idleSessionId = connect();
        String activeSessionId = connect();
        sessionService.getSessionInfo(idleSessionId).setLastActivity(Instant.now().minus(Duration.ofHours(1)));

        sessionService.cleanupExpiredSessions();

        verify(sessionManager).release(dfSession);
        verify(sessionManager, never()).release(activeDfSession);
        assertThrows(SessionNotFoundException.class, () -> sessionService.getSessionInfo(idleSessionId));
        assertNotNull(sessionService.getSessionInfo(activeSessionId));
    }

    @Test
    void shutdown_releasesEverySession() throws Exception {
        IDfSession secondDfSession = mock(IDfSession.class);
        when(sessionManager.getSession("TestRepo")).thenReturn(dfSession, secondDfSession);
        String firstSessionId = connect();
        String secondSessionId = connect();

        sessionService.shutdown();

        verify(sessionManager).release(dfSession);
        verify(sessionManager).release(secondDfSession);
        assertThrows(SessionNotFoundException.class, () -> sessionService.getSessionInfo(firstSessionId));
        assertThrows(SessionNotFoundException.class, () -> sessionService.getSessionInfo(secondSessionId));
    }

    private String connect() {
        return sessionService.connect(ConnectRequest.builder()
                .docbroker("localhost")
                .repository("TestRepo")
                .username("dmadmin")
                .password("password")
                .build()).getSessionId();
    }

    private AtomicReference<ClassLoader> recordLoaderAtIsConnected() {
        AtomicReference<ClassLoader> loader = new AtomicReference<>();
        when(dfSession.isConnected()).thenAnswer(invocation -> {
            loader.set(Thread.currentThread().getContextClassLoader());
            return true;
        });
        return loader;
    }

    private AtomicReference<ClassLoader> recordLoaderAtRelease() {
        AtomicReference<ClassLoader> loader = new AtomicReference<>();
        doAnswer(invocation -> {
            loader.set(Thread.currentThread().getContextClassLoader());
            return null;
        }).when(sessionManager).release(dfSession);
        return loader;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntilWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING) {
            assertTrue(System.nanoTime() < deadline, "thread did not start waiting for the session lock");
            Thread.sleep(10);
        }
    }
}
