package com.jxparallel.examples.nativeui.components;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What every component sample binds to: N values of the kind the component takes (one per instance), in two sets,
 * {@code phase} 0 (the build) and 1 (what the update stage changes every instance to). The sets are built once per
 * JVM and shared, so building a screen never pays for making its data; the JavaFX runner makes the same values with
 * the same formulas ({@code ComponentFxData}), and the fingerprint checks they agree.
 */
public abstract class ComponentController {
    private static final Map<String, Object> CACHE = new HashMap<String, Object>();
    private static final List<String> CHOICES = Collections.unmodifiableList(Arrays.asList("A", "B", "C", "D"));
    private static final List<String> TITLES = Collections.unmodifiableList(Arrays.asList("Home", "Search", "Settings", "About"));
    private static final int MODEL = 10000;
    private static final int FOLDERS = 100;

    private final int n;
    private int phase;

    protected ComponentController(String name) {
        this.n = ComponentTable.n(name);
        for (String kind : new String[] {"string", "digits", "dates", "colors", "times", "initials", "numbers", "fractions",
            "widths", "sizes", "ints", "steps", "pages", "flags", "firsts"}) {
            values(kind, 0);
            values(kind, 1);
        }
        model();
        rowsModel();
        folders();
        pixels();
    }

    public int getCount() {
        return n;
    }

    /** 0 for the build, 1 for the update (and back, for the passes after). */
    public void setPhase(int value) {
        this.phase = value & 1;
    }

    public int getPhase() {
        return phase;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> list(String kind) {
        return (List<T>) values(kind, phase);
    }

    public List<String> getItems() {
        return list("string");
    }

    public List<String> getDigits() {
        return list("digits");
    }

    public List<String> getDates() {
        return list("dates");
    }

    public List<String> getColors() {
        return list("colors");
    }

    public List<String> getTimes() {
        return list("times");
    }

    public List<String> getInitials() {
        return list("initials");
    }

    public List<Double> getNumbers() {
        return list("numbers");
    }

    public List<Double> getFractions() {
        return list("fractions");
    }

    public List<Double> getWidths() {
        return list("widths");
    }

    public List<Double> getSizes() {
        return list("sizes");
    }

    public List<Integer> getInts() {
        return list("ints");
    }

    public List<Integer> getSteps() {
        return list("steps");
    }

    public List<Integer> getPages() {
        return list("pages");
    }

    public List<Boolean> getFlags() {
        return list("flags");
    }

    /** The scroll position of every list: 0 at the build, 7 after the update. */
    public List<Integer> getFirsts() {
        return list("firsts");
    }

    public List<String> getChoices() {
        return CHOICES;
    }

    public List<String> getTitles() {
        return TITLES;
    }

    public int[] getPixels() {
        return pixels();
    }

    public List<String> getModel() {
        return model();
    }

    public List<BenchRow> getRowsModel() {
        return rowsModel();
    }

    public List<BenchFolder> getFolders() {
        return folders();
    }

    // ---- the data -------------------------------------------------------------------------------------------

    private List<Object> values(String kind, int ph) {
        String key = kind + ":" + n + ":" + ph;
        Object cached = CACHE.get(key);
        if (cached == null) {
            List<Object> out = new ArrayList<Object>(n);
            for (int k = 0; k < n; k++) {
                out.add(value(kind, ph, k));
            }
            cached = Collections.unmodifiableList(out);
            CACHE.put(key, cached);
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) cached;
        return list;
    }

    /** The value of instance {@code k} in a phase. {@code ComponentFxData} in the JavaFX runner has the same formulas. */
    static Object value(String kind, int ph, int k) {
        switch (kind) {
            case "string": return (ph == 0 ? "Item " : "Edited ") + k;
            case "digits": return String.valueOf((k + 37 * ph) % 100);
            case "dates": return LocalDate.of(2026, 1, 1).plusDays(k % 365 + ph).toString();
            case "colors": return String.format(Locale.ROOT, "#%06X", (k * 40503 + 0x1A2B3C + ph * 0x0F0F0F) & 0xFFFFFF);
            case "times": return String.format(Locale.ROOT, "%02d:%02d", (k + ph) % 24, (k % 4) * 15);
            case "initials": return "" + (char) ('A' + (k + ph) % 26) + (char) ('A' + (k / 26) % 26);
            case "numbers": return Double.valueOf((k * 7 + 31 * ph) % 100);
            case "fractions": return Double.valueOf(((k + 37 * ph) % 99 + 1) / 100.0);
            case "widths": return Double.valueOf(50 + (k % 20) * 10 + 5 * ph);
            case "sizes": return Double.valueOf(16 + (k % 12) * 2 + 2 * ph);
            case "ints": return Integer.valueOf((k + 7 * ph) % 120);
            case "steps": return Integer.valueOf((k + ph) % 4);
            case "pages": return Integer.valueOf((k + 3 * ph) % 10);
            case "flags": return Boolean.valueOf((k % 2 == 0) == (ph == 0));
            case "firsts": return Integer.valueOf(7 * ph);
            default: throw new IllegalArgumentException(kind);
        }
    }

    private static List<String> model() {
        @SuppressWarnings("unchecked")
        List<String> m = (List<String>) CACHE.get("model");
        if (m == null) {
            List<String> out = new ArrayList<String>(MODEL);
            for (int k = 0; k < MODEL; k++) {
                out.add("Row " + k);
            }
            m = Collections.unmodifiableList(out);
            CACHE.put("model", m);
        }
        return m;
    }

    private static List<BenchRow> rowsModel() {
        @SuppressWarnings("unchecked")
        List<BenchRow> m = (List<BenchRow>) CACHE.get("rows");
        if (m == null) {
            List<BenchRow> out = new ArrayList<BenchRow>(MODEL);
            for (int k = 0; k < MODEL; k++) {
                out.add(new BenchRow(k));
            }
            m = Collections.unmodifiableList(out);
            CACHE.put("rows", m);
        }
        return m;
    }

    private static List<BenchFolder> folders() {
        @SuppressWarnings("unchecked")
        List<BenchFolder> m = (List<BenchFolder>) CACHE.get("folders");
        if (m == null) {
            List<BenchFolder> out = new ArrayList<BenchFolder>(FOLDERS);
            for (int f = 0; f < FOLDERS; f++) {
                out.add(new BenchFolder(f));
            }
            m = Collections.unmodifiableList(out);
            CACHE.put("folders", m);
        }
        return m;
    }

    private static int[] pixels() {
        int[] p = (int[]) CACHE.get("pixels");
        if (p == null) {
            p = new int[16 * 16];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    p[y * 16 + x] = 0xFF000000 | (x * 16) << 16 | (y * 16) << 8 | 128;
                }
            }
            CACHE.put("pixels", p);
        }
        return p;
    }
}
