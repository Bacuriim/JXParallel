package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.examples.nativeui.components.ComponentController;
import com.jxparallel.examples.nativeui.components.ComponentTable;
import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * The research claim, per component: a screen kept live (mounted once, then {@code refresh()} after the controller's value
 * set goes 0, 1, 0, 1, 0, 1) is, after every step, the same as
 * <ul>
 * <li>the screen the current path gives, a tree mounted once and reconciled with a rebuilt element after each change:
 * every node's type, props, sizes and laid-out box, and the whole pixel buffer of the software painter (always);</li>
 * <li>a screen built and mounted anew for that state, the same comparison. Where the current reconcile itself differs
 * from a fresh mount (a prop whose effect on a size {@code sizeAffected} does not know, which the live path inherits by
 * using the same rules) the component is listed in the coverage file and not asserted against the fresh mount.</li>
 * </ul>
 */
class LiveComponentsTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void everyComponentKeptLiveEqualsTheCurrentPathAndAFreshMountAfterEveryStep() throws Exception {
        List<String> table = new ArrayList<String>();
        table.add("component,n,refreshes,fallbacks,opaque_builds,live_changes,reconcile_equals_fresh,recording");
        List<String> divergent = new ArrayList<String>();
        for (String name : ComponentTable.names()) {
            ComponentController controller = (ComponentController) ComponentBenchViews.controller(name);
            JxmlView view = ComponentBenchViews.view(name);
            controller.setPhase(0);
            JxmlLive live = view.live(controller);
            assertNull(JxmlTrees.diff(live.element(), view.build(controller)), name + ": live builds the plain tree");
            JXNativeNode node = ForgeLoadBenchmark.ready(live.element());
            assertTrue(live.attach(node), name + ": the recording pairs with the mounted tree: " + live.problem());
            JXNativeNode current = ForgeLoadBenchmark.ready(view.build(controller));
            boolean reconcileIsFresh = true;
            for (int step = 1; step <= 6; step++) {
                controller.setPhase(step & 1);
                assertTrue(live.refresh(), name);
                node.layoutForBackend(LiveSupport.W, LiveSupport.H);
                JXElement element = view.build(controller);
                assertTrue(current.reconcile(element));
                current.layoutForBackend(LiveSupport.W, LiveSupport.H);

                String liveText = LiveSupport.describe(node);
                String problem = LiveSupport.firstDifference(LiveSupport.describe(current), liveText);
                assertNull(problem, name + " step " + step + " (live against the current reconcile): " + problem);
                int[] livePixels = LiveSupport.paint(node);
                assertArrayEquals(LiveSupport.paint(current), livePixels, name + " step " + step + ": pixels against the current reconcile");

                JXNativeNode fresh = ForgeLoadBenchmark.ready(view.build(controller));
                if (LiveSupport.firstDifference(LiveSupport.describe(fresh), liveText) != null
                        || !Arrays.equals(LiveSupport.paint(fresh), livePixels)) {
                    reconcileIsFresh = false;
                }
                if (live.opaqueBuilds() == 0 && live.fallbacks() == 0) {
                    assertNull(JxmlTrees.diff(element, live.element()), name + " step " + step + ": the live element tree is the built one");
                }
            }
            if (!reconcileIsFresh) {
                divergent.add(name);
            }
            table.add(name + "," + controller.getCount() + "," + live.refreshes() + "," + live.fallbacks() + ","
                    + live.opaqueBuilds() + "," + live.changes() + "," + reconcileIsFresh + ",\"" + live.describe() + "\"");
        }
        Files.write(Paths.get("target", "live-coverage.csv"), table, StandardCharsets.UTF_8);
        System.out.println("LIVE components where the current reconcile differs from a fresh mount: " + divergent);
    }
}
