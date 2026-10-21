package com.jxparallel.examples.nativeui.components;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The components of the benchmark and how many instances each builds, read from
 * {@code /componentbench/components.csv} (written by {@code scripts/generate-component-bench.py}; the JavaFX
 * runner reads the same file, so both sides build the same N). {@code -Djx.comp.n=<name>:<n>[,<name>:<n>]} or
 * {@link #override} change N for one run (the pilot that chooses N does).
 */
public final class ComponentTable {
    /** One row of the table. */
    public static final class Entry {
        public final String name;
        public final String tag;
        public final String group;
        public final String variant;
        public final int n;
        public final String kind;
        /** Native node type, prop and kind (s string, d double, i int, b boolean) of the value the fingerprint reads. */
        public final String probeType;
        public final String probeProp;
        public final String probeKind;
        public final String update;
        /** The JavaFX case, or empty when the component has no JavaFX mode. */
        public final String fx;
        public final String aliasOf;
        public final String note;

        Entry(String[] f) {
            name = f[0];
            tag = f[1];
            group = f[2];
            variant = f[3];
            n = Integer.parseInt(f[4]);
            kind = f[5];
            probeType = f[6];
            probeProp = f[7];
            probeKind = f[8];
            update = f[9];
            fx = f[10];
            aliasOf = f[11];
            note = f.length > 12 ? f[12] : "";
        }
    }

    private static Map<String, Entry> entries;
    private static final Map<String, Integer> OVERRIDES = new HashMap<String, Integer>();

    static {
        String spec = System.getProperty("jx.comp.n");
        if (spec != null && !spec.isEmpty()) {
            for (String part : spec.split(",")) {
                String[] kv = part.split(":");
                OVERRIDES.put(kv[0].trim(), Integer.valueOf(kv[1].trim()));
            }
        }
    }

    private ComponentTable() {
    }

    public static synchronized Map<String, Entry> all() {
        if (entries == null) {
            Map<String, Entry> map = new LinkedHashMap<String, Entry>();
            InputStream in = ComponentTable.class.getResourceAsStream("/componentbench/components.csv");
            if (in == null) {
                throw new IllegalStateException("componentbench/components.csv is not on the classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine(); // header
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        Entry e = new Entry(line.split(",", -1));
                        map.put(e.name, e);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            entries = Collections.unmodifiableMap(map);
        }
        return entries;
    }

    public static Entry get(String name) {
        Entry e = all().get(name);
        if (e == null) {
            throw new IllegalArgumentException("unknown component " + name);
        }
        return e;
    }

    /** Instances of the component: the override if there is one, else the table's. */
    public static int n(String name) {
        Integer o = OVERRIDES.get(name);
        return o != null ? o.intValue() : get(name).n;
    }

    public static void override(String name, int n) {
        OVERRIDES.put(name, Integer.valueOf(n));
    }

    public static List<String> names() {
        return new ArrayList<String>(all().keySet());
    }
}
