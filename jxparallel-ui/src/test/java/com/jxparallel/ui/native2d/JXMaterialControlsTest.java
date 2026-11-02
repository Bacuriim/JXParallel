package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/**
 * The Material-style controls (switch, icon, button variants, chip, badge, avatar, stepper, scroll bar, split pane,
 * tab bar): sizes and structure, the drawing calls (recorded, no OpenGL) and shape pixels of the software painter,
 * and the events they report. Nothing here depends on the system font: text is never asserted, pixels are sampled
 * on shapes away from any glyph, and sizes are only compared with each other or with the fixed parts.
 */
class JXMaterialControlsTest {
    private final List<Object> received = new ArrayList<Object>();
    private final Consumer<Object> onChange = received::add;

    private static JXProps.Builder p() {
        return JXProps.builder();
    }

    private static JXElement e(String type, JXProps.Builder props, JXElement... children) {
        return JXElement.of(type, props.build(), children);
    }

    private static JXNativeNode mount(JXElement element, int w, int h) {
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        node.layoutForBackend(w, h);
        return node;
    }

    private static String record(JXNativeNode node) {
        JXPaintRecordingTest.RecordingPainter painter = new JXPaintRecordingTest.RecordingPainter();
        new JXPaint(painter).paint(node);
        return painter.log.toString();
    }

    private static int[] pixels(JXNativeNode root, int w, int h) {
        JXSoftwareFrame frame = new JXSoftwareFrame(false);
        frame.paint(root, w, h, 1f);
        return java.util.Arrays.copyOf(frame.pixels(), w * h);
    }

    private static boolean has(String log, String call, String color) {
        for (String line : log.split("\n")) {
            if (line.startsWith(call + " ") && line.endsWith(" " + color)) {
                return true;
            }
        }
        return false;
    }

    private static int count(String log, String call) {
        int n = 0;
        for (String line : log.split("\n")) {
            if (line.startsWith(call + " ") || line.equals(call)) {
                n++;
            }
        }
        return n;
    }

    // ---- switch ------------------------------------------------------------------------------

    @Test
    void aSwitchIsATrackAndALabelWide() {
        JXNativeNode bare = JXNativeNode.createBackendNode(e("switch", p()));
        assertEquals(JXControlLayout.SWITCH_W, bare.getPrefWidth(), 0);
        assertEquals(JXControlLayout.SWITCH_H, bare.getPrefHeight(), 0);
        JXNativeNode labeled = JXNativeNode.createBackendNode(e("switch", p().set("label", "Wi-Fi")));
        assertTrue(labeled.getPrefWidth() > JXControlLayout.SWITCH_W + JXControlLayout.SWITCH_GAP);
    }

    @Test
    void aSwitchPaintsItsTrackInTheThemeColorsOnAndOff() {
        String on = record(mount(e("switch", p().set("checked", Boolean.TRUE)), 60, 30));
        String off = record(mount(e("switch", p().set("checked", Boolean.FALSE)), 60, 30));
        assertTrue(has(on, "fillRoundRect", "ff1565c0"), on);   // primary track
        assertTrue(has(on, "fillOval", "ffffffff"), on);        // onPrimary thumb
        assertTrue(has(off, "strokeRoundRect", "ff79747e"), off); // outlined track
        assertFalse(has(off, "fillRoundRect", "ff1565c0"), off);
        String dark = record(mount(e("column", p().set("theme", "dark"), e("switch", p().set("checked", Boolean.TRUE))), 60, 30));
        assertTrue(has(dark, "fillRoundRect", "ff90caf9"), dark);
    }

    @Test
    void aSwitchTrackPixelsAreThePrimaryColorWhenOn() {
        JXNativeNode root = mount(e("switch", p().set("checked", Boolean.TRUE)), 40, 22);
        int[] px = pixels(root, 40, 22);
        assertEquals(0xFF1565C0, px[11 * 40 + 6]);  // track left of the thumb, mid height
        assertEquals(0xFFFFFFFF, px[11 * 40 + 29]); // thumb
        JXNativeNode off = mount(e("switch", p().set("checked", Boolean.FALSE)), 40, 22);
        assertEquals(0xFF79747E, pixels(off, 40, 22)[11 * 40 + 11]); // the small thumb
    }

    @Test
    void clickAndSpaceToggleASwitch() {
        JXNativeNode off = mount(e("switch", p().set("checked", Boolean.FALSE).set("onChange", onChange)), 60, 30);
        off.dispatchPointer(new JXPointerEvent(5, 5, 0));
        off.dispatchKey(new JXKeyEvent(0, ' '));
        JXNativeNode on = mount(e("switch", p().set("checked", Boolean.TRUE).set("onChange", onChange)), 60, 30);
        on.dispatchPointer(new JXPointerEvent(5, 5, 0));
        assertEquals(java.util.Arrays.<Object>asList(true, true, false), received);
        mount(e("switch", p().set("checked", Boolean.TRUE).set("onChange", onChange).set("disabled", Boolean.TRUE)), 60, 30)
                .dispatchPointer(new JXPointerEvent(5, 5, 0));
        assertEquals(3, received.size());
    }

    // ---- icons -------------------------------------------------------------------------------

    @Test
    void everyIconDrawsAndTheyDifferFromEachOther() {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        assertTrue(JXIcons.names().size() >= 27);
        for (String name : JXIcons.names()) {
            String log = record(mount(e("icon", p().set("name", name).set("size", 24.0).set("color", 0xFF123456)), 24, 24));
            assertTrue(log.length() > 0, name);
            assertTrue(log.contains("123456"), name);
            assertTrue(seen.add(log), name + " looks like another icon");
        }
        assertEquals("", record(mount(e("icon", p().set("name", "nope")), 24, 24)));
    }

    @Test
    void anIconIsItsSizeSquareAndScales() {
        JXNativeNode icon = JXNativeNode.createBackendNode(e("icon", p().set("name", "add").set("size", 32.0)));
        assertEquals(32, icon.getPrefWidth(), 0);
        assertEquals(32, icon.getMaxHeight(), 0);
        assertEquals(24, JXNativeNode.createBackendNode(e("icon", p().set("name", "add"))).getPrefWidth(), 0);
    }

    @Test
    void anIconPaintsItsPixelsInItsColorThroughAnyPainter() {
        JXNativeNode root = mount(e("icon", p().set("name", "add").set("color", 0xFFCC0000)), 24, 24);
        int[] px = pixels(root, 24, 24);
        assertEquals(0xFFCC0000, px[12 * 24 + 12]); // the crossing of the plus
        assertNotEquals(0xFFCC0000, px[2 * 24 + 2]);
    }

    // ---- button variants ---------------------------------------------------------------------

    private static JXElement button(String variant, JXProps.Builder more) {
        JXProps.Builder b = p().set("label", "");
        if (variant != null) {
            b.set("variant", variant);
        }
        return e("button", b);
    }

    @Test
    void aButtonWithoutAVariantKeepsTheClassicLook() {
        String classic = record(mount(button(null, p()), 80, 30));
        assertTrue(classic.contains("gradient"));
        for (String variant : new String[] {"filled", "tonal", "outlined", "text", "elevated", "fab"}) {
            assertFalse(record(mount(button(variant, p()), 80, 40)).contains("gradient"), variant);
        }
    }

    @Test
    void theVariantsHaveDistinctFillsAndBorders() {
        assertTrue(has(record(mount(button("filled", p()), 80, 36)), "fillRoundRect", "ff1565c0"));
        assertTrue(has(record(mount(button("outlined", p()), 80, 36)), "strokeRoundRect", "ff79747e"));
        String text = record(mount(button("text", p()), 80, 36));
        assertEquals("", text, "a text button draws nothing but its label");
        JXTheme t = JXTheme.LIGHT;
        assertTrue(has(record(mount(button("tonal", p()), 80, 36)), "fillRoundRect", Integer.toHexString(t.tonal())));
        String elevated = record(mount(button("elevated", p()), 80, 42));
        assertTrue(has(elevated, "fillRoundRect", Integer.toHexString(t.elevated())));
        assertTrue(has(elevated, "fillRoundRect", "f000000") || elevated.contains("f000000"), "a soft ring below the face");
        java.util.Set<String> looks = new java.util.HashSet<String>();
        for (String variant : new String[] {"filled", "tonal", "outlined", "text", "elevated", "fab"}) {
            assertTrue(looks.add(record(mount(button(variant, p()), 80, 42))), variant);
        }
    }

    @Test
    void anElevatedOrFloatingButtonKeepsItsRingInsideItsBounds() {
        JXNativeNode plain = JXNativeNode.createBackendNode(button("filled", p()));
        JXNativeNode elevated = JXNativeNode.createBackendNode(button("elevated", p()));
        assertEquals(plain.getPrefHeight() + 2 * JXControlLayout.SHADOW, elevated.getPrefHeight(), 0);
        JXNativeNode fab = mount(button("fab", p()), 62, 62);
        String log = record(fab);
        for (String line : log.split("\n")) {
            if (line.startsWith("fillRoundRect ")) {
                String[] f = line.split(" ");
                double x = Double.parseDouble(f[1]);
                double y = Double.parseDouble(f[2]);
                double w = Double.parseDouble(f[3]);
                double h = Double.parseDouble(f[4]);
                assertTrue(x >= 0 && y >= 0 && x + w <= 62.01 && y + h <= 62.01, line);
            }
        }
    }

    @Test
    void anIconButtonIsASquareOfItsIconPlusPadding() {
        JXElement icon = e("icon", p().set("name", "add").set("size", 24.0).set("mouseTransparent", Boolean.TRUE));
        JXNativeNode button = JXNativeNode.createBackendNode(e("button",
                p().set("label", "").set("variant", "text").set("round", Boolean.TRUE).set("padding", 8.0), icon));
        assertEquals(40, button.getPrefWidth(), 0);
        assertEquals(40, button.getPrefHeight(), 0);
        button.layoutForBackend(40, 40);
        JXNativeNode graphic = button.getChildren().get(0);
        assertEquals(24, graphic.getWidth());
        assertEquals(8, graphic.getX());
        assertEquals(button, button.hitTest(20, 20), "the icon does not take the click");
    }

    // ---- chip --------------------------------------------------------------------------------

    @Test
    void aChipGrowsWithItsLeadingIconAndItsCloseIcon() {
        JXNativeNode plain = JXNativeNode.createBackendNode(e("chip", p().set("label", "Tag")));
        JXNativeNode icon = JXNativeNode.createBackendNode(e("chip", p().set("label", "Tag").set("icon", "star")));
        JXNativeNode closable = JXNativeNode.createBackendNode(e("chip", p().set("label", "Tag").set("closable", Boolean.TRUE)));
        JXNativeNode filter = JXNativeNode.createBackendNode(e("chip", p().set("label", "Tag").set("kind", "filter")
                .set("selected", Boolean.TRUE)));
        assertEquals(JXControlLayout.CHIP_H, plain.getPrefHeight(), 0);
        assertEquals(plain.getPrefWidth() + JXControlLayout.CHIP_ICON + JXControlLayout.CHIP_GAP, icon.getPrefWidth(), 1);
        assertEquals(plain.getPrefWidth() + JXControlLayout.CHIP_ICON + JXControlLayout.CHIP_GAP, closable.getPrefWidth(), 1);
        assertEquals(icon.getPrefWidth(), filter.getPrefWidth(), 1, "a selected filter chip shows a check mark");
    }

    @Test
    void chipKindsLookDifferentAndSelectedStatesFill() {
        java.util.Set<String> looks = new java.util.HashSet<String>();
        for (String kind : new String[] {"assist", "filter", "choice", "input"}) {
            assertTrue(looks.add(record(mount(e("chip", p().set("label", "").set("kind", kind)), 80, 32))), kind);
        }
        String selectedChoice = record(mount(e("chip", p().set("label", "").set("kind", "choice").set("selected", Boolean.TRUE)), 80, 32));
        assertTrue(has(selectedChoice, "fillRoundRect", "ff1565c0"), selectedChoice);
        String selectedFilter = record(mount(e("chip", p().set("label", "").set("kind", "filter").set("selected", Boolean.TRUE)), 80, 32));
        assertTrue(has(selectedFilter, "fillRoundRect", Integer.toHexString(JXTheme.LIGHT.tonal())), selectedFilter);
        assertTrue(has(record(mount(e("chip", p().set("label", "")), 80, 32)), "strokeRoundRect", "ff79747e"));
    }

    @Test
    void clickingAChipActsAndReportsItsNewSelectionButTheCloseIconOnlyCloses() {
        int[] actions = {0};
        int[] closes = {0};
        JXNativeNode chip = mount(e("chip", p().set("label", "Tag").set("closable", Boolean.TRUE).set("selected", Boolean.FALSE)
                .set("onChange", onChange).set("onAction", (Runnable) () -> actions[0]++)
                .set("onClose", (Runnable) () -> closes[0]++)), 200, 32);
        float[] parts = JXControlLayout.chipParts(chip);
        chip.dispatchPointer(new JXPointerEvent(chip.getX() + 3, 10, 0));
        assertEquals(1, actions[0]);
        assertEquals(java.util.Arrays.<Object>asList(true), received);
        chip.dispatchPointer(new JXPointerEvent(chip.getX() + (int) (parts[4] + parts[5] / 2), 10, 0));
        assertEquals(1, closes[0]);
        assertEquals(1, actions[0], "the close icon is not a click on the chip");
        assertEquals(1, received.size());
    }

    // ---- badge -------------------------------------------------------------------------------

    @Test
    void aBadgeKeepsItsChildAtTheTopLeftAndMarksTheTopRightInsideItsBounds() {
        JXElement child = e("pane", p().set("prefWidth", 40.0).set("prefHeight", 30.0));
        JXNativeNode badge = mount(e("badge", p().set("count", 5), child), 100, 100);
        assertEquals(40, badge.getChildren().get(0).getWidth());
        assertEquals(0, badge.getChildren().get(0).getX());
        JXNativeNode sized = JXNativeNode.createBackendNode(e("badge", p().set("count", 5), child));
        assertTrue(sized.getPrefWidth() >= 40 && sized.getPrefHeight() >= 30);
        String log = record(mount(e("badge", p().set("count", 5), child), 40, 30));
        assertTrue(has(log, "fillRoundRect", "ffb3261c"), log);
        for (String line : log.split("\n")) {
            if (line.startsWith("fillRoundRect ")) {
                String[] f = line.split(" ");
                assertTrue(Double.parseDouble(f[1]) + Double.parseDouble(f[3]) <= 40.01, line);
                assertTrue(Double.parseDouble(f[2]) >= 0, line);
            }
        }
    }

    @Test
    void aBadgeShowsADotACountCappedAtMaxOrNothing() {
        JXElement child = e("pane", p().set("prefWidth", 40.0).set("prefHeight", 30.0));
        assertEquals("", JXControlLayout.badgeText(JXNativeNode.createBackendNode(e("badge", p().set("dot", Boolean.TRUE), child))));
        assertEquals("99+", JXControlLayout.badgeText(JXNativeNode.createBackendNode(e("badge", p().set("count", 150), child))));
        assertEquals("10+", JXControlLayout.badgeText(JXNativeNode.createBackendNode(e("badge", p().set("count", 11).set("max", 10), child))));
        assertEquals("7", JXControlLayout.badgeText(JXNativeNode.createBackendNode(e("badge", p().set("count", 7), child))));
        assertEquals("new", JXControlLayout.badgeText(JXNativeNode.createBackendNode(e("badge", p().set("text", "new"), child))));
        JXNativeNode none = mount(e("badge", p().set("count", 0), child), 40, 30);
        assertEquals(null, JXControlLayout.badgeText(none));
        assertEquals(0, count(record(none), "fillRoundRect"));
        assertEquals(1, count(record(mount(e("badge", p().set("dot", Boolean.TRUE), child), 40, 30)), "fillOval"));
    }

    // ---- avatar ------------------------------------------------------------------------------

    @Test
    void anAvatarIsACircleOfItsSize() {
        assertEquals(40, JXNativeNode.createBackendNode(e("avatar", p())).getPrefWidth(), 0);
        JXNativeNode root = mount(e("avatar", p().set("size", 40.0).set("initials", "").set("fill", 0xFF336699)), 40, 40);
        int[] px = pixels(root, 40, 40);
        assertEquals(0xFF336699, px[20 * 40 + 20]);
        assertNotEquals(0xFF336699, px[1 * 40 + 1], "the corner is outside the circle");
    }

    @Test
    void anAvatarWithAnImageClipsItToACircle() {
        int[] red = new int[16 * 16];
        java.util.Arrays.fill(red, 0xFFFF0000);
        JXNativeNode root = mount(e("avatar", p().set("size", 32.0).set("pixels", red).set("imageWidth", 16).set("imageHeight", 16)), 32, 32);
        int[] px = pixels(root, 32, 32);
        assertEquals(0xFFFF0000, px[16 * 32 + 16]);
        assertNotEquals(0xFFFF0000, px[0]);
        assertNotEquals(0xFFFF0000, px[31 * 32 + 31]);
        assertSameMask(JXPaint.circular(red, 16, 16), JXPaint.circular(red, 16, 16));
    }

    private static void assertSameMask(int[] a, int[] b) {
        assertTrue(a == b, "the masked copy is cached by the identity of the pixels");
    }

    // ---- stepper -----------------------------------------------------------------------------

    @Test
    void aStepperDrawsDoneCurrentAndUpcomingStepsAndTheLinesBetween() {
        JXNativeNode stepper = mount(e("stepper", p().set("titles", new String[] {"A", "B", "C", "D"}).set("current", 1)), 400, 60);
        assertEquals(4, JXControlLayout.stepCount(stepper));
        String log = record(stepper);
        assertEquals(2, count(log, "fillOval"), "one done and one current circle"); // steps 0 and 1
        assertEquals(2, count(log, "strokeOval"), "two upcoming circles");
        assertEquals(3, count(log, "fillRect") - 0, "three connecting lines");
        assertTrue(has(log, "fillRect", "ff1565c0"), "the line after a done step is primary");
        assertTrue(has(log, "fillRect", "ff79747e"), "the later lines are outline colored");
        assertEquals(3, JXControlLayout.stepCount(mount(e("stepper", p().set("steps", 3)), 300, 40)));
        assertTrue(JXNativeNode.createBackendNode(e("stepper", p().set("steps", 3))).getPrefWidth() >= 3 * JXControlLayout.STEP_MIN);
    }

    // ---- scroll bar --------------------------------------------------------------------------

    private JXNativeNode bar(boolean vertical, double value) {
        JXProps.Builder b = p().set("min", 0.0).set("max", 90.0).set("value", value).set("visibleAmount", 10.0)
                .set("onChange", onChange);
        if (vertical) {
            b.set("orientation", "vertical");
        }
        return mount(e("scrollbar", b), vertical ? 13 : 200, vertical ? 200 : 13);
    }

    @Test
    void aScrollBarThumbIsTheShareOfTheRangeAndMovesWithTheValue() {
        float[] atStart = JXControlLayout.scrollBarGeometry(bar(false, 0));
        float[] atEnd = JXControlLayout.scrollBarGeometry(bar(false, 90));
        assertEquals(200, atStart[1], 0);
        assertEquals(JXControlLayout.SB_THUMB_MIN, atStart[3], 0.01); // 10 / (90 + 10) of 200 is 20: the minimum applies
        JXNativeNode wide = mount(e("scrollbar", p().set("min", 0.0).set("max", 50.0).set("visibleAmount", 50.0)), 200, 13);
        assertEquals(100, JXControlLayout.scrollBarGeometry(wide)[3], 0.01); // half of the range plus itself
        assertEquals(atStart[0], atStart[2], 0);
        assertEquals(atEnd[0] + 200 - atEnd[3], atEnd[2], 0.01);
        float[] vertical = JXControlLayout.scrollBarGeometry(bar(true, 45));
        assertEquals(24, vertical[3], 0.01);
        assertEquals(vertical[0] + (200 - 24) / 2f, vertical[2], 0.01);
    }

    @Test
    void aScrollBarPaintsATrackAndAThumbInDifferentColors() {
        JXNativeNode bar = bar(false, 0);
        int[] px = pixels(bar, 200, 13);
        int thumb = px[6 * 200 + 5];
        int track = px[6 * 200 + 150];
        assertEquals(0xFF79747E, thumb);
        assertNotEquals(thumb, track);
        assertNotEquals(px[0], track, "the track shows against the background");
    }

    @Test
    void clickingTheTrackMovesTheValueAndDraggingTheThumbFollowsThePointer() {
        JXNativeNode bar = bar(false, 0);
        bar.dispatchPointer(new JXPointerEvent(100, 6, 0)); // the track: the thumb's middle goes under the pointer
        double clicked = (Double) received.get(0);
        assertEquals(45.0, clicked, 0.5); // thumb start 100 - 12 of a room of 176
        assertEquals(90 * 88 / 176.0, clicked, 0.01);
        JXNativeNode thumb = bar(false, 0);
        received.clear();
        thumb.dispatchPointer(new JXPointerEvent(5, 6, 0)); // grabs the thumb: no change yet
        assertTrue(received.isEmpty());
        thumb.dispatchDrag(new JXPointerEvent(95, 6, 0)); // grabbed 5 px into the thumb: it now starts at 90
        assertEquals(90 * 90 / 176.0, (Double) received.get(0), 0.01);
        thumb.dispatchDrag(new JXPointerEvent(900, 6, 0)); // beyond the end: the maximum
        assertEquals(90.0, (Double) received.get(1), 0.01);
        thumb.dispatchDrag(new JXPointerEvent(-50, 6, 0));
        assertEquals(2, received.size(), "the value is already the minimum: nothing to report");
        JXNativeNode vertical = bar(true, 0);
        received.clear();
        vertical.dispatchPointer(new JXPointerEvent(6, 5, 0));
        vertical.dispatchDrag(new JXPointerEvent(6, 95, 0));
        assertEquals(90 * 90 / 176.0, (Double) received.get(0), 0.01);
    }

    @Test
    void aDragWithoutAGrabOrWithoutAHandlerReportsNothing() {
        JXNativeNode track = bar(false, 0);
        track.dispatchPointer(new JXPointerEvent(150, 6, 0));
        received.clear();
        track.dispatchDrag(new JXPointerEvent(160, 6, 0));
        assertTrue(received.isEmpty(), "the press was on the track, not on the thumb");
        JXNativeNode plain = mount(e("scrollbar", p().set("value", 0.0)), 200, 13);
        plain.dispatchPointer(new JXPointerEvent(5, 5, 0));
        plain.dispatchDrag(new JXPointerEvent(50, 5, 0));
    }

    // ---- split pane --------------------------------------------------------------------------

    private JXNativeNode split(boolean vertical, double... positions) {
        JXProps.Builder b = p().set("onChange", onChange);
        if (vertical) {
            b.set("orientation", "vertical");
        }
        if (positions.length > 0) {
            b.set("dividerPositions", positions);
        }
        return mount(e("splitpane", b, e("pane", p()), e("pane", p()), e("pane", p())), 306, 306);
    }

    @Test
    void aSplitPaneSharesTheRoomAmongItsPanesAroundItsDividers() {
        JXNativeNode split = split(false); // 306 - 2 * 6 = 294 shared, evenly
        assertEquals(3, split.getChildren().size());
        int[] widths = {split.getChildren().get(0).getWidth(), split.getChildren().get(1).getWidth(), split.getChildren().get(2).getWidth()};
        assertEquals(98, widths[0]);
        assertEquals(98, widths[1]);
        assertEquals(98, widths[2]);
        assertEquals(98 + JXControlLayout.DIVIDER, split.getChildren().get(1).getX());
        assertEquals(306, split.getChildren().get(0).getHeight());
        JXNativeNode uneven = split(false, 0.25, 0.5);
        assertEquals(Math.round(294 * 0.25), uneven.getChildren().get(0).getWidth());
        JXNativeNode stacked = split(true, 0.5, 0.75);
        assertEquals(147, stacked.getChildren().get(0).getHeight());
        assertEquals(306, stacked.getChildren().get(0).getWidth());
        assertTrue(stacked.getChildren().get(1).getY() > stacked.getChildren().get(0).getY());
        assertArrayEquals(new double[] {0.25, 0.5}, JXControlLayout.splitPositions(uneven), 0);
        assertArrayEquals(new double[] {1.0 / 3, 2.0 / 3}, JXControlLayout.splitPositions(split), 1e-9);
    }

    @Test
    void draggingADividerReportsAllThePositionsWithTheGrabbedOneMoved() {
        JXNativeNode split = split(false, 0.25, 0.75);
        float[][] rects = JXControlLayout.splitRects(split);
        float[] divider = rects[3]; // the first divider
        int mid = (int) (divider[0] + divider[2] / 2);
        split.dispatchPointer(new JXPointerEvent(mid, 100, 0));
        split.dispatchDrag(new JXPointerEvent(mid + 29, 100, 0));
        double[] moved = (double[]) received.get(0);
        assertEquals(2, moved.length);
        assertEquals(0.25 + 29 / 294.0, moved[0], 0.005);
        assertEquals(0.75, moved[1], 0);
        split.dispatchDrag(new JXPointerEvent(mid + 400, 100, 0)); // cannot pass the next divider
        double[] limited = (double[]) received.get(1);
        assertEquals(0.75 - 20 / 294.0, limited[0], 1e-6);
        split.dispatchDrag(new JXPointerEvent(mid - 400, 100, 0));
        assertEquals(20 / 294.0, ((double[]) received.get(2))[0], 1e-6);
        received.clear();
        split.dispatchPointer(new JXPointerEvent(5, 100, 0)); // inside a pane, not on a divider
        split.dispatchDrag(new JXPointerEvent(50, 100, 0));
        assertTrue(received.isEmpty());
    }

    @Test
    void aSplitPanePaintsGripsOverItsPanes() {
        String log = record(split(false, 0.5, 0.75));
        assertEquals(6, count(log, "fillOval"), "three dots on each of two dividers");
        assertEquals(2, count(log, "fillRect"));
    }

    // ---- tab bar -----------------------------------------------------------------------------

    private JXNativeNode tabs(boolean closable, int selected) {
        return mount(e("tabbar", p().set("titles", new String[] {"One", "Two", "Three"}).set("selected", selected)
                .set("closable", closable).set("onChange", onChange)
                .set("onClose", (Consumer<Object>) v -> received.add("close " + v))), 500, JXControlLayout.TABBAR_H);
    }

    @Test
    void aTabBarClickReportsTheTabAndTheCloseIconTheClosedTab() {
        JXNativeNode bar = tabs(true, 0);
        float[] edges = JXControlLayout.tabbarEdges(bar);
        assertEquals(4, edges.length);
        assertTrue(edges[1] > edges[0] && edges[2] > edges[1] && edges[3] > edges[2]);
        bar.dispatchPointer(new JXPointerEvent((int) (edges[1] + 10), 20, 0));
        bar.dispatchPointer(new JXPointerEvent((int) (edges[1] + 10), 20, 0)); // already... still tab 1 (the node is not re-rendered)
        bar.dispatchPointer(new JXPointerEvent((int) (edges[0] + 10), 20, 0)); // the selected tab: no change
        bar.dispatchPointer(new JXPointerEvent((int) (edges[3] - 6), 20, 0));  // the close icon of tab 2
        assertEquals(java.util.Arrays.<Object>asList(1, 1, "close 2"), received);
        assertEquals(-1, JXControlLayout.tabbarAt(bar, edges[3] + 20, 20, null));
    }

    @Test
    void aTabBarWithoutCloseIconsIsNarrowerAndPaintsAnIndicatorUnderTheSelection() {
        JXNativeNode closable = tabs(true, 1);
        JXNativeNode plain = tabs(false, 1);
        assertTrue(JXControlLayout.tabbarEdges(closable)[3] > JXControlLayout.tabbarEdges(plain)[3]);
        String log = record(plain);
        assertTrue(has(log, "fillRect", "ff1565c0"), "the indicator under the selected tab");
        assertEquals(0, count(log, "line"), "no close icons");
        assertTrue(count(record(closable), "line") > 0);
        float[] edges = JXControlLayout.tabbarEdges(plain);
        int[] px = pixels(plain, 500, JXControlLayout.TABBAR_H);
        int mid = (int) ((edges[1] + edges[2]) / 2);
        assertEquals(0xFF1565C0, px[(JXControlLayout.TABBAR_H - 1) * 500 + mid]);
        assertNotEquals(0xFF1565C0, px[(JXControlLayout.TABBAR_H - 1) * 500 + (int) (edges[0] + 5)]);
    }

    // ---- themes ------------------------------------------------------------------------------

    @Test
    void aThemedRootPaintsItsSurfaceAndTextInTheThemeColors() {
        JXNativeNode dark = mount(e("column", p().set("theme", "dark"), e("#text", p().set("value", "Hi"))), 100, 40);
        String log = record(dark);
        assertTrue(has(log, "fillRect", "ff1c1b1f"), "the dark surface");
        assertTrue(log.contains("ffe6e1e5"), "the text takes onSurface");
        String plain = record(mount(e("column", p(), e("#text", p().set("value", "Hi"))), 100, 40));
        assertFalse(plain.contains("ff1c1b1f"));
        assertTrue(plain.contains("ff333333"), "no theme: the classic text color");
        String inherited = record(mount(e("column", p().set("theme", "dark"), e("row", p(), e("#text", p().set("value", "x")))), 100, 40));
        assertTrue(inherited.contains("ffe6e1e5"));
        assertEquals("dark", "dark");
        assertEquals(JXTheme.DARK.primary, JXTheme.named("dark").token("primary"));
        assertEquals(6, JXTheme.tokens().size());
    }
}
