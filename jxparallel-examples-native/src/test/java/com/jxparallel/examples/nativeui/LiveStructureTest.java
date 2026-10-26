package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.jxparallel.forge.JxmlLive;
import com.jxparallel.forge.JxmlTrees;
import com.jxparallel.forge.JxmlView;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * Changes that alter the shape of the screen (an {@code <If>} flips, a list grows, shrinks, is reordered or replaced by other
 * objects, a key changes, a window moves, a memoized list or a slot is replaced) must take the fallback, and every change,
 * structural or not, must leave the live screen equal to a fresh build and mount and to the current reconcile path.
 */
class LiveStructureTest {
    private static final String ITEM = "com.jxparallel.examples.nativeui.LiveModel.Item";
    private static final String CTRL = "com.jxparallel.examples.nativeui.LiveModel";

    private static final String KITCHEN = "<Column controller=\"" + CTRL + "\" gap=\"${gap}\" padding=\"4\">\n"
            + "  <Label text=\"${title}\"/>\n"
            + "  <If test=\"${flag}\"><Label text=\"on ${title}\"/><Slider value=\"${amount}\"/></If>\n"
            + "  <Else><Separator prefWidth=\"${amount}\"/></Else>\n"
            + "  <For items=\"${items}\" var=\"i\" type=\"" + ITEM + "\">\n"
            + "    <Row gap=\"2\"><Label text=\"${i.name}\"/><Slider value=\"${i.value}\"/><Button text=\"pick\" onAction=\"#pick(i)\"/></Row>\n"
            + "  </For>\n"
            + "  <For items=\"${items}\" var=\"k\" type=\"" + ITEM + "\" key=\"${k}\"><Label text=\"${k.name}\"/></For>\n"
            + "  <For items=\"${items}\" var=\"w\" type=\"" + ITEM + "\" from=\"${from}\" limit=\"${limit}\"><Slider value=\"${w.value}\"/></For>\n"
            + "  <For items=\"${names}\" var=\"n\" type=\"java.lang.String\" memo=\"true\"><Label text=\"${n}\"/></For>\n"
            + "  <Slot content=\"${banner}\"/>\n"
            + "  <Button text=\"ping\" onAction=\"#ping\"/>\n"
            + "</Column>";

    /** A live screen, the same screen as the current path keeps it, and the model both read. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class Rig {
        final LiveModel model = new LiveModel();
        final JxmlView view;
        final JxmlLive live;
        final JXNativeNode node;
        final JXNativeNode current;
        int step;

        Rig(String jxml) throws Exception {
            view = LiveCompile.compile(jxml);
            live = view.live(model);
            node = ForgeLoadBenchmark.ready(live.element());
            assertTrue(live.attach(node), "pairs: " + live.problem());
            current = ForgeLoadBenchmark.ready(view.build(model));
            check("mounted");
        }

        /** The controller changed: the live screen refreshes, the current path rebuilds and reconciles; both match a fresh mount. */
        int refresh(String what) {
            int before = live.fallbacks();
            assertTrue(live.refresh(), what);
            node.layoutForBackend(LiveSupport.W, LiveSupport.H);
            assertTrue(current.reconcile(view.build(model)), what);
            current.layoutForBackend(LiveSupport.W, LiveSupport.H);
            check(what);
            return live.fallbacks() - before;
        }

        void check(String what) {
            step++;
            String liveText = LiveSupport.describe(node);
            JXNativeNode fresh = ForgeLoadBenchmark.ready(view.build(model));
            assertNull(LiveSupport.firstDifference(LiveSupport.describe(fresh), liveText), what + " (step " + step + "): live against a fresh mount");
            assertNull(LiveSupport.firstDifference(LiveSupport.describe(current), liveText), what + " (step " + step + "): live against the current reconcile");
            int[] pixels = LiveSupport.paint(node);
            assertArrayEquals(LiveSupport.paint(fresh), pixels, what + " (step " + step + "): pixels against a fresh mount");
            assertArrayEquals(LiveSupport.paint(current), pixels, what + " (step " + step + "): pixels against the current reconcile");
        }
    }

    // each operation changes the model; true when it changes the shape of the screen
    private static boolean operate(LiveModel m, int op, Random r) {
        List<LiveModel.Item> items = m.getItems();
        switch (op) {
            case 0:
                m.setTitle("title " + r.nextInt(1000));
                return false;
            case 1:
                m.setAmount(r.nextInt(200));
                return false;
            case 2:
                m.setGap(r.nextInt(8));
                return false;
            case 3:
                if (!items.isEmpty()) {
                    items.get(r.nextInt(items.size())).edit("edited " + r.nextInt(100), r.nextInt(100));
                }
                return false;
            case 4: // the same objects in another list object: the usual snapshot
                m.setItems(new ArrayList<LiveModel.Item>(items));
                return false;
            case 5:
                m.setFlag(!m.getFlag());
                return true;
            case 6:
                items.add(new LiveModel.Item(100 + r.nextInt(1000), "new", r.nextInt(100)));
                return true;
            case 7:
                if (items.isEmpty()) {
                    return false;
                }
                items.remove(r.nextInt(items.size()));
                return true;
            case 8:
                if (items.size() < 2) {
                    return false;
                }
                Collections.swap(items, 0, items.size() - 1);
                return true;
            case 9: { // other objects, the same number: keys and captured items change
                List<LiveModel.Item> other = new ArrayList<LiveModel.Item>();
                for (LiveModel.Item item : items) {
                    other.add(new LiveModel.Item(item.getId(), item.getName(), item.getValue()));
                }
                m.setItems(other);
                return !items.isEmpty();
            }
            case 10:
                m.setFrom((m.getFrom() + 1) % 4);
                return true;
            case 11:
                m.setLimit((m.getLimit() + 1) % 5);
                return true;
            case 12:
                m.setNames(new ArrayList<String>(m.getNames()));
                return true;
            case 13:
                m.setBanner(JXElement.of("#text", JXProps.builder().set("value", "b" + r.nextInt(100)).build()));
                return true;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Test
    void valueChangesWriteInPlaceAndNeverFallBack() throws Exception {
        Rig rig = new Rig(KITCHEN);
        Random r = new Random(1);
        for (int i = 0; i < 80; i++) {
            int op = r.nextInt(5);
            assertTrue(!operate(rig.model, op, r), "op " + op + " is a value change");
            assertEquals(0, rig.refresh("value op " + op), "op " + op + " writes in place");
        }
        assertEquals(0, rig.live.fallbacks());
        assertTrue(rig.live.changes() > 0);
    }

    @Test
    void everyStructuralChangeFallsBackOnceAndTheSessionGoesOnLive() throws Exception {
        for (int op = 5; op <= 13; op++) {
            Rig rig = new Rig(KITCHEN);
            Random r = new Random(op);
            // grow the model first, so that the window and the reorder have something to move
            rig.model.getItems().add(new LiveModel.Item(50, "fifty", 50));
            rig.refresh("setup");
            int base = rig.live.fallbacks();
            assertTrue(operate(rig.model, op, r), "op " + op);
            assertEquals(1, rig.refresh("structural op " + op), "op " + op + " takes the fallback");
            assertEquals(base + 1, rig.live.fallbacks());
            assertTrue(rig.live.isLive(), "op " + op + ": recorded and paired again");
            operate(rig.model, 1, r);
            operate(rig.model, 3, r);
            assertEquals(0, rig.refresh("value op after op " + op), "after op " + op + " the session writes in place again");
        }
    }

    @Test
    void randomChangesOfAnyKindKeepTheLiveScreenEqualToAFreshMount() throws Exception {
        for (int seed = 0; seed < 4; seed++) {
            Rig rig = new Rig(KITCHEN);
            Random r = new Random(seed);
            for (int i = 0; i < 60; i++) {
                int op = r.nextInt(14);
                boolean structural = operate(rig.model, op, r);
                int fell = rig.refresh("seed " + seed + " step " + i + " op " + op);
                assertTrue(structural ? fell == 1 : fell == 0, "seed " + seed + " step " + i + " op " + op + " (structural " + structural + ") fell back " + fell);
            }
        }
    }

    @Test
    void aReplacedListOfTheSameSizeIsReboundWithoutFallingBack() throws Exception {
        String xml = "<Column controller=\"" + CTRL + "\"><For items=\"${amounts}\" var=\"a\" type=\"java.lang.Double\">"
                + "<Slider value=\"${a}\"/></For></Column>";
        Rig rig = new Rig(xml);
        List<Double> other = new ArrayList<Double>();
        for (int i = 0; i < rig.model.getAmounts().size(); i++) {
            other.add(Double.valueOf(3 + 5 * i));
        }
        rig.model.setAmounts(other);
        assertEquals(0, rig.refresh("another list of the same size"));
        assertEquals(rig.model.getAmounts().size(), rig.live.changes());
        other.add(Double.valueOf(1));
        assertEquals(1, rig.refresh("one more"));
    }

    @Test
    void aHandlerThatHoldsTheLoopItemSeesTheNewItemAfterAnotherListReplacesIt() throws Exception {
        String xml = "<Column controller=\"" + CTRL + "\"><For items=\"${items}\" var=\"i\" type=\"" + ITEM + "\">"
                + "<Button text=\"${i.name}\" onAction=\"#pick(i)\"/></For><Button text=\"ping\" onAction=\"#ping\"/></Column>";
        Rig rig = new Rig(xml);
        List<LiveModel.Item> other = new ArrayList<LiveModel.Item>();
        other.add(new LiveModel.Item(1, "other one", 1));
        for (int i = 1; i < rig.model.getItems().size(); i++) {
            other.add(new LiveModel.Item(i, "other " + i, i));
        }
        rig.model.setItems(other);
        assertEquals(1, rig.refresh("replaced by other objects"), "the handlers hold the old items: fall back and make new ones");
        ((Runnable) rig.node.getChildren().get(0).getProps().get("onAction")).run();
        assertEquals("pick other one", rig.model.calls.get(rig.model.calls.size() - 1));
        // a handler of the controller alone stays valid through a live refresh
        rig.model.setTitle("x");
        assertEquals(0, rig.refresh("nothing structural"));
        ((Runnable) rig.node.getChildren().get(rig.node.getChildren().size() - 1).getProps().get("onAction")).run();
        assertEquals("ping", rig.model.calls.get(rig.model.calls.size() - 1));
    }

    @Test
    void aPropThatBecomesNullOrComesBackTakesTheFallback() throws Exception {
        String xml = "<Column controller=\"" + CTRL + "\"><Image pixels=\"${pixels}\" imageWidth=\"2\" imageHeight=\"2\" fitWidth=\"${amount}\" fitHeight=\"10\" preserveRatio=\"false\"/></Column>";
        Rig rig = new Rig(xml);
        assertEquals(0, rig.refresh("null stays null"));
        rig.model.setPixels(new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF});
        assertEquals(1, rig.refresh("pixels appear"));
        rig.model.setAmount(33);
        assertEquals(0, rig.refresh("a value"));
        rig.model.setPixels(new int[] {0xFF000000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF});
        assertEquals(0, rig.refresh("other pixels"));
        rig.model.setPixels(null);
        assertEquals(1, rig.refresh("pixels go"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aLiveTreeIsTheBuiltTreeAndTheInterpreterHasNoRecordingButWorks() throws Exception {
        LiveModel model = new LiveModel();
        JxmlView view = LiveCompile.compile(KITCHEN);
        assertNull(JxmlTrees.diff(view.build(model), view.live(model).element()));
        JxmlView interpreted = com.jxparallel.forge.JxmlTemplate.read(
                new java.io.ByteArrayInputStream(KITCHEN.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "kitchen.jxml");
        JxmlLive plain = interpreted.live(model);
        JXNativeNode node = ForgeLoadBenchmark.ready(plain.element());
        assertTrue(!plain.attach(node), "no plan: nothing to pair");
        model.setTitle("changed");
        assertTrue(plain.refresh());
        assertEquals(1, plain.fallbacks());
        node.layoutForBackend(LiveSupport.W, LiveSupport.H);
        assertNull(LiveSupport.firstDifference(LiveSupport.describe(ForgeLoadBenchmark.ready(interpreted.build(model))), LiveSupport.describe(node)));
    }
}
