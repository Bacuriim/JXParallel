package com.jxparallel.forge.maven;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XsdMojoTest {
    @Test
    void writesTheSchemaToTheConfiguredFile(@TempDir Path dir) throws Exception {
        File target = dir.resolve("target/jxml.xsd").toFile();
        XsdMojo mojo = new XsdMojo();
        Field field = XsdMojo.class.getDeclaredField("outputFile");
        field.setAccessible(true);
        field.set(mojo, target);
        mojo.execute();
        String xsd = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
        assertTrue(xsd.contains("<xs:element name=\"Button\">"), "no Button element in the schema");
    }
}
