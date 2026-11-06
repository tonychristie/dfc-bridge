package com.spirecentral.dfcbridge.util;

import com.documentum.fc.client.DfAuthenticationException;
import com.documentum.fc.client.DfIdNotFoundException;
import com.documentum.fc.client.DfNoServersException;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class DfcExceptionUtilsTest {

    @Test
    void unwrap_returnsTargetOfInvocationTargetException() {
        RuntimeException target = new RuntimeException("DM_SESSION_E_AUTH_FAIL");

        assertSame(target, DfcExceptionUtils.unwrap(new InvocationTargetException(target)));
    }

    @Test
    void unwrap_returnsOtherExceptionUnchanged() {
        NoSuchMethodException e = new NoSuchMethodException("apiGet");

        assertSame(e, DfcExceptionUtils.unwrap(e));
    }

    @Test
    void statusOf_authentication_is401() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                DfcExceptionUtils.statusOf(new DfAuthenticationException("DM_SESSION_E_AUTH_FAIL")));
    }

    @Test
    void statusOf_idNotFoundThroughObjectNotFoundSuperclass_is404() {
        assertEquals(HttpStatus.NOT_FOUND,
                DfcExceptionUtils.statusOf(new DfIdNotFoundException("DM_API_E_EXIST")));
    }

    @Test
    void statusOf_noServersThroughDocbrokerSuperclass_is503() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                DfcExceptionUtils.statusOf(new DfNoServersException("DM_DOCBROKER_E_NO_SERVERS_FOR_DOCBASE")));
    }

    @Test
    void statusOf_noDeleteAccess_is403() {
        assertEquals(HttpStatus.FORBIDDEN,
                DfcExceptionUtils.statusOf(new DfException("DM_SYSOBJECT_E_NO_DELETE_ACCESS")));
    }

    @Test
    void statusOf_linkPermit_is403() {
        assertEquals(HttpStatus.FORBIDDEN,
                DfcExceptionUtils.statusOf(new DfException("DM_SYSOBJECT_E_LINK_PERMIT2")));
    }

    @Test
    void statusOf_querySyntax_is400() {
        assertEquals(HttpStatus.BAD_REQUEST,
                DfcExceptionUtils.statusOf(new DfException("DM_QUERY_E_SYNTAX")));
    }

    @Test
    void statusOf_badId_is400() {
        assertEquals(HttpStatus.BAD_REQUEST,
                DfcExceptionUtils.statusOf(new DfException("DM_API_E_BADID")));
    }

    @Test
    void statusOf_otherDfcMessageId_is502() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                DfcExceptionUtils.statusOf(new DfException("DM_SESSION_E_TIMEOUT")));
    }

    @Test
    void statusOf_dfcRuntimeException_is502() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                DfcExceptionUtils.statusOf(new DfRuntimeException("Using session handle that has already been released")));
    }

    @Test
    void statusOf_nonDfcException_is500() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                DfcExceptionUtils.statusOf(new NoSuchMethodException("apiGet")));
    }

    @Test
    void statusOf_classifiesTheExceptionAnInvocationTargetExceptionWraps() {
        assertEquals(HttpStatus.UNAUTHORIZED, DfcExceptionUtils.statusOf(
                new InvocationTargetException(new DfAuthenticationException("DM_SESSION_E_AUTH_FAIL"))));
    }
}
