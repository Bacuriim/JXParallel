package com.jxparallel.forge;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The translated texts of an application at run time: messages by locale and key, the current locale, and the formatting
 * of messages in the ICU subset Flutter's ARB files use. The generated {@code Strings} class holds one of these, and the
 * interpreter ({@link JxmlTemplate}) reads the same one from a {@link JxmlCatalog}, so both build the same texts.
 *
 * <p>A message is text with arguments: {@code {name}}, {@code {count, plural, =0{none} one{# item} other{# items}}} and
 * {@code {gender, select, male{He} female{She} other{They}}}. In the text, {@code '} quotes as in ICU: {@code ''} is an
 * apostrophe, and {@code '{'} (or {@code '}'}, or {@code '#'} inside a plural) is the character itself; any other lone
 * apostrophe is itself. Plural categories follow a small rule set for en, pt, es and fr (anything else is {@code other});
 * an exact {@code =N} wins over a category.
 *
 * <p>A key is looked up for the current locale {@code pt_BR}, then {@code pt}, then the default locale. A key that no table
 * has is an {@link IllegalArgumentException}; the build checks the keys screens use, so this only means a stale table.
 */
public final class JxmlMessages {
    // ---- the model -----------------------------------------------------------------------------------------

    abstract static class Part {
    }

    static final class Text extends Part {
        final String text;

        Text(String text) {
            this.text = text;
        }
    }

    static final class Arg extends Part {
        final String name;

        Arg(String name) {
            this.name = name;
        }
    }

    /** {@code #} inside a plural case: the number being counted. */
    static final class Hash extends Part {
    }

    /** A plural or a select with its cases by selector ({@code =0}, {@code one}, {@code male}, {@code other}). */
    static final class Choice extends Part {
        final String name;
        final boolean plural;
        final Map<String, Message> cases;

        Choice(String name, boolean plural, Map<String, Message> cases) {
            this.name = name;
            this.plural = plural;
            this.cases = cases;
        }
    }

    /** One parsed message. */
    public static final class Message {
        final List<Part> parts;

        Message(List<Part> parts) {
            this.parts = parts;
        }

        /** The message in ICU text; parsing it gives an equal message. */
        public String toIcu() {
            StringBuilder out = new StringBuilder();
            write(this, out, false);
            return out.toString();
        }

        /** The names of the arguments, in order of first appearance, with how each is used: plain, plural or select. */
        public Map<String, String> args() {
            Map<String, String> out = new LinkedHashMap<String, String>();
            collect(this, out);
            return out;
        }

        /** Whether some plural or select in the message has this name (as opposed to a plain argument only). */
        @Override
        public boolean equals(Object other) {
            return other instanceof Message && ((Message) other).toIcu().equals(toIcu());
        }

        @Override
        public int hashCode() {
            return toIcu().hashCode();
        }

        @Override
        public String toString() {
            return toIcu();
        }
    }

    private static void collect(Message m, Map<String, String> out) {
        for (Part p : m.parts) {
            if (p instanceof Arg) {
                String name = ((Arg) p).name;
                if (!out.containsKey(name)) {
                    out.put(name, "plain");
                }
            } else if (p instanceof Choice) {
                Choice c = (Choice) p;
                String kind = c.plural ? "plural" : "select";
                String known = out.get(c.name);
                if (known == null || "plain".equals(known)) {
                    out.put(c.name, kind);
                }
                for (Message sub : c.cases.values()) {
                    collect(sub, out);
                }
            }
        }
    }

    private static void write(Message m, StringBuilder out, boolean inPlural) {
        for (Part p : m.parts) {
            if (p instanceof Text) {
                quote(((Text) p).text, out, inPlural);
            } else if (p instanceof Arg) {
                out.append('{').append(((Arg) p).name).append('}');
            } else if (p instanceof Hash) {
                out.append('#');
            } else {
                Choice c = (Choice) p;
                out.append('{').append(c.name).append(c.plural ? ", plural," : ", select,");
                for (Map.Entry<String, Message> e : c.cases.entrySet()) {
                    out.append(' ').append(e.getKey()).append('{');
                    write(e.getValue(), out, c.plural || inPlural);
                    out.append('}');
                }
                out.append('}');
            }
        }
    }

    private static boolean special(char ch, boolean inPlural) {
        return ch == '{' || ch == '}' || ch == '#' && inPlural;
    }

    private static void quote(String text, StringBuilder out, boolean inPlural) {
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch != '\'' && !special(ch, inPlural)) {
                out.append(ch);
                i++;
                continue;
            }
            int end = i;
            boolean needsQuotes = false;
            while (end < text.length() && (text.charAt(end) == '\'' || special(text.charAt(end), inPlural))) {
                needsQuotes |= text.charAt(end) != '\'';
                end++;
            }
            if (!needsQuotes) {
                for (int k = i; k < end; k++) {
                    out.append("''");
                }
            } else {
                out.append('\'');
                for (int k = i; k < end; k++) {
                    out.append(text.charAt(k) == '\'' ? "''" : String.valueOf(text.charAt(k)));
                }
                out.append('\'');
            }
            i = end;
        }
    }

    // ---- the ICU parser ------------------------------------------------------------------------------------

    /** Parses a message; an {@link IllegalArgumentException} names the problem and where it is in the text. */
    public static Message parse(String text) {
        Parser parser = new Parser(text);
        Message m = parser.message(false, false);
        return m;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String what) {
            return new IllegalArgumentException(what + " at position " + i + " of \"" + s + "\"");
        }

        Message message(boolean nested, boolean inPlural) {
            List<Part> parts = new ArrayList<Part>();
            StringBuilder text = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\'') {
                    char next = i + 1 < s.length() ? s.charAt(i + 1) : 0;
                    if (next == '\'') {
                        text.append('\'');
                        i += 2;
                    } else if (next == '{' || next == '}' || next == '#' && inPlural) {
                        i++;
                        while (i < s.length()) {
                            char q = s.charAt(i);
                            if (q == '\'') {
                                if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                                    text.append('\'');
                                    i += 2;
                                    continue;
                                }
                                i++;
                                break;
                            }
                            text.append(q);
                            i++;
                        }
                    } else {
                        text.append('\'');
                        i++;
                    }
                } else if (c == '{') {
                    flush(parts, text);
                    i++;
                    parts.add(argument(inPlural));
                } else if (c == '}') {
                    if (nested) {
                        break;
                    }
                    throw error("unmatched }");
                } else if (c == '#' && inPlural) {
                    flush(parts, text);
                    parts.add(new Hash());
                    i++;
                } else {
                    text.append(c);
                    i++;
                }
            }
            flush(parts, text);
            return new Message(Collections.unmodifiableList(parts));
        }

        private static void flush(List<Part> parts, StringBuilder text) {
            if (text.length() > 0) {
                parts.add(new Text(text.toString()));
                text.setLength(0);
            }
        }

        private void skipSpaces() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private String until(String stops) {
            int start = i;
            while (i < s.length() && stops.indexOf(s.charAt(i)) < 0) {
                i++;
            }
            if (i >= s.length()) {
                throw error("missing }");
            }
            return s.substring(start, i).trim();
        }

        /** After the opening brace. */
        Part argument(boolean inPlural) {
            String name = until(",}");
            if (name.isEmpty() || !name.matches("[A-Za-z0-9_]+")) {
                throw error("bad argument name \"" + name + "\"");
            }
            if (s.charAt(i) == '}') {
                i++;
                return new Arg(name);
            }
            i++; // ,
            String type = until(",}");
            if ("number".equals(type) || "date".equals(type) || "time".equals(type)) {
                if (s.charAt(i) == ',') { // a style, which does not change what is shown here
                    int depth = 0;
                    while (i < s.length() && (s.charAt(i) != '}' || depth > 0)) {
                        depth += s.charAt(i) == '{' ? 1 : s.charAt(i) == '}' ? -1 : 0;
                        i++;
                    }
                    if (i >= s.length()) {
                        throw error("missing }");
                    }
                }
                i++;
                return new Arg(name);
            }
            boolean plural = "plural".equals(type);
            if (!plural && !"select".equals(type)) {
                throw error("unsupported argument type \"" + type + "\" (use plural or select)");
            }
            if (s.charAt(i) != ',') {
                throw error("expected , after " + type);
            }
            i++;
            Map<String, Message> cases = new LinkedHashMap<String, Message>();
            while (true) {
                skipSpaces();
                if (i >= s.length()) {
                    throw error("missing }");
                }
                if (s.charAt(i) == '}') {
                    i++;
                    break;
                }
                int start = i;
                while (i < s.length() && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '{' && s.charAt(i) != '}') {
                    i++;
                }
                String selector = s.substring(start, i);
                skipSpaces();
                if (selector.isEmpty() || i >= s.length() || s.charAt(i) != '{') {
                    throw error("expected a selector and {message} in the " + type);
                }
                if (plural && !isPluralSelector(selector)) {
                    throw error("\"" + selector + "\" is not =N, zero, one, two, few, many or other (offset is not supported)");
                }
                if (cases.containsKey(selector)) {
                    throw error("selector " + selector + " twice in the " + type);
                }
                i++;
                Message sub = message(true, plural || inPlural);
                if (i >= s.length()) {
                    throw error("missing }");
                }
                i++; // }
                cases.put(selector, sub);
            }
            if (!cases.containsKey("other")) {
                throw error("the " + type + " on " + name + " has no 'other' case");
            }
            return new Choice(name, plural, Collections.unmodifiableMap(cases));
        }

        private static boolean isPluralSelector(String s) {
            if (s.startsWith("=")) {
                return s.length() > 1 && s.substring(1).matches("[0-9]+");
            }
            return Arrays.asList("zero", "one", "two", "few", "many", "other").contains(s);
        }
    }

    // ---- plural rules --------------------------------------------------------------------------------------

    /**
     * The plural category of {@code n} in {@code locale}: {@code one}, {@code many} or {@code other} for en, pt, es and fr
     * (pt and fr count 0 and 1 as {@code one}, except pt_PT; es and fr, and pt, say {@code many} for whole millions);
     * {@code other} for every other language.
     */
    public static String category(Locale locale, double n) {
        double abs = Math.abs(n);
        boolean whole = abs == Math.rint(abs) && !Double.isInfinite(abs);
        long integer = (long) abs;
        boolean millions = whole && integer != 0 && integer % 1000000L == 0;
        switch (locale.getLanguage()) {
            case "en":
                return whole && integer == 1 ? "one" : "other";
            case "es":
                return whole && integer == 1 ? "one" : millions ? "many" : "other";
            case "pt":
                if ("PT".equals(locale.getCountry())) {
                    return whole && integer == 1 ? "one" : millions ? "many" : "other";
                }
                return integer <= 1 ? "one" : millions ? "many" : "other";
            case "fr":
                return integer <= 1 ? "one" : millions ? "many" : "other";
            default:
                return "other";
        }
    }

    // ---- formatting ----------------------------------------------------------------------------------------

    private static String render(Message m, Locale locale, Map<String, Object> args, Double count) {
        StringBuilder out = new StringBuilder();
        for (Part p : m.parts) {
            if (p instanceof Text) {
                out.append(((Text) p).text);
            } else if (p instanceof Arg) {
                out.append(text(value(args, ((Arg) p).name), locale));
            } else if (p instanceof Hash) {
                out.append(count == null ? "#" : number(count.doubleValue()));
            } else {
                Choice c = (Choice) p;
                Object value = value(args, c.name);
                Message chosen;
                Double inner = count;
                if (c.plural) {
                    if (!(value instanceof Number)) {
                        throw new IllegalArgumentException("the plural on " + c.name + " needs a number, not " + describe(value));
                    }
                    double n = ((Number) value).doubleValue();
                    inner = Double.valueOf(n);
                    chosen = n == Math.rint(n) ? c.cases.get("=" + (long) n) : null;
                    if (chosen == null) {
                        chosen = c.cases.get(category(locale, n));
                    }
                } else {
                    chosen = c.cases.get(String.valueOf(value));
                }
                out.append(render(chosen != null ? chosen : c.cases.get("other"), locale, args, inner));
            }
        }
        return out.toString();
    }

    private static Object value(Map<String, Object> args, String name) {
        if (!args.containsKey(name)) {
            throw new IllegalArgumentException("no argument " + name);
        }
        return args.get(name);
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String number(double n) {
        return n == Math.rint(n) && Math.abs(n) < 1e15 ? Long.toString((long) n) : Double.toString(n);
    }

    private static String text(Object value, Locale locale) {
        if (value == null) {
            return "";
        }
        if (value instanceof Date) {
            return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format((Date) value);
        }
        return String.valueOf(value);
    }

    // ---- the tables and the locale -------------------------------------------------------------------------

    private final String defaultTag;
    private final Map<String, Map<String, Message>> tables = new ConcurrentHashMap<String, Map<String, Message>>();
    private final Map<String, String[]> signatures = new ConcurrentHashMap<String, String[]>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();
    private volatile Locale current;

    /** A set of messages whose default locale is {@code defaultTag} ({@code en}, {@code pt_BR}). */
    public JxmlMessages(String defaultTag) {
        this.defaultTag = tag(defaultTag);
    }

    /** {@code pt-br} and {@code pt_BR} are {@code pt_BR}; {@code zh-hans-cn} is {@code zh_Hans_CN}. */
    public static String tag(String text) {
        String[] parts = text.trim().split("[-_]", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (i == 0) {
                p = p.toLowerCase(Locale.ROOT);
            } else if (p.length() == 4) {
                p = Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase(Locale.ROOT);
            } else {
                p = p.toUpperCase(Locale.ROOT);
            }
            out.append(i == 0 ? "" : "_").append(p);
        }
        return out.toString();
    }

    static Locale locale(String tag) {
        return Locale.forLanguageTag(tag.replace('_', '-'));
    }

    /** Adds the message {@code icu} (ICU text) for {@code key} in the locale {@code tag}. */
    public JxmlMessages add(String tag, String key, String icu) {
        return add(tag, key, parse(icu));
    }

    JxmlMessages add(String tag, String key, Message message) {
        Map<String, Message> table = tables.get(tag(tag));
        if (table == null) {
            table = new ConcurrentHashMap<String, Message>();
            tables.put(tag(tag), table);
        }
        table.put(key, message);
        return this;
    }

    /** Declares the arguments of {@code key} in the order a call passes them. */
    public JxmlMessages signature(String key, String... names) {
        signatures.put(key, names.clone());
        return this;
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    /** The locales that have messages, and the default one, sorted by tag. */
    public List<Locale> supported() {
        TreeSet<String> tags = new TreeSet<String>(tables.keySet());
        tags.add(defaultTag);
        List<Locale> out = new ArrayList<Locale>();
        for (String t : tags) {
            out.add(locale(t));
        }
        return Collections.unmodifiableList(out);
    }

    /** The supported locale that best matches {@code wanted}: {@code pt_BR}, then {@code pt}, else the default. */
    public Locale match(Locale wanted) {
        for (String t : chain(wanted)) {
            if (t.equals(defaultTag) || tables.containsKey(t)) {
                return locale(t);
            }
        }
        return locale(defaultTag);
    }

    /** The tags tried for {@code wanted}, most specific first, ending with the default. */
    List<String> chain(Locale wanted) {
        List<String> out = new ArrayList<String>();
        String lang = wanted.getLanguage();
        if (!lang.isEmpty()) {
            if (!wanted.getCountry().isEmpty()) {
                out.add(tag(lang + "_" + wanted.getCountry()));
            }
            out.add(tag(lang));
        }
        if (!out.contains(defaultTag)) {
            out.add(defaultTag);
        }
        return out;
    }

    /** The current locale; at first, the best match for {@link Locale#getDefault()}. */
    public Locale locale() {
        Locale now = current;
        if (now == null) {
            now = match(Locale.getDefault());
            current = now;
        }
        return now;
    }

    /** Changes the locale and calls the listeners when it is another one. */
    public void setLocale(Locale locale) {
        if (locale == null) {
            throw new NullPointerException("locale");
        }
        boolean changed = !locale.equals(locale());
        current = locale;
        if (changed) {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    }

    /** Calls {@code listener} after each change of locale, so an application can build its screens again. */
    public void onLocaleChange(Runnable listener) {
        listeners.add(listener);
    }

    public void offLocaleChange(Runnable listener) {
        listeners.remove(listener);
    }

    /** The text of {@code key} in the current locale, with its arguments in the order of the signature. */
    public String format(String key, Object... args) {
        return format(locale(), key, args);
    }

    public String format(Locale locale, String key, Object... args) {
        Message m = find(locale, key);
        String[] names = signatures.get(key);
        if (names == null) {
            names = new String[0];
        }
        if (names.length != args.length) {
            throw new IllegalArgumentException(key + " takes " + names.length + " argument(s), not " + args.length);
        }
        Map<String, Object> byName = new TreeMap<String, Object>();
        for (int i = 0; i < names.length; i++) {
            byName.put(names[i], args[i]);
        }
        return render(m, locale, byName, null);
    }

    private Message find(Locale locale, String key) {
        for (String t : chain(locale)) {
            Map<String, Message> table = tables.get(t);
            Message m = table == null ? null : table.get(key);
            if (m != null) {
                return m;
            }
        }
        throw new IllegalArgumentException("no text for the key " + key);
    }

    /** The message of {@code key} in exactly the locale {@code tag}, or null. */
    Message message(String tag, String key) {
        Map<String, Message> table = tables.get(tag(tag));
        return table == null ? null : table.get(key);
    }
}
