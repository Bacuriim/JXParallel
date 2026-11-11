package com.jxparallel.forge.maven;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FxmlMojoTest {
    private static void set(Object mojo, String name, Object value) throws Exception {
        Field field = mojo.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    private FxmlMojo mojo(Path dir, String fxml, boolean strict) throws Exception {
        Path src = dir.resolve("src/main/resources/screens");
        Files.createDirectories(src);
        Files.write(src.resolve("a.fxml"), fxml.getBytes(StandardCharsets.UTF_8));
        FxmlMojo mojo = new FxmlMojo();
        set(mojo, "sourceDirectory", dir.resolve("src/main/resources").toFile());
        set(mojo, "outputDirectory", dir.resolve("target/generated-jxml").toFile());
        set(mojo, "reportFile", dir.resolve("target/report.txt").toFile());
        set(mojo, "strict", strict);
        return mojo;
    }

    @Test
    void convertsTheFxmlFilesAndWritesTheReport(@TempDir Path dir) throws Exception {
        mojo(dir, "<VBox xmlns:fx='http://javafx.com/fxml/1' fx:controller='c.C'><Button fx:id='b' text='Go' onAction='#go'/></VBox>",
                false).execute();
        String jxml = new String(Files.readAllBytes(dir.resolve("target/generated-jxml/screens/a.jxml")), StandardCharsets.UTF_8);
        assertTrue(jxml.contains("<Column controller=\"c.C\">"), jxml);
        assertTrue(jxml.contains("onAction=\"#go\""), jxml);
        String report = new String(Files.readAllBytes(dir.resolve("target/report.txt")), StandardCharsets.UTF_8);
        assertTrue(report.contains("fx:id"), report);
    }

    @Test
    void strictFailsTheBuildWhenSomethingIsNotConverted(@TempDir Path dir) throws Exception {
        FxmlMojo mojo = mojo(dir, "<VBox xmlns:fx='http://javafx.com/fxml/1'><Canvas/></VBox>", true);
        assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(new File(dir.toFile(), "target/report.txt").isFile());
    }
}
