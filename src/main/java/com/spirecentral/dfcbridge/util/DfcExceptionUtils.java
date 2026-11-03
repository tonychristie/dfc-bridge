package com.spirecentral.dfcbridge.util;

import java.lang.reflect.InvocationTargetException;

/**
 * Utility class for exceptions raised by reflective DFC calls.
 */
public final class DfcExceptionUtils {

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
}
