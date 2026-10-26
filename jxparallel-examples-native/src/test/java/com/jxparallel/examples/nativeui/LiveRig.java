package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * A screen kept live, the same screen kept by the current path (a tree reconciled with a rebuilt element), and the model both
 * read. After every refresh the live screen must equal the current path and a fresh build and mount: every node (type, props,
 * sizes, box) and the whole pixel buffer, and the session's element tree must be the one a build gives.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class LiveRig<M> {
    final M model;
    final JxmlView view;
    final JxmlLive live;
    final JXNativeNode node;
    final JXNativeNode current;
    int step;

    LiveRig(String jxml, M model) throws Exception {
        this.model = model;
        view = LiveCompile.compile(jxml);
        live = view.live(model);
        node = ForgeLoadBenchmark.ready(live.element());
        assertTrue(live.attach(node), "pairs: " + live.problem());
        current = ForgeLoadBenchmark.ready(view.build(model));
        check("mounted");
    }

    /** The model changed: refresh; returns how many times the refresh fell back. */
    int refresh(String what) {
        int before = live.fallbacks();
        assertTrue(live.refresh(), what);
        node.layoutForBackend(LiveSupport.W, LiveSupport.H);
        assertTrue(current.reconcile(view.build(model)), what);
        current.layoutForBackend(LiveSupport.W, LiveSupport.H);
        check(what);
        assertNull(JxmlTrees.diff(view.build(model), live.element()), what + ": the element tree of the session is the built one");
        return live.fallbacks() - before;
    }

    void check(String what) {
        step++;
        String liveText = LiveSupport.describe(node);
        JXNativeNode fresh = ForgeLoadBenchmark.ready(view.build(model));
        String against = LiveSupport.firstDifference(LiveSupport.describe(fresh), liveText);
        assertNull(against, what + " (step " + step + "): live against a fresh mount (the current reconcile "
                + (LiveSupport.firstDifference(LiveSupport.describe(fresh), LiveSupport.describe(current)) == null ? "equals" : "also differs from") + " it)");
        assertNull(LiveSupport.firstDifference(LiveSupport.describe(current), liveText), what + " (step " + step + "): live against the current reconcile");
        int[] pixels = LiveSupport.paint(node);
        assertArrayEquals(LiveSupport.paint(fresh), pixels, what + " (step " + step + "): pixels against a fresh mount");
        assertArrayEquals(LiveSupport.paint(current), pixels, what + " (step " + step + "): pixels against the current reconcile");
    }
}
