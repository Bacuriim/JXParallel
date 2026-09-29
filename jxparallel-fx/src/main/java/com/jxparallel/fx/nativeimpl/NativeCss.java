package com.jxparallel.fx.nativeimpl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jxparallel.ui.JXProps;

import javafx.scene.paint.Color;

/**
 * The CSS subset native mode applies: author stylesheets (the scene's and every parent's
 * {@code stylesheets}), {@code styleClass}, {@code id} and inline {@code style}, with JavaFX's
 * priority (a stylesheet beats a value set in code, an inline style beats both) and specificity.
 *
 * <p>Selectors: type ({@code Button}), {@code .class} (including the default classes each control
 * gets, {@code .button}, {@code .text-field}...), {@code #id}, {@code *}, descendant and child
 * combinators, and the pseudo-classes {@code hover}, {@code focused}, {@code pressed},
 * {@code armed}, {@code selected}, {@code disabled}, {@code odd}, {@code even}, {@code empty},
 * {@code filled}, {@code expanded}, {@code collapsed}, {@code showing}, {@code default}.
 * Properties: {@code -fx-background-color}, {@code -fx-background-radius}, {@code -fx-border-color},
 * {@code -fx-border-width}, {@code -fx-border-radius}, {@code -fx-text-fill}, {@code -fx-fill},
 * {@code -fx-font-size}, {@code -fx-font-weight}, {@code -fx-font}, {@code -fx-padding},
 * {@code -fx-min/pref/max-width/height}, {@code -fx-opacity}, {@code -fx-underline},
 * {@code -fx-alignment}, {@code -fx-spacing}, {@code -fx-hgap}, {@code -fx-vgap}; colors as names,
 * hex, rgb(a), hsl(a), looked-up colors defined on {@code .root}, and the first stop of a gradient.
 * Everything else is ignored.
 */
final class NativeCss {
    private static final Map<String, List<Rule>> SHEETS = new ConcurrentHashMap<>();
    private static final Map<String, String[]> DEFAULT_CLASSES = new HashMap<>();
    private static final double EM = 12;

    static {
        put("Button", "button");
        put("ToggleButton", "toggle-button");
        put("CheckBox", "check-box");
        put("RadioButton", "radio-button");
        put("Hyperlink", "hyperlink");
        put("Label", "label");
        put("TextField", "text-input", "text-field");
        put("PasswordField", "text-input", "text-field", "password-field");
        put("CustomTextField", "text-input", "text-field", "custom-text-field");
        put("TextArea", "text-input", "text-area");
        put("ComboBox", "combo-box-base", "combo-box");
        put("ChoiceBox", "choice-box");
        put("DatePicker", "combo-box-base", "date-picker");
        put("Spinner", "spinner");
        put("Slider", "slider");
        put("ProgressBar", "progress-bar");
        put("ProgressIndicator", "progress-indicator");
        put("Separator", "separator");
        put("TitledPane", "titled-pane");
        put("Accordion", "accordion");
        put("ScrollPane", "scroll-pane");
        put("ListView", "list-view");
        put("TableView", "table-view");
        put("TabPane", "tab-pane");
        put("Tab", "tab");
        put("Pagination", "pagination");
        put("ListCell", "cell", "indexed-cell", "list-cell");
        put("TableCell", "cell", "indexed-cell", "table-cell");
        put("TableRow", "cell", "indexed-cell", "table-row-cell");
        put("DateCell", "cell", "date-cell");
        put("Tooltip", "tooltip");
        put("ImageView", "image-view");
        put("ButtonBar", "button-bar");
        put("MenuItem", "menu-item");
        put("ContextMenu", "context-menu");
    }

    private static void put(String type, String... classes) {
        DEFAULT_CLASSES.put(type, classes);
    }

    private final NativeScene scene;

    NativeCss(NativeScene scene) {
        this.scene = scene;
    }

    /*
     * Caches for one render (a NativeCss lives as long as its render's context): the rules in scope
     * of each node that has stylesheets, the looked-up colors of each scope, and the props a set of
     * matched rules gives. The cells of a table all match the same few rule sets, so a scrolled-in
     * cell replays those props instead of merging and parsing declarations again.
     */
    private List<Rule> sceneRules;
    private final Map<NativeModel, List<Rule>> scopes = new java.util.IdentityHashMap<>();
    private final Map<List<Rule>, Map<String, String>> lookupsByScope = new java.util.IdentityHashMap<>();
    private final Map<Applied, Object[]> applied = new HashMap<>();
    private final Applied probe = new Applied(null, new int[16], 0);

    /** Applies the matching declarations of every stylesheet in scope, then the inline style. */
    void apply(NativeModel m, JXProps.Builder p) {
        List<Rule> rules = rulesInScope(m);
        String inline = Native.value(m, "style") instanceof String ? (String) Native.value(m, "style") : null;
        boolean noInline = inline == null || inline.trim().isEmpty();
        if (rules.isEmpty() && noInline) {
            return;
        }
        if (noInline) {
            int n = 0;
            for (int i = 0; i < rules.size(); i++) {
                Rule rule = rules.get(i);
                if (!rule.lookupOnly && rule.selector.matches(m, this)) {
                    if (n == probe.matched.length) {
                        probe.matched = Arrays.copyOf(probe.matched, n * 2);
                    }
                    probe.matched[n++] = i;
                }
            }
            if (n == 0) {
                return;
            }
            probe.rules = rules;
            probe.count = n;
            Object[] pairs = applied.get(probe);
            if (pairs == null) {
                JXProps.Builder out = JXProps.builder();
                applyAll(m, rules, null, out);
                Map<String, Object> props = out.build().asMap();
                pairs = new Object[props.size() * 2];
                int k = 0;
                for (Map.Entry<String, Object> e : props.entrySet()) {
                    pairs[k++] = e.getKey();
                    pairs[k++] = e.getValue();
                }
                applied.put(new Applied(rules, Arrays.copyOf(probe.matched, n), n), pairs);
            }
            for (int k = 0; k < pairs.length; k += 2) {
                p.set((String) pairs[k], pairs[k + 1]);
            }
            return;
        }
        applyAll(m, rules, inline, p);
    }

    private void applyAll(NativeModel m, List<Rule> rules, String inline, JXProps.Builder p) {
        Map<String, String> lookups = lookupsByScope.get(rules);
        if (lookups == null) {
            lookups = lookups(rules);
            lookupsByScope.put(rules, lookups);
        }
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (!rule.lookupOnly && rule.selector.matches(m, this)) {
                matches.add(new Match(rule, i));
            }
        }
        Collections.sort(matches);
        Map<String, String> declared = new LinkedHashMap<>();
        for (Match match : matches) {
            declared.putAll(match.rule.declarations);
        }
        if (inline != null) {
            declared.putAll(parseDeclarations(inline));
        }
        for (Map.Entry<String, String> d : declared.entrySet()) {
            applyDeclaration(d.getKey(), resolve(d.getValue(), lookups), p);
        }
    }

    // ---- scope -------------------------------------------------------------------------------

    /** Stylesheets of the scene, then of each parent from the root down (inner ones win ties). */
    private List<Rule> rulesInScope(NativeModel m) {
        NativeModel c = m;
        while (c != null && !hasSheets(c)) {
            c = parentOf(c); // a node without stylesheets of its own sees its parent's scope
        }
        if (c == null) {
            if (sceneRules == null) {
                sceneRules = new ArrayList<>();
                if (scene != null && scene.sceneModel() != null) {
                    for (Object url : Native.list(scene.sceneModel(), "stylesheets")) {
                        sceneRules.addAll(sheet(String.valueOf(url)));
                    }
                }
            }
            return sceneRules;
        }
        List<Rule> scope = scopes.get(c);
        if (scope == null) {
            scope = new ArrayList<>(rulesInScope(parentOf(c)));
            for (Object url : (List<?>) c.values.get("stylesheets")) {
                scope.addAll(sheet(String.valueOf(url)));
            }
            scopes.put(c, scope);
        }
        return scope;
    }

    private static boolean hasSheets(NativeModel c) {
        Object sheets = c.values.get("stylesheets");
        return sheets instanceof List && !((List<?>) sheets).isEmpty();
    }

    /** A scope and the indices of its rules that matched: the props they give are the same for every node. */
    private static final class Applied {
        List<Rule> rules;
        int[] matched;
        int count;

        Applied(List<Rule> rules, int[] matched, int count) {
            this.rules = rules;
            this.matched = matched;
            this.count = count;
        }

        @Override
        public int hashCode() {
            int h = System.identityHashCode(rules);
            for (int i = 0; i < count; i++) {
                h = 31 * h + matched[i];
            }
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Applied)) {
                return false;
            }
            Applied a = (Applied) o;
            if (a.rules != rules || a.count != count) {
                return false;
            }
            for (int i = 0; i < count; i++) {
                if (a.matched[i] != matched[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    static NativeModel parentOf(NativeModel m) {
        Object rendered = m.state.get("renderParent");
        if (rendered instanceof NativeModel) {
            return (NativeModel) rendered;
        }
        return m.parent;
    }

    private static List<Rule> sheet(String url) {
        return SHEETS.computeIfAbsent(url, u -> {
            try {
                return parseSheet(read(u));
            } catch (Exception e) {
                System.err.println("JXParallel native CSS: cannot read " + u + ": " + e);
                return Collections.emptyList();
            }
        });
    }

    private static String read(String url) throws Exception {
        URL u;
        if (url.contains(":/") || url.startsWith("file:") || url.startsWith("jar:")) {
            u = new URL(url);
        } else {
            u = Thread.currentThread().getContextClassLoader().getResource(url.startsWith("/") ? url.substring(1) : url);
            if (u == null) {
                throw new IllegalArgumentException("not found");
            }
        }
        try (InputStream in = u.openStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Looked-up colors: declarations of custom properties on rules for {@code .root}. */
    private static Map<String, String> lookups(List<Rule> rules) {
        Map<String, String> out = new HashMap<>();
        for (Rule r : rules) {
            if (r.rootRule) {
                for (Map.Entry<String, String> d : r.declarations.entrySet()) {
                    out.put(d.getKey(), d.getValue());
                }
            }
        }
        return out;
    }

    private static String resolve(String value, Map<String, String> lookups) {
        String v = value.trim();
        for (int depth = 0; depth < 5 && lookups.containsKey(v); depth++) {
            v = lookups.get(v).trim();
        }
        return v;
    }

    // ---- parsing -----------------------------------------------------------------------------

    static List<Rule> parseSheet(String css) {
        String text = css.replaceAll("(?s)/\\*.*?\\*/", "");
        List<Rule> rules = new ArrayList<>();
        int i = 0;
        int order = 0;
        while (i < text.length()) {
            int open = text.indexOf('{', i);
            if (open < 0) {
                break;
            }
            String selectors = text.substring(i, open).trim();
            int close = matching(text, open);
            String body = text.substring(open + 1, Math.max(open + 1, close));
            i = close < 0 ? text.length() : close + 1;
            if (selectors.startsWith("@")) {
                continue; // @font-face, @media...: not supported
            }
            Map<String, String> declarations = parseDeclarations(body);
            for (String s : selectors.split(",")) {
                Selector selector = Selector.parse(s.trim());
                if (selector != null) {
                    rules.add(new Rule(selector, declarations, order++));
                }
            }
        }
        return rules;
    }

    private static int matching(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    static Map<String, String> parseDeclarations(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String d : splitTopLevel(body, ';')) {
            int colon = d.indexOf(':');
            if (colon > 0) {
                out.put(d.substring(0, colon).trim().toLowerCase(), d.substring(colon + 1).trim());
            }
        }
        return out;
    }

    /** Splits at {@code sep} outside parentheses and quotes. */
    static List<String> splitTopLevel(String s, char sep) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth--;
            } else if (!quoted && depth == 0 && c == sep) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) {
            out.add(s.substring(start));
        }
        return out;
    }

    // ---- declarations ------------------------------------------------------------------------

    static void applyDeclaration(String name, String value, JXProps.Builder p) {
        try {
            switch (name) {
                case "-fx-background-color": {
                    Integer color = lastColor(value);
                    if (color != null) {
                        p.set("background", color);
                    }
                    break;
                }
                case "-fx-background-radius":
                    p.set("backgroundRadius", firstLength(value));
                    break;
                case "-fx-border-color": {
                    Integer color = firstColor(value);
                    if (color != null) {
                        p.set("borderColor", color);
                    }
                    break;
                }
                case "-fx-border-width":
                    p.set("borderWidth", firstLength(value));
                    break;
                case "-fx-border-radius":
                    p.set("borderRadius", firstLength(value));
                    break;
                case "-fx-text-fill":
                case "-fx-fill": {
                    Integer color = firstColor(value);
                    if (color != null) {
                        p.set("textFill", color);
                    }
                    break;
                }
                case "-fx-font-size":
                    p.set("fontSize", length(value.trim()));
                    break;
                case "-fx-font-weight":
                    p.set("bold", isBold(value));
                    break;
                case "-fx-font":
                    font(value, p);
                    break;
                case "-fx-padding": {
                    double[] sides = lengths(value);
                    if (sides.length == 1) {
                        p.set("padding", new double[] {sides[0], sides[0], sides[0], sides[0]});
                    } else if (sides.length == 2) {
                        p.set("padding", new double[] {sides[0], sides[1], sides[0], sides[1]});
                    } else if (sides.length == 4) {
                        p.set("padding", sides);
                    }
                    break;
                }
                case "-fx-min-width":
                case "-fx-pref-width":
                case "-fx-max-width":
                case "-fx-min-height":
                case "-fx-pref-height":
                case "-fx-max-height": {
                    String key = name.substring(4);
                    String prop = key.substring(0, key.indexOf('-')) + Character.toUpperCase(key.charAt(key.indexOf('-') + 1))
                            + key.substring(key.indexOf('-') + 2);
                    String v = value.trim();
                    p.set(prop, "-fx-use-pref-size".equalsIgnoreCase(v) || "use_pref_size".equalsIgnoreCase(v)
                            ? Double.NEGATIVE_INFINITY : length(v));
                    break;
                }
                case "-fx-opacity":
                    p.set("opacity", Double.parseDouble(value.trim()));
                    break;
                case "-fx-underline":
                    p.set("underline", "true".equalsIgnoreCase(value.trim()));
                    break;
                case "-fx-wrap-text":
                    p.set("wrapText", "true".equalsIgnoreCase(value.trim()));
                    break;
                case "-fx-alignment":
                    p.set("alignment", value.trim().toUpperCase().replace('-', '_'));
                    break;
                case "-fx-text-alignment":
                    p.set("textAlignment", value.trim().toUpperCase());
                    break;
                case "-fx-spacing":
                    p.set("gap", length(value.trim()));
                    break;
                case "-fx-hgap":
                    p.set("hgap", length(value.trim()));
                    break;
                case "-fx-vgap":
                    p.set("vgap", length(value.trim()));
                    break;
                case "visibility":
                case "-fx-visibility":
                    if ("hidden".equalsIgnoreCase(value.trim())) {
                        p.set("hidden", true);
                    }
                    break;
                default:
                    break;
            }
        } catch (RuntimeException ignored) {
            // an invalid value is skipped, like JavaFX does after logging
        }
    }

    private static void font(String value, JXProps.Builder p) {
        for (String part : value.trim().split("\\s+")) {
            String t = part.toLowerCase();
            if (isBold(t)) {
                p.set("bold", true);
            } else if (t.matches("[0-9.]+(px|pt|em)?")) {
                p.set("fontSize", length(t));
            }
        }
    }

    private static boolean isBold(String value) {
        String v = value.trim().toLowerCase();
        if (v.equals("bold") || v.equals("bolder") || v.equals("extra_bold") || v.equals("black")) {
            return true;
        }
        return v.matches("[0-9]+") && Integer.parseInt(v) >= 600;
    }

    static double length(String v) {
        String t = v.trim().toLowerCase();
        if (t.endsWith("px")) {
            return Double.parseDouble(t.substring(0, t.length() - 2));
        }
        if (t.endsWith("pt")) {
            return Double.parseDouble(t.substring(0, t.length() - 2)) * 4 / 3;
        }
        if (t.endsWith("em")) {
            return Double.parseDouble(t.substring(0, t.length() - 2)) * EM;
        }
        if (t.endsWith("%")) {
            return Double.parseDouble(t.substring(0, t.length() - 1)) / 100 * EM;
        }
        return Double.parseDouble(t);
    }

    private static double firstLength(String value) {
        String first = splitTopLevel(value, ',').get(0).trim().split("\\s+")[0];
        return length(first);
    }

    private static double[] lengths(String value) {
        String[] parts = splitTopLevel(value, ',').get(0).trim().split("\\s+");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = length(parts[i]);
        }
        return out;
    }

    private static Integer firstColor(String value) {
        for (String layer : splitTopLevel(value, ',')) {
            Integer c = color(layer.trim().split("\\s+(?![^(]*\\))")[0]);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** The top layer of a layered background (the last color that is not transparent). */
    private static Integer lastColor(String value) {
        Integer found = null;
        for (String layer : splitTopLevel(value, ',')) {
            Integer c = color(layer.trim());
            if (c != null && (c >>> 24) != 0) {
                found = c;
            } else if (c != null && found == null) {
                found = c;
            }
        }
        return found;
    }

    static Integer color(String value) {
        String v = value.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) {
            return null;
        }
        if ("transparent".equalsIgnoreCase(v)) {
            return 0;
        }
        String lower = v.toLowerCase();
        if (lower.startsWith("linear-gradient") || lower.startsWith("radial-gradient")) {
            // the first color stop stands for the gradient
            String inner = v.substring(v.indexOf('(') + 1, v.lastIndexOf(')'));
            for (String part : splitTopLevel(inner, ',')) {
                String token = part.trim().split("\\s+(?![^(]*\\))")[0];
                Integer c = color(token);
                if (c != null) {
                    return c;
                }
            }
            return null;
        }
        if (lower.startsWith("derive(")) {
            List<String> args = splitTopLevel(v.substring(7, v.lastIndexOf(')')), ',');
            Integer base = color(args.get(0));
            if (base == null || args.size() < 2) {
                return base;
            }
            double amount = Double.parseDouble(args.get(1).trim().replace("%", "")) / 100;
            Color c = Color.rgb(base >> 16 & 0xFF, base >> 8 & 0xFF, base & 0xFF, (base >>> 24) / 255.0);
            Color derived = amount >= 0 ? c.interpolate(Color.WHITE, amount) : c.interpolate(Color.BLACK, -amount);
            return NativeElements.argb(derived);
        }
        if (lower.startsWith("ladder(")) {
            return null;
        }
        try {
            return NativeElements.argb(Color.web(v));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- selectors ---------------------------------------------------------------------------

    static final class Rule {
        final Selector selector;
        final Map<String, String> declarations;
        final int order;
        final boolean rootRule;
        final boolean lookupOnly;

        Rule(Selector selector, Map<String, String> declarations, int order) {
            this.selector = selector;
            this.declarations = declarations;
            this.order = order;
            this.rootRule = selector.isRoot();
            this.lookupOnly = false;
        }
    }

    private static final class Match implements Comparable<Match> {
        final Rule rule;
        final int index;

        Match(Rule rule, int index) {
            this.rule = rule;
            this.index = index;
        }

        @Override
        public int compareTo(Match o) {
            int s = Integer.compare(rule.selector.specificity, o.rule.selector.specificity);
            return s != 0 ? s : Integer.compare(index, o.index);
        }
    }

    /** A compound selector chain, right to left. */
    static final class Selector {
        final List<Simple> parts;
        /** Combinator before each part (index i relates part i to part i - 1): ' ' or '>'. */
        final List<Character> combinators;
        final int specificity;

        private Selector(List<Simple> parts, List<Character> combinators) {
            this.parts = parts;
            this.combinators = combinators;
            int ids = 0;
            int classes = 0;
            int types = 0;
            for (Simple s : parts) {
                ids += s.id == null ? 0 : 1;
                classes += s.classes.size() + s.pseudo.size();
                types += s.type == null || "*".equals(s.type) ? 0 : 1;
            }
            this.specificity = ids * 10000 + classes * 100 + types;
        }

        boolean isRoot() {
            return parts.size() == 1 && parts.get(0).classes.equals(Collections.singletonList("root"))
                    && parts.get(0).pseudo.isEmpty();
        }

        static Selector parse(String text) {
            if (text.isEmpty()) {
                return null;
            }
            List<Simple> parts = new ArrayList<>();
            List<Character> combinators = new ArrayList<>();
            String normalized = text.replaceAll("\\s*>\\s*", " > ").trim();
            char pending = ' ';
            for (String token : normalized.split("\\s+")) {
                if (token.equals(">")) {
                    pending = '>';
                    continue;
                }
                Simple simple = Simple.parse(token);
                if (simple == null) {
                    return null;
                }
                parts.add(simple);
                combinators.add(parts.size() == 1 ? ' ' : pending);
                pending = ' ';
            }
            return parts.isEmpty() ? null : new Selector(parts, combinators);
        }

        boolean matches(NativeModel m, NativeCss css) {
            return matchFrom(parts.size() - 1, m, css);
        }

        private boolean matchFrom(int index, NativeModel m, NativeCss css) {
            if (!parts.get(index).matches(m, css)) {
                return false;
            }
            if (index == 0) {
                return true;
            }
            char combinator = combinators.get(index);
            for (NativeModel a = parentOf(m); a != null; a = parentOf(a)) {
                if (matchFrom(index - 1, a, css)) {
                    return true;
                }
                if (combinator == '>') {
                    return false;
                }
            }
            return false;
        }
    }

    static final class Simple {
        String type;
        String id;
        final List<String> classes = new ArrayList<>();
        final List<String> pseudo = new ArrayList<>();

        static Simple parse(String token) {
            Simple s = new Simple();
            int i = 0;
            int n = token.length();
            int start = 0;
            char kind = 't';
            while (i <= n) {
                char c = i < n ? token.charAt(i) : '\0';
                if (i == n || c == '.' || c == '#' || c == ':') {
                    String name = token.substring(start, i);
                    if (!name.isEmpty()) {
                        switch (kind) {
                            case 't':
                                s.type = name;
                                break;
                            case '.':
                                s.classes.add(name);
                                break;
                            case '#':
                                s.id = name;
                                break;
                            default:
                                s.pseudo.add(name.toLowerCase());
                                break;
                        }
                    } else if (kind != 't' && i > start) {
                        return null;
                    }
                    kind = c;
                    start = i + 1;
                }
                i++;
            }
            return s;
        }

        boolean matches(NativeModel m, NativeCss css) {
            if (type != null && !"*".equals(type) && !type.equals(m.type)) {
                return false;
            }
            if (id != null && !id.equals(Native.value(m, "id"))) {
                return false;
            }
            if (!classes.isEmpty()) {
                List<String> own = styleClasses(m, css);
                if (!own.containsAll(classes)) {
                    return false;
                }
            }
            for (String p : pseudo) {
                if (!css.pseudo(m, p)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Style classes of a node: the defaults of its control, "root" for a scene root, and its own list. */
    static List<String> styleClasses(NativeModel m, NativeCss css) {
        List<String> out = new ArrayList<>();
        String[] defaults = DEFAULT_CLASSES.get(m.type);
        if (defaults != null) {
            out.addAll(Arrays.asList(defaults));
        }
        if (css != null && css.scene != null && css.scene.rootModel() == m) {
            out.add("root");
        }
        Object own = m.values.get("styleClass");
        if (own instanceof List) {
            for (Object c : (List<?>) own) {
                out.add(String.valueOf(c));
            }
        }
        return out;
    }

    boolean pseudo(NativeModel m, String name) {
        switch (name) {
            case "hover":
                return scene != null && scene.hover.contains(m);
            case "focused":
                return scene != null && scene.focus == m && scene.windowFocused;
            case "pressed":
            case "armed":
                return scene != null && scene.pressed == m && scene.pressInside;
            case "selected":
                return Boolean.TRUE.equals(Native.value(m, "selected"));
            case "disabled":
                return NativeRuntime.disabled(m);
            case "odd":
                return (int) NativeElements.number(m, "index", -1) % 2 == 1;
            case "even":
                return (int) NativeElements.number(m, "index", -1) % 2 == 0;
            case "empty":
                return Boolean.TRUE.equals(Native.value(m, "empty"));
            case "filled":
                return !Boolean.TRUE.equals(Native.value(m, "empty"));
            case "expanded":
                return !Boolean.FALSE.equals(Native.value(m, "expanded"));
            case "collapsed":
                return Boolean.FALSE.equals(Native.value(m, "expanded"));
            case "showing":
                return scene != null && NativeRuntime.popupOwner(scene) == m;
            case "default":
                return Boolean.TRUE.equals(Native.value(m, "defaultButton"));
            case "checked":
                return Boolean.TRUE.equals(Native.value(m, "selected"));
            default:
                return false;
        }
    }
}
