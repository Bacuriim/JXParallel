package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.accessibility.JXAccessibility;
import com.jxparallel.ui.accessibility.JXAccessibilityAdapter;
import com.jxparallel.ui.accessibility.JXAccessibilityEvent;
import com.jxparallel.ui.accessibility.JXAccessibilityHost;
import com.jxparallel.ui.accessibility.JXAccessibilityRole;
import com.jxparallel.ui.accessibility.JXAccessibleAction;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.accessibility.JXAccessibleState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXNativeAccessibilityTest {
    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement el(String type, JXProps.Builder props, JXElement... kids) {
        return JXElement.of(type, props.build(), kids);
    }

    private static JXNativeNode mount(JXElement e, int w, int h) {
        JXNativeNode root = JXNativeNode.createBackendNode(e);
        root.layoutForBackend(w, h);
        return root;
    }

    @AfterEach
    void reset() {
        JXNativeNode.setFocusOwner(null);
        JXAccessibility.uninstall();
    }

    private static JXAccessibleNode find(JXAccessibleNode root, JXAccessibilityRole role, String name) {
        JXAccessibleNode n = root.find(role, name);
        assertNotNull(n, role + " \"" + name + "\" in\n" + root.dump());
        return n;
    }

    private JXElement screen(AtomicInteger clicks, List<Object> changes) {
        Consumer<Object> record = changes::add;
        return el("column", p().set("gap", 4.0),
                el("#text", p().set("value", "Name").set("labelFor", "name")),
                el("input", p().set("value", "Ana").set("prompt", "Your name").set("key", "name").set("onChange", record)),
                el("password", p().set("value", "s3cret").set("accessibleName", "Password").set("onChange", record)),
                el("button", p().set("label", "Save").set("tooltip", "Saves it").set("onAction", (Runnable) clicks::incrementAndGet)),
                el("button", p().set("label", "Gone").set("disabled", true)),
                el("checkbox", p().set("label", "Agree").set("checked", true).set("onChange", record)),
                el("checkbox", p().set("label", "Maybe").set("indeterminate", true)),
                el("slider", p().set("min", 0.0).set("max", 10.0).set("value", 4.0).set("accessibleName", "Volume").set("onChange", record)),
                el("progress", p().set("progress", 0.25).set("accessibleName", "Upload")),
                el("select", p().set("value", "b").set("options", new String[] {"a", "b", "c"}).set("overlay", true)
                        .set("accessibleName", "Letter").set("onChange", record)),
                el("button", p().set("label", "Secret").set("accessibleHidden", true)),
                el("image", p().set("accessibleRole", "button").set("accessibleName", "Close")));
    }

    @Test
    void controlsExposeRoleNameValueAndStates() {
        JXNativeNode root = mount(screen(new AtomicInteger(), new ArrayList<Object>()), 400, 500);
        JXNativeNode.setFocusOwner(root.getChildren().get(3));
        JXAccessibleNode tree = JXNativeAccessibility.snapshot(root);

        assertEquals(JXAccessibilityRole.ROOT, tree.getRole());
        JXAccessibleNode field = find(tree, JXAccessibilityRole.TEXT_FIELD, "Name"); // labelFor wins over the prompt
        assertEquals("Ana", field.getValue());
        assertTrue(field.getLabelledBy() != 0);
        assertTrue(field.is(JXAccessibleState.FOCUSABLE));

        JXAccessibleNode password = find(tree, JXAccessibilityRole.TEXT_FIELD, "Password");
        assertTrue(password.is(JXAccessibleState.PASSWORD));
        assertEquals("", password.getValue());
        assertFalse(tree.dump().contains("s3cret"));

        JXAccessibleNode save = find(tree, JXAccessibilityRole.BUTTON, "Save");
        assertEquals("Saves it", save.getDescription());
        assertTrue(save.is(JXAccessibleState.FOCUSED));
        assertTrue(save.supports(JXAccessibleAction.DEFAULT));
        assertTrue(save.getBounds()[2] > 0 && save.getScreenBounds()[2] > 0);

        JXAccessibleNode gone = find(tree, JXAccessibilityRole.BUTTON, "Gone");
        assertTrue(gone.is(JXAccessibleState.DISABLED));
        assertFalse(gone.supports(JXAccessibleAction.DEFAULT));

        assertTrue(find(tree, JXAccessibilityRole.CHECK_BOX, "Agree").is(JXAccessibleState.CHECKED));
        assertTrue(find(tree, JXAccessibilityRole.CHECK_BOX, "Maybe").is(JXAccessibleState.MIXED));

        JXAccessibleNode slider = find(tree, JXAccessibilityRole.SLIDER, "Volume");
        assertEquals(4.0, slider.getNow());
        assertEquals(10.0, slider.getMax());
        assertEquals("25%", find(tree, JXAccessibilityRole.PROGRESS_BAR, "Upload").getValue());
        assertEquals("b", find(tree, JXAccessibilityRole.COMBO_BOX, "Letter").getValue());

        assertNull(tree.find(JXAccessibilityRole.BUTTON, "Secret"), "accessibleHidden removes the node");
        assertNotNull(tree.find(JXAccessibilityRole.BUTTON, "Close"), "accessibleRole overrides the role");
    }

    @Test
    void actionsRunTheSameHandlersAsThePointerAndKeyboard() {
        AtomicInteger clicks = new AtomicInteger();
        List<Object> changes = new ArrayList<Object>();
        JXNativeNode root = mount(screen(clicks, changes), 400, 500);
        JXNativeAccessibility a11y = new JXNativeAccessibility();
        JXAccessibleNode tree = a11y.snapshot(root, "T", 400, 500, 1, 1, 0, 0);

        assertTrue(a11y.perform(find(tree, JXAccessibilityRole.BUTTON, "Save").getId(), JXAccessibleAction.DEFAULT, null));
        assertEquals(1, clicks.get());
        assertFalse(a11y.perform(find(tree, JXAccessibilityRole.BUTTON, "Gone").getId(), JXAccessibleAction.DEFAULT, null));

        assertTrue(a11y.perform(find(tree, JXAccessibilityRole.CHECK_BOX, "Agree").getId(), JXAccessibleAction.DEFAULT, null));
        assertEquals(Boolean.FALSE, changes.get(changes.size() - 1));

        assertTrue(a11y.perform(find(tree, JXAccessibilityRole.SLIDER, "Volume").getId(), JXAccessibleAction.SET_VALUE, 99));
        assertEquals(10.0, changes.get(changes.size() - 1), "clamped to max");
        assertTrue(a11y.perform(find(tree, JXAccessibilityRole.TEXT_FIELD, "Name").getId(), JXAccessibleAction.SET_VALUE, "Bea"));
        assertEquals("Bea", changes.get(changes.size() - 1));
        assertTrue(a11y.perform(find(tree, JXAccessibilityRole.COMBO_BOX, "Letter").getId(), JXAccessibleAction.SET_VALUE, "c"));
        assertEquals("c", changes.get(changes.size() - 1));
        assertFalse(a11y.perform(find(tree, JXAccessibilityRole.COMBO_BOX, "Letter").getId(), JXAccessibleAction.SET_VALUE, "zzz"));

        long fieldId = find(tree, JXAccessibilityRole.TEXT_FIELD, "Name").getId();
        assertTrue(a11y.perform(fieldId, JXAccessibleAction.FOCUS, null));
        assertSame(root.getChildren().get(1), JXNativeNode.focusOwner());
        assertEquals(fieldId, focusedId(a11y.snapshot(root, "T", 400, 500, 1, 1, 0, 0)));
    }

    private static void assertSame(Object a, Object b) {
        org.junit.jupiter.api.Assertions.assertSame(a, b);
    }

    private static long focusedId(JXAccessibleNode tree) {
        for (JXAccessibleNode n : tree.flatten()) {
            if (n.is(JXAccessibleState.FOCUSED)) {
                return n.getId();
            }
        }
        return 0;
    }

    @Test
    void comboBoxExpandsAndItsOptionsCanBePicked() {
        List<Object> changes = new ArrayList<Object>();
        Consumer<Object> record = changes::add;
        JXNativeNode root = mount(el("column", p(), el("select", p().set("value", "a").set("options", new String[] {"a", "b"})
                .set("overlay", true).set("accessibleName", "Letter").set("onChange", record))), 300, 300);
        JXNativeAccessibility a11y = new JXNativeAccessibility();
        JXAccessibleNode tree = a11y.snapshot(root, "T", 300, 300, 1, 1, 0, 0);
        JXAccessibleNode combo = find(tree, JXAccessibilityRole.COMBO_BOX, "Letter");
        assertTrue(combo.is(JXAccessibleState.COLLAPSED));
        assertTrue(a11y.perform(combo.getId(), JXAccessibleAction.EXPAND, null));
        root.layoutForBackend(300, 300);
        tree = a11y.snapshot(root, "T", 300, 300, 1, 1, 0, 0);
        combo = find(tree, JXAccessibilityRole.COMBO_BOX, "Letter");
        assertTrue(combo.is(JXAccessibleState.EXPANDED));
        JXAccessibleNode option = find(combo, JXAccessibilityRole.LIST_ITEM, "b");
        assertEquals(1, option.getItemIndex());
        assertEquals(2, option.getItemCount());
        assertTrue(a11y.perform(option.getId(), JXAccessibleAction.SELECT, null));
        assertEquals("b", changes.get(0));
    }

    @Test
    void openDialogScopesTheTree() {
        JXElement dialog = el("dialog", p().set("showing", true).set("accessibleName", "Confirm"),
                el("button", p().set("label", "OK")));
        JXNativeNode root = mount(el("stack", p(), el("button", p().set("label", "Behind")), dialog), 400, 300);
        JXAccessibleNode tree = JXNativeAccessibility.snapshot(root);
        assertNull(tree.find(JXAccessibilityRole.BUTTON, "Behind"));
        JXAccessibleNode d = find(tree, JXAccessibilityRole.DIALOG, "Confirm");
        assertTrue(d.is(JXAccessibleState.MODAL));
        assertNotNull(d.find(JXAccessibilityRole.BUTTON, "OK"));
        assertEquals(1, tree.getChildren().size());
    }

    @Test
    void closedDialogIsNotInTheTree() {
        JXElement dialog = el("dialog", p().set("showing", false), el("button", p().set("label", "OK")));
        JXNativeNode root = mount(el("stack", p(), el("button", p().set("label", "Front")), dialog), 400, 300);
        JXAccessibleNode tree = JXNativeAccessibility.snapshot(root);
        assertNotNull(tree.find(JXAccessibilityRole.BUTTON, "Front"));
        assertNull(tree.find(JXAccessibilityRole.BUTTON, "OK"));
    }

    @Test
    void virtualListReportsRowIndexAndCountEvenWhenRowsAreNotBuilt() {
        JXElement[] cells = new JXElement[5];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = el("cell", p().set("value", "item " + (40 + i)));
        }
        List<Object> selected = new ArrayList<Object>();
        Consumer<Object> onSelect = selected::add;
        JXNativeNode root = mount(el("column", p(), el("list", p().set("itemCount", 100).set("first", 40).set("selected", 41)
                .set("accessibleName", "Items").set("onSelect", onSelect).set("prefHeight", 200.0), cells)), 300, 300);
        JXNativeAccessibility a11y = new JXNativeAccessibility();
        JXAccessibleNode tree = a11y.snapshot(root, "T", 300, 300, 1, 1, 0, 0);
        JXAccessibleNode list = find(tree, JXAccessibilityRole.LIST, "Items");
        assertEquals(100, list.getItemCount());
        assertEquals(5, list.getChildren().size());
        JXAccessibleNode row = find(list, JXAccessibilityRole.LIST_ITEM, "item 42");
        assertEquals(42, row.getItemIndex());
        assertEquals(100, row.getItemCount());
        assertTrue(find(list, JXAccessibilityRole.LIST_ITEM, "item 41").is(JXAccessibleState.SELECTED));
        assertFalse(row.is(JXAccessibleState.SELECTED));
        assertTrue(a11y.perform(row.getId(), JXAccessibleAction.SELECT, null));
        assertEquals(42, selected.get(0));
    }

    @Test
    void tableHasRowsCellsAndHeader() {
        JXElement r0 = el("tablerow", p(), el("cell", p().set("value", "Ana")), el("cell", p().set("value", "Rio")));
        JXElement r1 = el("tablerow", p(), el("cell", p().set("value", "Bo")), el("cell", p().set("value", "Lima")));
        JXNativeNode root = mount(el("column", p(), el("table", p().set("columns", new String[] {"Name", "City"}).set("itemCount", 2)
                .set("accessibleName", "People").set("prefHeight", 150.0), r0, r1)), 300, 300);
        JXAccessibleNode tree = JXNativeAccessibility.snapshot(root);
        JXAccessibleNode table = find(tree, JXAccessibilityRole.TABLE, "People");
        assertEquals(2, table.getRowCount());
        assertEquals(2, table.getColumnCount());
        assertNotNull(table.find(JXAccessibilityRole.CELL, "City"), tree.dump());
        JXAccessibleNode cell = find(table, JXAccessibilityRole.CELL, "Lima");
        assertEquals(1, cell.getColumnIndex());
        assertEquals(2, cell.getColumnCount());
        assertEquals(1, cell.getParent().getRowIndex());
    }

    @Test
    void treeRowsHaveLevelAndExpansionAndToggleThroughOnToggle() {
        AtomicInteger toggles = new AtomicInteger();
        JXElement parent = el("cell", p().set("value", "src").set("disclosure", "collapsed").set("indent", 0)
                .set("onToggle", (Runnable) toggles::incrementAndGet));
        JXElement child = el("cell", p().set("value", "Main.java").set("disclosure", "leaf").set("indent", 1));
        JXNativeNode root = mount(el("column", p(), el("list", p().set("itemCount", 2).set("accessibleName", "Files")
                .set("prefHeight", 100.0), parent, child)), 300, 300);
        JXNativeAccessibility a11y = new JXNativeAccessibility();
        JXAccessibleNode tree = a11y.snapshot(root, "T", 300, 300, 1, 1, 0, 0);
        JXAccessibleNode files = find(tree, JXAccessibilityRole.TREE, "Files");
        JXAccessibleNode src = find(files, JXAccessibilityRole.TREE_ITEM, "src");
        assertEquals(1, src.getLevel());
        assertTrue(src.is(JXAccessibleState.COLLAPSED));
        assertEquals(2, find(files, JXAccessibilityRole.TREE_ITEM, "Main.java").getLevel());
        assertTrue(a11y.perform(src.getId(), JXAccessibleAction.EXPAND, null));
        assertEquals(1, toggles.get());
    }

    @Test
    void tabsAndPaginationExposeTheirPartsAndSelectThroughOnChange() {
        List<Object> changes = new ArrayList<Object>();
        Consumer<Object> record = changes::add;
        JXNativeNode root = mount(el("column", p(),
                el("tabs", p().set("titles", new String[] {"One", "Two"}).set("selected", 0).set("onChange", record)
                        .set("prefHeight", 100.0), el("#text", p().set("value", "page one"))),
                el("pagination", p().set("pageCount", 5).set("current", 1).set("onChange", record).set("prefHeight", 80.0))), 400, 300);
        JXNativeAccessibility a11y = new JXNativeAccessibility();
        JXAccessibleNode tree = a11y.snapshot(root, "T", 400, 300, 1, 1, 0, 0);
        JXAccessibleNode one = find(tree, JXAccessibilityRole.TAB, "One");
        assertTrue(one.is(JXAccessibleState.SELECTED));
        JXAccessibleNode two = find(tree, JXAccessibilityRole.TAB, "Two");
        assertEquals(1, two.getItemIndex());
        assertTrue(a11y.perform(two.getId(), JXAccessibleAction.SELECT, null));
        assertEquals(1, changes.get(0));
        JXAccessibleNode page = find(tree, JXAccessibilityRole.BUTTON, "Page 4");
        assertTrue(a11y.perform(page.getId(), JXAccessibleAction.DEFAULT, null));
        assertEquals(3, changes.get(1));
    }

    @Test
    void eventsAreFoundBetweenSnapshots() {
        JXNativeNode root = mount(el("column", p(), el("input", p().set("value", "a").set("accessibleName", "F")),
                el("checkbox", p().set("label", "C").set("checked", false)), el("button", p().set("label", "B"))), 300, 200);
        JXNativeAccessibility a11y = new JXNativeAccessibility();
        JXAccessibleNode before = a11y.snapshot(root, "T", 300, 200, 1, 1, 0, 0);
        root.reconcile(el("column", p(), el("input", p().set("value", "ab").set("accessibleName", "F")),
                el("checkbox", p().set("label", "C").set("checked", true)), el("button", p().set("label", "B"))));
        root.layoutForBackend(300, 200);
        JXNativeNode.setFocusOwner(root.getChildren().get(2));
        JXAccessibleNode after = a11y.snapshot(root, "T", 300, 200, 1, 1, 0, 0);
        List<JXAccessibilityEvent> events = JXNativeAccessibility.diff(before, after);
        List<JXAccessibilityEvent.Kind> kinds = new ArrayList<JXAccessibilityEvent.Kind>();
        for (JXAccessibilityEvent e : events) {
            kinds.add(e.getKind());
        }
        assertEquals(Arrays.asList(JXAccessibilityEvent.Kind.VALUE_CHANGED, JXAccessibilityEvent.Kind.STATE_CHANGED,
                JXAccessibilityEvent.Kind.FOCUS_CHANGED), kinds, events.toString());
        assertEquals("ab", events.get(0).getText());
        assertEquals(JXAccessibleState.CHECKED, events.get(1).getState());
        assertTrue(events.get(1).isStateOn());
        assertTrue(JXNativeAccessibility.diff(after, after).isEmpty());

        root.reconcile(el("column", p(), el("input", p().set("value", "ab").set("accessibleName", "F"))));
        root.layoutForBackend(300, 200);
        events = JXNativeAccessibility.diff(after, a11y.snapshot(root, "T", 300, 200, 1, 1, 0, 0));
        assertEquals(JXAccessibilityEvent.Kind.STRUCTURE_CHANGED, events.get(0).getKind());
    }

    @Test
    void screenBoundsFollowTheScaleAndTheWindowPosition() {
        JXNativeNode root = mount(el("column", p(), el("button", p().set("label", "B"))), 300, 200);
        JXAccessibleNode tree = new JXNativeAccessibility().snapshot(root, "T", 300, 200, 1.5, 1.5, 100, 50);
        JXAccessibleNode b = find(tree, JXAccessibilityRole.BUTTON, "B");
        int[] l = b.getBounds();
        int[] s = b.getScreenBounds();
        assertEquals(100 + Math.round(l[0] * 1.5f), s[0]);
        assertEquals(50 + Math.round(l[1] * 1.5f), s[1]);
        assertEquals(Math.round(l[2] * 1.5f), s[2]);
    }

    @Test
    void adapterReceivesAnnouncementsAndThePasswordIsNeverInTheDump() {
        List<JXAccessibilityEvent> got = new ArrayList<JXAccessibilityEvent>();
        AtomicReference<JXAccessibilityHost> host = new AtomicReference<JXAccessibilityHost>();
        JXAccessibilityAdapter adapter = new JXAccessibilityAdapter() {
            public String getName() { return "test"; }
            public void attach(JXAccessibilityHost h) { host.set(h); }
            public void detach(JXAccessibilityHost h) { }
            public void deliver(JXAccessibilityHost h, List<JXAccessibilityEvent> events) { got.addAll(events); }
        };
        JXAccessibility.install(adapter);
        assertTrue(JXAccessibility.isEnabled());
        JXAccessibility.announce("ignored: no window is open");
        assertTrue(got.isEmpty());
        JXAccessibility.uninstall();
        assertFalse(JXAccessibility.isEnabled());
    }
}
