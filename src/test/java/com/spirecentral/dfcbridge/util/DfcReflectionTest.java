package com.spirecentral.dfcbridge.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

class DfcReflectionTest {

    @Test
    void invoke_methodDeclaredOnlyOnInterface_returnsResult() throws Exception {
        Object result = DfcReflection.invoke(new HiddenGreeter(), "greet", new Class<?>[]{String.class}, "Ann");

        assertEquals("hello Ann", result);
    }

    @Test
    void findMethod_secondLookup_returnsCachedMethod() throws Exception {
        Method first = DfcReflection.findMethod(HiddenGreeter.class, "greet", new Class<?>[]{String.class});
        Method second = DfcReflection.findMethod(HiddenGreeter.class, "greet", new Class<?>[]{String.class});

        assertSame(first, second);
    }

    @Test
    void dfcClass_loadsTheClassThroughTheThreadContextClassloader() throws Exception {
        URL testClasses = DfcReflectionTest.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader contextLoader = new URLClassLoader(new URL[]{testClasses},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> loaded = withContextLoader(contextLoader, () -> DfcReflection.dfcClass(Greeter.class.getName()));

            assertSame(contextLoader, loaded.getClassLoader());
        }
    }

    @Test
    void dfcClass_classNotOnTheThreadContextClassloader_throwsClassNotFound() {
        assertThrows(ClassNotFoundException.class, () -> withContextLoader(ClassLoader.getPlatformClassLoader(),
                () -> DfcReflection.dfcClass(Greeter.class.getName())));
    }

    private static <T> T withContextLoader(ClassLoader loader, Callable<T> work) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return work.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public interface Greeter {
        default String greet(String name) {
            return "hello " + name;
        }
    }

    private static class HiddenGreeter implements Greeter {
    }
}
