package com.jxparallel.examples.nativeui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.forge.JxmlThemes;
import com.jxparallel.forge.samples.SampleController;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXPointerEvent;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * JXML attributes that change while a window is open, mounted on the native renderer (the forge module cannot see it):
 * a bound theme switched by a reconcile, a selection mode that changes under a list, and the pickers and menus that open
 * by themselves when the file writes no {@code showing}. The trees themselves, interpreted and compiled, are checked
 * by {@code JxmlBindableAttributesTest} in the forge module.
 */
class ForgeMountedAttributesTest {
    private static final String CTRL = "com.jxparallel.forge.samples.SampleController";
    private static final int W = 360;
    private static final int H = 260;
    /** The Ctrl modifier bit of a pointer event ({@code JXInputEvent.CONTROL}). */
    private static final int CONTROL = 2;

    private static JxmlTemplate read(String xml) {
        return JxmlTemplate.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "t.jxml");
    }

    private static int[] paint(JXNativeNode root) {
        int[] argb = new int[W * H];
        JXWindow.paintSoftware(root, argb, W, H);
        return argb;
    }

    private static int pixel(JXNativeNode root, int x, int y) {
        return paint(root)[y * W + x];
    }

    private static JXNativeNode find(JXNativeNode node, String type) {
        if (type.equals(node.getType())) {
            return node;
        }
        for (JXNativeNode child : node.getChildren()) {
            JXNativeNode found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void press(JXNativeNode root, int x, int y, int modifiers) {
        root.pressPointer(new JXPointerEvent(x, y, 0, 1, modifiers));
        root.releasePointer(new JXPointerEvent(x, y, 0));
    }

    private static void press(JXNativeNode root, int x, int y) {
        press(root, x, y, 0);
    }

    private static void pressCenter(JXNativeNode root, JXNativeNode target, int modifiers) {
        press(root, target.getX() + target.getWidth() / 2, target.getY() + target.getHeight() / 2, modifiers);
    }

    /** The application renders again and the window lays out. */
    private static void render(JxmlTemplate view, SampleController c, JXNativeNode root) {
        assertTrue(root.reconcile(view.build(c)), "the same nodes are kept");
        root.layoutForBackend(W, H);
    }

    // ---- theme ------------------------------------------------------------------------------------------------

    @Test
    void aRunningWindowSwitchesThemeAndEveryTokenFollows() {
        JxmlTemplate view = read("<Column controller=\"" + CTRL + "\" theme=\"${themeName}\" background=\"surface\">\n"
                + "<Label text=\"x\" textFill=\"primary\"/>\n<IconButton icon=\"add\" variant=\"filled\"/>\n</Column>");
        SampleController c = new SampleController();
        c.themeName = "light";
        JXNativeNode root = JXNativeNode.createBackendNode(view.build(c));
        root.layoutForBackend(W, H);
        JXNativeNode label = root.getChildren().get(0);
        JXNativeNode icon = root.getChildren().get(1).getChildren().get(0);
        assertEquals("light", root.getProperty("theme"));
        assertEquals(JxmlThemes.color("light", "surface"), pixel(root, 2, 2));
        assertEquals(Integer.valueOf(JxmlThemes.color("light", "primary")), label.getProperty("textFill"));
        assertEquals(Integer.valueOf(JxmlThemes.color("light", "onPrimary")), icon.getProperty("color"));
        c.themeName = "dark";
        render(view, c, root);
        assertEquals("dark", root.getProperty("theme"));
        assertEquals(JxmlThemes.color("dark", "surface"), pixel(root, 2, 2), "the background token follows");
        assertEquals(Integer.valueOf(JxmlThemes.color("dark", "primary")), label.getProperty("textFill"));
        assertEquals(Integer.valueOf(JxmlThemes.color("dark", "onPrimary")), icon.getProperty("color"));
        c.themeName = "light";
        render(view, c, root);
        assertEquals(JxmlThemes.color("light", "surface"), pixel(root, 2, 2));
        assertEquals(Integer.valueOf(JxmlThemes.color("light", "primary")), label.getProperty("textFill"));
    }

    // ---- selection mode ---------------------------------------------------------------------------------------

    @Test
    void aListKeepsAtMostOneRowWhenTheModeSwitchesToSingle() {
        JxmlTemplate view = read("<Column controller=\"" + CTRL + "\">\n<ListView items=\"${rows}\" var=\"r\" rows=\"6\" cellHeight=\"24\" "
                + "selectionMode=\"${mode}\" selection=\"${picked}\" onSelection=\"#setPicked\" selected=\"${selected}\" "
                + "onSelect=\"#setSelected\"><ListItem text=\"${r}\"/></ListView>\n</Column>");
        SampleController c = new SampleController();
        c.mode = "multiple";
        c.selected = 1;
        c.picked = new double[] {1};
        JXNativeNode root = JXNativeNode.createBackendNode(view.build(c));
        root.layoutForBackend(W, H);
        JXNativeNode list = root.getChildren().get(0);
        pressCenter(root, list.getChildren().get(3), CONTROL);
        render(view, c, root);
        assertArrayEquals(new double[] {1, 3}, c.picked, "Ctrl+click adds a row in multiple mode");
        c.mode = "single";
        render(view, c, root);
        assertEquals("single", list.getProperty("selectionMode"));
        double[] before = c.picked;
        pressCenter(root, list.getChildren().get(4), CONTROL);
        assertEquals(4, c.selected, "a click selects that one row");
        assertArrayEquals(before, c.picked, "single mode reports no multiple selection, even with Ctrl held");
        c.mode = "multiple";
        render(view, c, root);
        pressCenter(root, list.getChildren().get(2), CONTROL);
        assertTrue(c.picked.length >= 1 && Arrays.stream(c.picked).anyMatch(v -> v == 2), "and back to multiple");
    }

    // ---- pickers and menus that open by themselves ------------------------------------------------------------

    @Test
    void theControlsWithAPopupOpenAndCloseByThemselvesWithoutShowing() {
        String[] tags = {
            "<ComboBox items=\"A, B, C\" value=\"A\"/>", "<ChoiceBox items=\"A, B\"/>", "<DatePicker value=\"2026-02-03\"/>",
            "<TimePicker value=\"09:30\"/>", "<ColorPicker value=\"#E91E63\"/>",
            "<MenuButton text=\"Menu\"><MenuItem text=\"One\"/><MenuItem text=\"Two\"/></MenuButton>"};
        String[] types = {"select", "select", "datepicker", "timepicker", "colorpicker", "menubutton"};
        for (int t = 0; t < tags.length; t++) {
            JxmlTemplate view = read("<Column controller=\"" + CTRL + "\" padding=\"8\">\n" + tags[t] + "\n</Column>");
            SampleController c = new SampleController();
            JXNativeNode root = JXNativeNode.createBackendNode(view.build(c));
            root.layoutForBackend(W, H);
            JXNativeNode host = find(root, types[t]);
            assertNull(host.getProperty("showing"), tags[t] + ": no showing prop");
            int[] closed = paint(root);
            pressCenter(root, host, 0);
            render(view, c, root); // the application renders again after the gesture; the node keeps its own state
            assertFalse(Arrays.equals(closed, paint(root)), tags[t] + ": it opened");
            press(root, W - 2, H - 2); // a press far from the popup closes it
            render(view, c, root);
            assertArrayEquals(closed, paint(root), tags[t] + ": it closed");
            pressCenter(root, host, 0);
            render(view, c, root);
            assertFalse(Arrays.equals(closed, paint(root)), tags[t] + ": it opened again");
            root.routeKey(new com.jxparallel.ui.native2d.JXKeyEvent(256, '\0'), host); // Escape
            render(view, c, root);
            assertArrayEquals(closed, paint(root), tags[t] + ": Escape closed it");
        }
    }

    @Test
    void anUncontrolledPopupStillTellsOnShowingAndAControlledOneOnlyAsks() {
        JxmlTemplate view = read("<Column controller=\"" + CTRL + "\" padding=\"8\">\n"
                + "<ComboBox items=\"A, B, C\" value=\"A\" onShowing=\"#setOpen\"/>\n"
                + "<ComboBox items=\"A, B, C\" value=\"A\" showing=\"${hidden}\" onShowing=\"#setOpen\"/>\n</Column>");
        SampleController c = new SampleController();
        JXNativeNode root = JXNativeNode.createBackendNode(view.build(c));
        root.layoutForBackend(W, H);
        JXNativeNode free = root.getChildren().get(0);
        JXNativeNode controlled = root.getChildren().get(1);
        int[] closed = paint(root);
        pressCenter(root, controlled, 0);
        assertEquals(Arrays.asList("showing true"), c.calls, "the controlled one only asks");
        render(view, c, root);
        assertArrayEquals(closed, paint(root), "the application did not open it, so it stays closed");
        pressCenter(root, free, 0);
        assertEquals(Arrays.asList("showing true", "showing true"), c.calls, "the free one opens and tells");
        render(view, c, root);
        assertFalse(Arrays.equals(closed, paint(root)), "the free one is open");
    }
}
