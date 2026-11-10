package com.jxparallel.forge.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.transform.stream.StreamSource;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Builds a small project with the plugin on the Gradle that runs the tests, or on {@code -PtestGradleVersion}. */
class JxforgePluginTest {
    private static final String HELLO = "<Column controller=\"acme.HelloController\" padding=\"8\">\n"
            + "  <Label text=\"${greeting}\"/>\n"
            + "  <Button text=\"Go\" onAction=\"#go\"/>\n"
            + "</Column>\n";

    private static final String CONTROLLER = "package acme;\n"
            + "public class HelloController {\n"
            + "    public String getGreeting() { return \"hello\"; }\n"
            + "    public void go() { }\n"
            + "}\n";

    @TempDir
    Path dir;

    private void write(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private String read(String path) throws IOException {
        return new String(Files.readAllBytes(dir.resolve(path)), StandardCharsets.UTF_8);
    }

    private void project(String jxforgeBlock) throws IOException {
        StringBuilder classpath = new StringBuilder();
        for (String jar : System.getProperty("jxforge.sampleClasspath").split(File.pathSeparator)) {
            classpath.append(classpath.length() == 0 ? "" : ", ").append('"').append(jar.replace('\\', '/')).append('"');
        }
        write("settings.gradle", "rootProject.name = 'sample'\n"
                + "buildCache { local { directory = file('cache') } }\n");
        write("build.gradle", "plugins {\n    id 'java'\n    id 'io.github.bacuriim.jxforge'\n}\n"
                + "sourceCompatibility = 1.8\ntargetCompatibility = 1.8\n"
                + "println 'GRADLE ' + gradle.gradleVersion + ' on Java ' + System.getProperty('java.version')\n"
                + "dependencies { implementation files(" + classpath + ") }\n"
                + "jxforge {\n" + jxforgeBlock + "\n}\n");
        write("src/main/java/acme/HelloController.java", CONTROLLER);
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner runner = GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath()
                .withArguments(arguments).forwardOutput();
        String version = System.getProperty("jxforge.testGradleVersion", "");
        if (!version.isEmpty()) {
            runner.withGradleVersion(version);
        }
        return runner;
    }

    private TaskOutcome outcome(BuildResult result, String task) {
        assertTrue(result.task(task) != null, "task " + task + " did not run: " + result.getOutput());
        return result.task(task).getOutcome();
    }

    @Test
    void runsOnTheRequestedGradle() throws IOException {
        project("packageName = 'acme.views'");
        String version = System.getProperty("jxforge.testGradleVersion", "");
        String output = runner("help").build().getOutput();
        assertTrue(output.contains("GRADLE " + version), output);
        System.out.println("TESTED ON " + output.replaceAll("(?s).*(GRADLE [^\\r\\n]+).*", "$1"));
    }

    @Test
    void generatesTheScreenAndCompileJavaCompilesIt() throws IOException {
        project("packageName = 'acme.views'");
        write("src/main/jxml/hello.jxml", HELLO);

        BuildResult result = runner("compileJava").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":jxforgeGenerate"));
        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":compileJava"));
        assertTrue(Files.isRegularFile(dir.resolve("build/generated/sources/jxforge/java/main/acme/views/HelloView.java")));
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/acme/views/HelloView.class")));
        assertTrue(read("build/generated/sources/jxforge/java/main/acme/views/HelloView.java").contains("getGreeting()"));
    }

    @Test
    void secondRunIsUpToDateAndAChangedJxmlRunsAgain() throws IOException {
        project("packageName = 'acme.views'");
        write("src/main/jxml/hello.jxml", HELLO);
        runner("compileJava").build();

        BuildResult second = runner("compileJava").build();
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":jxforgeGenerate"));
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":compileJava"));

        write("src/main/jxml/hello.jxml", HELLO.replace("text=\"Go\"", "text=\"Start\""));
        BuildResult third = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, outcome(third, ":jxforgeGenerate"));
        assertTrue(read("build/generated/sources/jxforge/java/main/acme/views/HelloView.java").contains("Start"));

        // a deleted screen leaves no stale class behind
        Files.delete(dir.resolve("src/main/jxml/hello.jxml"));
        write("src/main/jxml/bye.jxml", HELLO);
        runner("compileJava").build();
        assertFalse(Files.exists(dir.resolve("build/generated/sources/jxforge/java/main/acme/views/HelloView.java")));
        assertTrue(Files.exists(dir.resolve("build/generated/sources/jxforge/java/main/acme/views/ByeView.java")));
    }

    @Test
    void aChangedOptionRunsAgain() throws IOException {
        project("packageName = 'acme.views'");
        write("src/main/jxml/hello.jxml", HELLO);
        runner("jxforgeGenerate").build();

        project("packageName = 'acme.views'\nclassSuffix = 'Screen'");
        BuildResult result = runner("jxforgeGenerate").build();
        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":jxforgeGenerate"));
        assertTrue(Files.exists(dir.resolve("build/generated/sources/jxforge/java/main/acme/views/HelloScreen.java")));
        assertFalse(Files.exists(dir.resolve("build/generated/sources/jxforge/java/main/acme/views/HelloView.java")));
    }

    @Test
    void theBuildCacheRestoresTheGeneratedSources() throws IOException {
        project("packageName = 'acme.views'");
        write("src/main/jxml/hello.jxml", HELLO);
        runner("jxforgeGenerate", "--build-cache").build();
        runner("clean", "--build-cache").build();

        BuildResult result = runner("jxforgeGenerate", "--build-cache").build();
        assertEquals(TaskOutcome.FROM_CACHE, outcome(result, ":jxforgeGenerate"));
        assertTrue(Files.exists(dir.resolve("build/generated/sources/jxforge/java/main/acme/views/HelloView.java")));
    }

    @Test
    void aMisspelledBindingFailsWithTheJxmlFileAndLine() throws IOException {
        project("packageName = 'acme.views'");
        write("src/main/jxml/hello.jxml", HELLO.replace("${greeting}", "${greting}"));

        BuildResult result = runner("compileJava").buildAndFail();

        // javac reports the generated line, which ends with the .jxml file and line it came from
        assertEquals(TaskOutcome.FAILED, outcome(result, ":compileJava"));
        assertTrue(result.getOutput().contains("hello.jxml:2"), result.getOutput());
    }

    @Test
    void aJxmlErrorFailsTheGenerateTaskWithTheJxmlFileAndLine() throws IOException {
        project("packageName = 'acme.views'");
        write("src/main/jxml/screens/hello.jxml", "<Column controller=\"acme.HelloController\">\n"
                + "  <Label text=\"${greeting}\"/>\n"
                + "  <NoSuchTag/>\n"
                + "</Column>\n");

        BuildResult result = runner("jxforgeGenerate").buildAndFail();

        assertEquals(TaskOutcome.FAILED, outcome(result, ":jxforgeGenerate"));
        assertTrue(result.getOutput().contains("screens/hello.jxml:3"), result.getOutput());
    }

    @Test
    void withoutAPackageNameTheBuildSaysSo() throws IOException {
        project("");
        write("src/main/jxml/hello.jxml", HELLO);

        BuildResult result = runner("jxforgeGenerate").buildAndFail();
        assertTrue(result.getOutput().contains("packageName is not set"), result.getOutput());
    }

    @Test
    void withoutJxmlTheTaskHasNoSourceAndCompileJavaStillWorks() throws IOException {
        project("packageName = 'acme.views'");

        BuildResult result = runner("compileJava").build();
        assertEquals(TaskOutcome.NO_SOURCE, outcome(result, ":jxforgeGenerate"));
    }

    @Test
    void theXsdTaskWritesAValidSchemaThatAcceptsTheScreen() throws Exception {
        project("packageName = 'acme.views'");
        write("src/main/jxml/hello.jxml", HELLO);

        BuildResult result = runner("jxforgeXsd").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":jxforgeXsd"));
        File xsd = dir.resolve("build/jxml.xsd").toFile();
        assertTrue(xsd.isFile());
        // the schema itself is valid XSD, and the sample screen is valid against it
        javax.xml.validation.Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(xsd);
        Validator validator = schema.newValidator();
        validator.validate(new StreamSource(dir.resolve("src/main/jxml/hello.jxml").toFile()));

        assertEquals(TaskOutcome.UP_TO_DATE, outcome(runner("jxforgeXsd").build(), ":jxforgeXsd"));

        BuildResult elsewhere = runner("jxforgeXsd", "-Pjxforge.xsd=out/custom.xsd").build();
        assertEquals(TaskOutcome.SUCCESS, outcome(elsewhere, ":jxforgeXsd"));
        assertTrue(Files.isRegularFile(dir.resolve("out/custom.xsd")));
    }

    @Test
    void anFxmlFileIsConvertedThenCompiled() throws IOException {
        project("packageName = 'acme.views'\nfxml { enabled = true }");
        write("src/main/resources/screens/greeting.fxml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<?import javafx.scene.control.*?>\n<?import javafx.scene.layout.*?>\n"
                + "<VBox xmlns:fx=\"http://javafx.com/fxml/1\" fx:controller=\"acme.HelloController\">\n"
                + "  <Label text=\"${controller.greeting}\"/>\n"
                + "  <Button text=\"Go\" onAction=\"#go\"/>\n"
                + "</VBox>\n");

        BuildResult result = runner("compileJava").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":jxforgeFxml"));
        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":jxforgeGenerate"));
        assertTrue(Files.isRegularFile(dir.resolve("build/generated/jxforge/jxml/screens/greeting.jxml")));
        assertTrue(Files.isRegularFile(dir.resolve("build/reports/jxforge/fxml-report.txt")));
        assertTrue(Files.isRegularFile(dir.resolve("build/classes/java/main/acme/views/GreetingView.class")));

        BuildResult again = runner("compileJava").build();
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(again, ":jxforgeFxml"));
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(again, ":jxforgeGenerate"));

        // changing the FXML runs both again
        write("src/main/resources/screens/greeting.fxml", read("src/main/resources/screens/greeting.fxml").replace("\"Go\"", "\"Start\""));
        BuildResult changed = runner("compileJava").build();
        assertEquals(TaskOutcome.SUCCESS, outcome(changed, ":jxforgeFxml"));
        assertEquals(TaskOutcome.SUCCESS, outcome(changed, ":jxforgeGenerate"));
        assertTrue(read("build/generated/sources/jxforge/java/main/acme/views/GreetingView.java").contains("Start"));
    }

    @Test
    void fxmlIsLeftAloneUnlessEnabled() throws IOException {
        project("packageName = 'acme.views'");
        write("src/main/resources/screens/greeting.fxml", "<VBox xmlns:fx=\"http://javafx.com/fxml/1\"/>\n");

        BuildResult result = runner("compileJava").build();
        assertTrue(result.task(":jxforgeFxml") == null, result.getOutput());
    }

    @Test
    void strictFxmlFailsOnWhatIsNotConverted() throws IOException {
        project("packageName = 'acme.views'\nfxml {\n enabled = true\n strict = true\n}");
        write("src/main/resources/screens/bad.fxml",
                "<VBox xmlns:fx=\"http://javafx.com/fxml/1\"><Canvas/></VBox>\n");

        BuildResult result = runner("jxforgeGenerate").buildAndFail();
        assertEquals(TaskOutcome.FAILED, outcome(result, ":jxforgeFxml"));
        assertTrue(Files.isRegularFile(dir.resolve("build/reports/jxforge/fxml-report.txt")));
    }
}
