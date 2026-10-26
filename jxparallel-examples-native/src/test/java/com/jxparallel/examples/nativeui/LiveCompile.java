package com.jxparallel.examples.nativeui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.jxparallel.forge.JxmlCatalog;
import com.jxparallel.forge.JxmlCompiler;
import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.JXElement;

/** Compiles a JXML text (or a template) to a view in memory, as the build does, for tests of the generated code. */
final class LiveCompile {
    private static int counter;

    private LiveCompile() {
    }

    /** A compiled screen, and the generated {@code Strings} it was compiled with (null without a catalog). */
    @SuppressWarnings("rawtypes")
    static final class Built {
        final JxmlView view;
        final URLClassLoader loader;

        Built(JxmlView view, URLClassLoader loader) {
            this.view = view;
            this.loader = loader;
        }

        void setLocale(Locale locale) throws Exception {
            loader.loadClass("gen.Strings").getMethod("setLocale", Locale.class).invoke(null, locale);
        }
    }

    @SuppressWarnings({"rawtypes"})
    static JxmlView compile(String jxml) throws Exception {
        JxmlTemplate template = JxmlTemplate.read(new ByteArrayInputStream(jxml.getBytes(StandardCharsets.UTF_8)), "live-test.jxml");
        return compile(template);
    }

    @SuppressWarnings({"rawtypes"})
    static JxmlView compile(JxmlTemplate template) throws Exception {
        return build(template, null).view;
    }

    /** The screen with the {@code Strings} of the catalog, compiled together. */
    static Built compile(String jxml, JxmlCatalog catalog) throws Exception {
        JxmlTemplate template = JxmlTemplate.read(new ByteArrayInputStream(jxml.getBytes(StandardCharsets.UTF_8)), "live-test.jxml", null, catalog);
        return build(template, catalog);
    }

    @SuppressWarnings({"rawtypes"})
    private static synchronized Built build(JxmlTemplate template, JxmlCatalog catalog) throws Exception {
        String className = "LiveView" + (counter++);
        Path dir = Files.createTempDirectory("jx-live-test");
        String code = JxmlCompiler.compile(template, new JxmlCompiler.Options("gen", className, true));
        Path src = dir.resolve("gen/" + className + ".java");
        Files.createDirectories(src.getParent());
        Files.write(src, code.getBytes(StandardCharsets.UTF_8));
        java.util.List<String> files = new java.util.ArrayList<String>();
        files.add(src.toString());
        if (catalog != null) {
            Path strings = dir.resolve("gen/Strings.java");
            Files.write(strings, catalog.generateStrings("gen").getBytes(StandardCharsets.UTF_8));
            files.add(strings.toString());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        String classpath = location(JxmlView.class) + File.pathSeparator + location(JXElement.class) + File.pathSeparator
                + location(LiveModel.class) + File.pathSeparator + location(com.jxparallel.forge.samples.SampleController.class);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        java.util.List<String> args = new java.util.ArrayList<String>(java.util.Arrays.asList("-encoding", "UTF-8", "-source", "8",
                "-target", "8", "-nowarn", "-classpath", classpath, "-d", dir.toString()));
        args.addAll(files);
        if (javac.run(null, null, errors, args.toArray(new String[0])) != 0) {
            throw new AssertionError(new String(errors.toByteArray(), StandardCharsets.UTF_8) + "\n" + code);
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, LiveCompile.class.getClassLoader());
        return new Built((JxmlView) loader.loadClass("gen." + className).getDeclaredConstructor().newInstance(), loader);
    }

    private static String location(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }
}
