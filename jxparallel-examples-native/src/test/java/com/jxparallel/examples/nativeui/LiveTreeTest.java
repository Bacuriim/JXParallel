package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Trees and tree tables kept live: the item model is built again on every refresh (a memoized loop gives back the same
 * elements), and the rows of the items that stay in the window are not made again: the nodes stay, and with a memoized model
 * so do the row elements. After every refresh the screen equals the current path and a fresh mount, and no refresh takes the
 * global fallback (what changes shape here changes the rows inside the tree, which the tree's own reconcile handles).
 */
class LiveTreeTest {
    private static final String CTRL = "com.jxparallel.examples.nativeui.TreeModel";
    private static final String FOLDER = CTRL + ".Folder";

    private static String tree(String tag, String extra, String memo) {
        return "<Column controller=\"" + CTRL + "\"><Label text=\"${first}\"/><" + tag + " rowHeight=\"24\" first=\"${first}\" rows=\"${window}\" "
                + "prefHeight=\"300\"" + extra + "><For items=\"${folders}\" var=\"f\" type=\"" + FOLDER + "\" key=\"${f}\"" + memo
                + "><TreeItem text=\"${f.name}\" expanded=\"${f.open}\"" + (tag.equals("TreeTable") ? " values=\"${f.size}\"" : "") + ">"
                + "<For items=\"${f.children}\" var=\"c\" type=\"java.lang.String\" key=\"${c}\"><TreeItem text=\"${c}\""
                + (tag.equals("TreeTable") ? " values=\"${f.size}\"" : "") + "/></For></TreeItem></For></" + tag + "></Column>";
    }

    private static final String TREE = tree("Tree", "", "");
    private static final String TREE_MEMO = tree("Tree", "", " memo=\"true\"");
    private static final String TABLE = tree("TreeTable", " columns=\"Name, Size\" columnWidths=\"200, 80\" constrained=\"true\"", "");
    private static final String TABLE_MEMO = tree("TreeTable", " columns=\"Name, Size\" columnWidths=\"200, 80\" constrained=\"true\"", " memo=\"true\"");
    private static final String[] SCREENS = {TREE, TREE_MEMO, TABLE, TABLE_MEMO};
    private static final String[] NAMES = {"tree", "memoized tree", "tree table", "memoized tree table"};

    private static LiveRig<TreeModel> rig(String jxml, int window) throws Exception {
        TreeModel model = new TreeModel(8, 4);
        model.setWindow(window);
        return new LiveRig<TreeModel>(jxml, model);
    }

    @Test
    void randomScrollsKeepTheLiveScreenEqualToAFreshMountWithoutFallingBack() throws Exception {
        for (int s = 0; s < SCREENS.length; s++) {
            for (int seed = 0; seed < 3; seed++) {
                LiveRig<TreeModel> rig = rig(SCREENS[s], 14);
                Random r = new Random(seed * 17 + s);
                for (int i = 0; i < 40; i++) {
                    int first = r.nextInt(6) == 0 ? (r.nextBoolean() ? -3 : 60 + r.nextInt(20)) : r.nextInt(40);
                    rig.model.setFirst(first);
                    assertEquals(0, rig.refresh(NAMES[s] + " seed " + seed + " first " + first), NAMES[s]);
                }
            }
        }
    }

    @Test
    void windowsOfZeroOneAndTwoRowsAndChangesOfTheModelKeepParity() throws Exception {
        for (int s = 0; s < SCREENS.length; s++) {
            for (int window = 0; window <= 2; window++) {
                LiveRig<TreeModel> rig = rig(SCREENS[s], window);
                Random r = new Random(window + 5 * s);
                for (int i = 0; i < 20; i++) {
                    rig.model.setFirst(r.nextInt(46) - 2);
                    assertEquals(0, rig.refresh(NAMES[s] + " window " + window));
                }
            }
            LiveRig<TreeModel> rig = rig(SCREENS[s], 14);
            Random r = new Random(100 + s);
            for (int i = 0; i < 60; i++) {
                TreeModel m = rig.model;
                String what = NAMES[s] + " step " + i;
                switch (r.nextInt(7)) {
                    case 0:
                        TreeModel.Folder f = m.getFolders().get(r.nextInt(m.getFolders().size()));
                        f.edit("edited " + r.nextInt(50), r.nextBoolean(), String.valueOf(r.nextInt(900)));
                        break;
                    case 1:
                        m.setFolders(new ArrayList<TreeModel.Folder>(m.getFolders())); // a snapshot
                        break;
                    case 2:
                        m.getFolders().add(new TreeModel.Folder(50 + i, 1 + r.nextInt(4)));
                        break;
                    case 3:
                        if (m.getFolders().size() > 1) {
                            m.getFolders().remove(r.nextInt(m.getFolders().size()));
                        }
                        break;
                    case 4:
                        m.getFolders().get(r.nextInt(m.getFolders().size())).getChildren().add("new " + i);
                        break;
                    case 5:
                        m.setWindow(r.nextInt(20));
                        break;
                    default:
                        m.setFirst(r.nextInt(45) - 2);
                }
                assertEquals(0, rig.refresh(what), what);
            }
        }
    }

    @Test
    void theRowsThatStayKeepTheirNodesAndWithAMemoizedModelTheirElements() throws Exception {
        for (int s = 0; s < SCREENS.length; s++) {
            LiveRig<TreeModel> rig = rig(SCREENS[s], 14);
            assertEquals(0, rig.refresh(NAMES[s] + ": the first refresh makes the rows the next ones give back"));
            JXNativeNode tree = rig.node.getChildren().get(1);
            Map<Object, JXNativeNode> nodes = new IdentityHashMap<Object, JXNativeNode>();
            Map<Object, Object> elements = new IdentityHashMap<Object, Object>();
            for (JXNativeNode row : tree.getChildren()) {
                nodes.put(row.getProps().get("key"), row);
                elements.put(row.getProps().get("key"), row.getSource());
            }
            rig.model.setFirst(3); // odd: every row that stays changes parity
            assertEquals(0, rig.refresh(NAMES[s] + " scroll by 3"));
            assertSame(tree, rig.node.getChildren().get(1));
            int stayed = 0;
            for (JXNativeNode row : tree.getChildren()) {
                Object key = row.getProps().get("key");
                if (nodes.containsKey(key)) {
                    stayed++;
                    assertSame(nodes.get(key), row, NAMES[s] + ": a row that stays keeps its node");
                    if (SCREENS[s] == TREE_MEMO || SCREENS[s] == TABLE_MEMO) {
                        assertSame(elements.get(key), row.getSource(), NAMES[s] + ": and, the model being memoized, its element");
                    }
                }
            }
            assertEquals(11, stayed, NAMES[s] + ": rows 3..13 stay");
            assertTrue(tree.getChildren().size() == 14);
        }
    }
}
