package com.spirecentral.dfcbridge.util;

import org.junit.jupiter.api.Test;

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
}
