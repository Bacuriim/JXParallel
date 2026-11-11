package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the FXML converter could not map, or mapped with a different meaning: one {@link Entry} per element or
 * attribute, with the file, the line, the element, the attribute (or null), the reason and a {@link Level}.
 * Nothing is dropped without an entry; {@link FxmlToJxml.Options#strict} turns the entries that lose something
 * ({@link Level#UNSUPPORTED} and {@link Level#APPROXIMATE}) into a failure.
 */
public final class FxmlReport {
    /** How much an entry matters. */
    public enum Level {
        /** Converted; the developer has something to do (an {@code fx:id}, a handler's signature). Never fails a strict run. */
        NOTE,
        /** Converted to something close but not the same (an event that fires at another moment, a CSS class kept as a name). */
        APPROXIMATE,
        /** Not converted: the element or attribute is not in the JXML output. */
        UNSUPPORTED
    }

    /** One finding. */
    public static final class Entry {
        public final Level level;
        public final String file;
        public final int line;
        /** The FXML element (its tag name, with {@code fx:id} when it has one is in {@link #reason}). */
        public final String element;
        /** The FXML attribute or property element, or null when the finding is about the element. */
        public final String attribute;
        public final String reason;

        Entry(Level level, String file, int line, String element, String attribute, String reason) {
            this.level = level;
            this.file = file;
            this.line = line;
            this.element = element;
            this.attribute = attribute;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return file + ":" + line + ": " + level + " <" + element + ">" + (attribute == null ? "" : " " + attribute)
                    + ": " + reason;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();

    void add(Level level, String file, int line, String element, String attribute, String reason) {
        entries.add(new Entry(level, file, line, element, attribute, reason));
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<Entry> entries(Level level) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (entry.level == level) {
                out.add(entry);
            }
        }
        return out;
    }

    /** True when something was not converted or was converted to something different (what strict mode rejects). */
    public boolean hasLosses() {
        for (Entry entry : entries) {
            if (entry.level != Level.NOTE) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    void addAll(FxmlReport other) {
        entries.addAll(other.entries);
    }

    /** One line per entry, in the order found. */
    public String toText() {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            out.append(entry).append('\n');
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return toText();
    }
}
