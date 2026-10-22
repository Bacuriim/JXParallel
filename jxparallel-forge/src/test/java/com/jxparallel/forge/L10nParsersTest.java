package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pieces of the l10n files: the JSON reader, the ICU subset, the plural rules and the Android reader. */
class L10nParsersTest {
    @TempDir
    Path dir;

    // ---- JSON ------------------------------------------------------------------------------------------

    @Test
    void jsonReadsEscapesUnicodeNestingAndKeepsTheOrderAndLinesOfKeys() {
        String text = "{\n  \"b\": \"a\\nb\\t\\\"q\\\" \\\\ \\/ \\u00e9\\u4e2d\",\n  \"a\": {\"x\": [1, 2.5, true, null, \"s\"], \"y\": {}},\n  \"é\": \"ç\"\n}";
        JxmlJson.Obj root = (JxmlJson.Obj) JxmlJson.parse(text, "t.arb");
        assertEquals(Arrays.asList("b", "a", "é"), Arrays.asList(root.keySet().toArray()));
        assertEquals("a\nb\t\"q\" \\ / é中", root.get("b"));
        assertEquals(2, root.line("b"));
        assertEquals(3, root.line("a"));
        JxmlJson.Obj a = (JxmlJson.Obj) root.get("a");
        assertEquals(Arrays.asList(1.0, 2.5, true, null, "s"), a.get("x"));
        assertTrue(((JxmlJson.Obj) a.get("y")).isEmpty());
        assertEquals("ç", root.get("é"));
    }

    @Test
    void jsonErrorsNameTheFileAndTheLine() {
        assertJsonError("{\n  \"a\": 1,\n  \"b\": ,\n}", 3, "unexpected character");
        assertJsonError("{\n  \"a\": 1,\n}", 3, "expected a key");
        assertJsonError("{\"a\": \"x\n\"}", 1, "line break");
        assertJsonError("{\"a\": \"x", 1, "not closed");
        assertJsonError("{\"a\" 1}", 1, "expected :");
        assertJsonError("{\"a\": 1} x", 1, "after the end");
        assertJsonError("{\"a\": 1,\n\"a\": 2}", 2, "twice");
        assertJsonError("{\"a\": \"\\q\"}", 1, "bad escape");
        assertJsonError("[1, 2", 1, "array");
    }

    private static void assertJsonError(String text, int line, String words) {
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlJson.parse(text, "bad.arb"));
        assertEquals("bad.arb", e.getSource());
        assertEquals(line, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith("bad.arb:" + line + ": "), e.getMessage());
        assertTrue(e.getMessage().contains(words), e.getMessage());
    }

    // ---- ICU -------------------------------------------------------------------------------------------

    private static String fmt(String icu, Locale locale, Object... pairs) {
        JxmlMessages m = new JxmlMessages("en").add("en", "k", icu);
        String[] names = new String[pairs.length / 2];
        Object[] values = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = (String) pairs[2 * i];
            values[i] = pairs[2 * i + 1];
        }
        m.signature("k", names);
        return m.format(locale, "k", values);
    }

    private static String en(String icu, Object... pairs) {
        return fmt(icu, Locale.ENGLISH, pairs);
    }

    @Test
    void icuPlainTextArgumentsAndNumbers() {
        assertEquals("Hello", en("Hello"));
        assertEquals("Hi Ada, you are 7", en("Hi {name}, you are {age}", "name", "Ada", "age", 7));
        assertEquals("3.5", en("{x}", "x", 3.5));
        assertEquals("", en("{x}", "x", null));
        assertEquals("a b", en("{x} {y}", "x", "a", "y", "b"));
        assertEquals("a b", en("{x, number} {y, date, short}", "x", "a", "y", "b"));
        assertEquals("9", en("{x, number}", "x", 9));
    }

    @Test
    void icuPluralWithExactValuesCategoriesHashAndNestedPlaceholders() {
        String icu = "{count, plural, =0{No items} =1{One item} other{{count} items}}";
        assertEquals("No items", en(icu, "count", 0));
        assertEquals("One item", en(icu, "count", 1));
        assertEquals("5 items", en(icu, "count", 5));
        String hash = "{n, plural, one{# file of {who}} other{# files of {who}}}";
        assertEquals("1 file of Ada", en(hash, "n", 1, "who", "Ada"));
        assertEquals("2 files of Ada", en(hash, "n", 2, "who", "Ada"));
        assertEquals("1.5 files of Ada", en(hash, "n", 1.5, "who", "Ada"));
        assertEquals("exact", en("{n, plural, =2{exact} one{one} other{other}}", "n", 2));
    }

    @Test
    void icuSelectNestedInPluralAndPluralInSelect() {
        String icu = "{gender, select, male{{n, plural, one{He has # cat} other{He has # cats}}} other{{n, plural, one{# cat} other{# cats}}}}";
        assertEquals("He has 1 cat", en(icu, "gender", "male", "n", 1));
        assertEquals("He has 3 cats", en(icu, "gender", "male", "n", 3));
        assertEquals("2 cats", en(icu, "gender", "female", "n", 2));
        assertEquals("other", en("{g, select, a{x} other{other}}", "g", "zzz"));
        assertEquals("x", en("{g, select, a{x} other{other}}", "g", "a"));
    }

    @Test
    void icuQuoting() {
        assertEquals("Don't", en("Don't"));
        assertEquals("It's", en("It''s"));
        assertEquals("{literal}", en("'{'literal'}'"));
        assertEquals("a {b} c", en("a '{b}' c"));
        assertEquals("'{", en("'''{'"));
        assertEquals("'#' not a count", en("'#' not a count")); // outside a plural a # is nothing special
        assertEquals("1 # 1", en("{n, plural, other{# '#' #}}", "n", 1));
        assertEquals("rock 'n' roll", en("rock 'n' roll"));
        assertEquals("50# off", en("50# off"));
    }

    @Test
    void icuRoundTripsThroughToIcu() {
        for (String icu : Arrays.asList("plain", "Don''t {x} '{'y'}'", "{count, plural, =0{none} one{# item} other{{count} items '#'}}",
                "{g, select, a{{n, plural, one{x} other{y}}} other{z}}", "'''{'", "it's")) {
            JxmlMessages.Message m = JxmlMessages.parse(icu);
            assertEquals(m, JxmlMessages.parse(m.toIcu()), icu + " -> " + m.toIcu());
        }
    }

    @Test
    void icuErrorsSayWhatAndWhere() {
        assertIcuError("{count, plural, one{x}}", "'other'");
        assertIcuError("{g, select, a{x}}", "'other'");
        assertIcuError("a } b", "unmatched }");
        assertIcuError("{x", "missing }");
        assertIcuError("{count, plural, one{x} one{y} other{z}}", "twice");
        assertIcuError("{count, plural, many2{x} other{z}}", "not =N");
        assertIcuError("{count, plural, offset:1 other{z}}", "offset");
        assertIcuError("{x, bogus, a{b}}", "unsupported argument type");
        assertIcuError("{ , }", "bad argument name");
        assertIcuError("{n, plural, other{abc}", "missing }");
    }

    private static void assertIcuError(String icu, String words) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> JxmlMessages.parse(icu));
        assertTrue(e.getMessage().contains(words), e.getMessage());
        assertTrue(e.getMessage().contains("position"), e.getMessage());
    }

    @Test
    void messageArgsListNamesInOrderWithTheirUse() {
        JxmlMessages.Message m = JxmlMessages.parse("{a} {n, plural, one{{b}} other{# {a}}} {g, select, x{{c}} other{y}}");
        assertEquals("[a, n, b, g, c]", m.args().keySet().toString());
        assertEquals("plain", m.args().get("a"));
        assertEquals("plural", m.args().get("n"));
        assertEquals("select", m.args().get("g"));
    }

    // ---- plural rules ----------------------------------------------------------------------------------

    @Test
    void pluralRulesOfEnPtEsFrAndTheFallback() {
        assertEquals("other", JxmlMessages.category(Locale.ENGLISH, 0));
        assertEquals("one", JxmlMessages.category(Locale.ENGLISH, 1));
        assertEquals("other", JxmlMessages.category(Locale.ENGLISH, 2));
        assertEquals("other", JxmlMessages.category(Locale.ENGLISH, 1.5));
        assertEquals("one", JxmlMessages.category(new Locale("pt", "BR"), 0));
        assertEquals("one", JxmlMessages.category(new Locale("pt", "BR"), 1));
        assertEquals("one", JxmlMessages.category(new Locale("pt"), 1.5));
        assertEquals("other", JxmlMessages.category(new Locale("pt", "BR"), 2));
        assertEquals("many", JxmlMessages.category(new Locale("pt", "BR"), 1000000));
        assertEquals("other", JxmlMessages.category(new Locale("pt", "PT"), 0));
        assertEquals("one", JxmlMessages.category(new Locale("pt", "PT"), 1));
        assertEquals("one", JxmlMessages.category(new Locale("es"), 1));
        assertEquals("other", JxmlMessages.category(new Locale("es"), 0));
        assertEquals("many", JxmlMessages.category(new Locale("es"), 2000000));
        assertEquals("one", JxmlMessages.category(Locale.FRENCH, 0));
        assertEquals("one", JxmlMessages.category(Locale.FRENCH, 1.5));
        assertEquals("other", JxmlMessages.category(Locale.FRENCH, 2));
        assertEquals("other", JxmlMessages.category(Locale.JAPANESE, 1));
        assertEquals("one", JxmlMessages.category(Locale.ENGLISH, -1));
    }

    @Test
    void anExactValueBeatsACategoryAndAMissingCategoryFallsBackToOther() {
        String icu = "{n, plural, =0{zero!} one{one} many{many} other{other}}";
        assertEquals("zero!", fmt(icu, new Locale("pt"), "n", 0));
        assertEquals("one", fmt(icu, new Locale("pt"), "n", 1));
        assertEquals("many", fmt(icu, new Locale("pt"), "n", 3000000));
        assertEquals("other", fmt("{n, plural, one{one} other{other}}", new Locale("pt"), "n", 3000000));
        assertEquals("other", fmt("{n, plural, one{one} other{other}}", Locale.JAPANESE, "n", 1));
    }

    // ---- Android ---------------------------------------------------------------------------------------

    private JxmlCatalog android(String xml) throws IOException {
        Path values = dir.resolve("res/values/strings.xml");
        Files.createDirectories(values.getParent());
        Files.write(values, xml.getBytes(StandardCharsets.UTF_8));
        return JxmlCatalog.load(dir.resolve("res").toFile());
    }

    private static String text(JxmlCatalog catalog, String key, Object... args) {
        return catalog.messages().format(Locale.ENGLISH, key, args);
    }

    @Test
    void androidStringsWithPositionalArgumentsEscapesAndEntities() throws Exception {
        JxmlCatalog c = android("<resources>\n"
                + "<string name=\"hello\">Hello %1$s, %2$d new</string>\n"
                + "<string name=\"order\">%2$s before %1$s</string>\n"
                + "<string name=\"plain\">%s has %d (%.1f)</string>\n"
                + "<string name=\"quote\">Don\\'t &amp; \\\"go\\\" \\u00e9 100%%\\nnext</string>\n"
                + "<string name=\"spaced\">  many   spaces\n   here  </string>\n"
                + "<string name=\"kept\">\"  kept   spaces \"</string>\n"
                + "</resources>");
        assertEquals("Hello Ada, 3 new", text(c, "hello", "Ada", 3));
        assertEquals("arg1: String, arg2: int", c.signature("hello").describe());
        assertEquals("b before a", text(c, "order", "a", "b"));
        assertEquals("arg1: String, arg2: String", c.signature("order").describe());
        assertEquals("x has 2 (1.5)", text(c, "plain", "x", 2, 1.5));
        assertEquals("arg1: String, arg2: int, arg3: double", c.signature("plain").describe());
        assertEquals("Don't & \"go\" \u00e9 100%\nnext", text(c, "quote"));
        assertEquals("many spaces here", text(c, "spaced"));
        assertEquals("  kept   spaces ", text(c, "kept"));
    }

    @Test
    void androidPluralsBecomeAPluralOnCount() throws Exception {
        JxmlCatalog c = android("<resources><plurals name=\"files\">\n"
                + "<item quantity=\"one\">%d file</item><item quantity=\"other\">%d files in %2$s</item>\n"
                + "</plurals></resources>");
        assertEquals("count: int, arg2: String", c.signature("files").describe());
        assertEquals("1 file", text(c, "files", 1, "x"));
        assertEquals("4 files in x", text(c, "files", 4, "x"));
        assertEquals("{count, plural, one{{count} file} other{{count} files in {arg2}}}",
                c.messages().message("en", "files").toIcu());
    }

    @Test
    void androidSkipsWhatItCannotCarryAndReportsIt() throws Exception {
        JxmlCatalog c = android("<resources>\n<string name=\"a\">x</string>\n<string-array name=\"days\"><item>Mon</item></string-array>\n"
                + "<string name=\"hidden\" translatable=\"false\">no</string>\n</resources>");
        assertEquals(Arrays.asList("a"), c.keys());
        assertEquals(1, c.getWarnings().size());
        assertTrue(c.getWarnings().get(0).contains("strings.xml:3") && c.getWarnings().get(0).contains("string-array"), c.getWarnings().toString());
    }

    @Test
    void androidErrorsCarryTheLine() throws Exception {
        JxmlException e = assertThrows(JxmlException.class, () -> android("<resources>\n<string name=\"a\">%x</string>\n</resources>"));
        assertTrue(e.getMessage().contains("strings.xml:2:") && e.getMessage().contains("%x"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> android("<resources>\n<plurals name=\"p\"><item quantity=\"one\">x</item></plurals>\n</resources>"));
        assertTrue(e.getMessage().contains("strings.xml:2:") && e.getMessage().contains("other"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> android("<resources>\n<string name=\"a\">x</string\n</resources>"));
        assertTrue(e.getMessage().contains("strings.xml:") && e.getMessage().contains("bad XML"), e.getMessage());
    }

    @Test
    void methodNamesOfKeys() {
        assertEquals("app_menu_save", JxmlCatalog.methodName("app.menu-save"));
        assertEquals("default_", JxmlCatalog.methodName("default"));
        assertEquals("locale_", JxmlCatalog.methodName("locale"));
        assertEquals("_1a", JxmlCatalog.methodName("1a"));
        List<String> ok = Arrays.asList("a", "A_b", "a.b-c");
        for (String key : ok) {
            assertTrue(JxmlCatalog.isKey(key), key);
        }
        assertTrue(!JxmlCatalog.isKey("1a") && !JxmlCatalog.isKey("a b") && !JxmlCatalog.isKey(""));
    }
}
