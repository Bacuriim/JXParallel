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

/** {@link JXForge#compileDirectory} regenerates by content (the manifest), not by file time. */
class IncrementalTest {
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";
    private static final long PAST = 946684800000L; // 2000-01-01

    @TempDir
    Path dir;
    File src;
    File out;

    @BeforeEach
    void layout() throws IOException {
        src = dir.resolve("src").toFile();
        out = dir.resolve("out").toFile();
        write("a.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"a\"/></Column>");
        write("b.jxml", "<Column controller=\"" + CTRL + "\"><Include src=\"parts/p.jxml\"/></Column>");
        write("parts/p.jxml", "<Column><Include src=\"q.jxml\"/></Column>");
        write("parts/q.jxml", "<Label text=\"q\"/>");
        write("c.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"c\"/></Column>");
    }

    private void write(String name, String text) throws IOException {
        Path file = src.toPath().resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private JXForge.Result run() throws IOException {
        return JXForge.compileDirectory(src, out, "gen", "View", true);
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
        for (String cls : Arrays.asList("AView", "BView", "CView")) {
            if (generated(cls).isFile()) {
                assertTrue(generated(cls).setLastModified(PAST));
            }
        }
    }

    @Test
    void theFirstRunWritesEverythingAndTheSecondNothing() throws Exception {
        JXForge.Result first = run();
        assertEquals(Arrays.asList("AView.java", "BView.java", "CView.java"), names(first.written));
        assertTrue(new File(out, JXForge.MANIFEST).isFile());
        age();
        byte[] manifest = Files.readAllBytes(new File(out, JXForge.MANIFEST).toPath());
        long manifestTime = new File(out, JXForge.MANIFEST).lastModified();
        JXForge.Result second = run();
        assertTrue(second.written.isEmpty(), second.written.toString());
        assertEquals(3, second.upToDate.size());
        assertTrue(second.removed.isEmpty());
        for (String cls : Arrays.asList("AView", "BView", "CView")) {
            assertEquals(PAST, generated(cls).lastModified(), cls + " keeps its time");
        }
        assertEquals(manifestTime, new File(out, JXForge.MANIFEST).lastModified());
        assertTrue(Arrays.equals(manifest, Files.readAllBytes(new File(out, JXForge.MANIFEST).toPath())));
    }

    @Test
    void theManifestHasNoTimestampsAndIsTheSameForTheSameInput() throws Exception {
        run();
        byte[] first = Files.readAllBytes(new File(out, JXForge.MANIFEST).toPath());
        Files.delete(new File(out, JXForge.MANIFEST).toPath());
        run(); // everything is generated again, to the same text
        assertTrue(Arrays.equals(first, Files.readAllBytes(new File(out, JXForge.MANIFEST).toPath())));
    }

    @Test
    void touchingTheTimeOfASourceRegeneratesNothing() throws Exception {
        run();
        age();
        assertTrue(new File(src, "a.jxml").setLastModified(System.currentTimeMillis() + 60000));
        assertTrue(new File(src, "parts/q.jxml").setLastModified(System.currentTimeMillis() + 60000));
        JXForge.Result again = run();
        assertTrue(again.written.isEmpty(), again.written.toString());
        assertEquals(3, again.upToDate.size());
    }

    @Test
    void aChangedScreenRegeneratesOnlyItself() throws Exception {
        run();
        age();
        write("a.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"a2\"/></Column>");
        JXForge.Result again = run();
        assertEquals(Arrays.asList("AView.java"), names(again.written));
        assertEquals(PAST, generated("BView").lastModified());
        assertEquals(PAST, generated("CView").lastModified());
    }

    @Test
    void aChangedPartRegeneratesOnlyTheScreensThatIncludeItTransitively() throws Exception {
        run();
        age();
        write("parts/q.jxml", "<Label text=\"q2\"/>");
        JXForge.Result again = run();
        assertEquals(Arrays.asList("BView.java"), names(again.written), "b includes p which includes q");
        assertTrue(new String(Files.readAllBytes(generated("BView").toPath()), StandardCharsets.UTF_8).contains("q2"));
        assertEquals(PAST, generated("AView").lastModified());
        assertEquals(PAST, generated("CView").lastModified());
        age();
        write("parts/p.jxml", "<Column><Include src=\"q.jxml\"/><Label text=\"p2\"/></Column>");
        assertEquals(Arrays.asList("BView.java"), names(run().written));
    }

    @Test
    void aTextEqualToTheFileIsNotWrittenEvenWhenItsSourceChanged() throws Exception {
        run();
        age();
        // a comment changes the bytes of the source, not what is generated
        write("a.jxml", "<Column controller=\"" + CTRL + "\"><!-- note --><Label text=\"a\"/></Column>");
        JXForge.Result again = run();
        assertTrue(again.written.isEmpty(), again.written.toString());
        assertEquals(PAST, generated("AView").lastModified());
        assertEquals(3, again.upToDate.size());
        age();
        JXForge.Result third = run();
        assertTrue(third.written.isEmpty());
    }

    @Test
    void aMissingOutputIsGeneratedAgain() throws Exception {
        run();
        assertTrue(generated("CView").delete());
        assertEquals(Arrays.asList("CView.java"), names(run().written));
    }

    @Test
    void anotherSuffixPackageOrHoistRegeneratesAll() throws Exception {
        run();
        JXForge.Result suffix = JXForge.compileDirectory(src, out, "gen", "Screen", true);
        assertEquals(Arrays.asList("AScreen.java", "BScreen.java", "CScreen.java"), names(suffix.written));
        assertEquals(Arrays.asList("AView.java", "BView.java", "CView.java"), names(suffix.removed), "the old classes go");
        JXForge.Result pack = JXForge.compileDirectory(src, out, "other.pkg", "Screen", true);
        assertEquals(3, pack.written.size());
        assertEquals(3, pack.removed.size());
        assertFalse(new File(out, "gen").exists(), "the empty package folder goes too");
        JXForge.Result hoist = JXForge.compileDirectory(src, out, "other.pkg", "Screen", false);
        assertEquals(3, hoist.written.size() + hoist.upToDate.size());
        JXForge.Result same = JXForge.compileDirectory(src, out, "other.pkg", "Screen", false);
        assertTrue(same.written.isEmpty());
        assertEquals(3, same.upToDate.size());
    }

    @Test
    void aChangedCompilerRegeneratesEverythingThoughTheSourcesAreTheSame() throws Exception {
        String digest = JXForge.compilerDigest();
        assertTrue(digest.matches("[0-9a-f]{64}"), "the forge classes are read: " + digest);
        run();
        String fresh = new String(Files.readAllBytes(generated("AView").toPath()), StandardCharsets.UTF_8);
        // what an older compiler of the same snapshot left: the sources and the version did not change
        Files.write(generated("AView").toPath(), "// stale".getBytes(StandardCharsets.UTF_8));
        assertTrue(run().written.isEmpty(), "same compiler: the output is trusted");
        try {
            JXForge.compilerDigest = "another compiler";
            JXForge.Result again = run();
            assertEquals(Arrays.asList("AView.java"), names(again.written), "only the stale text differs");
            assertEquals(2, again.upToDate.size(), "the others were compiled again to the text they had");
            assertEquals(fresh, new String(Files.readAllBytes(generated("AView").toPath()), StandardCharsets.UTF_8));
        } finally {
            JXForge.compilerDigest = digest;
        }
    }

    @Test
    void aRemovedSourceDeletesItsClass() throws Exception {
        run();
        age();
        Files.delete(new File(src, "c.jxml").toPath());
        JXForge.Result again = run();
        assertEquals(Arrays.asList("CView.java"), names(again.removed));
        assertFalse(generated("CView").exists());
        assertTrue(again.written.isEmpty());
        assertEquals(2, again.upToDate.size());
        for (File f : new File[] {new File(src, "a.jxml"), new File(src, "b.jxml"), new File(src, "parts")}) {
            Files.walk(f.toPath()).filter(Files::isRegularFile).forEach(p -> p.toFile().delete());
        }
        JXForge.Result none = run();
        assertEquals(2, none.removed.size());
        assertFalse(new File(out, "gen").exists(), "the empty package folder is removed");
        assertTrue(out.isDirectory());
    }

    @Test
    void aCorruptOrForeignManifestRegeneratesEverythingWithoutError() throws Exception {
        run();
        File manifest = new File(out, JXForge.MANIFEST);
        for (String bad : Arrays.asList("garbage\u0000\n", "", "jxforge-manifest 99\noptions\tx\n",
                "jxforge-manifest 1\noptions\tx\nscreen\ta.jxml\nclass\tAView\n", "jxforge-manifest 1\noptions\tx\nbogus\n")) {
            Files.write(manifest.toPath(), bad.getBytes(StandardCharsets.UTF_8));
            JXForge.Result again = run();
            assertEquals(3, again.written.size() + again.upToDate.size());
            assertTrue(again.written.isEmpty(), "the text is the same, so nothing is written: " + again.written);
            assertTrue(Files.readAllBytes(manifest.toPath()).length > 50, "the manifest is rewritten");
        }
        // a half-written manifest (cut in the middle of a line)
        byte[] good = Files.readAllBytes(manifest.toPath());
        Files.write(manifest.toPath(), Arrays.copyOf(good, good.length / 2));
        run();
        assertTrue(Arrays.equals(good, Files.readAllBytes(manifest.toPath())));
    }

    @Test
    void theWarningsOfAnUpToDateFxmlScreenAreReplayedOnEveryRun() throws Exception {
        write("form.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\">\n"
                + "<Button fx:id=\"go\" text=\"Go\" onAction=\"#save\"/>\n<fx:include source=\"parts/foot.fxml\"/>\n</VBox>");
        write("parts/foot.fxml", "<HBox xmlns:fx=\"http://javafx.com/fxml\"><Label fx:id=\"foot\" text=\"Footer\"/></HBox>");
        JXForge.Result first = run();
        assertTrue(names(first.written).contains("FormView.java"));
        String all = String.join("\n", first.warnings);
        assertTrue(all.contains("form.fxml:2: ") && all.contains("fx:id=\"go\"") && all.contains("parts/foot.fxml:1: "), all);
        age();
        generated("FormView").setLastModified(PAST);
        for (int i = 0; i < 2; i++) {
            JXForge.Result again = run();
            assertTrue(again.written.isEmpty(), again.written.toString());
            assertEquals(first.warnings, again.warnings);
        }
        assertEquals(PAST, generated("FormView").lastModified());
        // the included FXML part is a dependency of the screen
        write("parts/foot.fxml", "<HBox xmlns:fx=\"http://javafx.com/fxml\"><Label fx:id=\"foot2\" text=\"Footer\"/></HBox>");
        JXForge.Result changed = run();
        assertTrue(changed.written.isEmpty(), "the generated text is the same: only the warnings changed");
        assertEquals(PAST, generated("FormView").lastModified());
        assertTrue(String.join("\n", changed.warnings).contains("foot2"));
        assertFalse(String.join("\n", changed.warnings).contains("\"foot\""));
    }

    @Test
    void aBrokenScreenFailsEveryTimeWhileTheOthersStayUpToDate() throws Exception {
        run();
        age();
        write("bad.jxml", "<Column controller=\"" + CTRL + "\">\n<Label text=\"x\"\n</Column>");
        for (int i = 0; i < 3; i++) {
            JxmlException e = assertThrows(JxmlException.class, this::run);
            assertTrue(e.getMessage().startsWith("bad.jxml:"), e.getMessage());
        }
        for (String cls : Arrays.asList("AView", "BView", "CView")) {
            assertEquals(PAST, generated(cls).lastModified());
        }
        assertFalse(generated("BadView").exists());
        assertFalse(new String(Files.readAllBytes(new File(out, JXForge.MANIFEST).toPath()), StandardCharsets.UTF_8).contains("bad.jxml"),
                "a failed screen has no entry");
        // the others still progress while one fails
        write("a.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"a3\"/></Column>");
        assertThrows(JxmlException.class, this::run);
        assertTrue(new String(Files.readAllBytes(generated("AView").toPath()), StandardCharsets.UTF_8).contains("a3"));
        // fixing it
        write("bad.jxml", "<Column controller=\"" + CTRL + "\"><Label text=\"ok\"/></Column>");
        JXForge.Result fixed = run();
        assertEquals(Arrays.asList("BadView.java"), names(fixed.written));
    }

    @Test
    void aScreenThatBreaksKeepsFailingEvenThoughItWasGeneratedBefore() throws Exception {
        run();
        write("c.jxml", "<Column controller=\"" + CTRL + "\"><Nope/></Column>");
        for (int i = 0; i < 2; i++) {
            JxmlException e = assertThrows(JxmlException.class, this::run);
            assertTrue(e.getMessage().startsWith("c.jxml:1: "), e.getMessage());
        }
    }

    @Test
    void aBrokenPartFailsTheScreensThatIncludeIt() throws Exception {
        run();
        write("parts/q.jxml", "<Label text=\"q\"");
        for (int i = 0; i < 2; i++) {
            JxmlException e = assertThrows(JxmlException.class, this::run);
            assertTrue(e.getMessage().contains("q.jxml"), e.getMessage());
        }
    }

    @Test
    void anIncludedFileThatAppearsLaterIsPickedUp() throws Exception {
        write("d.jxml", "<Column controller=\"" + CTRL + "\"><Include src=\"parts/late.jxml\"/></Column>");
        assertThrows(JxmlException.class, this::run); // the part is missing
        write("parts/late.jxml", "<Label text=\"late\"/>");
        JXForge.Result again = run();
        assertTrue(names(again.written).contains("DView.java"));
    }

    @Test
    void anFxmlScreenIsIncrementalToo() throws Exception {
        write("x.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\"><Label text=\"x\"/></VBox>");
        run();
        age();
        generated("XView").setLastModified(PAST);
        assertTrue(run().written.isEmpty());
        write("x.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"" + CTRL + "\"><Label text=\"x2\"/></VBox>");
        assertEquals(Arrays.asList("XView.java"), names(run().written));
        assertEquals(PAST, generated("AView").lastModified());
    }
}
