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
        /** {@code number}, {@code date} or {@code time} for {@code {n, number, percent}}; null for a plain {@code {n}}. */
        final String type;
        /** The style after the type ({@code percent}, {@code short}, a pattern), or null. */
        final String style;

        Arg(String name) {
            this(name, null, null);
        }

        Arg(String name, String type, String style) {
            this.name = name;
            this.type = type;
            this.style = style;
        }
    }

    /** {@code #} inside a plural case: the number being counted. */
    static final class Hash extends Part {
    }

    /**
     * A plural, a selectordinal or a select with its cases by selector ({@code =0}, {@code one}, {@code male},
     * {@code other}). {@code plural} is true for plural and selectordinal; {@code offset} is the plural's {@code offset:N}.
     */
    static final class Choice extends Part {
        final String name;
        final boolean plural;
        final boolean ordinal;
        final int offset;
        final Map<String, Message> cases;

        Choice(String name, boolean plural, Map<String, Message> cases) {
            this(name, plural, false, 0, cases);
        }

        Choice(String name, boolean plural, boolean ordinal, int offset, Map<String, Message> cases) {
            this.name = name;
            this.plural = plural;
            this.ordinal = ordinal;
            this.offset = offset;
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

        /** Equal when the ICU text is equal. */
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
                Arg a = (Arg) p;
                String known = out.get(a.name);
                if (known == null || "plain".equals(known)) {
                    out.put(a.name, a.type == null ? "plain" : a.type);
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
                Arg a = (Arg) p;
                out.append('{').append(a.name);
                if (a.type != null) {
                    out.append(", ").append(a.type);
                    if (a.style != null) {
                        out.append(", ").append(a.style);
                    }
                }
                out.append('}');
            } else if (p instanceof Hash) {
                out.append('#');
            } else {
                Choice c = (Choice) p;
                out.append('{').append(c.name).append(c.ordinal ? ", selectordinal," : c.plural ? ", plural," : ", select,");
                if (c.offset != 0) {
                    out.append(" offset:").append(c.offset);
                }
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
                String style = null;
                if (s.charAt(i) == ',') {
                    i++;
                    int start = i;
                    int depth = 0;
                    while (i < s.length() && (s.charAt(i) != '}' || depth > 0)) {
                        depth += s.charAt(i) == '{' ? 1 : s.charAt(i) == '}' ? -1 : 0;
                        i++;
                    }
                    if (i >= s.length()) {
                        throw error("missing }");
                    }
                    style = s.substring(start, i).trim();
                    checkStyle(type, style);
                }
                i++;
                return new Arg(name, type, style);
            }
            boolean ordinal = "selectordinal".equals(type);
            boolean plural = ordinal || "plural".equals(type);
            if (!plural && !"select".equals(type)) {
                throw error("unsupported argument type \"" + type + "\" (use plural, selectordinal, select, number, date or time)");
            }
            if (s.charAt(i) != ',') {
                throw error("expected , after " + type);
            }
            i++;
            int offset = 0;
            skipSpaces();
            if (s.startsWith("offset:", i)) {
                if (ordinal) {
                    throw error("offset belongs to plural, not selectordinal");
                }
                if (!plural) {
                    throw error("offset belongs to plural, not select");
                }
                i += "offset:".length();
                skipSpaces();
                int start = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
                if (start == i || i - start > 9) {
                    throw error("offset: must be followed by a whole number");
                }
                offset = Integer.parseInt(s.substring(start, i));
            }
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
                    throw error("\"" + selector + "\" is not =N, zero, one, two, few, many or other");
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
            return new Choice(name, plural, ordinal, offset, Collections.unmodifiableMap(cases));
        }

        private void checkStyle(String type, String style) {
            if (style.isEmpty()) {
                throw error("the " + type + " style is empty");
            }
            try {
                if ("number".equals(type)) {
                    numberFormat(style, Locale.ROOT);
                } else {
                    dateFormat(type, style, Locale.ROOT);
                }
            } catch (IllegalArgumentException e) {
                throw error(e.getMessage());
            }
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
     * The cardinal plural category of {@code n} in {@code locale}, by the rules of CLDR 42 (the operands are {@code i}, the
     * integer part, and {@code v}, the number of visible fraction digits, so {@code 1} and {@code 1.0} are the same value
     * and {@code 1.5} has a fraction):
     * <ul>
     * <li>{@code one}/{@code other}: en, de, nl, sv, nb, no, fi, et (i = 1, no fraction); tr, el, hu, bg (n = 1);
     * pt (i = 0 or 1; pt_PT: i = 1, no fraction) and fr (i = 0 or 1); hi (i = 0 or n = 1);</li>
     * <li>{@code one}/{@code many}/{@code other}: es and it (i = 1, no fraction; {@code many} for whole millions), and pt,
     * fr (whole millions are {@code many});</li>
     * <li>{@code one}/{@code few}/{@code many}/{@code other}: ru, uk, pl (whole numbers only; a fraction is {@code other});
     * cs, sk ({@code one} 1, {@code few} 2 to 4, {@code many} for any fraction);</li>
     * <li>{@code zero}/{@code one}/{@code two}/{@code few}/{@code many}/{@code other}: ar;</li>
     * <li>{@code one}/{@code two}/{@code other}: he (CLDR 42: no {@code many});</li>
     * <li>{@code other} always: ja, zh, ko, vi, th, id, ms, and every language not listed.</li>
     * </ul>
     * A negative number has the category of its absolute value.
     */
    public static String category(Locale locale, double n) {
        double abs = Math.abs(n);
        boolean whole = abs == Math.rint(abs) && !Double.isInfinite(abs);
        long i = (long) abs;
        boolean millions = whole && i != 0 && i % 1000000L == 0;
        boolean fraction = !whole;
        long mod10 = i % 10;
        long mod100 = i % 100;
        switch (locale.getLanguage()) {
            case "en":
            case "de":
            case "nl":
            case "sv":
            case "nb":
            case "no":
            case "fi":
            case "et":
                return whole && i == 1 ? "one" : "other";
            case "tr":
            case "el":
            case "hu":
            case "bg":
                return abs == 1 ? "one" : "other";
            case "es":
            case "it":
                return whole && i == 1 ? "one" : millions ? "many" : "other";
            case "pt":
                if ("PT".equals(locale.getCountry())) {
                    return whole && i == 1 ? "one" : millions ? "many" : "other";
                }
                return i <= 1 ? "one" : millions ? "many" : "other";
            case "fr":
                return i <= 1 ? "one" : millions ? "many" : "other";
            case "hi":
                return i == 0 || abs == 1 ? "one" : "other";
            case "ru":
            case "uk":
                if (fraction) {
                    return "other";
                }
                if (mod10 == 1 && mod100 != 11) {
                    return "one";
                }
                if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) {
                    return "few";
                }
                return "many";
            case "pl":
                if (fraction) {
                    return "other";
                }
                if (i == 1) {
                    return "one";
                }
                if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) {
                    return "few";
                }
                return "many";
            case "cs":
            case "sk":
                if (fraction) {
                    return "many";
                }
                return i == 1 ? "one" : i >= 2 && i <= 4 ? "few" : "other";
            case "ar":
                if (fraction) {
                    return "other";
                }
                return i == 0 ? "zero" : i == 1 ? "one" : i == 2 ? "two" : mod100 >= 3 && mod100 <= 10 ? "few"
                        : mod100 >= 11 ? "many" : "other";
            case "he":
            case "iw": // the code Java 8 to 16 gives Hebrew
                if (fraction) {
                    return i == 0 ? "one" : "other";
                }
                return i == 1 ? "one" : i == 2 ? "two" : "other";
            default:
                return "other";
        }
    }

    /**
     * The ordinal category of {@code n} (for {@code selectordinal}): in English {@code one} for 1, 21, 101... except 11
     * ({@code 1st}), {@code two} (2nd), {@code few} (3rd), {@code other} (4th, 11th, 12th, 13th, 1.5); {@code other} for
     * every other language, which includes es, fr and pt (CLDR gives French {@code one} for 1; this follows the spec of
     * the feature and keeps {@code other}).
     */
    public static String ordinalCategory(Locale locale, double n) {
        double abs = Math.abs(n);
        if ("en".equals(locale.getLanguage()) && abs == Math.rint(abs) && !Double.isInfinite(abs)) {
            long i = (long) abs;
            long mod10 = i % 10;
            long mod100 = i % 100;
            if (mod10 == 1 && mod100 != 11) {
                return "one";
            }
            if (mod10 == 2 && mod100 != 12) {
                return "two";
            }
            if (mod10 == 3 && mod100 != 13) {
                return "few";
            }
        }
        return "other";
    }

    // ---- number and date styles ----------------------------------------------------------------------------

    /** The number format for a style: none, {@code integer}, {@code percent}, {@code currency} or a pattern with # or 0. */
    static java.text.NumberFormat numberFormat(String style, Locale locale) {
        if (style == null || "decimalPattern".equals(style)) {
            return java.text.NumberFormat.getInstance(locale);
        }
        switch (style) {
            case "integer":
                return java.text.NumberFormat.getIntegerInstance(locale);
            case "percent":
            case "percentPattern":
                return java.text.NumberFormat.getPercentInstance(locale);
            case "currency":
            case "simpleCurrency":
                return java.text.NumberFormat.getCurrencyInstance(locale);
            default:
                if (style.indexOf('#') < 0 && style.indexOf('0') < 0) {
                    throw new IllegalArgumentException("the number style \"" + style + "\" is not supported (use integer, percent, "
                            + "currency, decimalPattern or a pattern such as #,##0.00)");
                }
                try {
                    return new java.text.DecimalFormat(style, java.text.DecimalFormatSymbols.getInstance(locale));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("the number pattern \"" + style + "\" is malformed: " + e.getMessage());
                }
        }
    }

    /** The date or time format for a style: none (medium), short, medium, long, full, or a pattern such as dd/MM/yyyy. */
    static DateFormat dateFormat(String type, String style, Locale locale) {
        boolean time = "time".equals(type);
        int named = -1;
        if (style == null || "medium".equals(style)) {
            named = DateFormat.MEDIUM;
        } else if ("short".equals(style)) {
            named = DateFormat.SHORT;
        } else if ("long".equals(style)) {
            named = DateFormat.LONG;
        } else if ("full".equals(style)) {
            named = DateFormat.FULL;
        }
        if (named >= 0) {
            return time ? DateFormat.getTimeInstance(named, locale) : DateFormat.getDateInstance(named, locale);
        }
        boolean separated = false;
        for (int k = 0; k < style.length(); k++) {
            separated |= !Character.isLetter(style.charAt(k));
        }
        if (!separated) {
            throw new IllegalArgumentException("the " + type + " style \"" + style + "\" is not supported (use short, medium, long, "
                    + "full or a pattern with separators such as dd/MM/yyyy)");
        }
        try {
            return new java.text.SimpleDateFormat(style, locale);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("the " + type + " pattern \"" + style + "\" is malformed: " + e.getMessage());
        }
    }

    private static String format(Arg a, Object value, Locale locale) {
        if (a.type == null) {
            return text(value, locale);
        }
        if (value == null) {
            return "";
        }
        if ("number".equals(a.type)) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("the number on " + a.name + " needs a number, not " + describe(value));
            }
            return numberFormat(a.style, locale).format(value);
        }
        if (!(value instanceof Date)) {
            throw new IllegalArgumentException("the " + a.type + " on " + a.name + " needs a date, not " + describe(value));
        }
        return dateFormat(a.type, a.style, locale).format((Date) value);
    }

    // ---- formatting ----------------------------------------------------------------------------------------

    private static String render(Message m, Locale locale, Map<String, Object> args, Double count) {
        StringBuilder out = new StringBuilder();
        for (Part p : m.parts) {
            if (p instanceof Text) {
                out.append(((Text) p).text);
            } else if (p instanceof Arg) {
                out.append(format((Arg) p, value(args, ((Arg) p).name), locale));
            } else if (p instanceof Hash) {
                out.append(count == null ? "#" : number(count.doubleValue()));
            } else {
                Choice c = (Choice) p;
                Object value = value(args, c.name);
                Message chosen;
                Double inner = count;
                if (c.plural) {
                    if (!(value instanceof Number)) {
                        throw new IllegalArgumentException("the " + (c.ordinal ? "selectordinal" : "plural") + " on " + c.name
                                + " needs a number, not " + describe(value));
                    }
                    double n = ((Number) value).doubleValue();
                    double shown = n - c.offset; // the category and the # count what is left after the offset; =N still tests n
                    inner = Double.valueOf(shown);
                    chosen = n == Math.rint(n) ? c.cases.get("=" + (long) n) : null;
                    if (chosen == null) {
                        chosen = c.cases.get(c.ordinal ? ordinalCategory(locale, shown) : category(locale, shown));
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
