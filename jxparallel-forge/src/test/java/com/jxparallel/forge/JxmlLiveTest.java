package com.jxparallel.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXLiveNode;

/**
 * The live session of a generated view (the recording, the checks, the in-place writes, the rebuilt parts and the
 * fallback) on the JVMs the forge module runs on, with a node that only mirrors the element tree. The renderer's nodes
 * are tested in the ui and examples-native modules ({@code JXLiveNodeTest}, {@code LiveComponentsTest}).
 */
class JxmlLiveTest {
    private static final String XML = "<Column controller=\"com.jxparallel.forge.LiveController\" gap=\"${gap}\">\n"
            + "  <Label text=\"${title}\"/>\n"
            + "  <If test=\"${flag}\"><Label text=\"on\"/></If>\n"
            + "  <For items=\"${amounts}\" var=\"a\" type=\"java.lang.Double\"><Slider value=\"${a}\"/></For>\n"
            + "  <Tabs><Tab title=\"One\"><Label text=\"${title}\"/></Tab></Tabs>\n"
            + "</Column>";

    @TempDir
    Path dir;

    /** A node that mirrors an element: what a renderer's node does for a live session, without layout. */
    private static final class Fake implements JXLiveNode {
        JXElement source;
        final List<Fake> children = new ArrayList<Fake>();
        int writes;

        Fake(JXElement element) {
            reset(element);
        }

        private void reset(JXElement element) {
            source = element;
            children.clear();
            for (JXElement child : element.getChildren()) {
                children.add(new Fake(child));
            }
        }

        @Override
        public JXElement getSource() {
            return source;
        }

        @Override
        public List<? extends JXLiveNode> getLiveChildren() {
            return children;
        }

        @Override
        public int setProp(String name, Object value) {
            int at = source.getProps().indexOfKey(name);
            if (at < 0 || value == null) {
                return -1;
            }
            source.getProps().setValueAt(at, value);
            writes++;
            return SET_RELAYOUT;
        }

        @Override
        public int commit(int bits) {
            return bits != 0 ? RESIZED : UNCHANGED;
        }

        @Override
        public void setSource(JXElement element) {
            assertTrue(element.getProps() == source.getProps() && element.getType().equals(source.getType()));
            source = element;
        }

        @Override
        public int reconcileLive(JXElement element) {
            if (!element.getType().equals(source.getType())) {
                return -1;
            }
            reset(element);
            return RESIZED;
        }
    }

    @SuppressWarnings("unchecked")
    private JxmlView<LiveController> compile() throws Exception {
        JxmlTemplate template = JxmlTemplate.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)), "live.jxml");
        String java = JxmlCompiler.compile(template, new JxmlCompiler.Options("gen", "LiveScreen", true));
        Path src = dir.resolve("gen/LiveScreen.java");
        Files.createDirectories(src.getParent());
        Files.write(src, java.getBytes(StandardCharsets.UTF_8));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "tests need a JDK");
        String classpath = location(LiveController.class) + File.pathSeparator + location(JxmlView.class) + File.pathSeparator
                + location(JXElement.class);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = javac.run(null, null, errors, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-nowarn",
                "-classpath", classpath, "-d", dir.toString(), src.toString());
        if (status != 0) {
            throw new AssertionError(new String(errors.toByteArray(), StandardCharsets.UTF_8) + "\n" + java);
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader());
        return (JxmlView<LiveController>) loader.loadClass("gen.LiveScreen").getDeclaredConstructor().newInstance();
    }

    private static String location(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }

    private static void sameAsBuilt(JxmlView<LiveController> view, LiveController c, JxmlLive<LiveController> live, Fake root, String what) {
        JXElement built = view.build(c);
        assertNull(JxmlTrees.diff(built, live.element()), what + ": the session's element tree is the built one");
        assertNull(JxmlTrees.diff(built, root.getSource()), what + ": the mounted tree mirrors it");
    }

    @Test
    void aLiveSessionWritesChangedValuesInPlaceAndFallsBackWhenTheShapeChanges() throws Exception {
        JxmlView<LiveController> view = compile();
        LiveController c = new LiveController();
        JxmlLive<LiveController> live = view.live(c);
        assertNull(JxmlTrees.diff(view.build(c), live.element()), "live builds the plain tree");
        Fake root = new Fake(live.element());
        assertTrue(live.attach(root), live.problem());
        assertTrue(live.isLive());
        sameAsBuilt(view, c, live, root, "mounted");

        // values: title (two places, one of them in the rebuilt tab pane), gap, and a replacement list of the same size
        c.setTitle("changed");
        c.setGap(7);
        List<Double> other = new ArrayList<Double>();
        for (int i = 0; i < 5; i++) {
            other.add(Double.valueOf(3 + i));
        }
        c.setAmounts(other);
        assertTrue(live.refresh());
        assertEquals(0, live.fallbacks(), "values are written in place");
        assertEquals(1, live.opaqueBuilds(), "the tab pane is built again");
        assertEquals(7, live.changes(), "title, gap and five values");
        sameAsBuilt(view, c, live, root, "after values");

        // nothing changed: nothing written
        int before = live.changes();
        assertTrue(live.refresh());
        assertEquals(before, live.changes());
        assertEquals(0, live.fallbacks());
        sameAsBuilt(view, c, live, root, "after nothing");

        // the shape: a longer list, then a flipped condition
        other.add(Double.valueOf(99));
        assertTrue(live.refresh());
        assertEquals(1, live.fallbacks(), "the list grew");
        assertTrue(live.isLive(), "recorded and attached again");
        sameAsBuilt(view, c, live, root, "after growing");
        c.setFlag(true);
        assertTrue(live.refresh());
        assertEquals(2, live.fallbacks(), "the condition flipped");
        sameAsBuilt(view, c, live, root, "after the flip");

        // and the session writes in place again
        c.setTitle("again");
        assertTrue(live.refresh());
        assertEquals(2, live.fallbacks());
        sameAsBuilt(view, c, live, root, "after the fallbacks");
    }

    @Test
    void theInterpreterHasNoPlanSoEveryRefreshRebuilds() throws Exception {
        JxmlTemplate template = JxmlTemplate.read(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)), "live.jxml");
        @SuppressWarnings({"unchecked", "rawtypes"})
        JxmlView<LiveController> view = (JxmlView) template;
        LiveController c = new LiveController();
        JxmlLive<LiveController> live = view.live(c);
        Fake root = new Fake(live.element());
        assertTrue(!live.attach(root), "nothing recorded, nothing to pair");
        c.setTitle("x");
        assertTrue(live.refresh());
        assertEquals(1, live.fallbacks());
        assertNull(JxmlTrees.diff(view.build(c), root.getSource()));
    }
}
