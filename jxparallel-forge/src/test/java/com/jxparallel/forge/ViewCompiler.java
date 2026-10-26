package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;

/** Test helper: the interpreter and the generated class (with and without hoisting) of one JXML text. */
final class ViewCompiler {
    private final Path dir;
    private int classes;

    ViewCompiler(Path dir) {
        this.dir = dir;
    }

    static JxmlTemplate read(String xml, String source) {
        // includes of a sample are found next to the samples, whatever name the text was given
        return JxmlTemplate.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), source,
                JxmlLoader.directory(new File("src/test/shared/samples")));
    }

    /** The interpreter, then the generated class with hoisting and without. */
    @SuppressWarnings("unchecked")
    List<JxmlView<Object>> views(String xml, String source) throws Exception {
        return views(read(xml, source));
    }

    /** The interpreter, then the generated class with hoisting and without, of a template already read. */
    @SuppressWarnings("unchecked")
    List<JxmlView<Object>> views(JxmlTemplate template) throws Exception {
        List<JxmlView<Object>> out = new ArrayList<JxmlView<Object>>();
        out.add(template);
        for (boolean hoist : new boolean[] {true, false}) {
            String name = "Gen" + (classes++);
            out.add((JxmlView<Object>) (JxmlView<?>) javac(JxmlCompiler.compile(template,
                    new JxmlCompiler.Options("gen", name, hoist)), name).getDeclaredConstructor().newInstance());
        }
        return out;
    }

    private Class<?> javac(String java, String className) throws Exception {
        Path src = dir.resolve("gen/" + className + ".java");
        Files.createDirectories(src.getParent());
        Files.write(src, java.getBytes(StandardCharsets.UTF_8));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "tests need a JDK");
        String classpath = location(SampleController.class) + File.pathSeparator + location(JxmlView.class)
                + File.pathSeparator + location(JXElement.class);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = javac.run(null, null, errors, "-encoding", "UTF-8", "-source", "8", "-target", "8",
                "-nowarn", "-classpath", classpath, "-d", dir.toString(), src.toString());
        if (status != 0) {
            throw new AssertionError(new String(errors.toByteArray(), StandardCharsets.UTF_8) + "\n" + java);
        }
        return new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader()).loadClass("gen." + className);
    }

    private static String location(Class<?> type) {
        try {
            return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
