package com.jxparallel.forge;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

/**
 * The translation files of an application read and checked: the build side of {@link JxmlMessages}.
 *
 * <p>A directory (default {@code src/main/l10n}) holds ARB files ({@code app_en.arb}, {@code app_pt_BR.arb}: JSON with
 * ICU messages, as Flutter's {@code gen-l10n} reads them) and/or Android resource folders ({@code values/strings.xml},
 * {@code values-pt/strings.xml}, {@code values-pt-rBR/strings.xml}, with {@code <string>} and {@code <plurals>}). Both
 * become the same messages. The default locale is the text of a file {@code l10n.default}, else {@code en}; the Android
 * {@code values} folder holds the default locale.
 *
 * <p>The default locale defines the keys and their arguments (a {@link Signature}); every other locale is compared with
 * it. A key that is missing or extra is a warning (an error when {@code strict}); a placeholder that differs, a plural
 * or select without {@code other}, a key defined twice and a file that does not parse are always errors.
 */
public final class JxmlCatalog {
    /** What a screen may call: the arguments of a key as the default locale declares them. */
    public static final class Signature {
        public final String key;
        /** The Java method of the generated {@code Strings} class. */
        public final String method;
        public final List<String> names;
        /** {@code String}, {@code int}, {@code double}, {@code num} or {@code DateTime}, one per name. */
        public final List<String> types;
        /** {@code file:line} where the default locale defines it. */
        public final String location;

        Signature(String key, String method, List<String> names, List<String> types, String location) {
            this.key = key;
            this.method = method;
            this.names = Collections.unmodifiableList(names);
            this.types = Collections.unmodifiableList(types);
            this.location = location;
        }

        /** The Java type of the argument {@code i} of the generated method. */
        public String javaType(int i) {
            return javaType(types.get(i));
        }

        static String javaType(String type) {
            switch (type) {
                case "int":
                    return "int";
                case "double":
                case "num":
                    return "double";
                case "DateTime":
                    return "java.util.Date";
                default:
                    return "String";
            }
        }

        /** {@code name: String, count: int}. */
        public String describe() {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < names.size(); i++) {
                out.append(i == 0 ? "" : ", ").append(names.get(i)).append(": ").append(types.get(i));
            }
            return out.toString();
        }
    }

    /** One message of one locale, with what its file declared about its arguments. */
    private static final class Entry {
        final String key;
        final JxmlMessages.Message message;
        /** Declared placeholders in order (ARB {@code placeholders}, Android positions), with their types. */
        final Map<String, String> declared;
        final String file;
        final int line;

        Entry(String key, JxmlMessages.Message message, Map<String, String> declared, String file, int line) {
            this.key = key;
            this.message = message;
            this.declared = declared;
            this.file = file;
            this.line = line;
        }

        String where() {
            return line > 0 ? file + ":" + line : file;
        }
    }

    private static final List<String> TYPES = Arrays.asList("String", "int", "double", "num", "DateTime");
    /** A key, optionally with an array index: {@code days[0]} is the first item of the array {@code days}. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-]*(?:\\[[0-9]{1,5}\\])?");
    private static final Pattern ITEM = Pattern.compile("(.+)\\[([0-9]{1,5})\\]");
    private static final Pattern DEFAULT_MARKER = Pattern.compile("<!--\\s*jxforge-default-locale:\\s*([A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,4})*)\\s*-->");
    private static final Pattern VALUES = Pattern.compile("values(?:-([a-z]{2,3})(?:-r([A-Z]{2}))?)?");
    private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList("abstract", "assert", "boolean", "break",
            "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
            "while", "true", "false", "null", "setLocale", "locale", "supported", "onLocaleChange", "offLocaleChange",
            "toString", "hashCode", "equals", "getClass", "notify", "notifyAll", "wait", "clone", "finalize"));

    private final File dir;
    private String defaultTag = "en";
    private final Map<String, Map<String, Entry>> locales = new TreeMap<String, Map<String, Entry>>();
    private final Map<String, String> filesOfTag = new TreeMap<String, String>();
    private final Map<String, Signature> signatures = new LinkedHashMap<String, Signature>();
    private final Map<String, List<String>> missing = new TreeMap<String, List<String>>();
    /** The arrays of the default locale (name to the keys of its items, in order) that get a {@code String[]} method. */
    private final Map<String, List<String>> arrays = new TreeMap<String, List<String>>();
    private final List<String[]> warnings = new ArrayList<String[]>(); // {file, line, message}
    private final List<String[]> problems = new ArrayList<String[]>(); // {file, line, message}
    private String digest;
    private JxmlMessages messages;

    private JxmlCatalog(File dir) {
        this.dir = dir;
    }

    /** Reads and checks {@code dir}; warnings are kept ({@link #getWarnings}), errors throw a {@link JxmlException}. */
    public static JxmlCatalog load(File dir) throws IOException {
        return load(dir, false);
    }

    /** As {@link #load(File)}; with {@code strict} a missing or extra translation is an error too. */
    public static JxmlCatalog load(File dir, boolean strict) throws IOException {
        JxmlCatalog catalog = new JxmlCatalog(dir);
        catalog.read();
        catalog.check();
        if (strict) {
            catalog.problems.addAll(catalog.warnings);
        }
        catalog.fail();
        return catalog;
    }

    // ---- the answers ---------------------------------------------------------------------------------------

    /** The default locale's tag, such as {@code en}. */
    public String getDefaultTag() {
        return defaultTag;
    }

    /** The tags of the locales that have files, sorted, the default one included. */
    public List<String> tags() {
        return new ArrayList<String>(locales.keySet());
    }

    /** What the checks found that does not stop the build, as {@code file:line: message}. */
    public List<String> getWarnings() {
        List<String> out = new ArrayList<String>();
        for (String[] w : warnings) {
            out.add(render(w));
        }
        return out;
    }

    /** The key's signature, or null when the default locale does not define it. */
    public Signature signature(String key) {
        return signatures.get(key);
    }

    public List<String> keys() {
        return new ArrayList<String>(signatures.keySet());
    }

    /** The tags of the locales that define {@code key}. */
    public List<String> definedIn(String key) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Map<String, Entry>> e : locales.entrySet()) {
            if (e.getValue().containsKey(key)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** The files of {@code tag}, as shown in messages. */
    public String fileOf(String tag) {
        return filesOfTag.get(tag);
    }

    /** SHA-256 (hex) of the names and bytes of every file in the directory: changes with any translation. */
    public String digest() {
        return digest;
    }

    /**
     * SHA-256 of what screens can see of the catalog: the keys and their signatures. It does not change with the text of a
     * translation, so the screens need not be generated again for that.
     */
    public String apiDigest() {
        StringBuilder text = new StringBuilder(defaultTag).append('\n');
        for (Signature s : signatures.values()) {
            text.append(s.key).append('\t').append(s.method).append('\t').append(s.describe()).append('\n');
        }
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The untranslated keys per locale, by tag; only the locales that lack some. */
    public Map<String, List<String>> missing() {
        return Collections.unmodifiableMap(missing);
    }

    /** The text of {@code l10n-missing.txt} (JSON), or null when every locale has every key. */
    public String missingReport() {
        if (missing.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder("{\n");
        int n = 0;
        for (Map.Entry<String, List<String>> e : missing.entrySet()) {
            out.append("  ").append(JxmlJson.quote(e.getKey())).append(": [");
            for (int i = 0; i < e.getValue().size(); i++) {
                out.append(i == 0 ? "" : ", ").append(JxmlJson.quote(e.getValue().get(i)));
            }
            out.append(++n < missing.size() ? "],\n" : "]\n");
        }
        return out.append("}\n").toString();
    }

    /** The messages for the interpreter and for tests: the same tables the generated {@code Strings} holds. */
    public synchronized JxmlMessages messages() {
        if (messages == null) {
            JxmlMessages m = new JxmlMessages(defaultTag);
            for (Map.Entry<String, Map<String, Entry>> e : locales.entrySet()) {
                for (Entry entry : e.getValue().values()) {
                    m.add(e.getKey(), entry.key, entry.message);
                }
            }
            for (Signature s : signatures.values()) {
                m.signature(s.key, s.names.toArray(new String[0]));
            }
            messages = m;
        }
        return messages;
    }

    // ---- reading -------------------------------------------------------------------------------------------

    private void problem(String file, int line, String message) {
        problems.add(new String[] {file, String.valueOf(line), message});
    }

    private void warn(String file, int line, String message) {
        warnings.add(new String[] {file, String.valueOf(line), message});
    }

    private static String render(String[] p) {
        return "0".equals(p[1]) ? p[0] + ": " + p[2] : p[0] + ":" + p[1] + ": " + p[2];
    }

    private void fail() {
        if (problems.isEmpty()) {
            return;
        }
        String[] first = problems.get(0);
        StringBuilder message = new StringBuilder(first[2]);
        for (int i = 1; i < problems.size(); i++) {
            message.append('\n').append(render(problems.get(i)));
        }
        throw new JxmlException(first[0], Integer.parseInt(first[1]), message.toString());
    }

    private String display(File file) {
        return file.getPath().replace('\\', '/');
    }

    private void read() throws IOException {
        List<File> files = new ArrayList<File>();
        walk(dir, files);
        MessageDigest hash = digestOf();
        for (File file : files) {
            String rel = dir.toURI().relativize(file.toURI()).getPath();
            hash.update((rel + "\n").getBytes(StandardCharsets.UTF_8));
            hash.update(Files.readAllBytes(file.toPath()));
            hash.update((byte) 0);
        }
        digest = hex(hash.digest());
        for (File file : files) {
            if (file.getName().equals("l10n.default")) {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                if (text.isEmpty() || !text.matches("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,4})*")) {
                    problem(display(file), 1, "l10n.default must hold a locale such as en or pt_BR");
                } else {
                    defaultTag = JxmlMessages.tag(text);
                }
            }
        }
        boolean named = false;
        for (File file : files) {
            named |= file.getName().equals("l10n.default");
        }
        for (File file : files) { // an export of ours says its default locale in a comment, since res/ takes no other file
            if (!named && file.getName().equals("strings.xml") && file.getParentFile().getName().equals("values")) {
                Matcher marker = DEFAULT_MARKER.matcher(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                if (marker.find()) {
                    defaultTag = JxmlMessages.tag(marker.group(1));
                }
            }
        }
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".arb")) {
                readArb(file);
            } else if (name.equals("strings.xml") && VALUES.matcher(file.getParentFile().getName()).matches()) {
                readAndroid(file);
            }
        }
        if (!locales.containsKey(defaultTag)) {
            problem(display(dir), 0, "the default locale " + defaultTag + " has no file (add app_" + defaultTag
                    + ".arb or values/strings.xml, or name another default in a file l10n.default); found: " + locales.keySet());
        }
    }

    private static void walk(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                walk(entry, out);
            } else {
                out.add(entry);
            }
        }
    }

    private void put(String tag, Entry entry) {
        Map<String, Entry> table = locales.get(tag);
        if (table == null) {
            table = new LinkedHashMap<String, Entry>();
            locales.put(tag, table);
            filesOfTag.put(tag, entry.file);
        }
        Entry known = table.get(entry.key);
        if (known != null) {
            problem(entry.file, entry.line, "the key " + entry.key + " is defined twice for " + tag + " (also at " + known.where() + ")");
            return;
        }
        table.put(entry.key, entry);
    }

    /** Whether {@code key} is written like a key: letters, digits, {@code _}, {@code .} and {@code -}, not starting with a digit. */
    static boolean isKey(String key) {
        return KEY.matcher(key).matches();
    }

    private boolean validKey(String file, int line, String key) {
        if (!KEY.matcher(key).matches()) {
            problem(file, line, "the key \"" + key + "\" must start with a letter and use letters, digits, _, . or -");
            return false;
        }
        return true;
    }

    // ---- ARB -----------------------------------------------------------------------------------------------

    private void readArb(File file) throws IOException {
        String shown = display(file);
        JxmlJson.Obj root;
        try {
            Object parsed = JxmlJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), shown);
            if (!(parsed instanceof JxmlJson.Obj)) {
                problem(shown, 1, "an ARB file is a JSON object");
                return;
            }
            root = (JxmlJson.Obj) parsed;
        } catch (JxmlException e) {
            problem(e.getSource(), e.getLine(), e.getMessage().substring((e.getSource() + ":" + e.getLine() + ": ").length()));
            return;
        }
        String name = file.getName().substring(0, file.getName().length() - 4);
        int underscore = name.indexOf('_');
        String fromName = underscore < 0 ? null : JxmlMessages.tag(name.substring(underscore + 1));
        Object at = root.get("@@locale");
        String fromFile = at instanceof String ? JxmlMessages.tag((String) at) : null;
        if (at != null && fromFile == null) {
            problem(shown, root.line("@@locale"), "@@locale must be a string such as \"pt_BR\"");
            return;
        }
        if (fromName != null && fromFile != null && !fromName.equals(fromFile)) {
            problem(shown, root.line("@@locale"), "@@locale " + fromFile + " is not the locale " + fromName + " in the file name");
            return;
        }
        String tag = fromFile != null ? fromFile : fromName;
        if (tag == null) {
            problem(shown, 1, "the locale is unknown: name the file like app_en.arb or write \"@@locale\": \"en\"");
            return;
        }
        for (Map.Entry<String, Object> e : root.entrySet()) {
            String key = e.getKey();
            int line = root.line(key);
            if (key.startsWith("@")) {
                continue;
            }
            if (!(e.getValue() instanceof String)) {
                problem(shown, line, key + " must be a string, the message");
                continue;
            }
            if (!validKey(shown, line, key)) {
                continue;
            }
            JxmlMessages.Message message;
            try {
                message = JxmlMessages.parse((String) e.getValue());
            } catch (IllegalArgumentException ex) {
                problem(shown, line, key + ": " + ex.getMessage());
                continue;
            }
            Map<String, String> declared = new LinkedHashMap<String, String>();
            Object meta = root.get("@" + key);
            if (meta instanceof JxmlJson.Obj && ((JxmlJson.Obj) meta).get("placeholders") instanceof JxmlJson.Obj) {
                JxmlJson.Obj placeholders = (JxmlJson.Obj) ((JxmlJson.Obj) meta).get("placeholders");
                for (Map.Entry<String, Object> p : placeholders.entrySet()) {
                    String type = "";
                    if (p.getValue() instanceof JxmlJson.Obj && ((JxmlJson.Obj) p.getValue()).get("type") instanceof String) {
                        type = (String) ((JxmlJson.Obj) p.getValue()).get("type");
                    }
                    if (!type.isEmpty() && !TYPES.contains(type)) {
                        problem(shown, root.line("@" + key), key + ": the placeholder " + p.getKey() + " has type " + type
                                + "; use one of " + TYPES);
                        continue;
                    }
                    declared.put(p.getKey(), type.isEmpty() ? null : type);
                }
            }
            put(tag, new Entry(key, message, declared, shown, line));
        }
    }

    // ---- Android -------------------------------------------------------------------------------------------

    private void readAndroid(File file) throws IOException {
        String shown = display(file);
        Matcher folder = VALUES.matcher(file.getParentFile().getName());
        folder.matches();
        String tag = folder.group(1) == null ? defaultTag : JxmlMessages.tag(folder.group(2) == null ? folder.group(1)
                : folder.group(1) + "_" + folder.group(2));
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
                // a parser without the feature: the file is the project's own
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            doc = builder.parse(new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } catch (SAXParseException e) {
            problem(shown, e.getLineNumber(), "bad XML: " + e.getMessage());
            return;
        } catch (Exception e) {
            problem(shown, 1, "bad XML: " + e.getMessage());
            return;
        }
        NodeList nodes = doc.getDocumentElement().getChildNodes();
        for (int n = 0; n < nodes.getLength(); n++) {
            Node node = nodes.item(n);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            String name = element.getAttribute("name");
            int line = lineOf(text, name);
            if ("false".equals(element.getAttribute("translatable"))) {
                continue;
            }
            try {
                if (element.getTagName().equals("string")) {
                    if (validKey(shown, line, name)) {
                        Entry entry = androidString(name, element.getTextContent(), shown, line);
                        put(tag, entry);
                    }
                } else if (element.getTagName().equals("plurals")) {
                    if (validKey(shown, line, name)) {
                        put(tag, androidPlurals(name, element, shown, line));
                    }
                } else if (element.getTagName().equals("string-array")) {
                    if (validKey(shown, line, name)) {
                        // each item is the key name[i], so the checks, the missing keys and the screens treat it as any text
                        NodeList items = element.getElementsByTagName("item");
                        for (int i = 0; i < items.getLength(); i++) {
                            put(tag, androidString(name + "[" + i + "]", items.item(i).getTextContent(), shown, line));
                        }
                        if (items.getLength() == 0) {
                            warn(shown, line, "<string-array name=\"" + name + "\"> has no items and was skipped");
                        }
                    }
                } else {
                    warn(shown, line, "<" + element.getTagName() + (name.isEmpty() ? "" : " name=\"" + name
                            + "\"") + "> is not supported and was skipped");
                }
            } catch (IllegalArgumentException e) {
                problem(shown, line, name + ": " + e.getMessage());
            }
        }
    }

    private static int lineOf(String text, String name) {
        int at = text.indexOf("name=\"" + name + "\"");
        if (at < 0) {
            return 1;
        }
        int line = 1;
        for (int i = 0; i < at; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    /** The positional arguments found while reading an Android string: position to type. */
    private static JxmlMessages.Message androidMessage(String raw, Map<Integer, String> positions, String countName) {
        String text = androidText(raw);
        List<JxmlMessages.Part> parts = new ArrayList<JxmlMessages.Part>();
        StringBuilder literal = new StringBuilder();
        Matcher m = FORMAT.matcher(text);
        int last = 0;
        int next = 1;
        while (m.find()) {
            literal.append(text, last, m.start());
            last = m.end();
            char conv = m.group(2).charAt(0);
            if (conv == '%') {
                literal.append('%');
                continue;
            }
            if (conv == 'n') {
                literal.append('\n');
                continue;
            }
            String type = conv == 'd' ? "int" : conv == 'f' ? "double" : conv == 's' || conv == 'S' ? "String" : null;
            if (type == null) {
                throw new IllegalArgumentException("the format " + m.group() + " is not supported (use %s, %d or %f)");
            }
            int position = m.group(1) == null ? next++ : Integer.parseInt(m.group(1));
            String known = positions.get(position);
            if (known != null && !known.equals(type) && !(position == 1 && countName != null)) {
                throw new IllegalArgumentException("argument " + position + " is used as " + known + " and as " + type);
            }
            if (known == null || position != 1 || countName == null) {
                positions.put(position, type);
            }
            if (literal.length() > 0) {
                parts.add(new JxmlMessages.Text(literal.toString()));
                literal.setLength(0);
            }
            parts.add(new JxmlMessages.Arg(position == 1 && countName != null ? countName : "arg" + position));
        }
        literal.append(text, last, text.length());
        if (literal.length() > 0) {
            parts.add(new JxmlMessages.Text(literal.toString()));
        }
        return new JxmlMessages.Message(parts);
    }

    private Entry androidString(String name, String raw, String file, int line) {
        Map<Integer, String> positions = new TreeMap<Integer, String>();
        JxmlMessages.Message message = androidMessage(raw, positions, null);
        Map<String, String> declared = new LinkedHashMap<String, String>();
        for (Map.Entry<Integer, String> p : positions.entrySet()) {
            declared.put("arg" + p.getKey(), p.getValue());
        }
        return new Entry(name, message, declared, file, line);
    }

    private Entry androidPlurals(String name, Element element, String file, int line) {
        Map<Integer, String> positions = new TreeMap<Integer, String>();
        Map<String, JxmlMessages.Message> cases = new LinkedHashMap<String, JxmlMessages.Message>();
        NodeList items = element.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String quantity = item.getAttribute("quantity");
            if (!Arrays.asList("zero", "one", "two", "few", "many", "other").contains(quantity)) {
                throw new IllegalArgumentException("quantity \"" + quantity + "\" is not zero, one, two, few, many or other");
            }
            cases.put(quantity, androidMessage(item.getTextContent(), positions, "count"));
        }
        if (!cases.containsKey("other")) {
            throw new IllegalArgumentException("the plurals has no item with quantity=\"other\"");
        }
        List<JxmlMessages.Part> parts = new ArrayList<JxmlMessages.Part>();
        parts.add(new JxmlMessages.Choice("count", true, cases));
        Map<String, String> declared = new LinkedHashMap<String, String>();
        declared.put("count", "double".equals(positions.get(1)) ? "double" : "int");
        for (Map.Entry<Integer, String> p : positions.entrySet()) {
            if (p.getKey() != 1) {
                declared.put("arg" + p.getKey(), p.getValue());
            }
        }
        return new Entry(name, new JxmlMessages.Message(parts), declared, file, line);
    }

    /** An Android string resource's value: quotes, escapes and white space as {@code aapt} reads them. */
    static String androidText(String raw) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        boolean pendingSpace = false; // white space outside quotes becomes one space, unless it is at an end
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            if (c == '\\' && i + 1 < raw.length()) {
                char e = raw.charAt(++i);
                if (e == 'u' && i + 4 < raw.length()) {
                    try {
                        out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                        i += 4;
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("bad \\u escape");
                    }
                } else {
                    out.append(e == 'n' ? '\n' : e == 't' ? '\t' : e);
                }
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
    // ---- checks --------------------------------------------------------------------------------------------

    /** The type of each argument of an entry: the declared one, or what the message does with it. */
    private static Map<String, String> typesOf(Entry e) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Map<String, String> used = e.message.args();
        for (Map.Entry<String, String> d : e.declared.entrySet()) {
            out.put(d.getKey(), d.getValue() != null ? d.getValue() : infer(used.get(d.getKey())));
        }
        for (Map.Entry<String, String> u : used.entrySet()) {
            if (!out.containsKey(u.getKey())) {
                out.put(u.getKey(), infer(u.getValue()));
            }
        }
        return out;
    }

    private static String infer(String use) {
        return "plural".equals(use) ? "int" : "number".equals(use) ? "num" : "date".equals(use) || "time".equals(use) ? "DateTime"
                : "String";
    }

    /** Whether an argument of this type can be what the message does with it (a plural counts, a number is formatted...). */
    private static boolean fits(String use, String type) {
        if ("plural".equals(use) || "number".equals(use)) {
            return Arrays.asList("int", "double", "num").contains(type);
        }
        if ("date".equals(use) || "time".equals(use)) {
            return "DateTime".equals(type);
        }
        return true;
    }

    private static String needs(String use) {
        return "plural".equals(use) ? "a plural, so it must be int, double or num" : "number".equals(use)
                ? "formatted as a number, so it must be int, double or num" : "formatted as a date, so it must be DateTime";
    }

    private void check() {
        Map<String, Entry> base = locales.get(defaultTag);
        if (base == null) {
            return;
        }
        Map<String, String> methods = new HashMap<String, String>();
        for (Entry e : base.values()) {
            Map<String, String> types = typesOf(e);
            Map<String, String> used = e.message.args();
            for (Map.Entry<String, String> t : types.entrySet()) {
                if (!fits(used.get(t.getKey()), t.getValue())) {
                    problem(e.file, e.line, e.key + ": " + t.getKey() + " is " + needs(used.get(t.getKey())) + ", not "
                            + t.getValue());
                }
            }
            String method = methodName(e.key);
            String other = methods.put(method, e.key);
            if (other != null) {
                problem(e.file, e.line, "the keys " + other + " and " + e.key + " would both be the method " + method + "()");
            }
            signatures.put(e.key, new Signature(e.key, method, new ArrayList<String>(types.keySet()),
                    new ArrayList<String>(types.values()), e.where()));
        }
        Map<String, TreeMap<Integer, Entry>> groups = new TreeMap<String, TreeMap<Integer, Entry>>();
        for (Entry e : base.values()) {
            Matcher item = ITEM.matcher(e.key);
            if (item.matches() && isKey(item.group(1))) {
                TreeMap<Integer, Entry> group = groups.get(item.group(1));
                if (group == null) {
                    group = new TreeMap<Integer, Entry>();
                    groups.put(item.group(1), group);
                }
                group.put(Integer.valueOf(item.group(2)), e);
            }
        }
        for (Map.Entry<String, TreeMap<Integer, Entry>> g : groups.entrySet()) {
            Entry first = g.getValue().firstEntry().getValue();
            if (base.containsKey(g.getKey())) {
                problem(first.file, first.line, "the key " + g.getKey() + " is both a text and the array " + g.getKey() + "[0]...");
                continue;
            }
            if (g.getValue().firstKey() != 0 || g.getValue().lastKey() != g.getValue().size() - 1) {
                problem(first.file, first.line, "the array " + g.getKey() + " has the items " + g.getValue().keySet()
                        + ": the indexes must be 0, 1, 2... without a gap");
                continue;
            }
            boolean plain = true;
            List<String> keys = new ArrayList<String>();
            for (Entry item : g.getValue().values()) {
                plain &= signatures.get(item.key).names.isEmpty();
                keys.add(item.key);
            }
            String other = plain ? methods.put(methodName(g.getKey()), g.getKey() + "[]") : null;
            if (other != null) {
                problem(first.file, first.line, "the array " + g.getKey() + " and the key " + other + " would both be the method "
                        + methodName(g.getKey()) + "()");
            } else if (plain) {
                arrays.put(g.getKey(), keys);
            }
        }
        for (Map.Entry<String, Map<String, Entry>> locale : locales.entrySet()) {
            String tag = locale.getKey();
            if (tag.equals(defaultTag)) {
                continue;
            }
            for (Entry e : locale.getValue().values()) {
                Entry def = base.get(e.key);
                if (def == null) {
                    warn(e.file, e.line, tag + " has the key " + e.key + ", which the default locale " + defaultTag
                            + " does not define");
                    continue;
                }
                compare(tag, e, def);
            }
            List<String> lacking = new ArrayList<String>();
            for (Entry def : base.values()) {
                if (!locale.getValue().containsKey(def.key)) {
                    lacking.add(def.key);
                    warn(filesOfTag.get(tag), 0, tag + " has no translation for " + def.key + " (defined at "
                            + def.where() + ")");
                }
            }
            if (!lacking.isEmpty()) {
                missing.put(tag, lacking);
            }
        }
    }

    private void compare(String tag, Entry e, Entry def) {
        Map<String, String> mine = typesOf(e);
        Map<String, String> theirs = typesOf(def);
        Set<String> a = new LinkedHashSet<String>(mine.keySet());
        Set<String> b = new LinkedHashSet<String>(theirs.keySet());
        if (!a.equals(b)) {
            problem(e.file, e.line, e.key + " in " + tag + " has the placeholders " + a + ", but " + defaultTag + " ("
                    + def.where() + ") has " + b);
            return;
        }
        Map<String, String> used = e.message.args();
        for (String name : b) {
            String declaredHere = e.declared.get(name);
            String there = theirs.get(name);
            if (declaredHere != null && !declaredHere.equals(there)) {
                problem(e.file, e.line, e.key + ": the placeholder " + name + " is " + declaredHere + " in " + tag + " but "
                        + there + " in " + defaultTag + " (" + def.where() + ")");
            } else if (!fits(used.get(name), there)) {
                String use = used.get(name);
                problem(e.file, e.line, e.key + ": " + name + " is a " + ("plural".equals(use) ? "plural" : use.equals("number")
                        ? "number" : "date") + " in " + tag + " but " + there + " in " + defaultTag);
            }
        }
    }

    /** Keys can have dots and dashes (as FXML bundle keys do); the Java method uses {@code _} for them. */
    static String methodName(String key) {
        StringBuilder out = new StringBuilder();
        for (char ch : key.toCharArray()) {
            out.append(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '_' ? ch : '_');
        }
        if (out.length() == 0 || Character.isDigit(out.charAt(0))) {
            out.insert(0, '_');
        }
        String name = out.toString();
        return RESERVED.contains(name) ? name + "_" : name;
    }

    // ---- the generated class -------------------------------------------------------------------------------

    /** The name of the generated class, in the package of the screens. */
    public static final String STRINGS = "Strings";

    /**
     * The source of {@code Strings}: the tables as calls that fill a {@link JxmlMessages}, and one static method per key
     * with the arguments as the default locale declares them. Equal catalogs give equal text.
     */
    public String generateStrings(String packageName) {
        StringBuilder out = new StringBuilder("// Generated by JXForge from the l10n files in ").append(dir.getName().replace('\n', ' '))
                .append(". Do not edit.\n");
        if (packageName != null && !packageName.isEmpty()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("import com.jxparallel.forge.JxmlMessages;\n\n")
                .append("public final class ").append(STRINGS).append(" {\n")
                .append("    private static final JxmlMessages M = new JxmlMessages(").append(JxmlCompiler.quote(defaultTag)).append(");\n\n");
        List<String> calls = new ArrayList<String>();
        for (Map.Entry<String, Map<String, Entry>> locale : locales.entrySet()) {
            for (Entry e : locale.getValue().values()) {
                calls.add("M.add(" + JxmlCompiler.quote(locale.getKey()) + ", " + JxmlCompiler.quote(e.key) + ", "
                        + JxmlCompiler.quote(e.message.toIcu()) + ");");
            }
        }
        for (Signature s : signatures.values()) {
            StringBuilder names = new StringBuilder();
            for (String n : s.names) {
                names.append(", ").append(JxmlCompiler.quote(n));
            }
            calls.add("M.signature(" + JxmlCompiler.quote(s.key) + names + ");");
        }
        int chunks = (calls.size() + 249) / 250;
        out.append("    static {\n");
        for (int c = 0; c < chunks; c++) {
            out.append("        l10n$").append(c).append("();\n");
        }
        out.append("    }\n");
        for (int c = 0; c < chunks; c++) { // methods stay under the 64 KB limit of a class file
            out.append("\n    private static void l10n$").append(c).append("() {\n");
            for (int k = c * 250; k < Math.min(calls.size(), (c + 1) * 250); k++) {
                out.append("        ").append(calls.get(k)).append('\n');
            }
            out.append("    }\n");
        }
        out.append("\n    private ").append(STRINGS).append("() {\n    }\n");
        out.append("\n    /** The locales with texts, the default one included. */\n")
                .append("    public static java.util.List<java.util.Locale> supported() {\n        return M.supported();\n    }\n")
                .append("\n    /** The current locale: at first the best match for the system's. */\n")
                .append("    public static java.util.Locale locale() {\n        return M.locale();\n    }\n")
                .append("\n    /** Changes the locale and tells the listeners, so screens can be built again. */\n")
                .append("    public static void setLocale(java.util.Locale locale) {\n        M.setLocale(locale);\n    }\n")
                .append("\n    public static void onLocaleChange(Runnable listener) {\n        M.onLocaleChange(listener);\n    }\n")
                .append("\n    public static void offLocaleChange(Runnable listener) {\n        M.offLocaleChange(listener);\n    }\n");
        for (Signature s : signatures.values()) {
            StringBuilder params = new StringBuilder();
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < s.names.size(); i++) {
                String javaName = JxmlCatalog.RESERVED.contains(s.names.get(i)) ? s.names.get(i) + "_" : s.names.get(i);
                params.append(i == 0 ? "" : ", ").append(s.javaType(i)).append(' ').append(javaName);
                args.append(", ").append(javaName);
            }
            out.append("\n    /** ").append(s.key.replace("*/", "")).append(" */\n")
                    .append("    public static String ").append(s.method).append('(').append(params).append(") {\n")
                    .append("        return M.format(").append(JxmlCompiler.quote(s.key)).append(args).append(");\n")
                    .append("    }\n");
        }
        for (Map.Entry<String, List<String>> array : arrays.entrySet()) { // days[0], days[1]... as one String[]
            StringBuilder items = new StringBuilder();
            for (String key : array.getValue()) {
                items.append(items.length() == 0 ? "" : ", ").append(signatures.get(key).method).append("()");
            }
            out.append("\n    /** ").append(array.getKey().replace("*/", "")).append(": the items ").append(array.getKey())
                    .append("[0] to [").append(array.getValue().size() - 1).append("] */\n")
                    .append("    public static String[] ").append(methodName(array.getKey())).append("() {\n")
                    .append("        return new String[] {").append(items).append("};\n    }\n");
        }
        return out.append("}\n").toString();
    }

    // ---- ARB output ----------------------------------------------------------------------------------------

    /** The ARB text of one locale: its messages, with a {@code placeholders} block for those with arguments. */
    public String toArb(String tag) {
        Map<String, Entry> table = locales.get(tag);
        StringBuilder out = new StringBuilder("{\n  \"@@locale\": ").append(JxmlJson.quote(tag));
        if (table != null) {
            for (Entry e : table.values()) {
                out.append(",\n  ").append(JxmlJson.quote(e.key)).append(": ").append(JxmlJson.quote(e.message.toIcu()));
                Map<String, String> types = typesOf(e);
                if (!types.isEmpty()) {
                    out.append(",\n  ").append(JxmlJson.quote("@" + e.key)).append(": {\n    \"placeholders\": {");
                    int n = 0;
                    for (Map.Entry<String, String> t : types.entrySet()) {
                        out.append(n++ == 0 ? "\n" : ",\n").append("      ").append(JxmlJson.quote(t.getKey()))
                                .append(": {\"type\": ").append(JxmlJson.quote(t.getValue())).append('}');
                    }
                    out.append("\n    }\n  }");
                }
            }
        }
        return out.append("\n}\n").toString();
    }

    // ---- Android output ------------------------------------------------------------------------------------

    /**
     * The folder of {@code tag} in an Android {@code res} directory: {@code values} for the default locale,
     * {@code values-pt}, {@code values-pt-rBR}; null for a tag Android names another way ({@code zh_Hans_CN}).
     */
    public String androidFolder(String tag) {
        if (tag.equals(defaultTag)) {
            return "values";
        }
        String[] parts = tag.split("_");
        if (parts.length == 1 && parts[0].matches("[a-z]{2,3}")) {
            return "values-" + parts[0];
        }
        if (parts.length == 2 && parts[0].matches("[a-z]{2,3}") && parts[1].matches("[A-Z]{2}")) {
            return "values-" + parts[0] + "-r" + parts[1];
        }
        return null;
    }

    /**
     * The {@code strings.xml} of one locale. Strings, plurals ({@code {n, plural, one{..} other{..}}} as the whole message)
     * and arrays ({@code name[0]}, {@code name[1]}...) are written; what Android cannot say is left out and added to
     * {@code warnings} as {@code file:line: message}: select, selectordinal, {@code offset}, {@code =N} cases, nested
     * choices, number and date arguments and a plural that is only a part of the text. Android has no argument names, so
     * the arguments are numbered in the order of the default locale's signature ({@code %1$s}, {@code %2$d}; a double is
     * {@code %f}), and a plural's count is the first.
     */
    public String toAndroid(String tag, List<String> warnings) {
        Map<String, Entry> table = locales.get(tag);
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        if (tag.equals(defaultTag) && !"en".equals(defaultTag)) {
            out.append("    <!-- jxforge-default-locale: ").append(defaultTag).append(" -->\n");
        }
        if (table != null) {
            Set<String> written = new HashSet<String>();
            for (Entry e : table.values()) {
                Matcher item = ITEM.matcher(e.key);
                boolean array = item.matches() && isKey(item.group(1));
                String name = array ? item.group(1) : e.key;
                if (array) {
                    if (!written.add(name)) {
                        continue;
                    }
                    androidArray(name, table, tag, out, warnings);
                    continue;
                }
                try {
                    out.append(androidEntry(e, tag));
                } catch (IllegalArgumentException ex) {
                    warnings.add(e.where() + ": " + e.key + " cannot be written in strings.xml (" + ex.getMessage()
                            + "); it is left out of " + androidFolder(tag));
                }
            }
        }
        return out.append("</resources>\n").toString();
    }

    private void androidArray(String name, Map<String, Entry> table, String tag, StringBuilder out, List<String> warnings) {
        TreeMap<Integer, Entry> items = new TreeMap<Integer, Entry>();
        for (Entry e : table.values()) {
            Matcher item = ITEM.matcher(e.key);
            if (item.matches() && item.group(1).equals(name)) {
                items.put(Integer.valueOf(item.group(2)), e);
            }
        }
        StringBuilder body = new StringBuilder("    <string-array name=\"").append(xml(name)).append("\">\n");
        for (int i = 0; i < items.size(); i++) {
            Entry e = items.get(Integer.valueOf(i));
            if (e == null) {
                warnings.add(items.firstEntry().getValue().where() + ": the array " + name + " has the items " + items.keySet()
                        + ", with a gap; it is left out of " + androidFolder(tag));
                return;
            }
            try {
                body.append("        <item>").append(androidText(e, e.message, null)).append("</item>\n");
            } catch (IllegalArgumentException ex) {
                warnings.add(e.where() + ": " + e.key + " cannot be written in strings.xml (" + ex.getMessage()
                        + "); the array " + name + " is left out of " + androidFolder(tag));
                return;
            }
        }
        out.append(body).append("    </string-array>\n");
    }

    private String androidEntry(Entry e, String tag) {
        List<JxmlMessages.Part> parts = e.message.parts;
        if (parts.size() == 1 && parts.get(0) instanceof JxmlMessages.Choice) {
            JxmlMessages.Choice c = (JxmlMessages.Choice) parts.get(0);
            if (c.plural && !c.ordinal) {
                if (c.offset != 0) {
                    throw new IllegalArgumentException("a plural with offset:" + c.offset + " has no Android form");
                }
                StringBuilder out = new StringBuilder("    <plurals name=\"").append(xml(e.key)).append("\">\n");
                for (Map.Entry<String, JxmlMessages.Message> item : c.cases.entrySet()) {
                    if (item.getKey().startsWith("=")) {
                        throw new IllegalArgumentException("the case " + item.getKey() + " has no Android form; Android has only "
                                + "zero, one, two, few, many and other");
                    }
                    out.append("        <item quantity=\"").append(item.getKey()).append("\">")
                            .append(androidText(e, item.getValue(), c.name)).append("</item>\n");
                }
                return out.append("    </plurals>\n").toString();
            }
        }
        return "    <string name=\"" + xml(e.key) + "\">" + androidText(e, e.message, null) + "</string>\n";
    }

    /** The Android text of {@code m}, an entry's message or one case of its plural on {@code count}. */
    private String androidText(Entry e, JxmlMessages.Message m, String count) {
        Signature sig = signatures.get(e.key);
        List<String> order = new ArrayList<String>(sig == null ? typesOf(e).keySet() : sig.names);
        if (count != null) {
            order.remove(count);
            order.add(0, count);
        }
        Map<String, String> types = typesOf(e);
        StringBuilder text = new StringBuilder();
        for (JxmlMessages.Part p : m.parts) {
            if (p instanceof JxmlMessages.Text) {
                text.append(((JxmlMessages.Text) p).text.replace("%", "%%"));
            } else if (p instanceof JxmlMessages.Hash) {
                text.append(placeholder(1, types.get(count)));
            } else if (p instanceof JxmlMessages.Arg) {
                JxmlMessages.Arg a = (JxmlMessages.Arg) p;
                if (a.type != null) {
                    throw new IllegalArgumentException("{" + a.name + ", " + a.type + "} formats a " + a.type + ", which strings.xml cannot say");
                }
                int position = order.indexOf(a.name) + 1;
                if (position == 0) {
                    throw new IllegalArgumentException("the argument " + a.name + " is not in the signature of " + defaultTag);
                }
                String type = types.get(a.name);
                if ("DateTime".equals(type)) {
                    throw new IllegalArgumentException(a.name + " is a DateTime, which strings.xml cannot say");
                }
                text.append(placeholder(position, type));
            } else {
                JxmlMessages.Choice c = (JxmlMessages.Choice) p;
                throw new IllegalArgumentException((c.ordinal ? "a selectordinal" : c.plural ? "a plural that is only a part of the "
                        + "text, or inside another choice," : "a select") + " has no Android form");
            }
        }
        return androidEscape(text.toString());
    }

    private static String placeholder(int position, String type) {
        char conv = "int".equals(type) ? 'd' : "double".equals(type) || "num".equals(type) ? 'f' : 's';
        return "%" + position + "$" + conv;
    }

    /** The text as it goes between the tags of a string resource: quotes, XML entities, line breaks and white space. */
    static String androidEscape(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\'':
                    out.append("\\'");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                default:
                    out.append(c);
            }
        }
        String text = out.toString();
        if (text.startsWith("@") || text.startsWith("?")) {
            text = "\\" + text;
        }
        boolean spaces = text.startsWith(" ") || text.endsWith(" ") || text.contains("  ") || text.indexOf('\r') >= 0;
        return spaces ? "\"" + text + "\"" : text;
    }

    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    // ---- hashing -------------------------------------------------------------------------------------------

    private static MessageDigest digestOf() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        }
        return hex.toString();
    }

    static String sha256(byte[] bytes) {
        return hex(digestOf().digest(bytes));
    }
}
