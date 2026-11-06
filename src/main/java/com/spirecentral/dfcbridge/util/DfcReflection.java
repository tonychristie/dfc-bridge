package com.spirecentral.dfcbridge.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls DFC methods by name and parameter types through reflection.
 *
 * <p>The bridge compiles without DFC, so DFC classes are found by name at call time.
 * DFC returns proxy and non-public implementation classes, so a method is looked up
 * as a public method of the class first and then on each of its interfaces, and is
 * made accessible before it is called. Each method found is cached by class, name
 * and parameter types.
 *
 * <p>An exception thrown by the called method reaches the caller as the
 * {@link java.lang.reflect.InvocationTargetException} from {@link Method#invoke}.
 */
public final class DfcReflection {

    private static final Class<?>[] NO_PARAMS = new Class<?>[0];

    private static final Map<List<Object>, Method> METHODS = new ConcurrentHashMap<>();

    private DfcReflection() {
        // Utility class
    }

    /**
     * Calls a no-argument method on a DFC object.
     */
    public static Object invoke(Object target, String methodName) throws Exception {
        return invoke(target.getClass(), target, methodName, NO_PARAMS);
    }

    /**
     * Calls a method on a DFC object, looking it up on the object's class.
     */
    public static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args)
            throws Exception {
        return invoke(target.getClass(), target, methodName, paramTypes, args);
    }

    /**
     * Calls a method looked up on the given class or interface. The target is null for a static method.
     */
    public static Object invoke(Class<?> type, Object target, String methodName, Class<?>[] paramTypes,
                                Object... args) throws Exception {
        return findMethod(type, methodName, paramTypes).invoke(target, args);
    }

    /**
     * Calls a method looked up on the DFC class or interface with the given name.
     */
    public static Object invoke(String typeName, Object target, String methodName, Class<?>[] paramTypes,
                                Object... args) throws Exception {
        return invoke(Class.forName(typeName), target, methodName, paramTypes, args);
    }

    /**
     * Creates an instance of the DFC class with the given name.
     */
    public static Object newInstance(String className, Class<?>[] paramTypes, Object... args) throws Exception {
        return Class.forName(className).getConstructor(paramTypes).newInstance(args);
    }

    /**
     * Creates an instance of the DFC class with the given name through its no-argument constructor.
     */
    public static Object newInstance(String className) throws Exception {
        return newInstance(className, NO_PARAMS);
    }

    static Method findMethod(Class<?> type, String methodName, Class<?>[] paramTypes) throws NoSuchMethodException {
        List<Object> key = List.of(type, methodName, List.of(paramTypes));
        Method method = METHODS.get(key);
        if (method == null) {
            method = lookUp(type, methodName, paramTypes);
            method.setAccessible(true);
            METHODS.put(key, method);
        }
        return method;
    }

    private static Method lookUp(Class<?> type, String methodName, Class<?>[] paramTypes)
            throws NoSuchMethodException {
        try {
            return type.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            for (Class<?> iface : allInterfaces(type)) {
                try {
                    return iface.getMethod(methodName, paramTypes);
                } catch (NoSuchMethodException ignored) {
                    // Try the next interface
                }
            }
            throw new NoSuchMethodException(methodName + " on " + type.getName());
        }
    }

    private static List<Class<?>> allInterfaces(Class<?> type) {
        List<Class<?>> interfaces = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                if (!interfaces.contains(iface)) {
                    interfaces.add(iface);
                    for (Class<?> superIface : iface.getInterfaces()) {
                        if (!interfaces.contains(superIface)) {
                            interfaces.add(superIface);
                        }
                    }
                }
            }
        }
        return interfaces;
    }
}
