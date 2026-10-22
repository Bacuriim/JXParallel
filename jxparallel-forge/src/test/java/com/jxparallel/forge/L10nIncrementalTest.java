package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link JXForge#compileDirectory} with translation files: Strings.java, the checks, and what is regenerated when. */
class L10nIncrementalTest {
    private static final String CTRL = "com.jxparallel.forge.L10nController";
    private static final long PAST = 946684800000L; // 2000-01-01

    @TempDir
    Path dir;
    File src;
    File out;
    File l10n;

    @BeforeEach
    void layout() throws IOException {
        src = dir.resolve("src").toFile();
        out = dir.resolve("out").toFile();
        l10n = dir.resolve("l10n").toFile();
        screen("a.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"%{hello(${user.name})}\"/></Column>");
        screen("b.jxml", "<Column controller=\"" + CTRL + "\"><Include src=\"parts/p.jxml\"/></Column>");
        screen("parts/p.jxml", "<Label text=\"%{bye}\"/>");
        screen("c.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"plain\"/></Column>");
        text("app_en.arb", en("Hello {name}", "Bye"));
        text("app_pt.arb", "{\"hello\": \"Olá {name}\", \"bye\": \"Tchau\"}");
    }

    private static String en(String hello, String bye) {
        return "{\"@@locale\": \"en\", \"hello\": \"" + hello + "\", \"@hello\": {\"placeholders\": {\"name\": {\"type\": \"String\"}}},"
                + " \"bye\": \"" + bye + "\"}";
    }

    private void screen(String name, String text) throws IOException {
        write(src, name, text);
    }

    private void text(String name, String text) throws IOException {
        write(l10n, name, text);
    }

    private static void write(File root, String name, String text) throws IOException {
        Path file = root.toPath().resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private JXForge.Result run() throws IOException {
        return JXForge.compileDirectory(src, out, "gen", "View", true, l10n);
    }

    private static List<String> names(List<File> files) {
        List<String> names = new ArrayList<String>();
        for (File f : files) {
            names.add(f.getName());
        }
        Collections.sort(names);
        return names;
    }

    private File generated(String cls) {
        return new File(out, "gen/" + cls + ".java");
    }

    private void age() {
        for (String cls : Arrays.asList("AView", "BView", "CView", "Strings")) {
            if (generated(cls).isFile()) {
                assertTrue(generated(cls).setLastModified(PAST));
            }
        }
    }

    private String manifest() throws IOException {
        return new String(Files.readAllBytes(new File(out, JXForge.MANIFEST).toPath()), StandardCharsets.UTF_8);
    }

    private String api(String screen) throws IOException {
        String text = manifest();
        int at = text.indexOf("screen\t" + screen + "\n");
        String block = text.substring(at, text.indexOf("end\n", at));
        int api = block.indexOf("api\t");
        return api < 0 ? null : block.substring(api + 4, block.indexOf('\n', api));
    }

    @Test
    void theRunWritesStringsAndTheScreensThatCallIt() throws Exception {
        JXForge.Result first = run();
        assertEquals(Arrays.asList("AView.java", "BView.java", "CView.java", "Strings.java"), names(first.written));
        String a = new String(Files.readAllBytes(generated("AView").toPath()), StandardCharsets.UTF_8);
        assertTrue(a.contains("Strings.hello(JxmlValues.asString(c.getUser().getName()))"), a);
        String strings = new String(Files.readAllBytes(generated("Strings").toPath()), StandardCharsets.UTF_8);
        assertTrue(strings.contains("public static String hello(String name)") && strings.contains("public static String bye()"), strings);
        assertFalse(new File(out, JXForge.MISSING_REPORT).exists(), "nothing is untranslated");
        assertEquals(null, api("c.jxml"), "a screen without translations has no dependency on them");
        assertTrue(api("a.jxml") != null && api("a.jxml").equals(api("b.jxml")));
        age();
        long manifestTime = new File(out, JXForge.MANIFEST).lastModified();
        JXForge.Result second = run();
        assertTrue(second.written.isEmpty(), second.written.toString());
        assertEquals(4, second.upToDate.size());
        for (String cls : Arrays.asList("AView", "BView", "CView", "Strings")) {
            assertEquals(PAST, generated(cls).lastModified(), cls + " keeps its time");
        }
        assertEquals(manifestTime, new File(out, JXForge.MANIFEST).lastModified());
    }

    @Test
    void changingAScreenWithoutTranslationsLeavesStringsAlone() throws Exception {
        run();
        age();
        screen("c.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"changed\"/></Column>");
        JXForge.Result r = run();
        assertEquals(Arrays.asList("CView.java"), names(r.written));
        assertEquals(PAST, generated("Strings").lastModified());
        assertEquals(PAST, generated("AView").lastModified());
    }

    @Test
    void changingTheTextOfATranslationRegeneratesStringsAndNoScreen() throws Exception {
        run();
        age();
        String api = api("a.jxml");
        text("app_en.arb", en("Hi {name}!", "Bye"));
        JXForge.Result r = run();
        assertEquals(Arrays.asList("Strings.java"), names(r.written));
        assertEquals(PAST, generated("AView").lastModified());
        assertEquals(api, api("a.jxml"), "the screens did not even have to be compiled again");
        assertTrue(new String(Files.readAllBytes(generated("Strings").toPath()), StandardCharsets.UTF_8).contains("Hi {name}!"));
        age();
        text("app_pt.arb", "{\"hello\": \"Oi {name}\", \"bye\": \"Tchau\"}");
        assertEquals(Arrays.asList("Strings.java"), names(run().written));
    }

    @Test
    void aNewKeyOrAChangedSignatureRegeneratesTheScreensThatUseTranslations() throws Exception {
        run();
        age();
        String before = api("a.jxml");
        text("app_en.arb", en("Hello {name}", "Bye").replace("\"bye\": \"Bye\"", "\"bye\": \"Bye\", \"more\": \"More\""));
        text("app_pt.arb", "{\"hello\": \"Olá {name}\", \"bye\": \"Tchau\", \"more\": \"Mais\"}");
        JXForge.Result added = run();
        assertEquals(Arrays.asList("Strings.java"), names(added.written)); // the screens compiled again to the same text
        assertFalse(before.equals(api("a.jxml")));
        assertEquals(api("a.jxml"), api("b.jxml"));
        assertEquals(null, api("c.jxml"));
        assertEquals(PAST, generated("AView").lastModified());
        // a signature change: hello takes two arguments now, so the screen that passes one no longer builds
        age();
        text("app_en.arb", "{\"hello\": \"{n} {m}\", \"@hello\": {\"placeholders\": {\"n\": {\"type\": \"int\"}, \"m\": {\"type\": \"int\"}}}, \"bye\": \"Bye\", \"more\": \"More\"}");
        text("app_pt.arb", "{\"hello\": \"{n} {m}\", \"bye\": \"Tchau\", \"more\": \"Mais\"}");
        JxmlException e = assertThrows(JxmlException.class, this::run);
        assertTrue(e.getMessage().startsWith("a.jxml:1:") && e.getMessage().contains("takes 2 argument(s), 1 given"), e.getMessage());
        // fix the screen: it is generated again with the new call
        screen("a.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"%{hello(${count}, 3)}\"/></Column>");
        JXForge.Result fixed = run();
        assertTrue(names(fixed.written).contains("AView.java"), fixed.written.toString()); // Strings was already written by the run that failed
        assertTrue(new String(Files.readAllBytes(generated("AView").toPath()), StandardCharsets.UTF_8).contains("Strings.hello(JxmlValues.asInt(c.getCount()).intValue(), 3)"));
    }

    @Test
    void aRenamedKeyBreaksTheScreenThatUsesIt() throws Exception {
        run();
        text("app_en.arb", en("Hello {name}", "Bye").replace("\"bye\"", "\"goodbye\""));
        text("app_pt.arb", "{\"hello\": \"Olá {name}\", \"goodbye\": \"Tchau\"}");
        JxmlException e = assertThrows(JxmlException.class, this::run);
        assertTrue(e.getMessage().contains("parts/p.jxml:1:") && e.getMessage().contains("bye"), e.getMessage());
    }

    @Test
    void warningsOfTheCatalogAreShownOnEveryRunAndStrictMakesThemErrors() throws Exception {
        text("app_pt.arb", "{\"hello\": \"Olá {name}\"}");
        JXForge.Result first = run();
        assertEquals(1, first.warnings.size(), first.warnings.toString());
        assertTrue(first.warnings.get(0).contains("pt has no translation for bye"), first.warnings.get(0));
        File report = new File(out, JXForge.MISSING_REPORT);
        assertEquals("{\n  \"pt\": [\"bye\"]\n}\n", new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8));
        JXForge.Result second = run();
        assertEquals(first.warnings, second.warnings);
        assertTrue(second.written.isEmpty(), second.written.toString());
        JxmlException strict = assertThrows(JxmlException.class,
                () -> JXForge.compileDirectory(src, out, "gen", "View", true, l10n, true));
        assertTrue(strict.getMessage().contains("pt has no translation for bye"), strict.getMessage());
        text("app_pt.arb", "{\"hello\": \"Olá {name}\", \"bye\": \"Tchau\"}");
        JXForge.Result complete = run();
        assertTrue(complete.warnings.isEmpty());
        assertFalse(report.exists(), "the report goes when nothing is untranslated");
        assertTrue(names(complete.removed).contains(JXForge.MISSING_REPORT));
        JXForge.compileDirectory(src, out, "gen", "View", true, l10n, true); // strict is fine now
    }

    @Test
    void aBadCatalogStopsTheRunBeforeAnythingIsWritten() throws Exception {
        text("app_pt.arb", "{\"hello\": \"Olá {nome}\", \"bye\": \"Tchau\"}");
        JxmlException e = assertThrows(JxmlException.class, this::run);
        assertTrue(e.getMessage().contains("app_pt.arb:1:"), e.getMessage());
        assertFalse(out.exists());
    }

    @Test
    void withoutADirectoryTheOldBehaviorHoldsAndAScreenWithTranslationsFails() throws Exception {
        JxmlException old = assertThrows(JxmlException.class, () -> JXForge.compileDirectory(src, out, "gen", "View", true));
        assertTrue(old.getMessage().contains("needs the translation files"), old.getMessage());
        JxmlException missing = assertThrows(JxmlException.class, () -> JXForge.compileDirectory(src, out, "gen", "View", true, new File(dir.toFile(), "nothing")));
        assertTrue(missing.getMessage().contains("needs the translation files"), missing.getMessage());
        assertTrue(generated("CView").isFile() && !generated("Strings").exists()); // the other screens are still generated
    }

    @Test
    void removingTheDirectoryRemovesStringsAndTheReport() throws Exception {
        text("app_pt.arb", "{\"hello\": \"Olá {name}\"}");
        run();
        assertTrue(generated("Strings").isFile() && new File(out, JXForge.MISSING_REPORT).isFile());
        screen("a.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"plain\"/></Column>");
        screen("parts/p.jxml", "<Label text=\"plain\"/>");
        JXForge.Result r = JXForge.compileDirectory(src, out, "gen", "View", true, null);
        assertTrue(names(r.removed).containsAll(Arrays.asList("Strings.java", JXForge.MISSING_REPORT)), r.removed.toString());
        assertFalse(generated("Strings").exists());
    }

    @Test
    void aScreenCannotTakeTheNameOfTheGeneratedClass() throws Exception {
        screen("strings.jxml", "<Label text=\"x\"/>");
        JXForge.Result ok = run(); // StringsView is not Strings
        assertTrue(generated("StringsView").isFile() && generated("Strings").isFile(), ok.written.toString());
        JxmlException e = assertThrows(JxmlException.class, () -> JXForge.compileDirectory(src, dir.resolve("out2").toFile(), "gen", "", true, l10n));
        assertTrue(e.getMessage().contains("Strings") && e.getMessage().contains("generated translations"), e.getMessage());
    }

    @Test
    void anFxmlScreenWithBundleKeysDependsOnTheTranslationsToo() throws Exception {
        screen("f.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\"><Label text=\"%bye\"/></VBox>");
        run();
        assertTrue(api("f.fxml") != null);
        assertTrue(new String(Files.readAllBytes(generated("FView").toPath()), StandardCharsets.UTF_8).contains("Strings.bye()"));
    }
}
