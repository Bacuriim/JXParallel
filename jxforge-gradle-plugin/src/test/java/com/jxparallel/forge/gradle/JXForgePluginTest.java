package com.jxparallel.forge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
