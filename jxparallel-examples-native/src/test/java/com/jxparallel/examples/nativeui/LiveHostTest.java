package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXLiveHost;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.LiveWindow;

/**
 * The host of a live screen, without a display: {@code JXLiveHost} (what {@code JXFrame.mountLive} returns) and the
 * display-thread part of {@code JXWindow.setLive} (through {@code LiveWindow}). After every refresh the tree must be what a
 * fresh build and mount gives, and a screen whose root changed type is mounted anew.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class LiveHostTest {
    private static final String CTRL = "com.jxparallel.examples.nativeui.LiveModel";
    private static final String SCREEN = "<Column controller=\"" + CTRL + "\" gap=\"${gap}\" padding=\"4\">"
            + "<Label text=\"${title}\"/><Slider value=\"${amount}\"/>"
            + "<For items=\"${items}\" var=\"i\" type=\"" + CTRL + ".Item\"><Label text=\"${i.name}\"/></For></Column>";
    /** The root is a slot: another element in the model can be another type of root. */
    private static final String SLOT = "<Slot controller=\"" + CTRL + "\" content=\"${banner}\"/>";

    private static void assertFresh(JxmlView view, LiveModel model, JXNativeNode root, String what) {
        root.layoutForBackend(LiveSupport.W, LiveSupport.H);
        JXNativeNode fresh = ForgeLoadBenchmark.ready(view.build(model));
        String text = LiveSupport.describe(root);
        assertNull(LiveSupport.firstDifference(LiveSupport.describe(fresh), text), what);
        assertArrayEquals(LiveSupport.paint(fresh), LiveSupport.paint(root), what + ": pixels");
    }

    @Test
    void theHostMountsRefreshesInPlaceAndKeepsTheRoot() throws Exception {
        LiveModel model = new LiveModel();
        JxmlView view = LiveCompile.compile(SCREEN);
        JXLiveHost host = com.jxparallel.ui.native2d.JXFrame.mountLive(view.live(model));
        JXNativeNode root = host.root();
        assertTrue(((JxmlLive) host.session()).isLive(), "the session is paired with the mounted tree");
        assertFresh(view, model, root, "mounted");
        for (int i = 0; i < 5; i++) {
            model.setTitle("title " + i);
            model.setAmount(10 + i);
            model.getItems().get(i % 3).edit("edited " + i, i);
            assertFalse(host.refresh(), "a value change does not mount anew");
            assertSame(root, host.root());
            assertFresh(view, model, host.root(), "step " + i);
        }
        assertEquals(0, ((JxmlLive) host.session()).fallbacks());
        assertEquals(0, host.remounts());
        model.getItems().add(new LiveModel.Item(77, "added", 1)); // a shape change: the fallback, still the same root
        assertFalse(host.refresh());
        assertSame(root, host.root());
        assertFresh(view, model, host.root(), "shape change");
    }

    @Test
    void aRootThatChangesTypeIsMountedAnewAndTheNewSessionGoesOnLive() throws Exception {
        LiveModel model = new LiveModel();
        JxmlView view = LiveCompile.compile(SLOT);
        JXLiveHost host = new JXLiveHost(view.live(model));
        JXNativeNode first = host.root();
        com.jxparallel.ui.JXLiveSession before = host.session();
        assertEquals("#text", first.getType());
        model.setBanner(JXElement.of("label", JXProps.builder().set("text", "a label now").build()));
        assertTrue(host.refresh(), "the root is another type: mounted anew");
        assertNotSame(first, host.root());
        assertNotSame(before, host.session());
        assertEquals("label", host.root().getType());
        assertEquals(1, host.remounts());
        assertFresh(view, model, host.root(), "after the remount");
        assertTrue(((JxmlLive) host.session()).isLive() || ((JxmlLive) host.session()).problem() == null);
        model.setBanner(JXElement.of("label", JXProps.builder().set("text", "another text").build()));
        assertFalse(host.refresh(), "the same type of root again: reconciled");
        assertEquals(1, host.remounts());
        assertFresh(view, model, host.root(), "after the second refresh");
    }

    @Test
    void theWindowRefreshesBeforeAFrameAndMountsAnewWhenTheRootChanges() throws Exception {
        LiveModel model = new LiveModel();
        JxmlView view = LiveCompile.compile(SCREEN);
        LiveWindow window = new LiveWindow();
        window.mount(view.live(model));
        JXNativeNode root = window.root();
        assertFresh(view, model, root, "mounted by the window");
        model.setTitle("from the window");
        model.setAmount(42);
        assertFalse(window.refresh(), "no remount");
        assertSame(root, window.root());
        assertFresh(view, model, window.root(), "refreshed before the frame");
        // without a live screen the frame loop's refresh does nothing and the tree keeps what it has
        String shown = LiveSupport.describe(window.root());
        window.mount(null);
        model.setTitle("not shown");
        assertFalse(window.refresh());
        assertEquals(shown, LiveSupport.describe(window.root()));

        JxmlView slot = LiveCompile.compile(SLOT);
        LiveWindow other = new LiveWindow();
        other.mount(slot.live(model));
        JXNativeNode before = other.root();
        model.setBanner(JXElement.of("label", JXProps.builder().set("text", "x").build()));
        assertTrue(other.refresh(), "the window reports the new root");
        assertNotSame(before, other.root());
        assertFresh(slot, model, other.root(), "after the remount");
    }
}
