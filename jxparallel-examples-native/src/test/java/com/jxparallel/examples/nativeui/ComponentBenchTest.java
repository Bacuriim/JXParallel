package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.jxparallel.examples.nativeui.components.ComponentController;
import com.jxparallel.examples.nativeui.components.ComponentTable;
import com.jxparallel.forge.JxmlSchema;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * The per-component samples of {@code ComponentBench}, without a window: the table, the files and the generated views
 * agree, every component builds the same tree interpreted, from a template and compiled (both value sets), mounts and
 * lays out headless, updates in place, paints with the software painter, and its fingerprint is computed and changes
 * with the update.
 */
class ComponentBenchTest {
    /** Tags that are measured inside their parent, or are control flow. */
    private static final Set<String> INSIDE_PARENTS = new HashSet<String>(Arrays.asList(
            "Tab", "For", "If", "Else", "Slot", "Menu", "MenuItem", "MenuSeparator", "TreeItem", "TableRow", "TableCell", "ListItem"));

    private static List<String> names() {
        return ComponentTable.names();
    }

    @Test
    void everyTagIsAComponentOrIsMeasuredInsideItsParent() {
        Set<String> tags = new TreeSet<String>();
        for (ComponentTable.Entry e : ComponentTable.all().values()) {
            tags.add(e.tag);
            assertNotNull(JxmlSchema.tag(e.tag), e.name + " names a tag the schema has");
        }
        for (String tag : JxmlSchema.tags().keySet()) {
            if (!INSIDE_PARENTS.contains(tag)) {
                assertTrue(tags.contains(tag), "<" + tag + "> has a component sample");
            }
        }
    }

    @Test
    void theTableTheFilesAndTheGeneratedViewsAgree() throws Exception {
        File dir = new File(ComponentBenchTest.class.getResource("/components").toURI());
        Set<String> files = new TreeSet<String>();
        for (String f : dir.list()) {
            if (f.endsWith(".jxml")) {
                files.add(f.substring(0, f.length() - 5));
            }
        }
        assertEquals(new TreeSet<String>(names()), files, "one JXML file per row of components.csv");
        for (String name : names()) {
            assertNotNull(ComponentBenchViews.view(name), name);
            assertTrue(ComponentBenchViews.controller(name) instanceof ComponentController, name);
            ComponentTable.Entry e = ComponentTable.get(name);
            assertTrue(ComponentTable.n(name) > 0, name + " has instances");
            assertTrue(e.fx.isEmpty() ? !e.note.isEmpty() : true, name + " says why it has no JavaFX mode");
        }
    }

    @Test
    void interpretedTemplateAndCompiledBuildTheSameTreeForEveryComponentAndBothValueSets() throws Exception {
        for (String name : names()) {
            ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
            Object[] interpreted = new Object[1];
            Object[] template = new Object[1];
            Object[] compiled = new Object[1];
            for (int phase = 0; phase <= 1; phase++) {
                controller.setPhase(phase);
                JXElement a = ComponentBench.build("interpreted", name, controller, interpreted);
                JXElement b = ComponentBench.build("template", name, controller, template);
                JXElement c = ComponentBench.build("compiled", name, controller, compiled);
                assertNull(JxmlTrees.diff(a, c), name + " phase " + phase + " interpreted vs compiled");
                assertNull(JxmlTrees.diff(a, b), name + " phase " + phase + " interpreted vs template");
                assertEquals(controller.getCount(), c.getChildren().size(), name + ": one element per instance");
            }
        }
    }

    @Test
    void everyComponentMountsHeadlessUpdatesInPlaceAndPaints() throws Exception {
        int[] pixels = new int[ComponentBench.WIDTH * ComponentBench.HEIGHT];
        for (String name : names()) {
            ComponentTable.Entry entry = ComponentTable.get(name);
            ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
            Object[] cache = new Object[1];
            controller.setPhase(0);
            JXElement tree = ComponentBench.build("compiled", name, controller, cache);
            JXNativeNode node = ForgeLoadBenchmark.ready(tree);
            assertTrue(node.getWidth() > 0 && node.getHeight() > 0, name + " is laid out");
            assertEquals(controller.getCount(), node.getChildren().size(), name + ": one node per instance");
            ComponentBench.Fingerprint before = ComponentBench.fingerprint(node, entry);
            assertTrue(before.count > 0, name + ": the probe finds its nodes");
            ComponentBench.Fingerprint again = ComponentBench.fingerprint(node, entry);
            assertEquals(before.sum, again.sum, name + ": the fingerprint is deterministic");
            assertTrue(ComponentBench.visible(node) >= 0);

            controller.setPhase(1);
            assertTrue(node.reconcile(ComponentBench.build("compiled", name, controller, cache)), name + " reconciles");
            node.layoutForBackend(ComponentBench.WIDTH, ComponentBench.HEIGHT);
            ComponentBench.Fingerprint after = ComponentBench.fingerprint(node, entry);
            assertEquals(before.count, after.count, name + ": the update keeps the instances");
            assertNotEquals(before.sum, after.sum, name + ": the update changes the probed prop (" + entry.update + ")");

            Arrays.fill(pixels, 0);
            JXWindow.paintSoftware(node, pixels, ComponentBench.WIDTH, ComponentBench.HEIGHT);
            boolean painted = false;
            for (int p : pixels) {
                if (p != 0) {
                    painted = true;
                    break;
                }
            }
            assertTrue(painted, name + " paints something");
        }
    }

    @Test
    void theOpenVariantsDrawTheirPopupsAndTheClosedOnesDoNot() throws Exception {
        for (String base : new String[] {"ComboBox", "DatePicker", "TimePicker", "ColorPicker", "MenuButton"}) {
            JXNativeNode closed = mount(base);
            JXNativeNode open = mount(base + "Open");
            boolean anyClosed = false;
            boolean anyOpen = false;
            for (JXNativeNode child : closed.getChildren()) {
                anyClosed |= child.getPopupBounds() != null;
            }
            for (JXNativeNode child : open.getChildren()) {
                anyOpen |= child.getPopupBounds() != null;
            }
            assertTrue(!anyClosed, base + " closed has no popup");
            assertTrue(anyOpen, base + "Open has a popup");
        }
    }

    private static JXNativeNode mount(String name) throws Exception {
        ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
        return ForgeLoadBenchmark.ready(ComponentBench.build("compiled", name, controller, new Object[1]));
    }

    @Test
    void theModelsOfTheVirtualComponentsHaveTenThousandRowsAndAWindowOfThirty() throws Exception {
        for (String name : new String[] {"ListView", "TableView", "Tree", "TreeTable"}) {
            ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
            JXNativeNode node = ForgeLoadBenchmark.ready(ComponentBench.build("compiled", name, controller, new Object[1]));
            JXNativeNode list = node.getChildren().get(0);
            assertEquals(10000, ((Integer) list.getProperty("itemCount")).intValue(), name + " model");
            assertEquals(30, list.getChildren().size(), name + " window");
        }
    }
}
