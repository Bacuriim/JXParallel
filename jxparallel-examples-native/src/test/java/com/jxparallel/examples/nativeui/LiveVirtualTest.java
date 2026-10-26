package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.native2d.JXNativeNode;

/**
 * Virtual lists and tables kept live: a change of the window (the first row), of the values of the rows, or of the list that
 * holds them keeps the rows that stay, with their nodes, and builds only the rows that come in; a change of the number of
 * items or of the number of rows is another shape and falls back. After every refresh the screen equals the current path and
 * a fresh mount (nodes, sizes, boxes, pixels), and the session's element tree is the built one.
 */
class LiveVirtualTest {
    private static final String CTRL = "com.jxparallel.examples.nativeui.VirtualModel";
    private static final String ROW = CTRL + ".Row";

    private static String list(String key, String item) {
        return "<Column controller=\"" + CTRL + "\"><Label text=\"${first}\"/><ListView items=\"${rows}\" var=\"r\" type=\"" + ROW + "\""
                + key + " first=\"${first}\" rows=\"${window}\" cellHeight=\"24\" prefHeight=\"200\">" + item + "</ListView></Column>";
    }

    private static final String LIST = list(" key=\"${r}\"", "<ListItem text=\"${r.name}\" selected=\"${r.picked}\"/>");
    private static final String LIST_UNKEYED = list("", "<ListItem text=\"${r.name}\" selected=\"${r.picked}\"/>");
    private static final String LIST_CAPTURING = list(" key=\"${r}\"", "<ListItem text=\"${r.name}\" onAction=\"#pick(r)\"/>");
    private static final String TABLE = "<Column controller=\"" + CTRL + "\"><TableView items=\"${rows}\" var=\"r\" type=\"" + ROW
            + "\" key=\"${r}\" first=\"${first}\" rows=\"${window}\" rowHeight=\"24\" columns=\"Id, Name, Total\""
            + " columnWidths=\"60, 160, 80\" prefHeight=\"300\"><TableRow><TableCell text=\"${r.id}\"/><TableCell text=\"${r.name}\"/>"
            + "<TableCell text=\"${r.total}\"/></TableRow></TableView></Column>";
    private static final String[] SCREENS = {LIST, LIST_UNKEYED, LIST_CAPTURING, TABLE};
    private static final String[] NAMES = {"list", "unkeyed list", "list with a handler", "table"};

    private static LiveRig<VirtualModel> rig(String jxml, int count, int window) throws Exception {
        VirtualModel model = new VirtualModel(count);
        model.setWindow(window);
        return new LiveRig<VirtualModel>(jxml, model);
    }

    private static JXNativeNode listNode(LiveRig<VirtualModel> rig) {
        return rig.node.getChildren().get(1);
    }

    @Test
    void everyRandomScrollKeepsTheLiveScreenEqualToAFreshMountWithoutFallingBack() throws Exception {
        for (int s = 0; s < SCREENS.length; s++) {
            for (int seed = 0; seed < 3; seed++) {
                LiveRig<VirtualModel> rig = rig(SCREENS[s], 40, 12);
                Random r = new Random(seed * 31 + s);
                for (int i = 0; i < 40; i++) {
                    int first = r.nextInt(5) == 0 ? (r.nextBoolean() ? -1 - r.nextInt(5) : 36 + r.nextInt(20)) : r.nextInt(40);
                    rig.model.setFirst(first);
                    assertEquals(0, rig.refresh(NAMES[s] + " seed " + seed + " first " + first), NAMES[s] + " first " + first);
                }
                assertEquals(0, rig.live.opaqueBuilds(), NAMES[s]);
                assertTrue(rig.live.rowsMade() > 0, NAMES[s]);
            }
        }
    }

    @Test
    void windowsOfZeroOneTwoAndThreeRowsScrollLive() throws Exception {
        for (int s = 0; s < SCREENS.length; s++) {
            for (int window = 0; window <= 3; window++) {
                LiveRig<VirtualModel> rig = rig(SCREENS[s], 9, window);
                Random r = new Random(window);
                for (int i = 0; i < 25; i++) {
                    rig.model.setFirst(r.nextInt(14) - 2);
                    assertEquals(0, rig.refresh(NAMES[s] + " window " + window + " first " + rig.model.getFirst()));
                }
            }
        }
    }

    @Test
    void editsOfTheRowsAndAnotherListOfTheSameSizeAreBoundLiveWhileScrolling() throws Exception {
        for (int s = 0; s < SCREENS.length; s++) {
            LiveRig<VirtualModel> rig = rig(SCREENS[s], 30, 10);
            Random r = new Random(7 + s);
            for (int i = 0; i < 60; i++) {
                VirtualModel m = rig.model;
                String what = NAMES[s] + " step " + i;
                switch (r.nextInt(5)) {
                    case 0:
                        m.getRows().get(r.nextInt(30)).edit("edited " + r.nextInt(99), String.valueOf(r.nextInt(500)), r.nextBoolean());
                        break;
                    case 1:
                        m.setRows(new ArrayList<VirtualModel.Row>(m.getRows())); // a snapshot: the same objects
                        break;
                    case 2: { // other objects, the same number
                        List<VirtualModel.Row> other = new ArrayList<VirtualModel.Row>();
                        for (VirtualModel.Row row : m.getRows()) {
                            VirtualModel.Row copy = new VirtualModel.Row(other.size());
                            copy.edit(row.getName() + "'", row.getTotal(), row.getPicked());
                            other.add(copy);
                        }
                        m.setRows(other);
                        break;
                    }
                    default:
                        m.setFirst(r.nextInt(34) - 2);
                }
                assertEquals(0, rig.refresh(what), what);
            }
            assertEquals(0, rig.live.fallbacks(), NAMES[s]);
        }
    }

    @Test
    void aChangeOfTheNumberOfItemsOrOfRowsFallsBackOnceAndTheSessionGoesOnLive() throws Exception {
        for (int s = 0; s < SCREENS.length; s++) {
            LiveRig<VirtualModel> rig = rig(SCREENS[s], 30, 10);
            rig.model.setFirst(4);
            assertEquals(0, rig.refresh("scroll"));
            rig.model.getRows().add(new VirtualModel.Row(99));
            assertEquals(1, rig.refresh(NAMES[s] + ": an item added"));
            rig.model.getRows().remove(3);
            assertEquals(1, rig.refresh(NAMES[s] + ": an item removed"));
            rig.model.setWindow(7);
            assertEquals(1, rig.refresh(NAMES[s] + ": another number of rows"));
            assertTrue(rig.live.isLive(), "recorded and paired again");
            rig.model.setFirst(11);
            assertEquals(0, rig.refresh(NAMES[s] + ": scrolling goes on live"));
            rig.model.getRows().get(12).edit("late edit", "1", true);
            assertEquals(0, rig.refresh(NAMES[s] + ": an edit goes on live"));
            assertEquals(3, rig.live.fallbacks());
        }
    }

    @Test
    void theRowsThatStayKeepTheirNodesTheRowsThatComeInAreNewAndTheRowsThatLeaveAreGone() throws Exception {
        for (String jxml : new String[] {LIST, TABLE}) {
            LiveRig<VirtualModel> rig = rig(jxml, 40, 12);
            JXNativeNode list = jxml == LIST ? listNode(rig) : rig.node.getChildren().get(0);
            Map<Object, JXNativeNode> before = new IdentityHashMap<Object, JXNativeNode>();
            for (JXNativeNode row : list.getChildren()) {
                before.put(row.getProps().get("key"), row);
            }
            assertEquals(12, before.size());
            int made = rig.live.rowsMade();
            rig.model.setFirst(5);
            assertEquals(0, rig.refresh("scroll by 5"));
            assertSame(list, jxml == LIST ? listNode(rig) : rig.node.getChildren().get(0), "the list node stays");
            int stayed = 0;
            for (JXNativeNode row : list.getChildren()) {
                JXNativeNode was = before.get(row.getProps().get("key"));
                if (was != null) {
                    assertSame(was, row, "a row that stays keeps its node");
                    stayed++;
                } else {
                    assertNotNull(row.getProps().get("key"));
                }
            }
            assertEquals(7, stayed, "rows 5..11 stay");
            assertEquals(5, list.getChildren().size() - stayed, "rows 12..16 come in");
            assertEquals(5, rig.live.rowsMade() - made, "only the rows that came in were built");
            for (int i = 0; i < 5; i++) {
                assertTrue(!list.getChildren().contains(before.get(rig.model.getRows().get(i))), "row " + i + " left");
            }
            // back: rows 5..11 stay again, and the rows that left come back as new nodes
            rig.model.setFirst(0);
            assertEquals(0, rig.refresh("scroll back"));
            for (JXNativeNode row : list.getChildren()) {
                JXNativeNode was = before.get(row.getProps().get("key"));
                if (was != null && rig.model.getRows().indexOf(row.getProps().get("key")) < 5) {
                    assertNotSame(was, row, "a row that left does not come back as the old node");
                }
            }
            assertEquals(10, rig.live.rowsMade() - made, "5 came in, then the 5 that had left came back as new rows");
        }
    }

    @Test
    void aHandlerOfARowHoldsTheItemOfItsKeyAfterScrollingAndAfterAnotherListReplacesTheObjects() throws Exception {
        LiveRig<VirtualModel> rig = rig(LIST_CAPTURING, 30, 8);
        rig.model.setFirst(6);
        assertEquals(0, rig.refresh("scroll"));
        JXNativeNode list = listNode(rig);
        ((Runnable) list.getChildren().get(0).getProps().get("onAction")).run();
        assertEquals("pick Name 6", rig.model.calls.get(rig.model.calls.size() - 1));
        List<VirtualModel.Row> other = new ArrayList<VirtualModel.Row>();
        for (int k = 0; k < 30; k++) {
            VirtualModel.Row row = new VirtualModel.Row(k);
            row.edit("Other " + k, "0", false);
            other.add(row);
        }
        rig.model.setRows(other);
        assertEquals(0, rig.refresh("other objects"), "the rows are made again with the new items");
        ((Runnable) listNode(rig).getChildren().get(0).getProps().get("onAction")).run();
        assertEquals("pick Other 6", rig.model.calls.get(rig.model.calls.size() - 1));
    }
}
