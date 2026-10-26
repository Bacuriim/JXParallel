package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * The sample screens of the forge tests, compiled: each builds the same tree live and plain (and as the interpreter reads
 * it), and when the controller changes in several ways at once, the screen kept live is the one a fresh mount gives.
 */
class LiveSamplesTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private static List<String> samples() throws Exception {
        File dir = new File(LiveSamplesTest.class.getResource("/samples").toURI());
        List<String> out = new ArrayList<String>();
        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(".jxml")) {
                out.add(f.getName().substring(0, f.getName().length() - ".jxml".length()));
            }
        }
        Collections.sort(out);
        assertTrue(out.size() >= 8, "the shared samples are on the classpath: " + out);
        return out;
    }

    private static String describe(JXNativeNode node) {
        return LiveSupport.describe(node);
    }

    private static int[] paint(JXNativeNode node) {
        int[] pixels = new int[WIDTH * HEIGHT];
        JXWindow.paintSoftware(node, pixels, WIDTH, HEIGHT);
        return pixels;
    }

    private static JXNativeNode mount(JXElement element) {
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layoutForBackend(WIDTH, HEIGHT);
        return node;
    }

    private static void change(SampleController c, int round) {
        c.name = c.name + "!" + round;
        c.rating = c.rating + 1;
        c.volume = c.volume + 3;
        c.note = "note " + round;
        c.city = "City " + round;
        c.alpha = 0.25 * (1 + round % 3);
        if (round % 2 == 1) {
            c.agree = !c.agree;
            c.busy = !c.busy;
            c.page = c.page + 1;
            c.choice = round % 4 == 1 ? "a" : "b";
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void everySampleBuildsTheSameTreeLiveAndPlainAndStaysEqualToAFreshMountWhenTheControllerChanges() throws Exception {
        StringBuilder report = new StringBuilder();
        for (String sample : samples()) {
            JxmlTemplate template = JxmlTemplate.readResource(LiveSamplesTest.class.getClassLoader(), "samples/" + sample + ".jxml");
            JxmlView view = LiveCompile.compile(template);
            SampleController c = new SampleController();
            JXElement plain = view.build(c);
            JxmlLive live = view.live(c);
            assertNull(JxmlTrees.diff(plain, live.element()), sample + ": live builds the plain tree");
            assertNull(JxmlTrees.diff(template.build(c), live.element()), sample + ": and the interpreter's");
            JXNativeNode node = mount(live.element());
            assertTrue(live.attach(node), sample + ": " + live.problem());
            JXNativeNode current = mount(view.build(c));
            for (int round = 1; round <= 4; round++) {
                change(c, round);
                assertTrue(live.refresh(), sample);
                node.layoutForBackend(WIDTH, HEIGHT);
                assertTrue(current.reconcile(view.build(c)));
                current.layoutForBackend(WIDTH, HEIGHT);
                String liveText = describe(node);
                assertNull(LiveSupport.firstDifference(describe(current), liveText), sample + " round " + round + ": live against the current reconcile");
                JXNativeNode fresh = mount(view.build(c));
                assertNull(LiveSupport.firstDifference(describe(fresh), liveText), sample + " round " + round + ": live against a fresh mount");
                int[] pixelsLive = paint(node);
                Thread.sleep(40);
                if (!java.util.Arrays.equals(pixelsLive, paint(node))) { // an indeterminate progress bar draws by the clock
                    report.append(sample).append(" round ").append(round).append(": draws by the clock, pixels not compared\n");
                    continue;
                }
                assertArrayEquals(paint(current), pixelsLive, sample + " round " + round + ": pixels against the current reconcile");
                assertArrayEquals(paint(fresh), pixelsLive, sample + " round " + round + ": pixels against a fresh mount");
            }
            report.append(sample).append(": ").append(live.refreshes()).append(" refreshes, ").append(live.fallbacks()).append(" fallbacks, ")
                    .append(live.opaqueBuilds()).append(" opaque builds, ").append(live.changes()).append(" props written; ")
                    .append(live.describe()).append('\n');
        }
        System.out.print(report);
    }
}
