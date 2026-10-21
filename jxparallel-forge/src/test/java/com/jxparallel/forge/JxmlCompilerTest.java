package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.JXElement;

/** The generated class must build exactly the tree the interpreter builds. */
class JxmlCompilerTest {
    @TempDir
    Path dir;

    @SuppressWarnings("unchecked")
    private JxmlView<FeatureController> compileFeature(boolean hoist) throws Exception {
        String java = JxmlCompiler.compile(JxmlTemplateTest.feature(),
                new JxmlCompiler.Options("gen", hoist ? "FeatureView" : "FeaturePlainView", hoist));
        Class<?> type = javac(java, hoist ? "FeatureView" : "FeaturePlainView");
        return (JxmlView<FeatureController>) type.getDeclaredConstructor().newInstance();
    }

    private Class<?> javac(String java, String className) throws IOException, ClassNotFoundException {
        Path src = dir.resolve("gen/" + className + ".java");
        Files.createDirectories(src.getParent());
        Files.write(src, java.getBytes(StandardCharsets.UTF_8));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "tests need a JDK");
        String classpath = location(FeatureController.class) + File.pathSeparator + location(JxmlView.class)
                + File.pathSeparator + location(JXElement.class);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = javac.run(null, null, errors, "-encoding", "UTF-8", "-source", "8", "-target", "8",
                "-nowarn", "-classpath", classpath, "-d", dir.toString(), src.toString());
        if (status != 0) {
            throw new AssertionError(new String(errors.toByteArray(), StandardCharsets.UTF_8) + "\n" + java);
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader());
        return loader.loadClass("gen." + className);
    }

    private static String location(Class<?> type) {
        try {
            return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void generatedCodeBuildsTheInterpretersTree() throws Exception {
        JxmlTemplate template = JxmlTemplateTest.feature();
        for (boolean hoist : new boolean[] {true, false}) {
            JxmlView<FeatureController> view = compileFeature(hoist);
            FeatureController c = new FeatureController();
            assertNull(JxmlTrees.diff(template.build(c), view.build(c)), "hoist=" + hoist);
        }
    }

    @Test
    void generatedHandlersCallTheController() throws Exception {
        FeatureController c = new FeatureController();
        JXElement top = compileFeature(true).build(c).getChildren().get(0);
        ((Runnable) top.getChildren().get(0).getProps().get("onAction")).run();
        ((Runnable) top.getChildren().get(1).getProps().get("onAction")).run();
        assertEquals(Arrays.asList("save", "cancel"), c.calls);
    }

    @Test
    void hoistedSubtreesAreSharedBetweenBuilds() throws Exception {
        JxmlView<FeatureController> hoisted = compileFeature(true);
        JxmlView<FeatureController> plain = compileFeature(false);
        FeatureController c = new FeatureController();
        // the third tab has nothing bound: built once with hoisting, every time without
        assertSame(staticTab(hoisted.build(c)), staticTab(hoisted.build(c)));
        assertNotSame(staticTab(plain.build(c)), staticTab(plain.build(c)));
    }

    private static JXElement staticTab(JXElement root) {
        return ((JXElement[]) root.getChildren().get(1).getProps().get("deferredTabs"))[1];
    }

    @Test
    void aWrongHandlerFailsInJavacWithTheJxmlLine() throws Exception {
        String xml = "<Column controller=\"com.jxparallel.forge.FeatureController\">\n"
                + "  <Button text=\"Go\" onAction=\"#sav\"/>\n</Column>";
        JxmlTemplate t = JxmlTemplate.read(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "views/typo.jxml");
        String java = JxmlCompiler.compile(t, new JxmlCompiler.Options("gen", "TypoView", true));
        AssertionError e = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () -> javac(java, "TypoView"));
        assertTrue(e.getMessage().contains("sav"), e.getMessage());
        assertTrue(java.contains("c::sav) // typo.jxml:2"), java);
    }

    @Test
    void classNamesFollowTheFileName() {
        assertEquals("OrderListView", JxmlCompiler.className("order-list.jxml", "View"));
        assertEquals("Screen01View", JxmlCompiler.className("screen01.jxml", "View"));
        assertEquals("V2faView", JxmlCompiler.className("2fa.jxml", "View"));
    }

    @Test
    void directoryCompilationSkipsUpToDateFiles() throws Exception {
        Path src = dir.resolve("jxml/orders");
        Files.createDirectories(src);
        Files.write(src.resolve("order-list.jxml"), "<Column gap=\"4\"><Label text=\"Orders\"/></Column>".getBytes(StandardCharsets.UTF_8));
        File out = dir.resolve("out").toFile();
        JXForge.Result first = JXForge.compileDirectory(dir.resolve("jxml").toFile(), out, "com.acme.views", "View", true);
        assertEquals(1, first.written.size());
        String java = new String(Files.readAllBytes(first.written.get(0).toPath()), StandardCharsets.UTF_8);
        assertTrue(java.contains("package com.acme.views;") && java.contains("class OrderListView"), java);
        assertTrue(java.contains("from orders/order-list.jxml"), java);
        JXForge.Result second = JXForge.compileDirectory(dir.resolve("jxml").toFile(), out, "com.acme.views", "View", true);
        assertEquals(0, second.written.size());
        assertEquals(1, second.upToDate.size());
    }
}
