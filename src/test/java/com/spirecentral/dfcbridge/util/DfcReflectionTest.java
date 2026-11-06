package com.spirecentral.dfcbridge.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

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

    public interface Greeter {
        default String greet(String name) {
            return "hello " + name;
        }
    }

    private static class HiddenGreeter implements Greeter {
    }
}
