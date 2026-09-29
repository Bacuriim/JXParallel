package com.jxparallel.fx.nativeimpl;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every registered native implementation must match a generated JX method: its key is the
 * declaring class's simple name, the method name and the parameter types' simple names, and a
 * key that matches nothing would silently never run (a method of Parent registered as Node's).
 */
class NativeKeysTest extends NativeTestSupport {

    @Test
    void everyRegisteredKeyMatchesAGeneratedMethod() throws Exception {
        Map<String, List<Class<?>>> bySimpleName = jxClasses();
        List<String> unmatched = new ArrayList<>();
        for (String key : Native.registeredKeys()) {
            String owner = key.substring(0, key.indexOf('.'));
            String method = key.substring(key.indexOf('.') + 1, key.indexOf('('));
            String params = key.substring(key.indexOf('(') + 1, key.length() - 1);
            if ("Notifications".equals(owner)) {
                continue; // checked in jxparallel-fx-controlsfx, where the class is generated
            }
            if (!matches(bySimpleName.get(owner), method, params)) {
                unmatched.add(key);
            }
        }
        assertTrue(unmatched.isEmpty(), "registered but never called: " + unmatched);
    }

    private static boolean matches(List<Class<?>> owners, String method, String params) {
        if (owners == null) {
            return false;
        }
        for (Class<?> owner : owners) {
            for (Method m : owner.getDeclaredMethods()) {
                if (!m.getName().equals(method)) {
                    continue;
                }
                StringBuilder s = new StringBuilder();
                for (Class<?> p : m.getParameterTypes()) {
                    s.append(s.length() == 0 ? "" : ",").append(p.getSimpleName());
                }
                if (s.toString().equals(params)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** JX classes (and nested classes) of com.jxparallel.fx by simple name, from the compiled classes. */
    private static Map<String, List<Class<?>>> jxClasses() throws Exception {
        URL root = com.jxparallel.fx.Fx.class.getProtectionDomain().getCodeSource().getLocation();
        File base = new File(root.toURI());
        Map<String, List<Class<?>>> out = new HashMap<>();
        collect(base, new File(base, "com/jxparallel/fx"), out);
        return out;
    }

    private static void collect(File base, File dir, Map<String, List<Class<?>>> out) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().equals("peer") && !f.getName().equals("nativeimpl")) {
                    collect(base, f, out);
                }
            } else if (f.getName().endsWith(".class")) {
                String path = base.toURI().relativize(f.toURI()).getPath();
                String name = path.substring(0, path.length() - 6).replace('/', '.');
                try {
                    Class<?> c = Class.forName(name, false, NativeKeysTest.class.getClassLoader());
                    out.computeIfAbsent(c.getSimpleName(), k -> new ArrayList<>()).add(c);
                } catch (Throwable ignored) {
                    // anonymous and synthetic classes
                }
            }
        }
    }
}
