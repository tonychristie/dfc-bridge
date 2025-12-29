package com.spire.dfcbridge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DqlServiceImpl helper methods using reflection.
 * These methods are private but critical for correct data type handling.
 */
class DqlServiceHelperTest {

    @ParameterizedTest
    @CsvSource({
            "BOOLEAN, getBoolean",
            "INTEGER, getInt",
            "DOUBLE, getDouble",
            "TIME, getTime",
            "ID, getId",
            "STRING, getString",
            "UNDEFINED, getString"
    })
    void testGetterMethodName(String dataType, String expectedMethod) throws Exception {
        Class<?> serviceClass = Class.forName("com.spire.dfcbridge.service.impl.DqlServiceImpl");

        // Get the private method
        Method method = serviceClass.getDeclaredMethod("getGetterMethodName", String.class);
        method.setAccessible(true);

        // Create instance with null dependency (we won't call methods that need it)
        Object instance = serviceClass.getDeclaredConstructor(DfcSessionService.class).newInstance((Object) null);

        String result = (String) method.invoke(instance, dataType);
        assertEquals(expectedMethod, result);
    }

    @ParameterizedTest
    @CsvSource({
            "BOOLEAN, getRepeatingBoolean",
            "INTEGER, getRepeatingInt",
            "DOUBLE, getRepeatingDouble",
            "TIME, getRepeatingTime",
            "ID, getRepeatingId",
            "STRING, getRepeatingString",
            "UNDEFINED, getRepeatingString"
    })
    void testRepeatingGetterMethodName(String dataType, String expectedMethod) throws Exception {
        Class<?> serviceClass = Class.forName("com.spire.dfcbridge.service.impl.DqlServiceImpl");

        Method method = serviceClass.getDeclaredMethod("getRepeatingGetterMethodName", String.class);
        method.setAccessible(true);

        Object instance = serviceClass.getDeclaredConstructor(DfcSessionService.class).newInstance((Object) null);

        String result = (String) method.invoke(instance, dataType);
        assertEquals(expectedMethod, result);
    }

    @Test
    void testGetterMethodNameDefault() throws Exception {
        Class<?> serviceClass = Class.forName("com.spire.dfcbridge.service.impl.DqlServiceImpl");

        Method method = serviceClass.getDeclaredMethod("getGetterMethodName", String.class);
        method.setAccessible(true);

        Object instance = serviceClass.getDeclaredConstructor(DfcSessionService.class).newInstance((Object) null);

        // Unknown types should default to getString
        String result = (String) method.invoke(instance, "UNKNOWN_TYPE");
        assertEquals("getString", result);
    }

    @Test
    void testRepeatingGetterMethodNameDefault() throws Exception {
        Class<?> serviceClass = Class.forName("com.spire.dfcbridge.service.impl.DqlServiceImpl");

        Method method = serviceClass.getDeclaredMethod("getRepeatingGetterMethodName", String.class);
        method.setAccessible(true);

        Object instance = serviceClass.getDeclaredConstructor(DfcSessionService.class).newInstance((Object) null);

        // Unknown types should default to getRepeatingString
        String result = (String) method.invoke(instance, "UNKNOWN_TYPE");
        assertEquals("getRepeatingString", result);
    }
}
