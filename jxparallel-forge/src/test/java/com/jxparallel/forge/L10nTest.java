package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.JXElement;

/** Translations: the catalog and its checks, the generated Strings class, and the interpreter against the compiler. */
class L10nTest {
    private static final File SAMPLES = new File("src/test/shared/samples-l10n");
    private static final String CTRL = "com.jxparallel.forge.L10nController";
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final Locale PT = new Locale("pt");

    @TempDir
    Path dir;
    private int builds;

    // ---- helpers ---------------------------------------------------------------------------------------

    private static JxmlCatalog sampleRaw() throws IOException {
        return JxmlCatalog.load(new File(SAMPLES, "l10n"));
    }

    /** The samples with English chosen, whatever the system's locale is. */
    private static JxmlCatalog sample() throws IOException {
        JxmlCatalog catalog = sampleRaw();
        catalog.messages().setLocale(Locale.ENGLISH);
        return catalog;
    }

    private static String sampleScreen(String name) throws IOException {
        return new String(Files.readAllBytes(new File(SAMPLES, "screens/" + name).toPath()), StandardCharsets.UTF_8);
    }

    /** Writes the files (name, text, name, text...) under {@code dir/l10n} and returns that directory. */
    private File l10n(String... files) throws IOException {
        File root = dir.resolve("l10n" + (builds++)).toFile();
        for (int i = 0; i < files.length; i += 2) {
            Path file = root.toPath().resolve(files[i]);
            Files.createDirectories(file.getParent());
            Files.write(file, files[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    private static final String EN = "{\"@@locale\": \"en\",\n"
            + "\"hello\": \"Hello {name}\", \"@hello\": {\"placeholders\": {\"name\": {\"type\": \"String\"}}},\n"
            + "\"items\": \"{count, plural, =0{none} one{# item} other{# items}}\", \"@items\": {\"placeholders\": {\"count\": {\"type\": \"int\"}}},\n"
            + "\"bye\": \"Bye\"}";

    private static JxmlTemplate read(String xml, JxmlCatalog catalog) {
        return JxmlTemplate.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "t.jxml", null, catalog);
    }

    /** Line 1 is the root; the body starts on line 2. */
    private static String screen(String body) {
        return "<Column controller=\"" + CTRL + "\">\n" + body + "\n</Column>";
    }

    private static JxmlException fails(String xml, JxmlCatalog catalog) {
        return assertThrows(JxmlException.class, () -> read(xml, catalog));
    }

    private static void assertMessage(JxmlException e, int line, String... words) {
        assertEquals(line, e.getLine(), e.getMessage());
        assertTrue(e.getMessage().startsWith("t.jxml:" + line + ": "), e.getMessage());
        for (String word : words) {
            assertTrue(e.getMessage().contains(word), "\"" + word + "\" is not in: " + e.getMessage());
        }
    }

    /** The generated Strings and the screens, compiled by javac into a class loader of their own. */
    private final class Built {
        final URLClassLoader loader;
        final Map<String, JxmlTemplate> templates = new LinkedHashMap<String, JxmlTemplate>();

        Built(JxmlCatalog catalog, Map<String, String> screens, boolean hoist) throws Exception {
            Path out = dir.resolve("b" + (builds++));
            Path gen = out.resolve("gen");
            Files.createDirectories(gen);
            List<String> sources = new ArrayList<String>();
            Files.write(gen.resolve("Strings.java"), catalog.generateStrings("gen").getBytes(StandardCharsets.UTF_8));
            sources.add(gen.resolve("Strings.java").toString());
            for (Map.Entry<String, String> s : screens.entrySet()) {
                JxmlTemplate template = read(s.getValue(), catalog);
                templates.put(s.getKey(), template);
                Files.write(gen.resolve(s.getKey() + ".java"),
                        JxmlCompiler.compile(template, new JxmlCompiler.Options("gen", s.getKey(), hoist)).getBytes(StandardCharsets.UTF_8));
                sources.add(gen.resolve(s.getKey() + ".java").toString());
            }
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            assertNotNull(javac, "tests need a JDK");
            String classpath = location(L10nController.class) + File.pathSeparator + location(JxmlView.class)
                    + File.pathSeparator + location(JXElement.class);
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            List<String> args = new ArrayList<String>(Arrays.asList("-encoding", "UTF-8", "-source", "8", "-target", "8", "-nowarn",
                    "-classpath", classpath, "-d", out.toString()));
            args.addAll(sources);
            if (javac.run(null, null, errors, args.toArray(new String[0])) != 0) {
                throw new AssertionError(new String(errors.toByteArray(), StandardCharsets.UTF_8) + "\n"
                        + new String(Files.readAllBytes(gen.resolve("Strings.java")), StandardCharsets.UTF_8));
            }
            loader = new URLClassLoader(new URL[] {out.toUri().toURL()}, getClass().getClassLoader());
            setLocale(Locale.ENGLISH);
        }

        @SuppressWarnings("unchecked")
        JxmlView<Object> view(String name) throws Exception {
            return (JxmlView<Object>) (JxmlView<?>) loader.loadClass("gen." + name).getDeclaredConstructor().newInstance();
        }

        Class<?> strings() throws Exception {
            return loader.loadClass("gen.Strings");
        }

        void setLocale(Locale locale) throws Exception {
            strings().getMethod("setLocale", Locale.class).invoke(null, locale);
        }

        Object call(String method, Class<?>[] types, Object... args) throws Exception {
            return strings().getMethod(method, types).invoke(null, args);
        }
    }

    private static String location(Class<?> type) {
        try {
            return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> labels(JXElement column) {
        List<String> out = new ArrayList<String>();
        for (JXElement child : column.getChildren()) {
            out.add(String.valueOf(child.getProps().get("value")));
        }
        return out;
    }

    // ---- the catalog -----------------------------------------------------------------------------------

    @Test
    void theSamplesHoldBothFormatsAndTheDefaultLocaleDefinesTheKeys() throws Exception {
        JxmlCatalog c = sample();
        assertEquals("en", c.getDefaultTag());
        assertEquals(Arrays.asList("en", "pt", "pt_BR"), c.tags());
        assertEquals("name: String", c.signature("welcome").describe());
        assertEquals("count: int", c.signature("itemCount").describe());
        assertEquals("gender: String", c.signature("reply").describe());
        assertEquals("amount: double", c.signature("price").describe());
        assertEquals("date: DateTime", c.signature("since").describe());
        assertEquals("arg1: String, arg2: int", c.signature("android_hello").describe());
        assertEquals("arg1: double", c.signature("android_price").describe());
        assertEquals("count: int", c.signature("android_files").describe());
        assertEquals("app_menu_save", c.signature("app.menu-save").method);
        assertEquals(Arrays.asList("en", "pt"), c.definedIn("android_hello"));
        assertNull(c.signature("nothing"));
        // pt_BR is a partial override: everything else is reported as untranslated, the rest is complete
        assertEquals(1, c.missing().size());
        assertEquals(c.keys().size() - 2, c.missing().get("pt_BR").size());
        assertFalse(c.missing().get("pt_BR").contains("greeting"));
        assertTrue(c.missingReport().startsWith("{\n  \"pt_BR\": [\"appTitle\""), c.missingReport());
        assertEquals(c.missing().get("pt_BR").size(), c.getWarnings().size());
        assertTrue(c.getWarnings().get(0).contains("app_pt_BR.arb: pt_BR has no translation for appTitle (defined at "), c.getWarnings().get(0));
    }

    @Test
    void theFallbackChainGoesFromTheRegionToTheLanguageToTheDefault() throws Exception {
        JxmlMessages m = sample().messages();
        assertEquals("Oi", m.format(PT_BR, "greeting"));
        assertEquals("Bem-vindo, Ada!", m.format(PT_BR, "welcome", "Ada")); // not in pt_BR: pt
        assertEquals("Olá", m.format(new Locale("pt", "PT"), "greeting")); // no pt_PT: pt
        assertEquals("Olá", m.format(PT, "greeting"));
        assertEquals("Hello", m.format(Locale.FRENCH, "greeting")); // no fr: the default
        assertEquals("Hello", m.format(new Locale("en", "GB"), "greeting"));
        assertEquals("Hello", m.format(Locale.ROOT, "greeting"));
        assertEquals("Nenhum item", m.format(PT_BR, "itemCount", 0));
        assertEquals("1 item", m.format(PT_BR, "itemCount", 1));
        assertEquals("2 itens", m.format(PT_BR, "itemCount", 2));
        assertEquals("Um item", m.format(PT, "itemCount", 1));
        assertEquals("One item", m.format(Locale.ENGLISH, "itemCount", 1));
        assertEquals("No items", m.format(Locale.ENGLISH, "itemCount", 0));
        assertEquals("7 items", m.format(Locale.ENGLISH, "itemCount", 7));
        assertEquals("Ela respondeu", m.format(PT, "reply", "female"));
        assertEquals("Responderam", m.format(PT, "reply", "other-thing"));
        assertEquals("1 unread message", m.format(Locale.ENGLISH, "unread", 1));
        assertEquals("2 mensagens não lidas", m.format(PT, "unread", 2));
        assertEquals("Don't use { or } here, it's \"odd\"", m.format(Locale.ENGLISH, "quoted"));
        assertEquals("Olá Ada, você tem 3 mensagens", m.format(PT_BR, "android_hello", "Ada", 3));
        assertEquals("Hello Ada, you have 3 messages", m.format(Locale.ENGLISH, "android_hello", "Ada", 3));
        assertEquals("Don't panic & carry on, 100%", m.format(Locale.ENGLISH, "android_apostrophe"));
        assertEquals("1 file", m.format(Locale.ENGLISH, "android_files", 1));
        assertEquals("3 arquivos", m.format(PT, "android_files", 3));
        assertEquals(Arrays.asList("en", "pt", "pt_BR"), localeTags(m));
        assertEquals(new Locale("pt", "BR"), m.match(PT_BR));
        assertEquals(PT, m.match(new Locale("pt", "PT")));
        assertEquals(Locale.ENGLISH, m.match(Locale.JAPANESE));
    }

    private static List<String> localeTags(JxmlMessages m) {
        List<String> out = new ArrayList<String>();
        for (Locale l : m.supported()) {
            out.add(l.getCountry().isEmpty() ? l.getLanguage() : l.getLanguage() + "_" + l.getCountry());
        }
        return out;
    }

    @Test
    void theLocaleStartsAsTheBestMatchForTheSystemOneAndListenersHearChanges() throws Exception {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("pt", "BR"));
            JxmlMessages m = sampleRaw().messages();
            assertEquals(PT_BR, m.locale());
            Locale.setDefault(Locale.JAPANESE);
            assertEquals(Locale.ENGLISH, sampleRaw().messages().locale());
            AtomicInteger heard = new AtomicInteger();
            Runnable listener = heard::incrementAndGet;
            m.onLocaleChange(listener);
            m.setLocale(PT_BR);
            assertEquals(0, heard.get());
            m.setLocale(PT);
            assertEquals(1, heard.get());
            assertEquals("Olá", m.format("greeting"));
            m.offLocaleChange(listener);
            m.setLocale(Locale.ENGLISH);
            assertEquals(1, heard.get());
            assertThrows(NullPointerException.class, () -> m.setLocale(null));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void thedefaultLocaleCanBeNamedInL10nDefaultOrBeAndroidValues() throws Exception {
        JxmlCatalog pt = JxmlCatalog.load(l10n("l10n.default", "pt_BR\n", "app_pt_BR.arb", "{\"a\": \"A\"}", "app_en.arb", "{\"a\": \"AA\"}"));
        assertEquals("pt_BR", pt.getDefaultTag());
        assertEquals("A", pt.messages().format(Locale.JAPANESE, "a"));
        JxmlCatalog android = JxmlCatalog.load(l10n("values/strings.xml", "<resources><string name=\"a\">A</string></resources>",
                "values-pt-rBR/strings.xml", "<resources><string name=\"a\">B</string></resources>"));
        assertEquals("en", android.getDefaultTag());
        assertEquals(Arrays.asList("en", "pt_BR"), android.tags());
        assertEquals("B", android.messages().format(PT_BR, "a"));
        JxmlException none = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_pt.arb", "{\"a\": \"A\"}")));
        assertTrue(none.getMessage().contains("default locale en has no file") && none.getMessage().contains("l10n.default"), none.getMessage());
    }

    // ---- the checks ------------------------------------------------------------------------------------

    @Test
    void aMissingKeyAndAnExtraKeyAreWarningsAndMissingOnesAreReported() throws Exception {
        JxmlCatalog c = JxmlCatalog.load(l10n("app_en.arb", EN, "app_pt.arb", "{\"@@locale\": \"pt\",\n\"hello\": \"Olá {name}\",\n\"extra\": \"x\"}"));
        assertEquals(3, c.getWarnings().size(), c.getWarnings().toString());
        String all = String.join("\n", c.getWarnings());
        assertTrue(all.contains("app_pt.arb:3: pt has the key extra, which the default locale en does not define"), all);
        assertTrue(all.contains("pt has no translation for items (defined at ") && all.contains("app_en.arb:3)"), all);
        assertTrue(all.contains("pt has no translation for bye"), all);
        assertEquals(Arrays.asList("items", "bye"), c.missing().get("pt"));
        assertEquals("{\n  \"pt\": [\"items\", \"bye\"]\n}\n", c.missingReport());
    }

    @Test
    void aCompleteTranslationHasNoWarningsAndNoReport() throws Exception {
        JxmlCatalog c = JxmlCatalog.load(l10n("app_en.arb", EN, "app_pt.arb", "{\"hello\": \"Olá {name}\", \"items\": \"{count, plural, other{# itens}}\", \"bye\": \"Tchau\"}"));
        assertTrue(c.getWarnings().isEmpty(), c.getWarnings().toString());
        assertNull(c.missingReport());
        assertTrue(c.missing().isEmpty());
    }

    @Test
    void strictTurnsTheWarningsIntoErrors() throws Exception {
        File files = l10n("app_en.arb", EN, "app_pt.arb", "{\"hello\": \"Olá {name}\"}");
        JxmlCatalog.load(files, false);
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlCatalog.load(files, true));
        assertTrue(e.getMessage().contains("pt has no translation for items") && e.getMessage().contains("pt has no translation for bye"), e.getMessage());
        assertTrue(e.getSource().endsWith("app_pt.arb"), e.getSource());
    }

    @Test
    void aPlaceholderThatDiffersInNameCountOrTypeIsAnError() throws Exception {
        JxmlException name = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", EN,
                "app_pt.arb", "{\n\"hello\": \"Olá {nome}\"}")));
        assertTrue(name.getMessage().contains("app_pt.arb:2:") && name.getMessage().contains("[nome]") && name.getMessage().contains("[name]"), name.getMessage());
        assertEquals(2, name.getLine());
        JxmlException count = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", EN,
                "app_pt.arb", "{\"hello\": \"Olá {name} {more}\"}")));
        assertTrue(count.getMessage().contains("placeholders [name, more]"), count.getMessage());
        JxmlException type = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", EN,
                "app_pt.arb", "{\"items\": \"{count, plural, other{#}}\", \"@items\": {\"placeholders\": {\"count\": {\"type\": \"String\"}}}}")));
        assertTrue(type.getMessage().contains("count is String in pt but int in en"), type.getMessage());
        JxmlException plural = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", EN,
                "app_pt.arb", "{\"hello\": \"{name, plural, other{x}}\"}")));
        assertTrue(plural.getMessage().contains("name is a plural in pt but String in en"), plural.getMessage());
    }

    @Test
    void aPluralWithoutOtherAndOtherBadFilesAreErrorsWithFileAndLine() throws Exception {
        JxmlException other = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb",
                "{\n\"ok\": \"fine\",\n\"items\": \"{count, plural, one{x}}\"}")));
        assertEquals(3, other.getLine());
        assertTrue(other.getMessage().contains("app_en.arb:3:") && other.getMessage().contains("items") && other.getMessage().contains("'other'"), other.getMessage());
        JxmlException json = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", "{\n\"a\": \"x\",,\n}")));
        assertTrue(json.getMessage().contains("app_en.arb:2:") && json.getMessage().contains("bad JSON"), json.getMessage());
        JxmlException twice = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", "{\"a\": \"x\"}",
                "values/strings.xml", "<resources>\n<string name=\"a\">y</string></resources>")));
        assertTrue(twice.getMessage().contains("the key a is defined twice for en"), twice.getMessage());
        assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", "{\"1bad\": \"x\"}")));
        assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", "{\"a\": 3}")));
        assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", "{\"a\": \"x\", \"@a\": {\"placeholders\": {\"n\": {\"type\": \"Float\"}}}}")));
        JxmlException locale = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", "{\"@@locale\": \"pt\", \"a\": \"x\"}")));
        assertTrue(locale.getMessage().contains("@@locale pt is not the locale en"), locale.getMessage());
        JxmlException unknown = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("strings.arb", "{\"a\": \"x\"}")));
        assertTrue(unknown.getMessage().contains("the locale is unknown"), unknown.getMessage());
    }

    @Test
    void severalErrorsAreReportedTogether() throws Exception {
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlCatalog.load(l10n("app_en.arb", EN,
                "app_pt.arb", "{\"hello\": \"{nome}\", \"items\": \"{n}\"}")));
        assertEquals(2, e.getMessage().split("\n").length, e.getMessage());
    }

    // ---- the generated class ---------------------------------------------------------------------------

    @Test
    void theGeneratedStringsCompilesAndAnswersForEachLocaleAndCount() throws Exception {
        Built b = new Built(sample(), new LinkedHashMap<String, String>(), true);
        assertEquals("Hello", b.call("greeting", new Class<?>[0]));
        assertEquals("Welcome, Ada!", b.call("welcome", new Class<?>[] {String.class}, "Ada"));
        assertEquals("Total: 9.5", b.call("price", new Class<?>[] {double.class}, 9.5));
        assertEquals("No items", b.call("itemCount", new Class<?>[] {int.class}, 0));
        assertEquals("One item", b.call("itemCount", new Class<?>[] {int.class}, 1));
        assertEquals("4 items", b.call("itemCount", new Class<?>[] {int.class}, 4));
        assertEquals("Save", b.call("app_menu_save", new Class<?>[0]));
        assertEquals("Hello Bo, you have 2 messages", b.call("android_hello", new Class<?>[] {String.class, int.class}, "Bo", 2));
        assertTrue(((String) b.call("since", new Class<?>[] {java.util.Date.class}, new java.util.Date(0L))).startsWith("Since "));
        assertEquals("[en, pt, pt_BR]", b.strings().getMethod("supported").invoke(null).toString());
        b.setLocale(PT_BR);
        assertEquals(PT_BR, b.strings().getMethod("locale").invoke(null));
        assertEquals("Oi", b.call("greeting", new Class<?>[0]));
        assertEquals("Bem-vindo, Ada!", b.call("welcome", new Class<?>[] {String.class}, "Ada"));
        assertEquals("1 item", b.call("itemCount", new Class<?>[] {int.class}, 1));
        assertEquals("5 itens", b.call("itemCount", new Class<?>[] {int.class}, 5));
        b.setLocale(PT);
        assertEquals("Um item", b.call("itemCount", new Class<?>[] {int.class}, 1));
        b.setLocale(Locale.GERMAN);
        assertEquals("Hello", b.call("greeting", new Class<?>[0]));
        List<String> heard = new ArrayList<String>();
        Runnable listener = () -> heard.add("changed");
        b.strings().getMethod("onLocaleChange", Runnable.class).invoke(null, listener);
        b.setLocale(PT);
        b.setLocale(PT);
        assertEquals(1, heard.size());
        b.strings().getMethod("offLocaleChange", Runnable.class).invoke(null, listener);
        b.setLocale(Locale.ENGLISH);
        assertEquals(1, heard.size());
    }

    @Test
    void theGeneratedTextIsTheSameForTheSameFiles() throws Exception {
        assertEquals(sample().generateStrings("a.b"), sample().generateStrings("a.b"));
        assertTrue(sample().generateStrings("a.b").startsWith("// Generated by JXForge from the l10n files in l10n. Do not edit.\npackage a.b;\n"));
        assertFalse(sample().generateStrings("").contains("package "));
    }

    @Test
    void aBigCatalogStaysUnderTheMethodSizeLimit() throws Exception {
        StringBuilder arb = new StringBuilder("{");
        for (int i = 0; i < 3000; i++) {
            arb.append(i == 0 ? "" : ",").append("\"k").append(i).append("\": \"text ").append(i).append(" {n}\",\"@k").append(i)
                    .append("\": {\"placeholders\": {\"n\": {\"type\": \"int\"}}}");
        }
        Built b = new Built(JxmlCatalog.load(l10n("app_en.arb", arb.append("}").toString())), new LinkedHashMap<String, String>(), true);
        assertEquals("text 2999 5", b.call("k2999", new Class<?>[] {int.class}, 5));
    }

    // ---- the interpreter and the compiler agree --------------------------------------------------------

    @Test
    void theInterpreterAndTheCompilerBuildTheSameTreeForEveryLocaleAndCount() throws Exception {
        JxmlCatalog catalog = sample();
        Map<String, String> screens = new LinkedHashMap<String, String>();
        screens.put("Welcome", sampleScreen("welcome.jxml"));
        Built hoisted = new Built(catalog, screens, true);
        Built plain = new Built(catalog, screens, false);
        JxmlTemplate interpreter = hoisted.templates.get("Welcome");
        for (Locale locale : Arrays.asList(Locale.ENGLISH, PT, PT_BR, new Locale("pt", "PT"), Locale.JAPANESE)) {
            for (int count : new int[] {0, 1, 2, 5}) {
                for (String gender : new String[] {"male", "female", "x"}) {
                    catalog.messages().setLocale(locale);
                    hoisted.setLocale(locale);
                    plain.setLocale(locale);
                    L10nController c = new L10nController();
                    c.count = count;
                    c.gender = gender;
                    JXElement expected = interpreter.build(c);
                    String where = locale + " " + count + " " + gender;
                    assertNull(JxmlTrees.diff(expected, hoisted.view("Welcome").build(c)), "hoisted " + where);
                    assertNull(JxmlTrees.diff(expected, plain.view("Welcome").build(c)), "plain " + where);
                }
            }
        }
    }

    @Test
    void theSampleScreenShowsTheTextsOfTheLocale() throws Exception {
        JxmlCatalog catalog = sample();
        JxmlTemplate template = read(sampleScreen("welcome.jxml"), catalog);
        L10nController c = new L10nController();
        c.count = 1;
        catalog.messages().setLocale(Locale.ENGLISH);
        JXElement en = template.build(c);
        List<String> texts = labels(en);
        assertEquals("Hello", texts.get(0));
        assertEquals("Welcome, Ada!", texts.get(1));
        assertEquals("Welcome, Ada!", texts.get(2));
        assertEquals("One item", texts.get(3));
        assertEquals("3 items", texts.get(4));
        assertEquals("1 unread message", texts.get(5));
        assertEquals("She replied", texts.get(6));
        assertEquals("Total: 9.5", texts.get(7));
        assertEquals("Total: 9.5", texts.get(8));
        assertEquals("Don't use { or } here, it's \"odd\"", texts.get(10));
        assertEquals("Save", texts.get(11));
        assertEquals("Hello Ada, you have 1 messages", texts.get(12));
        assertEquals("Don't panic & carry on, 100%", texts.get(13));
        assertEquals("Cost: 9.5", texts.get(14));
        assertEquals("1 file", texts.get(15));
        assertEquals("%{greeting}", texts.get(16));
        assertEquals("no translation here", texts.get(17));
        JXElement combo = en.getChildren().get(18);
        assertEquals(Arrays.asList("Hello", "plain", "Welcome, x!"), Arrays.asList((String[]) combo.getProps().get("options")));
        assertEquals("My App", combo.getProps().get("value"));
        catalog.messages().setLocale(PT_BR);
        List<String> pt = labels(template.build(c));
        assertEquals("Oi", pt.get(0));
        assertEquals("Bem-vindo, Ada!", pt.get(1));
        assertEquals("1 item", pt.get(3));
        assertEquals("Meu App", template.build(c).getChildren().get(18).getProps().get("value"));
    }

    @Test
    void aTranslatedTextIsNotFrozenByHoistingButItsConstantSiblingsAre() throws Exception {
        JxmlCatalog catalog = sample();
        Map<String, String> screens = new LinkedHashMap<String, String>();
        screens.put("Frozen", "<Column>\n<Label text=\"%{greeting}\"/>\n<Label text=\"always\"/>\n<Row><Label text=\"%{welcome('Bo')}\"/></Row>\n</Column>");
        Built b = new Built(catalog, screens, true);
        JxmlView<Object> view = b.view("Frozen");
        JXElement en = view.build(null);
        assertEquals("Hello", en.getChildren().get(0).getProps().get("value"));
        b.setLocale(PT);
        JXElement pt = view.build(null); // no controller is needed: the arguments are literals
        assertEquals("Olá", pt.getChildren().get(0).getProps().get("value"));
        assertEquals("Bem-vindo, Bo!", pt.getChildren().get(2).getChildren().get(0).getProps().get("value"));
        assertSame(en.getChildren().get(1), pt.getChildren().get(1));
        assertFalse(read(screens.get("Frozen"), catalog).root.constant());
        // and the interpreter agrees
        catalog.messages().setLocale(PT);
        assertNull(JxmlTrees.diff(pt, b.templates.get("Frozen").build(null)));
    }

    // ---- errors in screens -----------------------------------------------------------------------------

    @Test
    void aMissingKeyNamesTheKeyTheLocaleAndTheFile() throws Exception {
        JxmlException e = fails(screen("<Label text=\"%{farewell}\"/>"), sample());
        assertMessage(e, 2, "farewell", "default locale en", "app_en.arb", "keys: [");
    }

    @Test
    void aWrongNumberOfArgumentsNamesTheKeyAndWhereItIsDefined() throws Exception {
        JxmlException none = fails(screen("<Label text=\"%{welcome}\"/>"), sample());
        assertMessage(none, 2, "welcome takes 1 argument(s), 0 given", "welcome(name: String)", "app_en.arb:", "[en, pt]");
        JxmlException two = fails(screen("<Label text=\"%{welcome('a', 'b')}\"/>"), sample());
        assertMessage(two, 2, "2 given");
        JxmlException extra = fails(screen("<Label text=\"%{greeting('a')}\"/>"), sample());
        assertMessage(extra, 2, "greeting takes 0 argument(s)");
    }

    @Test
    void anArgumentOfTheWrongKindForTheDeclaredTypeIsAnErrorOnTheLineOfTheAttribute() throws Exception {
        JxmlException text = fails(screen("<Label text=\"x\"/>\n<Label text=\"%{itemCount('three')}\"/>"), sample());
        assertMessage(text, 3, "itemCount", "argument 1 (count: int)", "the text 'three'", "app_en.arb");
        JxmlException number = fails(screen("<Label text=\"%{welcome(3)}\"/>"), sample());
        assertMessage(number, 2, "argument 1 (name: String)", "the number 3");
        JxmlException fraction = fails(screen("<Label text=\"%{itemCount(1.5)}\"/>"), sample());
        assertMessage(fraction, 2, "the number 1.5");
        JxmlException date = fails(screen("<Label text=\"%{since(5)}\"/>"), sample());
        assertMessage(date, 2, "date: DateTime");
        JxmlException junk = fails(screen("<Label text=\"%{welcome(name)}\"/>"), sample());
        assertMessage(junk, 2, "neither ${binding}, 'text' nor a number");
        assertMessage(fails(screen("<Label text=\"%{price('x')}\"/>"), sample()), 2, "amount: double");
    }

    @Test
    void aBadBindingOrSyntaxInAnArgumentIsAnError() throws Exception {
        assertMessage(fails(screen("<Label text=\"%{welcome(${a b})}\"/>"), sample()), 2, "bad binding");
        assertMessage(fails(screen("<Label text=\"%{welcome('a)}\"/>"), sample()), 2, "unbalanced");
        assertMessage(fails(screen("<Label text=\"%{welcome(${user.name}\"/>"), sample()), 2, "bad translation");
        assertMessage(fails(screen("<Label text=\"%{}\"/>"), sample()), 2, "bad translation");
        assertMessage(fails(screen("<Label text=\"%{Hello world}\"/>"), sample()), 2, "bad translation");
        assertMessage(fails(screen("<Label text=\"%{greeting} and more %{greeting}\"/>"), sample()), 2, "bad translation");
    }

    @Test
    void aTranslationOnAnAttributeThatIsNotBindableTextIsAnError() throws Exception {
        assertMessage(fails(screen("<Row gap=\"%{greeting}\"/>"), sample()), 2, "gap", "cannot be a translation");
        assertMessage(fails(screen("<Label text=\"x\"/>\n<Row><Button text=\"a\" onAction=\"%{greeting}\"/></Row>"), sample()), 3);
        assertMessage(fails(screen("<Tabs><Tab title=\"%{greeting}\"><Label text=\"x\"/></Tab></Tabs>"), sample()), 2, "title", "cannot be a translation");
    }

    @Test
    void aTranslationWithoutACatalogIsAnErrorAndTheBackslashEscapeIsALiteral() throws Exception {
        JxmlException none = assertThrows(JxmlException.class, () -> JxmlTemplate.read(
                new ByteArrayInputStream(screen("<Label text=\"%{greeting}\"/>").getBytes(StandardCharsets.UTF_8)), "t.jxml"));
        assertMessage(none, 2, "needs the translation files");
        JxmlTemplate literal = JxmlTemplate.read(new ByteArrayInputStream(screen("<Label text=\"\\%{greeting}\"/>").getBytes(StandardCharsets.UTF_8)), "t.jxml");
        assertEquals("%{greeting}", literal.build(new L10nController()).getChildren().get(0).getProps().get("value"));
    }

    @Test
    void aTranslationInsideAMemoLoopIsRefusedBecauseItsTextChangesWithTheLocale() throws Exception {
        JxmlException e = fails(screen("<For items=\"${count}\" var=\"n\" memo=\"true\"><Label text=\"%{greeting}\"/></For>"), sample());
        assertEquals(2, e.getLine());
        assertTrue(e.getMessage().contains("memo") && e.getMessage().contains("translation"), e.getMessage());
    }

    @Test
    void aBoundArgumentThatIsNotOfTheDeclaredTypeFailsWhenTheTreeIsBuilt() throws Exception {
        JxmlCatalog catalog = sample();
        JxmlTemplate template = read(screen("<Label text=\"%{itemCount(${number})}\"/>"), catalog);
        L10nController c = new L10nController();
        assertEquals("12 items", template.build(c).getChildren().get(0).getProps().get("value"));
        c.number = "many";
        JxmlException e = assertThrows(JxmlException.class, () -> template.build(c));
        assertMessage(e, 2, "%{itemCount}", "count: int", "itemCount is defined at");
    }

    @Test
    void forLoopVariablesAreArgumentsToo() throws Exception {
        JxmlCatalog catalog = sample();
        String xml = "<Column controller=\"com.jxparallel.forge.LanguageController\">\n"
                + "<For items=\"${orders}\" var=\"o\" type=\"com.jxparallel.forge.LanguageController.Order\">"
                + "<Label text=\"%{welcome(${o.name})}\"/></For>\n</Column>";
        Map<String, String> screens = new LinkedHashMap<String, String>();
        screens.put("Loop", xml);
        Built b = new Built(catalog, screens, true);
        LanguageController c = new LanguageController();
        JXElement compiled = b.view("Loop").build(c);
        assertNull(JxmlTrees.diff(b.templates.get("Loop").build(c), compiled));
        assertEquals(Arrays.asList("Welcome, A!", "Welcome, B!", "Welcome, C!"), labels(compiled));
    }

    @Test
    void aScreenWithoutAControllerCompilesWhenItsArgumentsAreLiterals() throws Exception {
        JxmlCatalog catalog = sample();
        Map<String, String> screens = new LinkedHashMap<String, String>();
        screens.put("NoController", "<Label text=\"%{welcome('x')}\"/>");
        Built b = new Built(catalog, screens, true);
        assertEquals("Welcome, x!", b.view("NoController").build(null).getProps().get("value"));
        JxmlException e = assertThrows(JxmlException.class, () -> JxmlCompiler.compile(read("<Label text=\"%{welcome(${a})}\"/>", catalog),
                new JxmlCompiler.Options("gen", "X", true)));
        assertTrue(e.getMessage().contains("controller"), e.getMessage());
    }

    // ---- FXML ------------------------------------------------------------------------------------------

    @Test
    void anFxmlResourceKeyBecomesATranslation() throws Exception {
        String head = "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\">\n";
        JxmlParser.Node root = FxmlTranslator.translate(new ByteArrayInputStream((head
                + "<Label text=\"%app.title\"/><Label text=\"\\%raw\"/><Label text=\"\\%{x}\"/>\n</VBox>").getBytes(StandardCharsets.UTF_8)), "t.fxml", null);
        assertEquals("%{app.title}", root.children.get(0).attrs.get("text"));
        assertEquals("%raw", root.children.get(1).attrs.get("text"));
        assertEquals("\\%{x}", root.children.get(2).attrs.get("text"));
    }

    @Test
    void anFxmlScreenWithBundleKeysIsGeneratedAndInterpretedLikeJxml() throws Exception {
        JxmlCatalog catalog = sample();
        String fxml = sampleScreen("bundle.fxml");
        JxmlTemplate template = JxmlTemplate.read(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)), "bundle.fxml", null, catalog);
        catalog.messages().setLocale(PT);
        assertEquals(Arrays.asList("Olá", "Salvar", "%greeting"), labels(template.build(new L10nController())).subList(0, 3));
        String java = JxmlCompiler.compile(template, new JxmlCompiler.Options("gen", "BundleView", true));
        assertTrue(java.contains("Strings.greeting()") && java.contains("Strings.app_menu_save()"), java);
    }

    // ---- scaffolding -----------------------------------------------------------------------------------

    @Test
    void l10nInitCreatesTemplatesThatLoadAndLeavesExistingFilesAlone() throws Exception {
        File target = dir.resolve("init").toFile();
        List<File> created = JXForge.l10nInit(target, Arrays.asList("en", "pt-br", "es"));
        assertEquals(3, created.size());
        assertTrue(new File(target, "app_pt_BR.arb").isFile());
        JxmlCatalog c = JxmlCatalog.load(target);
        assertEquals(Arrays.asList("en", "es", "pt_BR"), c.tags());
        assertEquals("Hello World!", c.messages().format(Locale.ENGLISH, "helloWorld"));
        Files.write(new File(target, "app_en.arb").toPath(), "{\"mine\": \"x\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(JXForge.l10nInit(target, Arrays.asList("en", "es")).isEmpty());
        assertTrue(new String(Files.readAllBytes(new File(target, "app_en.arb").toPath()), StandardCharsets.UTF_8).contains("mine"));
        File pt = dir.resolve("initpt").toFile();
        JXForge.l10nInit(pt, Arrays.asList("pt", "en"));
        assertEquals("pt", JxmlCatalog.load(pt).getDefaultTag());
    }

    @Test
    void l10nFromAndroidConvertsStringsAndPluralsToArbFilesThatMeanTheSame() throws Exception {
        File res = new File(SAMPLES, "l10n/res");
        File target = dir.resolve("converted").toFile();
        List<String> warnings = new ArrayList<String>();
        List<File> written = JXForge.l10nFromAndroid(res, target, warnings);
        assertEquals(2, written.size());
        JxmlCatalog original = JxmlCatalog.load(res);
        JxmlCatalog converted = JxmlCatalog.load(target);
        assertEquals(original.keys(), converted.keys());
        for (String key : original.keys()) {
            assertEquals(original.signature(key).describe(), converted.signature(key).describe(), key);
            for (Locale locale : Arrays.asList(Locale.ENGLISH, PT)) {
                Object[] args = new Object[original.signature(key).names.size()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = "int".equals(original.signature(key).types.get(i)) ? (Object) 3 : "double".equals(original.signature(key).types.get(i)) ? (Object) 2.5 : "x";
                }
                assertEquals(original.messages().format(locale, key, args), converted.messages().format(locale, key, args), key + " " + locale);
            }
        }
        String arb = new String(Files.readAllBytes(new File(target, "app_en.arb").toPath()), StandardCharsets.UTF_8);
        assertTrue(arb.contains("\"@@locale\": \"en\"") && arb.contains("{count, plural, one{{count} file} other{{count} files}}"), arb);
    }

    // ---- the samples cover the syntax ------------------------------------------------------------------

    private static String allSamples() throws IOException {
        StringBuilder all = new StringBuilder();
        List<File> pending = new ArrayList<File>(Arrays.asList(SAMPLES));
        while (!pending.isEmpty()) {
            File f = pending.remove(pending.size() - 1);
            if (f.isDirectory()) {
                pending.addAll(Arrays.asList(f.listFiles()));
            } else {
                all.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return all.toString();
    }

    @Test
    void everyAcceptedSyntaxAppearsInASample() throws Exception {
        String all = allSamples();
        String[] syntax = {
            "\"@@locale\"", "\"placeholders\"", "\"type\": \"String\"", "\"type\": \"int\"", "\"type\": \"double\"", "\"type\": \"DateTime\"",
            "\"description\"", ", plural, =0{", "=1{", "other{", "one{", ", select, male{", "{count} items", "# unread", "'{'", "it''s",
            "\"@@locale\": \"pt_BR\"", "<string name=", "<plurals name=", "quantity=\"one\"", "quantity=\"other\"", "%1$s", "%2$d", "%d ", "%.2f", "%%",
            "\\'", "&amp;", "%{greeting}", "%{welcome(${", "%{welcome('", "%{itemCount(3)}", "%{price(9.5)}", "\\%{greeting}", "items=\"%{",
            "%greeting", "\\%greeting", "app.menu-save"};
        for (String s : syntax) {
            assertTrue(all.contains(s), "no sample shows " + s);
        }
        for (String file : new String[] {"l10n/app_en.arb", "l10n/app_pt_BR.arb", "l10n/res/values/strings.xml",
            "l10n/res/values-pt/strings.xml", "screens/welcome.jxml", "screens/bundle.fxml"}) {
            assertTrue(new File(SAMPLES, file).isFile(), file);
        }
    }
}
