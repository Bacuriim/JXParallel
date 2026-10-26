package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import org.junit.jupiter.api.Test;

import com.jxparallel.examples.nativeui.components.ComponentController;
import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.LiveFrame;

/**
 * What a refresh costs: the bytes allocated per node by {@code refresh()} when one prop of every one of 5000 controls
 * changes (the benchmark's update), when nothing changed, and that a refresh with nothing changed paints nothing new.
 */
class LiveAllocationTest {
    private static final com.sun.management.ThreadMXBean BEAN = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static long allocated() {
        return BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    /** Bytes per node, per refresh, over {@code rounds} refreshes that flip the value set each time. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static double perNode(String name, int rounds, boolean change, StringBuilder report) throws Exception {
        ComponentController c = (ComponentController) ComponentBenchViews.controller(name);
        JxmlView view = ComponentBenchViews.view(name);
        c.setPhase(0);
        JxmlLive live = view.live(c);
        JXNativeNode node = ForgeLoadBenchmark.ready(live.element());
        assertTrue(live.attach(node), live.problem());
        for (int i = 0; i < 60; i++) { // warm up: compiled, the cached boxed values are all made
            c.setPhase(change ? (i + 1) & 1 : 0);
            live.refresh();
        }
        long before = allocated();
        for (int i = 0; i < rounds; i++) {
            c.setPhase(change ? i & 1 : 0);
            live.refresh();
        }
        long bytes = allocated() - before;
        assertEquals(0, live.fallbacks(), name);
        assertEquals(change, live.changes() > 0, name + ": the refreshes " + (change ? "write" : "write nothing"));
        double per = bytes / (double) rounds / c.getCount();
        report.append(String.format(java.util.Locale.ROOT, "%s: %.2f B per node per refresh (%d nodes, %d refreshes, changing=%s)%n",
                name, per, c.getCount(), rounds, change));
        return per;
    }

    @Test
    void oneChangedPropOfEveryNodeAllocatesAlmostNothing() throws Exception {
        StringBuilder report = new StringBuilder();
        double separator = perNode("Separator", 40, true, report);
        double slider = perNode("Slider", 40, true, report);
        double scrollBar = perNode("ScrollBar", 40, true, report);
        double label = perNode("Label", 40, true, report);
        System.out.print(report);
        // measured: see docs/research/live-bindings.md; the bound is a safe margin above it
        assertTrue(separator < 8, "Separator " + separator);
        assertTrue(slider < 8, "Slider " + slider);
        assertTrue(scrollBar < 8, "ScrollBar " + scrollBar);
        assertTrue(label < 8, "Label " + label);
    }

    @Test
    void aRefreshWithNothingChangedAllocatesAlmostNothing() throws Exception {
        StringBuilder report = new StringBuilder();
        double separator = perNode("Separator", 40, false, report);
        double slider = perNode("Slider", 40, false, report);
        System.out.print(report);
        assertTrue(separator < 8, "Separator " + separator);
        assertTrue(slider < 8, "Slider " + slider);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aRefreshWithNothingChangedPaintsNothingNewAndOneChangePaintsSomething() throws Exception {
        ComponentController c = (ComponentController) ComponentBenchViews.controller("Slider");
        JxmlView view = ComponentBenchViews.view("Slider");
        c.setPhase(0);
        JxmlLive live = view.live(c);
        JXNativeNode node = ForgeLoadBenchmark.ready(live.element());
        assertTrue(live.attach(node), live.problem());
        LiveFrame frame = new LiveFrame();
        assertTrue(frame.paint(node, LiveSupport.W, LiveSupport.H) > 0, "the first frame paints");
        assertEquals(0, frame.paint(node, LiveSupport.W, LiveSupport.H), "the second frame, unchanged, paints nothing");
        assertTrue(live.refresh());
        node.layoutForBackend(LiveSupport.W, LiveSupport.H);
        assertEquals(0, live.changes());
        assertEquals(0, frame.paint(node, LiveSupport.W, LiveSupport.H), "a refresh that found nothing changed leaves the frame as it was");
        c.setPhase(1);
        assertTrue(live.refresh());
        node.layoutForBackend(LiveSupport.W, LiveSupport.H);
        assertTrue(live.changes() > 0);
        assertTrue(frame.paint(node, LiveSupport.W, LiveSupport.H) > 0, "a changed value is painted");
    }
}
