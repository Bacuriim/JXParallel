package com.jxparallel.forge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JXForgePluginTest {
    private static final String JXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Label text=\"hello\"/>\n";

    @TempDir
    Path dir;

    @BeforeEach
    void project() throws IOException {
        write("settings.gradle", "rootProject.name = 'demo'\n");
        write("src/main/jxml/Home.jxml", JXML);
    }

    private void build(String jxforgeBlock) throws IOException {
        write("build.gradle", "plugins {\n  id 'java'\n  id 'com.jxparallel.jxforge'\n}\n"
                + "repositories { mavenLocal() }\n"
                + "dependencies { implementation 'com.jxparallel:jxparallel-forge:" + System.getProperty("jxforge.version")
                + "' }\n"
                + "jxforge {\n" + jxforgeBlock + "\n}\n");
    }

    private void write(String name, String text) throws IOException {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
    }

    private GradleRunner runner(String... args) {
        String[] all = new String[args.length + 1];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = "--offline";
        return GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath().withArguments(all);
    }

    @Test
    void generatesAndCompiles() throws IOException {
        build("packageName = 'demo.ui'");
        BuildResult r = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, r.task(":compileJava").getOutcome());
        Path java = dir.resolve("build/generated/sources/jxforge/demo/ui/HomeView.java");
        assertTrue(Files.isRegularFile(java), "generated " + java);
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/demo/ui/HomeView.class")), "compiled");
    }

    @Test
    void secondRunIsUpToDate() throws IOException {
        build("packageName = 'demo.ui'");
        runner("compileJava").build();
        BuildResult r = runner("compileJava").build();
        assertEquals(TaskOutcome.UP_TO_DATE, r.task(":jxforgeGenerate").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, r.task(":compileJava").getOutcome());
    }

    @Test
    void changedSourceRegenerates() throws IOException {
        build("packageName = 'demo.ui'");
        runner("compileJava").build();
        write("src/main/jxml/Home.jxml", JXML.replace("hello", "changed"));
        BuildResult r = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
        String java = new String(Files.readAllBytes(dir.resolve("build/generated/sources/jxforge/demo/ui/HomeView.java")),
                StandardCharsets.UTF_8);
        assertTrue(java.contains("changed"), "regenerated");
    }

    @Test
    void brokenJxmlFailsWithFileAndLine() throws IOException {
        build("packageName = 'demo.ui'");
        write("src/main/jxml/Broken.jxml", "<?xml version=\"1.0\"?>\n<Label text=\"x\">\n</Nope>\n");
        BuildResult r = runner("compileJava").buildAndFail();
        assertEquals(TaskOutcome.FAILED, r.task(":jxforgeGenerate").getOutcome());
        assertTrue(r.getOutput().matches("(?s).*Broken\\.jxml:\\d+:.*"), r.getOutput());
    }

    @Test
    void suffixPackageAndDirectoriesApply() throws IOException {
        write("ui/Panel.jxml", JXML);
        Files.delete(dir.resolve("src/main/jxml/Home.jxml"));
        build("packageName = 'a.b'\nclassSuffix = 'Screen'\nsourceDirectory = file('ui')\n"
                + "outputDirectory = layout.buildDirectory.dir('gen')\nhoistConstants = false");
        runner("compileJava").build();
        assertTrue(Files.isRegularFile(dir.resolve("build/gen/a/b/PanelScreen.java")));
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/a/b/PanelScreen.class")));
    }

    @Test
    void missingSourceDirectoryIsNotAnError() throws IOException {
        Files.delete(dir.resolve("src/main/jxml/Home.jxml"));
        Files.delete(dir.resolve("src/main/jxml"));
        build("packageName = 'demo.ui'");
        BuildResult r = runner("jxforgeGenerate").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
    }

    @Test
    void packageNameIsRequired() throws IOException {
        build("");
        BuildResult r = runner("jxforgeGenerate").buildAndFail();
        assertTrue(r.getOutput().contains("packageName"), r.getOutput());
    }

    // ---- translations ---------------------------------------------------------------------------------

    private static final String ARB_EN = "{\"@@locale\": \"en\", \"greeting\": \"Hello\", \"bye\": \"Bye\"}";

    private String read(String name) throws IOException {
        return new String(Files.readAllBytes(dir.resolve(name)), StandardCharsets.UTF_8);
    }

    @Test
    void l10nFilesGenerateAStringsClassThatTheScreensUseAndCompile() throws IOException {
        write("src/main/l10n/app_en.arb", ARB_EN);
        write("src/main/jxml/Home.jxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Label text=\"%{greeting}\"/>\n");
        build("packageName = 'demo.ui'");
        BuildResult r = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
        assertTrue(read("build/generated/sources/jxforge/demo/ui/Strings.java").contains("public static String greeting()"));
        assertTrue(read("build/generated/sources/jxforge/demo/ui/HomeView.java").contains("Strings.greeting()"));
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/demo/ui/Strings.class")), "Strings compiled");
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/demo/ui/HomeView.class")), "HomeView compiled");
        assertTrue(r.getOutput().contains("2 written"), r.getOutput()); // the screen and Strings
        // nothing changed: both tasks are up to date
        r = runner("compileJava").build();
        assertEquals(TaskOutcome.UP_TO_DATE, r.task(":jxforgeGenerate").getOutcome());
        // a changed text regenerates Strings, not the screen
        write("src/main/l10n/app_en.arb", ARB_EN.replace("Hello", "Hi"));
        r = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
        assertTrue(r.getOutput().contains("1 written, 1 up to date"), r.getOutput());
        assertTrue(read("build/generated/sources/jxforge/demo/ui/Strings.java").contains("Hi"));
    }

    @Test
    void aMissingTranslationIsAWarningAndFailsTheBuildInStrictMode() throws IOException {
        write("src/main/l10n/app_en.arb", ARB_EN);
        write("src/main/l10n/app_pt.arb", "{\"@@locale\": \"pt\", \"greeting\": \"Ola\"}");
        write("src/main/jxml/Home.jxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Label text=\"%{greeting}\"/>\n");
        build("packageName = 'demo.ui'");
        BuildResult r = runner("compileJava").build();
        assertTrue(r.getOutput().contains("pt has no translation for bye"), r.getOutput());
        assertTrue(Files.isRegularFile(dir.resolve("build/generated/sources/jxforge/l10n-missing.txt")), "the report of the untranslated keys");

        build("packageName = 'demo.ui'\nl10nStrict = true");
        r = runner("compileJava").buildAndFail();
        assertEquals(TaskOutcome.FAILED, r.task(":jxforgeGenerate").getOutcome());
        assertTrue(r.getOutput().contains("app_pt.arb") && r.getOutput().contains("pt has no translation for bye"), r.getOutput());

        // a key a screen uses that the default locale lacks is an error in any mode, with the line of the attribute
        write("src/main/jxml/Home.jxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Column>\n<Label text=\"%{nothing}\"/>\n</Column>\n");
        build("packageName = 'demo.ui'");
        r = runner("compileJava").buildAndFail();
        assertTrue(r.getOutput().matches("(?s).*Home\\.jxml:3:.*nothing.*"), r.getOutput());
    }

    // ---- FXML -----------------------------------------------------------------------------------------

    @Test
    void anFxmlScreenIsGeneratedAndCompiledAndItsWarningsAreLogged() throws IOException {
        write("src/main/l10n/app_en.arb", ARB_EN);
        write("src/main/jxml/Form.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<VBox xmlns:fx=\"http://javafx.com/fxml\" spacing=\"4\">\n"
                + "  <Label fx:id=\"status\" text=\"%greeting\"/>\n  <Label text=\"plain\"/>\n</VBox>\n");
        build("packageName = 'demo.ui'");
        BuildResult r = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
        assertTrue(Files.isRegularFile(dir.resolve("build/generated/sources/jxforge/demo/ui/FormView.java")), "generated from the .fxml");
        assertTrue(read("build/generated/sources/jxforge/demo/ui/FormView.java").contains("Strings.greeting()"));
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/demo/ui/FormView.class")), "compiled");
        assertTrue(r.getOutput().contains("Form.fxml:3") && r.getOutput().contains("fx:id=\"status\""), r.getOutput()); // the warning, with its line
        // and an FXML error points at the FXML line
        write("src/main/jxml/Bad.fxml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<VBox xmlns:fx=\"http://javafx.com/fxml\">\n"
                + "  <Label text=\"${x}\"/>\n</VBox>\n");
        r = runner("compileJava").buildAndFail();
        assertTrue(r.getOutput().matches("(?s).*Bad\\.fxml:3:.*"), r.getOutput());
    }

    // ---- incremental generation -----------------------------------------------------------------------

    @Test
    void removingAJxmlFileDeletesItsGeneratedClassAndSaysSo() throws IOException {
        write("src/main/jxml/Other.jxml", JXML);
        build("packageName = 'demo.ui'");
        BuildResult r = runner("compileJava").build();
        Path other = dir.resolve("build/generated/sources/jxforge/demo/ui/OtherView.java");
        assertTrue(Files.isRegularFile(other), "generated");
        assertTrue(Files.isRegularFile(dir.resolve("build/generated/sources/jxforge/.jxforge-manifest")), "the manifest");
        assertTrue(r.getOutput().contains("0 removed"), r.getOutput());
        Files.delete(dir.resolve("src/main/jxml/Other.jxml"));
        r = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
        assertFalse(Files.exists(other), "the generated class of the removed screen is deleted");
        assertTrue(r.getOutput().contains("JXForge: removed demo/ui/OtherView.java") && r.getOutput().contains("1 removed"), r.getOutput());
        assertTrue(Files.isRegularFile(dir.resolve("build/generated/sources/jxforge/demo/ui/HomeView.java")), "the other screen stays");
        assertFalse(Files.exists(dir.resolve("build/classes/java/main/demo/ui/OtherView.class")), "and it is not compiled any more");
        // the whole source directory going away is the same
        Files.delete(dir.resolve("src/main/jxml/Home.jxml"));
        Files.delete(dir.resolve("src/main/jxml"));
        r = runner("compileJava").build();
        assertFalse(Files.exists(dir.resolve("build/generated/sources/jxforge/demo/ui/HomeView.java")), "no source directory: nothing is left");
        assertTrue(r.getOutput().contains("JXForge: removed demo/ui/HomeView.java"), r.getOutput());
    }

    @Test
    void aForgeWithoutRemovedAndWarningsInItsResultStillRuns() throws Exception {
        // a stand-in for a forge that predates both fields: only the five-argument method and a result with three of them
        Path stub = dir.resolve("stub");
        Files.createDirectories(stub);
        Path source = stub.resolve("com/jxparallel/forge/JXForge.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ("package com.jxparallel.forge;\nimport java.io.File;\nimport java.util.*;\n"
                + "public final class JXForge {\n"
                + "  public static final class Result { public final List<File> written = new ArrayList<File>(); "
                + "public final List<File> upToDate = new ArrayList<File>(); public long nanos = 1000000; }\n"
                + "  public static Result compileDirectory(File src, File out, String pkg, String suffix, boolean hoist) {\n"
                + "    Result r = new Result(); r.written.add(new File(out, \"A.java\")); return r; }\n}\n").getBytes(StandardCharsets.UTF_8));
        javax.tools.JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "-d", stub.toString(), source.toString()));
        Path libs = dir.resolve("libs");
        Files.createDirectories(libs);
        try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(Files.newOutputStream(libs.resolve("jxparallel-forge-0.0.1-old.jar")))) {
            for (String entry : new String[] {"com/jxparallel/forge/JXForge.class", "com/jxparallel/forge/JXForge$Result.class"}) {
                jar.putNextEntry(new java.util.jar.JarEntry(entry));
                jar.write(Files.readAllBytes(stub.resolve(entry)));
                jar.closeEntry();
            }
        }
        write("build.gradle", "plugins {\n  id 'java'\n  id 'com.jxparallel.jxforge'\n}\n"
                + "repositories { flatDir { dirs 'libs' } }\n"
                + "jxforge {\n  packageName = 'demo.ui'\n  forgeVersion = '0.0.1-old'\n}\n");
        BuildResult r = runner("jxforgeGenerate").build();
        assertEquals(TaskOutcome.SUCCESS, r.task(":jxforgeGenerate").getOutcome());
        assertTrue(r.getOutput().matches("(?s).*JXForge: 1 written, 0 up to date, 1[.,]0 ms \\(demo\\.ui\\).*"), r.getOutput());
        assertFalse(r.getOutput().contains("removed"), r.getOutput());
    }
}
