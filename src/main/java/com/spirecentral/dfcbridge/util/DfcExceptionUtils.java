package com.spirecentral.dfcbridge.util;

import com.spirecentral.dfcbridge.exception.InvalidInputException;
import org.springframework.http.HttpStatus;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/**
 * Utility class for exceptions raised by reflective DFC calls.
 *
 * <p>The bridge compiles without DFC, so DFC exceptions are matched by class name
 * and their message id is read through reflection.
 */
public final class DfcExceptionUtils {

    public static final String DFC_ID_NOT_FOUND_EXCEPTION = "com.documentum.fc.client.DfIdNotFoundException";

    private static final String DFC_PACKAGE_PREFIX = "com.documentum.";
    private static final String DFC_AUTHENTICATION_EXCEPTION = "com.documentum.fc.client.DfAuthenticationException";
    private static final String DFC_OBJECT_NOT_FOUND_EXCEPTION = "com.documentum.fc.client.DfObjectNotFoundException";
    private static final String DFC_DOCBROKER_EXCEPTION = "com.documentum.fc.client.DfDocbrokerException";
    private static final String DFC_DOCBASE_UNREACHABLE_EXCEPTION = "com.documentum.fc.client.DfDocbaseUnreachableException";

    private static final Set<String> BAD_REQUEST_MESSAGE_IDS = Set.of(
            "DM_API_E_BADID", "DM_API_E_BADATTRNAME", "DM_API_E_BADCMD");

    private DfcExceptionUtils() {
    }

    /**
     * Returns the exception DFC threw when {@code e} wraps it, otherwise {@code e} itself.
     */
    public static Throwable unwrap(Exception e) {
        if (e instanceof InvocationTargetException) {
            return ((InvocationTargetException) e).getTargetException();
        }
        return e;
    }

    /**
     * Returns the HTTP status for an error caused by {@code cause}.
     */
    public static HttpStatus statusOf(Throwable cause) {
        Throwable e = cause instanceof Exception ? unwrap((Exception) cause) : cause;
        if (e instanceof InvalidInputException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (e == null || !e.getClass().getName().startsWith(DFC_PACKAGE_PREFIX)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (isInstanceOf(e, DFC_AUTHENTICATION_EXCEPTION)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (isInstanceOf(e, DFC_OBJECT_NOT_FOUND_EXCEPTION)) {
            return HttpStatus.NOT_FOUND;
        }
        if (isInstanceOf(e, DFC_DOCBROKER_EXCEPTION) || isInstanceOf(e, DFC_DOCBASE_UNREACHABLE_EXCEPTION)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        String messageId = messageId(e);
        if (messageId != null) {
            if (messageId.startsWith("DM_QUERY") || BAD_REQUEST_MESSAGE_IDS.contains(messageId)) {
                return HttpStatus.BAD_REQUEST;
            }
            if ((messageId.startsWith("DM_SYSOBJECT_E_NO_") && messageId.endsWith("_ACCESS"))
                    || messageId.startsWith("DM_SYSOBJECT_E_LINK_PERMIT")) {
                return HttpStatus.FORBIDDEN;
            }
        }
        return HttpStatus.BAD_GATEWAY;
    }

    /**
     * Returns true when the class of {@code e}, or one of its superclasses, has the given name.
     */
    public static boolean isInstanceOf(Throwable e, String className) {
        for (Class<?> c = e.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static String messageId(Throwable e) {
        try {
            Object id = e.getClass().getMethod("getMessageId").invoke(e);
            return id instanceof String ? (String) id : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
