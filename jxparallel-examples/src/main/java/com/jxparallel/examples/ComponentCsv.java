package com.jxparallel.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The components of the benchmark, read from {@code /componentbench/components.csv}: the file
 * {@code scripts/generate-component-bench.py} also copies into the JXML module, so both runners build the same N.
 * {@code -Djx.comp.n=name:n[,name:n]} overrides N for one run (the pilot does).
 */
final class ComponentCsv {
    /** One row: only the columns this runner needs. */
    static final class Entry {
        final String name;
        final String group;
        final String variant;
        int n;
        final String kind;
        final String update;
        /** The JavaFX case, or empty when the component has no JavaFX mode. */
        final String fx;
        final String aliasOf;
        final String note;

        Entry(String[] f) {
            name = f[0];
            group = f[2];
            variant = f[3];
            n = Integer.parseInt(f[4]);
            kind = f[5];
            update = f[9];
            fx = f[10];
            aliasOf = f[11];
            note = f.length > 12 ? f[12] : "";
        }
    }

    private ComponentCsv() {
    }

    static Map<String, Entry> read() throws IOException {
        Map<String, Integer> overrides = new HashMap<String, Integer>();
        String spec = System.getProperty("jx.comp.n");
        if (spec != null && !spec.isEmpty()) {
            for (String part : spec.split(",")) {
                String[] kv = part.split(":");
                overrides.put(kv[0].trim(), Integer.valueOf(kv[1].trim()));
            }
        }
        InputStream in = ComponentCsv.class.getResourceAsStream("/componentbench/components.csv");
        if (in == null) {
            throw new IOException("componentbench/components.csv is not on the classpath");
        }
        Map<String, Entry> map = new LinkedHashMap<String, Entry>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Entry e = new Entry(line.split(",", -1));
                    Integer n = overrides.get(e.name);
                    if (n != null) {
                        e.n = n.intValue();
                    }
                    map.put(e.name, e);
                }
            }
        }
        return map;
    }
}
