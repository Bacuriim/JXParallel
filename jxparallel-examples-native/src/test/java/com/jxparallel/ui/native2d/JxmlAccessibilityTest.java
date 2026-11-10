package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.jxparallel.ui.accessibility.JXAccessibilityRole;
import com.jxparallel.ui.accessibility.JXAccessibilityTree;
import com.jxparallel.ui.accessibility.JXAccessibleAction;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.accessibility.JXAccessibleState;

/**
 * The accessibility tree of the JXML screens of {@link JxmlComponentBehaviorTest}: what a screen reader would be told
 * for the controls a JXML author writes, the JXML attributes that shape it, and the actions it can run.
 */
class JxmlAccessibilityTest {
    @AfterEach
    void unfocus() {
        JXNativeNode.setFocusOwner(null);
    }

    private static JXAccessibleNode node(JXAccessibleNode root, JXAccessibilityRole role, String name) {
        JXAccessibleNode n = root.find(role, name);
        assertNotNull(n, role + " \"" + name + "\" in\n" + JXAccessibilityTree.dump(root, false));
        return n;
    }

    @Test
    void formControlsAndTheJxmlAccessibilityAttributes() {
        JxmlComponentBehaviorTest.Ui ui = new JxmlComponentBehaviorTest.Ui(
                "<Label text=\"Name\" labelFor=\"who\"/>"
                + "<TextField key=\"who\" text=\"@{name}\" accessibleDescription=\"Your full name\"/>"
                + "<PasswordField text=\"@{secret}\" accessibleName=\"PIN\"/>"
                + "<Button text=\"Save\" accessibleName=\"Save the form\" onAction=\"#click\"/>"
                + "<Button text=\"Ghost\" accessibleHidden=\"true\"/>"
                + "<CheckBox text=\"Agree\" selected=\"@{agree}\"/>"
                + "<Slider accessibleName=\"Volume\" min=\"0\" max=\"10\" value=\"3\"/>"
                + "<Label text=\"Decoration\" accessibleRole=\"separator\"/>");
        ui.c.setSecret("hunter2");
        ui.refresh();
        JXAccessibleNode tree = JXNativeAccessibility.snapshot(ui.root);

        JXAccessibleNode field = node(tree, JXAccessibilityRole.TEXT_FIELD, "Name");
        assertEquals("Your full name", field.getDescription());
        JXAccessibleNode pin = node(tree, JXAccessibilityRole.TEXT_FIELD, "PIN");
        assertTrue(pin.is(JXAccessibleState.PASSWORD));
        assertFalse(JXAccessibilityTree.dump(tree).contains("hunter2"));
        node(tree, JXAccessibilityRole.BUTTON, "Save the form");
        assertNull(tree.find(JXAccessibilityRole.BUTTON, "Ghost"));
        assertEquals(10.0, node(tree, JXAccessibilityRole.SLIDER, "Volume").getMax());
        assertNotNull(tree.find(JXAccessibilityRole.SEPARATOR, "Decoration"));
    }

    @Test
    void actionsDriveTheControllerLikeAUser() {
        JxmlComponentBehaviorTest.Ui ui = new JxmlComponentBehaviorTest.Ui(
                "<Button text=\"Save\" onAction=\"#click\"/><CheckBox text=\"Agree\" selected=\"@{agree}\"/>"
                + "<TextField text=\"@{name}\"/>");
        JXNativeAccessibility a11y = new JXNativeAccessibility();
        JXAccessibleNode tree = a11y.snapshot(ui.root, "T", ui.w, ui.h, 1, 1, 0, 0);
        assertTrue(a11y.perform(node(tree, JXAccessibilityRole.BUTTON, "Save").getId(), JXAccessibleAction.DEFAULT, null));
        assertEquals(1, ui.c.clicks);
        assertTrue(a11y.perform(node(tree, JXAccessibilityRole.CHECK_BOX, "Agree").getId(), JXAccessibleAction.DEFAULT, null));
        ui.refresh();
        assertTrue(ui.c.getAgree());
        JXAccessibleNode field = tree.find(JXAccessibilityRole.TEXT_FIELD, "");
        assertNotNull(field, JXAccessibilityTree.dump(tree, false));
        assertTrue(a11y.perform(field.getId(), JXAccessibleAction.SET_VALUE, "typed by a reader"));
        assertEquals("typed by a reader", ui.c.getName());
    }

    @Test
    void aListOfTwoHundredItemsKeepsItsCountWhenOnlyAWindowIsBuilt() {
        JxmlComponentBehaviorTest.Ui ui = new JxmlComponentBehaviorTest.Ui(
                "<ListView accessibleName=\"Rows\" items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\" rows=\"30\""
                + " prefHeight=\"240\"><ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView>");
        JXAccessibleNode tree = JXNativeAccessibility.snapshot(ui.root);
        JXAccessibleNode list = node(tree, JXAccessibilityRole.LIST, "Rows");
        assertEquals(200, list.getItemCount());
        assertTrue(list.getChildren().size() < 200, "only a window of the rows is built");
        JXAccessibleNode row = node(list, JXAccessibilityRole.LIST_ITEM, "Row 3");
        assertEquals(3, row.getItemIndex());
        assertEquals(200, row.getItemCount());
    }

    @Test
    void tabsOfAScreenAreListedAndTheOtherPagesAreNot() {
        JxmlComponentBehaviorTest.Ui ui = new JxmlComponentBehaviorTest.Ui(
                "<Tabs selected=\"@{tab}\"><Tab title=\"First\"><Label text=\"one\"/></Tab><Tab title=\"Second\"><Label text=\"two\"/></Tab></Tabs>");
        JXAccessibleNode tree = JXNativeAccessibility.snapshot(ui.root);
        assertTrue(node(tree, JXAccessibilityRole.TAB, "First").is(JXAccessibleState.SELECTED));
        assertFalse(node(tree, JXAccessibilityRole.TAB, "Second").is(JXAccessibleState.SELECTED));
        assertNotNull(tree.find(JXAccessibilityRole.TEXT, "one"));
        assertNull(tree.find(JXAccessibilityRole.TEXT, "two"));
    }
}
