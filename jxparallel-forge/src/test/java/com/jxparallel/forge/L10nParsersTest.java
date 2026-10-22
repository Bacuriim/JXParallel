package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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
        assertEquals("9", en("{x, number}", "x", 9));
    }

    @Test
    void icuNumberAndDateStylesUseTheLocaleAndNeverHardCodeAText() {
        Date moment = new Date(1700000000000L);
        Locale ptBr = new Locale("pt", "BR");
        // the expected texts come from the same java.text call, so a JDK with other locale data (CLDR from 9) agrees with itself
        assertEquals(NumberFormat.getInstance(ptBr).format(1234567.891), fmt("{x, number}", ptBr, "x", 1234567.891));
        assertEquals(NumberFormat.getInstance(Locale.ENGLISH).format(1234567.891), en("{x, number}", "x", 1234567.891));
        assertEquals("1,234,567.891", en("{x, number}", "x", 1234567.891));
        assertEquals(NumberFormat.getPercentInstance(ptBr).format(0.25), fmt("{x, number, percent}", ptBr, "x", 0.25));
        assertEquals("25%", en("{x, number, percent}", "x", 0.25));
        assertEquals(NumberFormat.getIntegerInstance(Locale.ENGLISH).format(3.7), en("{x, number, integer}", "x", 3.7));
        assertEquals(NumberFormat.getCurrencyInstance(Locale.US).format(9.5), fmt("{x, number, currency}", Locale.US, "x", 9.5));
        assertEquals("1,234.50", en("{x, number, #,##0.00}", "x", 1234.5));
        assertEquals("12", en("{x, number}", "x", Integer.valueOf(12)));
        for (String style : new String[] {"short", "medium", "long", "full"}) {
            int named = style.equals("short") ? DateFormat.SHORT : style.equals("medium") ? DateFormat.MEDIUM
                    : style.equals("long") ? DateFormat.LONG : DateFormat.FULL;
            for (Locale locale : new Locale[] {Locale.ENGLISH, ptBr, Locale.FRENCH}) {
                assertEquals(DateFormat.getDateInstance(named, locale).format(moment),
                        fmt("{d, date, " + style + "}", locale, "d", moment), style + " " + locale);
                assertEquals(DateFormat.getTimeInstance(named, locale).format(moment),
                        fmt("{d, time, " + style + "}", locale, "d", moment), style + " " + locale);
            }
        }
        assertEquals(DateFormat.getDateInstance(DateFormat.MEDIUM, ptBr).format(moment), fmt("{d, date}", ptBr, "d", moment));
        assertEquals(DateFormat.getDateInstance(DateFormat.MEDIUM, ptBr).format(moment), fmt("{d}", ptBr, "d", moment));
        assertEquals(new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(moment), en("{d, date, dd/MM/yyyy}", "d", moment));
        assertEquals("", en("{x, number}", "x", null));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> en("{x, number}", "x", "a")).getMessage().contains("needs a number"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> en("{x, date, short}", "x", 5)).getMessage().contains("needs a date"));
    }

    @Test
    void icuStylesAreCheckedWhenTheMessageIsParsed() {
        assertIcuError("{x, number, compact}", "number style \"compact\" is not supported");
        assertIcuError("{x, number, #,##0.0.0}", "malformed");
        assertIcuError("{d, date, yMd}", "date style \"yMd\" is not supported");
        assertIcuError("{d, date, dd/MM/QQQ}", "malformed");
        assertIcuError("{d, time, }", "empty");
    }

    @Test
    void icuSelectordinalUsesTheOrdinalRulesOfEnglishAndOtherForTheRest() {
        String icu = "{n, selectordinal, one{#st} two{#nd} few{#rd} other{#th}}";
        String[] expected = {"1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "101st", "111th", "112th", "113th", "100th", "0th"};
        int[] values = {1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111, 112, 113, 100, 0};
        for (int i = 0; i < values.length; i++) {
            assertEquals(expected[i], en(icu, "n", values[i]), "n=" + values[i]);
        }
        assertEquals("1.5th", en(icu, "n", 1.5));
        for (Locale locale : new Locale[] {new Locale("es"), Locale.FRENCH, new Locale("pt", "BR"), Locale.JAPANESE}) {
            assertEquals("1th", fmt(icu, locale, "n", 1), locale.toString());
            assertEquals("2th", fmt(icu, locale, "n", 2), locale.toString());
        }
        assertEquals("exactly first", en("{n, selectordinal, =1{exactly first} one{#st} other{#th}}", "n", 1));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> en(icu, "n", "x")).getMessage().contains("selectordinal"));
    }

    @Test
    void icuPluralOffsetCountsWhatIsLeftAfterTheOffsetAndKeepsExactValuesOnTheWholeNumber() {
        String icu = "{n, plural, offset:1 =0{Nobody} =1{Only {host}} one{{host} and # other} other{{host} and # others}}";
        assertEquals("Nobody", en(icu, "n", 0, "host", "Ada"));
        assertEquals("Only Ada", en(icu, "n", 1, "host", "Ada"));
        assertEquals("Ada and 1 other", en(icu, "n", 2, "host", "Ada"));
        assertEquals("Ada and 2 others", en(icu, "n", 3, "host", "Ada"));
        assertEquals("Ada and 99 others", en(icu, "n", 100, "host", "Ada"));
        assertEquals("a 4", en("{n, plural, offset: 2 other{a #}}", "n", 6)); // a space after the colon is fine
        assertIcuError("{n, plural, offset:x other{z}}", "offset: must be followed by a whole number");
        assertIcuError("{n, selectordinal, offset:1 other{z}}", "offset belongs to plural");
        assertIcuError("{n, select, offset:1 other{z}}", "offset belongs to plural");
    }

    @Test
    void theNewIcuFormsRoundTripThroughToIcuAndTellHowTheirArgumentsAreUsed() {
        for (String icu : Arrays.asList("{x, number} {y, number, percent} {z, number, #,##0.00}", "{d, date, short} {t, time, long} {u, date}",
                "{n, selectordinal, one{#st} two{#nd} few{#rd} other{#th}}", "{n, plural, offset:2 =0{no} one{# more} other{# more}}")) {
            JxmlMessages.Message m = JxmlMessages.parse(icu);
            assertEquals(m, JxmlMessages.parse(m.toIcu()), icu + " -> " + m.toIcu());
            assertEquals(icu, m.toIcu());
        }
        JxmlMessages.Message m = JxmlMessages.parse("{a, number} {b, date, short} {c, time} {d, selectordinal, other{#}} {e} {e, number}");
        assertEquals("number", m.args().get("a"));
        assertEquals("date", m.args().get("b"));
        assertEquals("time", m.args().get("c"));
        assertEquals("plural", m.args().get("d"));
        assertEquals("number", m.args().get("e")); // used plain and as a number: the number wins
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
        assertIcuError("{count, plural, offset:1 many2{z} other{z}}", "not =N");
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

    private static void assertCategories(String language, String expected, double... values) {
        for (double n : values) {
            assertEquals(expected, JxmlMessages.category(new Locale(language), n), language + " " + n);
        }
    }

    @Test
    void pluralRulesOfTheSlavicLanguagesByTheirBoundaries() {
        for (String lang : new String[] {"ru", "uk"}) {
            assertCategories(lang, "one", 1, 21, 31, 101, 1001, -1, 1.0);
            assertCategories(lang, "few", 2, 3, 4, 22, 23, 24, 102, 104);
            assertCategories(lang, "many", 0, 5, 6, 9, 10, 11, 12, 13, 14, 15, 19, 20, 25, 100, 111, 112, 114, 1000000);
            assertCategories(lang, "other", 1.5, 0.5, 2.5, 11.5);
        }
        assertCategories("pl", "one", 1);
        assertCategories("pl", "few", 2, 3, 4, 22, 23, 24, 102, 104);
        assertCategories("pl", "many", 0, 5, 9, 10, 11, 12, 13, 14, 15, 20, 21, 25, 31, 100, 101, 111, 112, 1000000);
        assertCategories("pl", "other", 1.5, 0.5, 2.5);
        for (String lang : new String[] {"cs", "sk"}) {
            assertCategories(lang, "one", 1);
            assertCategories(lang, "few", 2, 3, 4);
            assertCategories(lang, "other", 0, 5, 11, 21, 100, 1000000);
            assertCategories(lang, "many", 1.5, 0.5, 2.5);
        }
    }

    @Test
    void pluralRulesOfArabicHebrewHindiTurkishAndTheOthers() {
        assertCategories("ar", "zero", 0);
        assertCategories("ar", "one", 1);
        assertCategories("ar", "two", 2);
        assertCategories("ar", "few", 3, 4, 10, 103, 110, 203);
        assertCategories("ar", "many", 11, 12, 26, 99, 111, 199, 1011);
        assertCategories("ar", "other", 100, 101, 102, 200, 1000, 1.5, 3.5);
        for (String lang : new String[] {"he", "iw"}) { // iw: what Java 8 to 16 call Hebrew
            assertCategories(lang, "one", 1, 0.5);
            assertCategories(lang, "two", 2);
            assertCategories(lang, "other", 0, 3, 5, 10, 11, 20, 100, 1.5);
        }
        assertCategories("hi", "one", 0, 1, 0.5, 1.0);
        assertCategories("hi", "other", 2, 5, 100, 1.5);
        assertCategories("tr", "one", 1, 1.0);
        assertCategories("tr", "other", 0, 2, 1.5, 0.5);
        for (String lang : new String[] {"de", "nl", "sv", "nb", "fi"}) {
            assertCategories(lang, "one", 1);
            assertCategories(lang, "other", 0, 2, 11, 1.5);
        }
        assertCategories("it", "one", 1);
        assertCategories("it", "other", 0, 2, 11, 1.5, 999999);
        assertCategories("it", "many", 1000000, 5000000);
        for (String lang : new String[] {"ja", "zh", "ko", "vi", "th", "id", "xx"}) {
            assertCategories(lang, "other", 0, 1, 2, 5, 1.5, 1000000);
        }
    }

    @Test
    void theOrdinalCategoriesOfEnglishHaveNoOtherLanguage() {
        String[] expected = {"other", "one", "two", "few", "other", "other", "other", "other", "one", "two", "few", "other"};
        int[] values = {0, 1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 24};
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 11 || values[i] == 12 || values[i] == 13) {
                assertEquals("other", JxmlMessages.ordinalCategory(Locale.ENGLISH, values[i]));
            } else {
                assertEquals(expected[i], JxmlMessages.ordinalCategory(Locale.ENGLISH, values[i]), "n=" + values[i]);
            }
        }
        assertEquals("one", JxmlMessages.ordinalCategory(new Locale("en", "GB"), 101));
        assertEquals("other", JxmlMessages.ordinalCategory(Locale.ENGLISH, 2.5));
        for (String lang : new String[] {"es", "fr", "pt", "ru", "ja"}) {
            assertEquals("other", JxmlMessages.ordinalCategory(new Locale(lang), 1), lang);
        }
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
        JxmlCatalog c = android("<resources>\n<string name=\"a\">x</string>\n<integer-array name=\"days\"><item>1</item></integer-array>\n"
                + "<string name=\"hidden\" translatable=\"false\">no</string>\n</resources>");
        assertEquals(Arrays.asList("a"), c.keys());
        assertEquals(1, c.getWarnings().size());
        assertTrue(c.getWarnings().get(0).contains("strings.xml:3") && c.getWarnings().get(0).contains("integer-array"), c.getWarnings().toString());
    }

    @Test
    void androidStringArraysBecomeOneKeyPerItemAndAStringArrayMethod() throws Exception {
        JxmlCatalog c = android("<resources>\n<string-array name=\"days\">\n<item>Mon</item><item>Tue</item><item>Wed %1$s</item>\n</string-array>\n"
                + "<string-array name=\"seasons\"><item>Spring</item><item>Summer</item></string-array>\n</resources>");
        assertEquals(Arrays.asList("days[0]", "days[1]", "days[2]", "seasons[0]", "seasons[1]"), c.keys());
        assertEquals("Tue", text(c, "days[1]"));
        assertEquals("Wed x", text(c, "days[2]", "x"));
        assertEquals("days_1_", c.signature("days[1]").method);
        String strings = c.generateStrings("gen");
        assertTrue(strings.contains("public static String[] seasons() {"), strings);
        assertTrue(strings.contains("return new String[] {seasons_0_(), seasons_1_()};"), strings);
        assertTrue(!strings.contains("String[] days()"), "an item with an argument leaves the array without a String[] method");
        assertTrue(c.getWarnings().isEmpty(), c.getWarnings().toString());
    }

    @Test
    void anArrayWithAGapOrNamedLikeATextIsAnError() throws Exception {
        JxmlException e = assertThrows(JxmlException.class, () -> androidArb("{\"@@locale\": \"en\", \"d[0]\": \"a\", \"d[2]\": \"b\"}"));
        assertTrue(e.getMessage().contains("indexes must be 0, 1, 2") && e.getMessage().contains("[0, 2]"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> androidArb("{\"@@locale\": \"en\", \"d[0]\": \"a\", \"d\": \"b\"}"));
        assertTrue(e.getMessage().contains("both a text and the array d"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> androidArb("{\"@@locale\": \"en\", \"d[0]\": \"a\", \"d\": \"b\", \"x[1]\": \"c\"}"));
        assertTrue(e.getMessage().contains("both a text and the array d"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> androidArb("{\"@@locale\": \"en\", \"d[0]\": \"a\", \"d_0_\": \"b\"}"));
        assertTrue(e.getMessage().contains("would both be the method d_0_()"), e.getMessage());
        // an array and a text whose method has the same name
        e = assertThrows(JxmlException.class, () -> androidArb("{\"@@locale\": \"en\", \"a-b\": \"y\", \"a.b[0]\": \"a\"}"));
        assertTrue(e.getMessage().contains("the array a.b and the key a-b would both be the method a_b()"), e.getMessage());
    }

    private JxmlCatalog androidArb(String arb) throws IOException {
        Path file = dir.resolve("arb/app_en.arb");
        Files.createDirectories(file.getParent());
        Files.write(file, arb.getBytes(StandardCharsets.UTF_8));
        return JxmlCatalog.load(file.getParent().toFile());
    }

    @Test
    void numberAndDateArgumentsInferTheirTypesAndAWrongDeclaredTypeIsAnError() throws Exception {
        JxmlCatalog c = androidArb("{\"@@locale\": \"en\", \"a\": \"{n, number} {p, number, percent} {d, date, short} {t, time} {i, selectordinal, other{#}}\","
                + "\"b\": \"{n, number}\", \"@b\": {\"placeholders\": {\"n\": {\"type\": \"int\"}}}}");
        assertEquals("n: num, p: num, d: DateTime, t: DateTime, i: int", c.signature("a").describe());
        assertEquals("n: int", c.signature("b").describe());
        JxmlException e = assertThrows(JxmlException.class, () -> androidArb("{\"@@locale\": \"en\", \"a\": \"{n, number}\","
                + "\"@a\": {\"placeholders\": {\"n\": {\"type\": \"String\"}}}}"));
        assertTrue(e.getMessage().contains("formatted as a number"), e.getMessage());
        e = assertThrows(JxmlException.class, () -> androidArb("{\"@@locale\": \"en\", \"a\": \"{n, date, short}\","
                + "\"@a\": {\"placeholders\": {\"n\": {\"type\": \"int\"}}}}"));
        assertTrue(e.getMessage().contains("formatted as a date"), e.getMessage());
    }

    // ---- ARB to strings.xml ----------------------------------------------------------------------------

    @Test
    void exportWritesStringsPluralsArraysAndEscapesWhatAndroidReadsBack() throws Exception {
        JxmlCatalog arb = androidArb("{\"@@locale\": \"en\",\n"
                + "\"hello\": \"Hello {name}, {n} new\", \"@hello\": {\"placeholders\": {\"name\": {\"type\": \"String\"}, \"n\": {\"type\": \"int\"}}},\n"
                + "\"price\": \"Price {amount}\", \"@price\": {\"placeholders\": {\"amount\": {\"type\": \"double\"}}},\n"
                + "\"files\": \"{count, plural, one{# file} other{{count} files by {who}}}\", \"@files\": {\"placeholders\": {\"count\": {\"type\": \"int\"}, \"who\": {\"type\": \"String\"}}},\n"
                + "\"quote\": \"Don't \\\"go\\\" & <b> 100% \\\\ done\\nnext\",\n"
                + "\"lead\": \"  spaced  \", \"at\": \"@home\", \"ask\": \"?why\",\n"
                + "\"days[0]\": \"Mon\", \"days[1]\": \"Tue {x}\", \"@days[1]\": {\"placeholders\": {\"x\": {\"type\": \"String\"}}},\n"
                + "\"sel\": \"{g, select, a{x} other{y}}\", \"ord\": \"{n, selectordinal, other{#th}}\", \"when\": \"{d, date, short}\",\n"
                + "\"off\": \"{n, plural, offset:1 other{#}}\", \"exact\": \"{n, plural, =0{none} other{some}}\",\n"
                + "\"mixed\": \"You have {n, plural, one{# file} other{# files}}\", \"nested\": \"{n, plural, other{{g, select, a{x} other{y}}}}\"}");
        List<String> warnings = new java.util.ArrayList<String>();
        String xml = arb.toAndroid("en", warnings);
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"), xml);
        assertTrue(xml.contains("<string name=\"hello\">Hello %1$s, %2$d new</string>"), xml);
        assertTrue(xml.contains("<string name=\"price\">Price %1$f</string>"), xml);
        assertTrue(xml.contains("<item quantity=\"one\">%1$d file</item>"), xml);
        assertTrue(xml.contains("<item quantity=\"other\">%1$d files by %2$s</item>"), xml);
        assertTrue(xml.contains("<string name=\"lead\">\"  spaced  \"</string>"), xml);
        assertTrue(xml.contains("<string name=\"at\">\\@home</string>") && xml.contains("<string name=\"ask\">\\?why</string>"), xml);
        assertTrue(xml.contains("&amp; &lt;b&gt; 100%%"), xml);
        assertTrue(xml.contains("<string-array name=\"days\">\n        <item>Mon</item>\n        <item>Tue %1$s</item>\n    </string-array>"), xml);
        assertEquals(7, warnings.size(), warnings.toString());
        for (String key : new String[] {"sel", "ord", "when", "off", "exact", "mixed", "nested"}) {
            assertTrue(warnings.toString().contains(" " + key + " cannot be written in strings.xml"), key + " in " + warnings);
            assertTrue(!xml.contains("name=\"" + key + "\""), key);
        }
        assertTrue(warnings.get(0).startsWith(dir.resolve("arb/app_en.arb").toString().replace('\\', '/') + ":"), warnings.get(0));
        // Android reads it back to the same texts
        Path values = dir.resolve("back/values/strings.xml");
        Files.createDirectories(values.getParent());
        Files.write(values, xml.getBytes(StandardCharsets.UTF_8));
        JxmlCatalog back = JxmlCatalog.load(dir.resolve("back").toFile());
        assertEquals("Hello Ada, 3 new", text(back, "hello", "Ada", 3));
        assertEquals("Price 2.5", text(back, "price", 2.5));
        assertEquals("1 file", text(back, "files", 1, "x"));
        assertEquals("4 files by Bo", text(back, "files", 4, "Bo"));
        assertEquals(text(arb, "quote"), text(back, "quote"));
        assertEquals("Don't \"go\" & <b> 100% \\ done\nnext", text(back, "quote"));
        assertEquals("  spaced  ", text(back, "lead"));
        assertEquals("@home", text(back, "at"));
        assertEquals("?why", text(back, "ask"));
        assertEquals("Tue z", text(back, "days[1]", "z"));
    }

    @Test
    void exportNamesTheFoldersOfTheLocalesAndSkipsTheOnesAndroidDoesNotName() throws Exception {
        File l10n = dir.resolve("l10nout").toFile();
        Files.createDirectories(l10n.toPath());
        Files.write(l10n.toPath().resolve("app_en.arb"), "{\"hello\": \"Hi\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(l10n.toPath().resolve("app_pt.arb"), "{\"hello\": \"Oi\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(l10n.toPath().resolve("app_pt_BR.arb"), "{\"hello\": \"E ai\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(l10n.toPath().resolve("app_zh_Hans_CN.arb"), "{\"hello\": \"Ni hao\"}".getBytes(StandardCharsets.UTF_8));
        File res = dir.resolve("resout").toFile();
        List<String> warnings = new java.util.ArrayList<String>();
        List<File> written = JXForge.l10nToAndroid(l10n, res, warnings);
        assertEquals(3, written.size());
        assertTrue(new File(res, "values/strings.xml").isFile() && new File(res, "values-pt/strings.xml").isFile()
                && new File(res, "values-pt-rBR/strings.xml").isFile());
        assertTrue(!new File(res, "values-zh-rCN").exists());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("zh_Hans_CN") && warnings.get(0).contains("skipped"), warnings.get(0));
        assertTrue(new String(Files.readAllBytes(new File(res, "values-pt-rBR/strings.xml").toPath()), StandardCharsets.UTF_8)
                .contains("<string name=\"hello\">E ai</string>"));
    }

    @Test
    void exportTakesTheArgumentTypesFromTheDefaultLocaleBecauseOtherFilesDoNotDeclareThem() throws Exception {
        File l10n = dir.resolve("typed").toFile();
        Files.createDirectories(l10n.toPath());
        Files.write(l10n.toPath().resolve("app_en.arb"), ("{\"@@locale\": \"en\",\n"
                + "\"hello\": \"Hello {name}, {n} new\", \"@hello\": {\"placeholders\": {\"name\": {\"type\": \"String\"}, \"n\": {\"type\": \"int\"}}},\n"
                + "\"files\": \"{count, plural, one{# file} other{{count} files}}\", \"@files\": {\"placeholders\": {\"count\": {\"type\": \"double\"}}}}")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(l10n.toPath().resolve("app_pt.arb"), ("{\"@@locale\": \"pt\",\n\"hello\": \"Ola {name}, {n} novos\",\n"
                + "\"files\": \"{count, plural, one{# arquivo} other{{count} arquivos}}\"}").getBytes(StandardCharsets.UTF_8));
        File res = dir.resolve("typedres").toFile();
        List<String> warnings = new java.util.ArrayList<String>();
        JXForge.l10nToAndroid(l10n, res, warnings);
        assertEquals(Collections.<String>emptyList(), warnings);
        String values = new String(Files.readAllBytes(new File(res, "values/strings.xml").toPath()), StandardCharsets.UTF_8);
        String pt = new String(Files.readAllBytes(new File(res, "values-pt/strings.xml").toPath()), StandardCharsets.UTF_8);
        assertTrue(values.contains("Hello %1$s, %2$d new") && values.contains("<item quantity=\"one\">%1$f file</item>"), values);
        assertTrue(pt.contains("Ola %1$s, %2$d novos") && pt.contains("<item quantity=\"one\">%1$f arquivo</item>"), pt);
        // and the Android files load back, with the default locale's types in both
        File back = dir.resolve("typedback").toFile();
        JXForge.l10nFromAndroid(res, back, null);
        JxmlCatalog original = JxmlCatalog.load(l10n);
        JxmlCatalog again = JxmlCatalog.load(back);
        for (String key : original.keys()) {
            assertEquals(original.signature(key).types, again.signature(key).types, key);
        }
        assertEquals("Ola Ada, 3 novos", again.messages().format(new Locale("pt"), "hello", "Ada", 3));
    }

    private static void assertSameCatalog(JxmlCatalog expected, JxmlCatalog actual) {
        assertEquals(expected.getDefaultTag(), actual.getDefaultTag());
        assertEquals(expected.tags(), actual.tags());
        assertEquals(expected.keys(), actual.keys());
        for (String tag : expected.tags()) {
            for (String key : expected.keys()) {
                assertEquals(expected.messages().message(tag, key) == null ? null : expected.messages().message(tag, key).toIcu(),
                        actual.messages().message(tag, key) == null ? null : actual.messages().message(tag, key).toIcu(), tag + " " + key);
            }
        }
        for (String key : expected.keys()) {
            assertEquals(expected.signature(key).describe(), actual.signature(key).describe(), key);
        }
    }

    @Test
    void androidToArbToAndroidKeepsEveryTextAndASecondRoundIsIdentical() throws Exception {
        String values = "<resources>\n<string name=\"hello\">Hello %1$s, %2$d new</string>\n<string name=\"bye\">Bye &amp; \\'later\\'</string>\n"
                + "<plurals name=\"files\"><item quantity=\"one\">%d file</item><item quantity=\"other\">%d files</item></plurals>\n"
                + "<string-array name=\"days\"><item>Mon</item><item>Tue</item></string-array>\n</resources>";
        String pt = "<resources>\n<string name=\"hello\">Ola %1$s, %2$d novos</string>\n"
                + "<plurals name=\"files\"><item quantity=\"one\">%d arquivo</item><item quantity=\"other\">%d arquivos</item></plurals>\n"
                + "<string-array name=\"days\"><item>Seg</item><item>Ter</item></string-array>\n</resources>";
        String ptBr = "<resources>\n<string name=\"bye\">Tchau</string>\n</resources>";
        File res1 = dir.resolve("res1").toFile();
        for (String[] f : new String[][] {{"values", values}, {"values-pt", pt}, {"values-pt-rBR", ptBr}}) {
            Path file = res1.toPath().resolve(f[0] + "/strings.xml");
            Files.createDirectories(file.getParent());
            Files.write(file, f[1].getBytes(StandardCharsets.UTF_8));
        }
        JxmlCatalog first = JxmlCatalog.load(res1);
        File arbDir = dir.resolve("arbs").toFile();
        List<String> warnings = new java.util.ArrayList<String>();
        JXForge.l10nFromAndroid(res1, arbDir, warnings);
        File res2 = dir.resolve("res2").toFile();
        JXForge.l10nToAndroid(arbDir, res2, warnings);
        // 5 untranslated keys reported when reading the Android files and 5 again when reading the ARB files: nothing is left out of the export
        assertEquals(10, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().noneMatch(w -> w.contains("cannot be written")), warnings.toString());
        JxmlCatalog second = JxmlCatalog.load(res2);
        assertSameCatalog(first, second);
        assertEquals("Hello Ada, 3 new", text(second, "hello", "Ada", 3));
        assertEquals("Bye & 'later'", text(second, "bye"));
        assertEquals("Seg", second.messages().format(new Locale("pt"), "days[0]"));
        // xml -> ARB -> xml -> ARB -> xml: the second xml is the first one's
        File arb2 = dir.resolve("arbs2").toFile();
        JXForge.l10nFromAndroid(res2, arb2, null);
        File res3 = dir.resolve("res3").toFile();
        JXForge.l10nToAndroid(arb2, res3, null);
        for (String folder : new String[] {"values", "values-pt", "values-pt-rBR"}) {
            assertEquals(new String(Files.readAllBytes(new File(res2, folder + "/strings.xml").toPath()), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(new File(res3, folder + "/strings.xml").toPath()), StandardCharsets.UTF_8), folder);
        }
    }

    @Test
    void aDefaultLocaleThatIsNotEnglishTravelsInAnXmlCommentOfTheValuesFolder() throws Exception {
        File l10n = dir.resolve("ptdefault").toFile();
        Files.createDirectories(l10n.toPath());
        Files.write(l10n.toPath().resolve("l10n.default"), "pt\n".getBytes(StandardCharsets.UTF_8));
        Files.write(l10n.toPath().resolve("app_pt.arb"), "{\"hello\": \"Oi\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(l10n.toPath().resolve("app_en.arb"), "{\"hello\": \"Hi\"}".getBytes(StandardCharsets.UTF_8));
        File res = dir.resolve("ptres").toFile();
        JXForge.l10nToAndroid(l10n, res, null);
        String values = new String(Files.readAllBytes(new File(res, "values/strings.xml").toPath()), StandardCharsets.UTF_8);
        assertTrue(values.contains("<!-- jxforge-default-locale: pt -->") && values.contains(">Oi<"), values);
        assertTrue(new String(Files.readAllBytes(new File(res, "values-en/strings.xml").toPath()), StandardCharsets.UTF_8).contains(">Hi<"));
        JxmlCatalog back = JxmlCatalog.load(res);
        assertEquals("pt", back.getDefaultTag());
        assertEquals(Arrays.asList("en", "pt"), back.tags());
        assertEquals("Oi", back.messages().format(new Locale("pt"), "hello"));
        assertEquals("Hi", back.messages().format(Locale.ENGLISH, "hello"));
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
