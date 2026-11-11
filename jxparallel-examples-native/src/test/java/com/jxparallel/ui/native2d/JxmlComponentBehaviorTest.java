package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.jxparallel.forge.JxmlTemplate;
import com.jxparallel.ui.JXElement;

/**
 * What a user expects of each control of a JXML screen in a desktop {@link JXWindow} with no input listener: every
 * gesture goes through the same calls the window makes for GLFW's callbacks (see {@link Ui}), and the screen is built
 * again after each one, as an application does. A failure is a basic behaviour that does not work.
 */
class JxmlComponentBehaviorTest {
    private static final String CTRL = BehaviorController.class.getName();
    private static final int ESCAPE = 256;
    private static final int ENTER = 257;
    private static final int TAB = 258;
    private static final int BACKSPACE = 259;
    private static final int DELETE = 261;
    private static final int RIGHT = 262;
    private static final int LEFT = 263;
    private static final int DOWN = 264;
    private static final int UP = 265;
    private static final int HOME = 268;
    private static final int END = 269;

    /** A window of {@code w} x {@code h} showing a JXML screen, driven as {@link JXWindow} drives its root. */
    static final class Ui {
        final BehaviorController c = new BehaviorController();
        final JxmlTemplate template;
        final int w;
        final int h;
        JXNativeNode root;
        JXNativeNode focused;
        JXNativeNode drag;

        Ui(String body) {
            this(body, 400, 400);
        }

        Ui(String body, int w, int h) {
            this(w, h, "<Stack controller=\"" + CTRL + "\">\n<Column padding=\"10\" gap=\"8\">\n" + body + "\n</Column>\n</Stack>");
        }

        /** A screen written whole: the root is yours, e.g. {@code <Stack controller=...>} with an overlay over the content. */
        Ui(int w, int h, String xml) {
            this.template = JxmlTemplate.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "behavior.jxml");
            this.w = w;
            this.h = h;
            refresh();
        }

        /** The application renders again: {@code setContent(view.build(controller))}, then the window lays out. */
        void refresh() {
            JXElement e = template.build(c);
            if (root == null || !root.reconcile(e)) {
                root = JXNativeNode.createBackendNode(e);
                focused = null;
            }
            root.layoutForBackend(w, h);
        }

        /** GLFW mouse button callback, press (primary 0, secondary 1). */
        JXNativeNode press(int x, int y, int button) {
            return press(x, y, button, 0);
        }

        /** The press with Shift ({@link JXInputEvent#SHIFT}) and Ctrl ({@link JXInputEvent#CONTROL}) held. */
        JXNativeNode press(int x, int y, int button, int modifiers) {
            pointerX = x;
            pointerY = y;
            JXNativeNode hit = root.pressPointer(new JXPointerEvent(x, y, button, 1, modifiers));
            if (hit != null && button == 0) {
                focused = hit;
                JXNativeNode.setFocusOwner(hit);
                drag = hit;
            }
            refresh();
            return hit;
        }

        JXNativeNode press(int x, int y) {
            return press(x, y, 0);
        }

        int pointerX;
        int pointerY;

        /** GLFW mouse button callback, release (of the primary button, where the pointer is now). */
        void release() {
            drag = null;
            root.releasePointer(new JXPointerEvent(pointerX, pointerY, 0));
            refresh();
        }

        /** The pointer moves with the button held, to (x, y), over whatever is there. */
        void moveTo(int x, int y) {
            pointerX = x;
            pointerY = y;
            dragTo(x, y);
            root.movePointer(x, y);
        }

        /** A double (2) or triple (3) click at (x, y): GLFW counts the presses, the last one carries the count. */
        void clicks(int x, int y, int count) {
            for (int i = 1; i <= count; i++) {
                pointerX = x;
                pointerY = y;
                JXNativeNode hit = root.pressPointer(new JXPointerEvent(x, y, 0, i));
                if (hit != null) {
                    focused = hit;
                    JXNativeNode.setFocusOwner(hit);
                }
                refresh();
                release();
            }
        }

        /** A key with Shift ({@link JXInputEvent#SHIFT}) and Ctrl ({@link JXInputEvent#CONTROL}) held. */
        void key(int code, int modifiers) {
            focusAfter(root.routeKey(new JXKeyEvent(code, '\0', modifiers), focused));
            refresh();
        }

        /** As JXWindow: the control a popup or menu belongs to has the focus again once Escape closed it. */
        private void focusAfter(JXNativeNode owner) {
            if (owner != focused) {
                focused = owner;
                JXNativeNode.setFocusOwner(owner);
            }
        }

        /** The pointer moves to (x, y) with no button held. */
        void hover(int x, int y) {
            root.hoverPointer(x, y);
            refresh();
        }

        JXNativeNode click(int x, int y) {
            JXNativeNode hit = press(x, y);
            release();
            return hit;
        }

        /** A click with modifier keys held. */
        JXNativeNode click(int x, int y, int modifiers) {
            JXNativeNode hit = press(x, y, 0, modifiers);
            release();
            return hit;
        }

        /** What the screen paints at (x, y): the whole window is painted with the software painter. */
        int pixel(int x, int y) {
            int[] argb = new int[w * h];
            JXWindow.paintSoftware(root, argb, w, h);
            return argb[y * w + x];
        }

        JXNativeNode click(JXNativeNode node) {
            int[] c = center(node);
            return click(c[0], c[1]);
        }

        /** GLFW cursor callback with the button held. */
        void dragTo(int x, int y) {
            pointerX = x;
            pointerY = y;
            if (drag != null) {
                drag.dispatchDrag(new JXPointerEvent(x, y, 0));
                refresh();
            }
        }

        /** GLFW key callback (press). */
        void key(int code) {
            if (code == TAB) { // as JXWindow: Tab moves the focus
                JXNativeNode next = root.nextFocus(focused, false);
                if (next != null) {
                    focused = next;
                    JXNativeNode.setFocusOwner(next);
                }
            } else {
                focusAfter(root.routeKey(new JXKeyEvent(code, '\0'), focused));
            }
            refresh();
        }

        /** Typing: GLFW sends the key, then the character (which an open popup or menu that took the key does not get again). */
        void type(String text) {
            for (char ch : text.toCharArray()) {
                boolean taken = root.capturesKeys();
                focusAfter(root.routeKey(new JXKeyEvent(Character.toUpperCase(ch), '\0'), focused));
                refresh();
                if (focused != null && !taken) {
                    focused.dispatchKey(new JXKeyEvent(0, ch));
                }
                refresh();
            }
        }

        /** GLFW scroll callback: {@code notches} positive is towards the user (down the content). */
        boolean wheel(int x, int y, double notches) {
            boolean moved = JXScrolling.wheel(root, x, y, 0, -notches, false);
            refresh();
            return moved;
        }

        JXNativeNode find(String type) {
            return find(type, 0);
        }

        JXNativeNode find(String type, int index) {
            int[] seen = {0};
            JXNativeNode n = find(root, type, index, seen);
            assertNotNull(n, "no " + type + " #" + index + " on the screen");
            return n;
        }

        private static JXNativeNode find(JXNativeNode node, String type, int index, int[] seen) {
            if (type.equals(node.getType()) && seen[0]++ == index) {
                return node;
            }
            for (JXNativeNode child : node.getChildren()) {
                JXNativeNode n = find(child, type, index, seen);
                if (n != null) {
                    return n;
                }
            }
            return null;
        }

        /** The text of the first cell (or label) of each row a list or table shows inside its viewport. */
        String deepestTextAt(int x, int y) {
            List<JXNativeNode> path = root.hitPath(x, y);
            for (int i = path.size() - 1; i >= 0; i--) {
                Object v = path.get(i).getProperty("value");
                if (v instanceof String && !((String) v).isEmpty()) {
                    return (String) v;
                }
            }
            return null;
        }
    }

    static int[] center(JXNativeNode n) {
        return new int[] {n.getX() + n.getWidth() / 2, n.getY() + n.getHeight() / 2};
    }

    static int[] center(int[] r) {
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    // ---- buttons -------------------------------------------------------------------------------

    @Nested
    class ButtonBehaviour {
        @Test
        void aClickRunsOnActionOnce() {
            Ui ui = new Ui("<Button text=\"Save\" onAction=\"#click\"/>");
            ui.click(ui.find("button"));
            assertEquals(1, ui.c.clicks);
        }

        @Test
        void aDisabledButtonIgnoresClicks() {
            Ui ui = new Ui("<Button text=\"Save\" disabled=\"true\" onAction=\"#click\"/>");
            ui.click(ui.find("button"));
            assertEquals(0, ui.c.clicks);
        }

        @Test
        void theActionRunsOnReleaseNotOnPress() {
            Ui ui = new Ui("<Button text=\"Save\" onAction=\"#click\"/>");
            int[] c = center(ui.find("button"));
            ui.press(c[0], c[1]);
            assertEquals(0, ui.c.clicks, "a press alone (the user may still slide off the button) must not run the action");
            ui.release();
            assertEquals(1, ui.c.clicks, "the release over it does");
            ui.press(c[0], c[1]);
            ui.moveTo(ui.w - 3, ui.h - 3);
            ui.release();
            assertEquals(1, ui.c.clicks, "sliding off before the release cancels the click");
        }

        @Test
        void enterInATextFieldPressesTheDefaultButton() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<Button text=\"Save\" defaultButton=\"true\" onAction=\"#click\"/>"
                    + "\n<Modal showing=\"false\"><Button text=\"Hidden\" defaultButton=\"true\" onAction=\"#toggleBold\"/></Modal>");
            ui.click(ui.find("input"));
            ui.type("x");
            ui.key(ENTER);
            assertEquals(1, ui.c.clicks, "Enter in a field presses the screen's default button");
            assertTrue(!ui.c.getBold(), "not the one of a closed dialog");
        }

        @Test
        void spaceOnTheFocusedButtonRunsIt() {
            Ui ui = new Ui("<Button text=\"Save\" onAction=\"#click\"/>");
            ui.click(ui.find("button"));
            int before = ui.c.clicks;
            ui.type(" ");
            assertEquals(before + 1, ui.c.clicks, "Space on the focused button");
        }

        @Test
        void enterOnTheFocusedButtonRunsIt() {
            Ui ui = new Ui("<Button text=\"Save\" onAction=\"#click\"/>");
            ui.click(ui.find("button"));
            int before = ui.c.clicks;
            ui.key(ENTER);
            assertEquals(before + 1, ui.c.clicks, "Enter on the focused button");
        }

        @Test
        void aHyperlinkClickRunsOnAction() {
            Ui ui = new Ui("<Hyperlink text=\"More\" onAction=\"#click\"/>");
            ui.click(ui.find("hyperlink"));
            assertEquals(1, ui.c.clicks);
        }

        @Test
        void aToggleButtonClickRunsOnAction() {
            Ui ui = new Ui("<ToggleButton text=\"Bold\" selected=\"${bold}\" onAction=\"#toggleBold\"/>");
            ui.click(ui.find("toggle"));
            assertTrue(ui.c.getBold());
            ui.click(ui.find("toggle"));
            assertTrue(!ui.c.getBold(), "a second click unselects");
        }

        @Test
        void aRadioGroupChoosesTheClickedValue() {
            Ui ui = new Ui("<RadioButton text=\"Left\" group=\"@{align}\" value=\"left\"/>\n"
                    + "<RadioButton text=\"Right\" group=\"@{align}\" value=\"right\"/>");
            ui.click(ui.find("radio", 1));
            assertEquals("right", ui.c.getAlign());
            assertEquals(Boolean.TRUE, ui.find("radio", 1).getProperty("checked"), "the clicked one shows selected");
            assertEquals(Boolean.FALSE, ui.find("radio", 0).getProperty("checked"), "the other one no longer does");
        }
    }

    // ---- toggles -------------------------------------------------------------------------------

    @Nested
    class Toggles {
        @Test
        void aCheckBoxClickTogglesIt() {
            Ui ui = new Ui("<CheckBox text=\"Agree\" selected=\"@{agree}\"/>");
            ui.click(ui.find("checkbox"));
            assertTrue(ui.c.getAgree());
            ui.click(ui.find("checkbox"));
            assertTrue(!ui.c.getAgree());
        }

        @Test
        void spaceTogglesTheFocusedCheckBox() {
            Ui ui = new Ui("<CheckBox text=\"Agree\" selected=\"@{agree}\"/>");
            ui.click(ui.find("checkbox"));
            ui.type(" ");
            assertTrue(!ui.c.getAgree(), "click on, Space off");
        }

        @Test
        void aSwitchClickAndSpaceToggleIt() {
            Ui ui = new Ui("<Switch text=\"Wi-Fi\" selected=\"@{on}\"/>");
            ui.click(ui.find("switch"));
            assertTrue(ui.c.getOn());
            ui.type(" ");
            assertTrue(!ui.c.getOn());
        }

        @Test
        void aDisabledSwitchIgnoresClicks() {
            Ui ui = new Ui("<Switch text=\"Wi-Fi\" selected=\"@{on}\" disabled=\"true\"/>");
            ui.click(ui.find("switch"));
            assertTrue(!ui.c.getOn());
        }

        @Test
        void aChipClickSelectsAndItsCloseIconCloses() {
            Ui ui = new Ui("<Chip text=\"Filter\" kind=\"filter\" selected=\"@{chip}\" closable=\"true\" onClose=\"#closeChip\"/>");
            JXNativeNode chip = ui.find("chip");
            ui.click(chip.getX() + 10, center(chip)[1]);
            assertTrue(ui.c.getChip(), "a click on the chip selects it");
            int closeX = -1;
            for (int x = chip.getX() + chip.getWidth() - 1; x > chip.getX(); x--) {
                if (JXControlLayout.chipCloseAt(chip, x)) {
                    closeX = x - 2;
                    break;
                }
            }
            assertTrue(closeX > 0, "a closable chip has a close icon");
            ui.click(closeX, center(chip)[1]);
            assertEquals(1, ui.c.chipClosed);
            assertTrue(ui.c.getChip(), "closing is not a click on the chip");
        }
    }

    // ---- text ----------------------------------------------------------------------------------

    @Nested
    class Text {
        @Test
        void typingBackspaceAndDeleteEditAtTheCaret() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>");
            ui.click(ui.find("input"));
            ui.type("hello");
            assertEquals("hello", ui.c.getName());
            ui.key(BACKSPACE);
            assertEquals("hell", ui.c.getName());
            ui.key(LEFT);
            ui.key(LEFT);
            ui.type("X");
            assertEquals("heXll", ui.c.getName(), "typing goes in at the caret");
            ui.key(HOME);
            ui.key(DELETE);
            assertEquals("eXll", ui.c.getName());
            ui.key(END);
            ui.type("!");
            assertEquals("eXll!", ui.c.getName());
        }

        @Test
        void aClickPutsTheCaretWhereItLands() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>");
            ui.c.setName("hello");
            ui.refresh();
            JXNativeNode field = ui.find("input");
            ui.click(field.getX() + 3, center(field)[1]); // before the first letter
            ui.type("X");
            assertEquals("Xhello", ui.c.getName(), "a click at the start of the text puts the caret there");
        }

        @Test
        void typingGoesToTheFieldClickedLast() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<TextField text=\"@{other}\"/>");
            ui.click(ui.find("input", 0));
            ui.type("a");
            ui.click(ui.find("input", 1));
            ui.type("b");
            assertEquals("a", ui.c.getName());
            assertEquals("b", ui.c.getOther());
        }

        @Test
        void tabMovesTheFocusToTheNextField() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<TextField text=\"@{other}\"/>");
            ui.click(ui.find("input", 0));
            ui.key(TAB);
            ui.type("z");
            assertEquals("z", ui.c.getOther(), "Tab moves to the next field");
            assertEquals("", ui.c.getName());
        }

        @Test
        void theFocusedFieldDrawsItsFocusAndItsCaretWhereTheTypingGoes() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<TextField text=\"@{other}\"/>");
            ui.click(ui.find("input", 0));
            ui.type("abc");
            ui.key(LEFT);
            assertEquals(Boolean.TRUE, ui.find("input", 0).getProperty("focused"), "the focus ring is drawn");
            assertEquals(Integer.valueOf(2), ui.find("input", 0).getProperty("caret"), "the caret is drawn before the c");
            assertNull(ui.find("input", 1).getProperty("focused"), "the other field has no focus");
        }

        @Test
        void tabStaysInsideAnOpenDialog() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<Modal showing=\"${dialog}\" onShowing=\"#setDialog\">"
                    + "<TextField text=\"@{other}\"/><Button text=\"OK\" onAction=\"#click\"/></Modal>");
            ui.click(ui.find("input", 1));
            ui.key(TAB);
            ui.key(TAB);
            ui.type("q");
            assertEquals("q", ui.c.getOther(), "Tab went from the dialog's field to its button and back, not to the screen behind");
        }

        @Test
        void aPasswordFieldTakesTyping() {
            Ui ui = new Ui("<PasswordField text=\"@{secret}\"/>");
            ui.click(ui.find("password"));
            ui.type("s3cr3t");
            assertEquals("s3cr3t", ui.c.getSecret());
        }

        @Test
        void enterAddsALineInATextArea() {
            Ui ui = new Ui("<TextArea text=\"@{notes}\" rows=\"4\"/>");
            ui.click(ui.find("textarea"));
            ui.type("ab");
            ui.key(ENTER);
            ui.type("cd");
            assertEquals("ab\ncd", ui.c.getNotes());
        }

        @Test
        void upAndDownMoveTheCaretBetweenLinesOfATextArea() {
            Ui ui = new Ui("<TextArea text=\"@{notes}\" rows=\"4\"/>");
            ui.click(ui.find("textarea"));
            ui.type("ab");
            ui.key(ENTER);
            ui.type("cd");
            ui.key(UP);
            ui.type("X");
            assertEquals("abX\ncd", ui.c.getNotes(), "Up goes to the line above, same column");
        }

        @Test
        void theWheelScrollsALongTextArea() {
            Ui ui = new Ui("<TextArea text=\"@{notes}\" rows=\"4\" prefHeight=\"80\"/>");
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                s.append("line ").append(i).append('\n');
            }
            ui.c.setNotes(s.toString());
            ui.refresh();
            int[] c = center(ui.find("textarea"));
            assertTrue(ui.wheel(c[0], c[1], 3), "the wheel scrolls a text area longer than its height");
        }
    }

    // ---- numbers -------------------------------------------------------------------------------

    @Nested
    class Numbers {
        /** The window x of {@code fraction} along a slider's track (7 px of thumb room at each end). */
        private int along(JXNativeNode s, double fraction) {
            return (int) Math.round(s.getX() + 7 + fraction * (s.getWidth() - 14));
        }

        @Test
        void aClickOnTheSliderTrackSetsTheValue() {
            Ui ui = new Ui("<Slider value=\"@{volume}\"/>");
            JXNativeNode s = ui.find("slider");
            ui.click(along(s, 0.75), center(s)[1]);
            assertEquals(75, ui.c.getVolume(), 1);
        }

        @Test
        void draggingTheSliderThumbMovesTheValue() {
            Ui ui = new Ui("<Slider value=\"@{volume}\"/>");
            JXNativeNode s = ui.find("slider");
            int y = center(s)[1];
            ui.press(along(s, 0.5), y); // on the thumb: value 50
            ui.dragTo(along(s, 0.7), y);
            ui.release();
            assertEquals(70, ui.c.getVolume(), 1, "the thumb follows the drag");
        }

        @Test
        void arrowKeysStepTheFocusedSlider() {
            Ui ui = new Ui("<Slider value=\"@{volume}\"/>");
            JXNativeNode s = ui.find("slider");
            ui.click(along(s, 0.5), center(s)[1]);
            double before = ui.c.getVolume();
            ui.key(RIGHT);
            assertTrue(ui.c.getVolume() > before, "Right increases the value");
        }

        @Test
        void upAndDownStepASpinnerWithinItsRange() {
            Ui ui = new Ui("<Spinner value=\"${countText}\" min=\"0\" max=\"6\" onChange=\"#setCount\"/>");
            ui.click(ui.find("spinner").getX() + 5, center(ui.find("spinner"))[1]);
            ui.key(UP);
            assertEquals(6, ui.c.getCount(), 0);
            ui.key(UP);
            assertEquals(6, ui.c.getCount(), 0, "not above max");
            ui.key(DOWN);
            assertEquals(5, ui.c.getCount(), 0);
        }

        @Test
        void theSpinnerArrowButtonsStepIt() {
            Ui ui = new Ui("<Spinner value=\"${countText}\" min=\"0\" max=\"10\" onChange=\"#setCount\"/>");
            JXNativeNode s = ui.find("spinner");
            int bx = s.getX() + s.getWidth() - JXControlLayout.SPINNER_BUTTON / 2;
            ui.click(bx, s.getY() + 3);
            assertEquals(6, ui.c.getCount(), 0, "the up arrow adds one");
            ui.click(bx, s.getY() + s.getHeight() - 3);
            assertEquals(5, ui.c.getCount(), 0, "the down arrow takes one");
        }
    }

    // ---- pickers ---------------------------------------------------------------------------------

    @Nested
    class Pickers {
        private static final String COMBO = "<ComboBox items=\"Apple, Banana, Cherry\" value=\"@{fruit}\" showing=\"${fruitOpen}\" onShowing=\"#setFruitOpen\"/>";

        @Test
        void aComboBoxOpensOnClickAndAClickOnAnOptionChoosesItAndCloses() {
            Ui ui = new Ui(COMBO);
            ui.click(ui.find("select"));
            assertTrue(ui.c.getFruitOpen(), "a click opens the list");
            int[][] cells = ui.find("select").getPopupCells();
            assertEquals(3, cells.length, "the three options are listed");
            int[] c = center(cells[2]);
            ui.click(c[0], c[1]);
            assertEquals("Cherry", ui.c.getFruit());
            assertTrue(!ui.c.getFruitOpen(), "choosing closes the list");
        }

        @Test
        void escapeAndAClickOutsideCloseAComboBox() {
            Ui ui = new Ui(COMBO + "\n<Button text=\"x\" onAction=\"#click\"/>");
            ui.click(ui.find("select"));
            ui.key(ESCAPE);
            assertTrue(!ui.c.getFruitOpen(), "Escape closes");
            ui.click(ui.find("select"));
            assertTrue(ui.c.getFruitOpen());
            ui.click(ui.w - 5, ui.h - 5);
            assertTrue(!ui.c.getFruitOpen(), "a click outside closes");
            assertEquals(0, ui.c.clicks);
        }

        @Test
        void upAndDownChangeTheFocusedComboBox() {
            Ui ui = new Ui(COMBO);
            ui.click(ui.find("select"));
            ui.key(ESCAPE);
            ui.key(DOWN);
            assertEquals("Banana", ui.c.getFruit());
            ui.key(UP);
            assertEquals("Apple", ui.c.getFruit());
        }

        @Test
        void aDatePickerOpensItsCalendarAndAClickOnADayChoosesIt() {
            Ui ui = new Ui("<DatePicker value=\"@{date}\" showing=\"${dateOpen}\" onShowing=\"#setDateOpen\"/>");
            ui.click(ui.find("datepicker"));
            assertTrue(ui.c.getDateOpen(), "a click opens the calendar");
            JXNativeNode calendar = JXOverlays.popup(ui.find("datepicker")).calendar;
            assertNotNull(calendar);
            int[] at = null;
            for (int y = calendar.getY(); y < calendar.getY() + calendar.getHeight() && at == null; y += 2) {
                for (int x = calendar.getX(); x < calendar.getX() + calendar.getWidth(); x += 2) {
                    int[] hit = JXCalendar.hit(calendar, x, y);
                    if (hit[0] == JXCalendar.DAY && hit[2] == 9 && hit[3] == 20) {
                        at = new int[] {x, y};
                        break;
                    }
                }
            }
            assertNotNull(at, "the 20th is in the calendar");
            ui.click(at[0], at[1]);
            assertEquals("2026-09-20", ui.c.getDate());
            assertTrue(!ui.c.getDateOpen(), "choosing closes the calendar");
        }

        @Test
        void aTimePickerOpensAndAClickOnAMinuteChangesTheTime() {
            Ui ui = new Ui("<TimePicker value=\"@{time}\" showing=\"${timeOpen}\" onShowing=\"#setTimeOpen\"/>");
            ui.click(ui.find("timepicker"));
            assertTrue(ui.c.getTimeOpen());
            int[][] cells = ui.find("timepicker").getPopupCells();
            assertEquals(24 + 12, cells.length, "24 hours and 12 minutes");
            int[] c = center(cells[24 + 9]); // minute 45
            ui.click(c[0], c[1]);
            assertEquals("10:45", ui.c.getTime());
        }

        @Test
        void aColorPickerOpensAndAClickOnASwatchChoosesIt() {
            Ui ui = new Ui("<ColorPicker value=\"@{color}\" showing=\"${colorOpen}\" onShowing=\"#setColorOpen\"/>");
            ui.click(ui.find("colorpicker"));
            assertTrue(ui.c.getColorOpen());
            int[][] cells = ui.find("colorpicker").getPopupCells();
            assertTrue(cells.length >= 40);
            int[] c = center(cells[5]);
            ui.click(c[0], c[1]);
            assertTrue(!"#FF0000".equals(ui.c.getColor()), "the swatch's color is chosen, was " + ui.c.getColor());
        }
    }

    // ---- bars, dividers, tabs, pages -----------------------------------------------------------------

    @Nested
    class BarsAndTabs {
        @Test
        void aScrollBarTakesATrackClickAndAThumbDrag() {
            Ui ui = new Ui("<ScrollBar value=\"@{bar}\" prefWidth=\"300\"/>");
            JXNativeNode bar = ui.find("scrollbar");
            float[] g = JXControlLayout.scrollBarGeometry(bar);
            int y = center(bar)[1];
            ui.click((int) (g[0] + g[1] - 20), y);
            assertTrue(ui.c.getBar() > 50, "a click near the end of the track moves there, was " + ui.c.getBar());
            g = JXControlLayout.scrollBarGeometry(bar);
            int grab = (int) (g[2] + g[3] / 2);
            ui.press(grab, y);
            ui.dragTo(grab - 2000, y);
            ui.release();
            assertEquals(0, ui.c.getBar(), 0.001, "dragging the thumb to the start");
        }

        @Test
        void draggingASplitPaneDividerMovesIt() {
            Ui ui = new Ui("<SplitPane dividerPositions=\"@{split}\" prefHeight=\"100\">"
                    + "<Label text=\"A\"/><Label text=\"B\"/></SplitPane>", 400, 400);
            JXNativeNode split = ui.find("splitpane");
            float[][] r = JXControlLayout.splitRects(split);
            float[] divider = r[split.getChildren().size()];
            int x = (int) (divider[0] + divider[2] / 2);
            int y = (int) (divider[1] + divider[3] / 2);
            ui.press(x, y);
            ui.dragTo(x + 60, y);
            ui.release();
            assertTrue(ui.c.getSplit()[0] > 0.6, "the divider followed the drag, at " + ui.c.getSplit()[0]);
        }

        @Test
        void aTabBarClickSelectsAndItsCloseIconCloses() {
            Ui ui = new Ui("<TabBar titles=\"One, Two, Three\" selected=\"@{tab}\" closable=\"true\" onClose=\"#closeTab\"/>");
            JXNativeNode bar = ui.find("tabbar");
            int y = center(bar)[1];
            int x2 = -1;
            int close3 = -1;
            boolean[] close = new boolean[1];
            for (int x = bar.getX(); x < bar.getX() + bar.getWidth(); x++) {
                close[0] = false;
                int t = JXControlLayout.tabbarAt(bar, x, y, close);
                if (t == 1 && !close[0] && x2 < 0) {
                    x2 = x + 3;
                }
                if (t == 2 && close[0] && close3 < 0) {
                    close3 = x + 2;
                }
            }
            ui.click(x2, y);
            assertEquals(1, ui.c.getTab());
            ui.click(close3, y);
            assertEquals(2, ui.c.tabClosed);
        }

        @Test
        void aClickOnATabHeaderShowsThatTab() {
            Ui ui = new Ui("<Tabs selected=\"@{tab}\" prefHeight=\"200\">"
                    + "<Tab title=\"First\"><Label text=\"first content\"/></Tab>"
                    + "<Tab title=\"Second\"><Label text=\"second content\"/></Tab></Tabs>");
            JXNativeNode tabs = ui.find("tabs");
            float[] tx = JXControlLayout.tabPositions(tabs);
            ui.click((int) (tabs.getX() + (tx[1] + tx[2]) / 2), tabs.getY() + JXControlLayout.TAB_HEADER / 2 + 2);
            assertEquals("second content", ui.find("#text").getProperty("value"), "the second tab is shown");
        }

        @Test
        void theCloseIconOfATabClosesItAndIsOnlyThereWithOnClose() {
            String tabs = "<Tab title=\"First\"><Label text=\"a\"/></Tab><Tab title=\"Second\"><Label text=\"b\"/></Tab></Tabs>";
            Ui plain = new Ui("<Tabs selected=\"@{tab}\" prefHeight=\"200\">" + tabs);
            assertTrue(!JXControlLayout.tabClosable(plain.find("tabs"), 0), "no onClose: no close icon that would do nothing");
            Ui ui = new Ui("<Tabs selected=\"@{tab}\" onClose=\"#closeTab\" prefHeight=\"200\">" + tabs);
            JXNativeNode node = ui.find("tabs");
            assertTrue(JXControlLayout.tabClosable(node, 0));
            float[] tx = JXControlLayout.tabPositions(node);
            ui.click((int) (node.getX() + tx[1] - JXControlLayout.TAB_PAD - 4), node.getY() + JXControlLayout.TAB_HEADER / 2 + 2);
            assertEquals(0, ui.c.tabClosed, "the close icon of the first tab");
            assertEquals(0, ui.c.getTab(), "closing is not selecting");
        }

        @Test
        void aClickOnATitledPaneHeaderTogglesIt() {
            Ui ui = new Ui("<TitledPane text=\"Details\" expanded=\"${expanded}\" onAction=\"#toggleExpanded\">"
                    + "<Label text=\"inside\"/></TitledPane>");
            JXNativeNode titled = ui.find("titled");
            ui.click(titled.getX() + 30, titled.getY() + 8);
            assertTrue(!ui.c.getExpanded(), "a click on the header collapses it");
        }

        @Test
        void aClickOnAPageNumberChangesThePage() {
            Ui ui = new Ui("<Pagination pageCount=\"5\" current=\"@{page}\" prefHeight=\"120\"><Label text=\"page\"/></Pagination>");
            JXNativeNode p = ui.find("pagination");
            float[] three = null;
            for (float[] b : JXControlLayout.pageButtons(p)) {
                if ((int) b[4] == 2) {
                    three = b;
                }
            }
            assertNotNull(three, "page 3 has a button");
            ui.click((int) (three[0] + three[2] / 2), (int) (three[1] + three[3] / 2));
            assertEquals(2, ui.c.getPage(), "the click on page 3");
            JXNativeNode next = ui.find("pagination");
            float[][] buttons = JXControlLayout.pageButtons(next);
            float[] arrow = buttons[buttons.length - 1];
            ui.click((int) (arrow[0] + arrow[2] / 2), (int) (arrow[1] + arrow[3] / 2));
            assertEquals(3, ui.c.getPage(), "the next arrow");
        }
    }

    // ---- scrolling -------------------------------------------------------------------------------

    @Nested
    class Scrolling {
        private String rowsInAScroll() {
            return "<Scroll fitToWidth=\"true\" prefHeight=\"200\"><Column gap=\"4\">"
                    + "<For items=\"${rows}\" var=\"r\" type=\"java.lang.String\" limit=\"40\">"
                    + "<Button text=\"${r}\" onAction=\"#pick(r)\" prefHeight=\"30\"/></For></Column></Scroll>";
        }

        @Test
        void theWheelScrollsAPaneAndAClickHitsWhatIsShownThere() {
            Ui ui = new Ui(rowsInAScroll());
            JXNativeNode pane = ui.find("scroll");
            int[] c = center(pane);
            assertTrue(ui.wheel(c[0], c[1], 3), "the wheel scrolls");
            assertEquals(120, pane.userScrollY, 0.5, "3 notches of 40 px");
            JXNativeNode shown = null;
            for (JXNativeNode row : pane.getChildren().get(0).getChildren()) {
                if (row.getY() >= pane.getY() && row.getY() + row.getHeight() <= pane.getY() + pane.getHeight()) {
                    shown = row;
                    break;
                }
            }
            assertNotNull(shown);
            String expected = (String) shown.getProperty("label");
            ui.click(shown);
            assertEquals(expected, ui.c.picked.get(0), "the click hits the row drawn under the pointer");
        }

        @Test
        void theScrollPositionSurvivesARenderAfterAClick() {
            Ui ui = new Ui(rowsInAScroll());
            JXNativeNode pane = ui.find("scroll");
            int[] c = center(pane);
            ui.wheel(c[0], c[1], 5);
            ui.click(pane.getChildren().get(0).getChildren().get(6));
            assertEquals(200, ui.find("scroll").userScrollY, 0.5, "re-rendering after the click keeps the position");
        }

        @Test
        void theWheelReachesTheEndOfAPaneAndStops() {
            Ui ui = new Ui(rowsInAScroll());
            JXNativeNode pane = ui.find("scroll");
            int[] c = center(pane);
            for (int i = 0; i < 100; i++) {
                ui.wheel(c[0], c[1], 1);
            }
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(pane);
            assertEquals(g.maxScrollY(), pane.userScrollY, 0.5);
            assertTrue(!ui.wheel(c[0], c[1], 1), "nothing moves past the end");
        }

        @Test
        void thePaneScrollBarTakesAThumbDragAndATrackClick() {
            Ui ui = new Ui(rowsInAScroll());
            JXNativeNode pane = ui.find("scroll");
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(pane);
            int x = (int) (g.vx + g.vw / 2);
            int grab = (int) (g.vThumbY + g.vThumbH / 2);
            ui.press(x, grab);
            ui.dragTo(x, grab + 50);
            ui.release();
            assertTrue(pane.userScrollY > 0, "the thumb drag scrolls");
            ui.click(x, (int) (g.vy + g.vh - JXControlLayout.BAR_BUTTON - 3));
            assertTrue(pane.userScrollY > g.maxScrollY() * 0.8, "a click near the end of the track goes there");
        }

        @Test
        void aListViewOfTwoHundredItemsReachesItsLastItemWithTheWheel() {
            Ui ui = new Ui("<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\" rows=\"30\" prefHeight=\"240\">"
                    + "<ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView>");
            JXNativeNode list = ui.find("list");
            int[] c = center(list);
            for (int i = 0; i < 400; i++) {
                ui.wheel(c[0], c[1], 1);
            }
            String bottom = ui.deepestTextAt(c[0], list.getY() + list.getHeight() - 6);
            assertEquals("Row 199", bottom, "the wheel takes the user to the last of the 200 items");
        }

        @Test
        void aListViewThumbDraggedToTheBottomShowsTheLastItem() {
            Ui ui = new Ui("<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\" rows=\"30\" prefHeight=\"240\">"
                    + "<ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView>");
            JXNativeNode list = ui.find("list");
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(list);
            int x = (int) (g.vx + g.vw / 2);
            int grab = (int) (g.vThumbY + g.vThumbH / 2);
            ui.press(x, grab);
            ui.dragTo(x, grab + 2000);
            ui.release();
            String bottom = ui.deepestTextAt(center(list)[0], list.getY() + list.getHeight() - 6);
            assertEquals("Row 199", bottom, "dragging the thumb to the bottom shows the last item");
        }

        @Test
        void aClickOnAListItemRunsItsAction() {
            Ui ui = new Ui("<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\" rows=\"30\" prefHeight=\"240\">"
                    + "<ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView>");
            JXNativeNode list = ui.find("list");
            ui.click(center(list)[0], list.getY() + 24 * 3 + 12);
            assertEquals("Row 3", ui.c.picked.isEmpty() ? null : ui.c.picked.get(0));
        }

        @Test
        void aTableViewOfTwoHundredRowsReachesItsLastRowWithTheWheel() {
            Ui ui = new Ui("<TableView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\" rows=\"30\" prefHeight=\"240\""
                    + " columns=\"Name\" columnWidths=\"-1\" constrained=\"true\">"
                    + "<TableRow><TableCell text=\"${r}\" onAction=\"#pick(r)\"/></TableRow></TableView>");
            JXNativeNode table = ui.find("table");
            int[] c = center(table);
            for (int i = 0; i < 400; i++) {
                ui.wheel(c[0], c[1], 1);
            }
            String bottom = ui.deepestTextAt(c[0], table.getY() + table.getHeight() - 6);
            assertEquals("Row 199", bottom, "the wheel takes the user to the last of the 200 rows");
        }

        @Test
        void aScrollStartsAtItsVvalueAndReportsTheWheelThroughOnScroll() {
            Ui ui = new Ui("<Scroll fitToWidth=\"true\" vvalue=\"1\" onScroll=\"#scrolledTo\" prefHeight=\"200\"><Column gap=\"4\">"
                    + "<For items=\"${rows}\" var=\"r\" type=\"java.lang.String\" limit=\"40\">"
                    + "<Button text=\"${r}\" prefHeight=\"30\"/></For></Column></Scroll>");
            JXNativeNode pane = ui.find("scroll");
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(pane);
            assertEquals(g.maxScrollY(), g.scrollY, 0.5, "vvalue 1 is the bottom");
            int[] c = center(pane);
            ui.wheel(c[0], c[1], -2);
            assertNotNull(ui.c.scrolled, "onScroll hears of the wheel");
            assertEquals(g.maxScrollY() - 80, ui.c.scrolled[1], 0.5);
        }

        @Test
        void aTreeOfTwoHundredItemsReachesItsLastItemWithTheWheel() {
            Ui ui = new Ui("<Tree first=\"@{top}\" rows=\"30\" prefHeight=\"240\">"
                    + "<For items=\"${rows}\" var=\"r\" type=\"java.lang.String\"><TreeItem text=\"${r}\"/></For></Tree>");
            JXNativeNode tree = ui.find("list");
            int[] c = center(tree);
            for (int i = 0; i < 400; i++) {
                ui.wheel(c[0], c[1], 1);
            }
            assertEquals("Row 199", ui.deepestTextAt(c[0], tree.getY() + tree.getHeight() - 6));
        }

        @Test
        void aListScrolledByTheWheelMovesSmoothlyAcrossItsWindow() {
            Ui ui = new Ui("<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\" rows=\"30\" prefHeight=\"240\">"
                    + "<ListItem text=\"${r}\"/></ListView>");
            JXNativeNode list = ui.find("list");
            int[] c = center(list);
            for (int i = 1; i <= 30; i++) {
                ui.wheel(c[0], c[1], 1);
                assertEquals(40.0 * i, JXControlLayout.scrollGeometry(ui.find("list")).scrollY, 0.5,
                        "every notch moves 40 px, also when the application moves the window");
            }
            assertEquals(1200 / 24, ui.c.getTop(), "the application was told the row at the top");
        }
    }

    // ---- trees, menus, layers ------------------------------------------------------------------------

    @Nested
    class TreesMenusAndLayers {
        @Test
        void theTreeArrowTogglesAndTheRowSelects() {
            Ui ui = new Ui("<Tree prefHeight=\"120\"><TreeItem text=\"src\" expanded=\"${srcOpen}\" onToggle=\"#toggleSrc\""
                    + " onAction=\"#click\"><TreeItem text=\"main\"/></TreeItem></Tree>");
            JXNativeNode row = ui.find("cell");
            int arrow = -1;
            for (int x = row.getX(); x < row.getX() + row.getWidth(); x++) {
                if (JXControlLayout.disclosureAt(row, x)) {
                    arrow = x + 2;
                    break;
                }
            }
            assertTrue(arrow > 0, "the row has an arrow");
            ui.click(arrow, center(row)[1]);
            assertTrue(ui.c.getSrcOpen(), "the arrow expands");
            assertEquals(0, ui.c.clicks, "the arrow is not a click on the row");
            ui.click(row.getX() + row.getWidth() - 10, center(row)[1]);
            assertEquals(1, ui.c.clicks, "the rest of the row runs onAction");
        }

        @Test
        void aMenuButtonOpensAndItsItemRunsAndCloses() {
            Ui ui = new Ui("<MenuButton text=\"Actions\"><MenuItem text=\"Open\" onAction=\"#click\"/>"
                    + "<MenuItem text=\"Save\" onAction=\"#click\"/></MenuButton>");
            JXNativeNode host = ui.find("menubutton");
            ui.click(host);
            JXOverlays.Popup popup = JXOverlays.popup(ui.find("menubutton"));
            assertNotNull(popup, "a click opens the menu");
            JXNativeNode item = popup.panels.get(0).container.getChildren().get(0);
            ui.click(item);
            assertEquals(1, ui.c.clicks, "the item runs");
            assertNull(JXOverlays.popup(ui.find("menubutton")), "and the menu closes");
        }

        @Test
        void escapeClosesAnOpenMenuButton() {
            Ui ui = new Ui("<MenuButton text=\"Actions\"><MenuItem text=\"Open\" onAction=\"#click\"/></MenuButton>");
            ui.click(ui.find("menubutton"));
            ui.key(ESCAPE);
            assertNull(JXOverlays.popup(ui.find("menubutton")));
        }

        @Test
        void aRightClickOpensTheContextMenuOfItsParent() {
            Ui ui = new Ui("<Column prefHeight=\"150\" background=\"#EEEEEE\"><Label text=\"area\"/>"
                    + "<ContextMenu><MenuItem text=\"Copy\" onAction=\"#click\"/></ContextMenu></Column>");
            JXNativeNode area = ui.find("column", 1);
            int[] c = center(area);
            ui.press(c[0], c[1], 1);
            ui.release();
            JXOverlays.Popup popup = JXOverlays.popup(ui.find("contextmenu"));
            assertNotNull(popup, "a secondary press opens the context menu");
            ui.click(popup.panels.get(0).container.getChildren().get(0));
            assertEquals(1, ui.c.clicks);
        }

        @Test
        void aMenuBarOpensAMenuAndRunsAnItem() {
            Ui ui = new Ui("<MenuBar><Menu text=\"File\"><MenuItem text=\"Open\" onAction=\"#click\"/></Menu></MenuBar>");
            JXNativeNode bar = ui.find("menubar");
            ui.click(bar.getX() + 12, center(bar)[1]);
            JXOverlays.Popup popup = JXOverlays.popup(ui.find("menubar"));
            assertNotNull(popup, "a click on the title opens its menu");
            ui.click(popup.panels.get(0).container.getChildren().get(0));
            assertEquals(1, ui.c.clicks);
        }

        @Test
        void aModalClosesOnTheScrimAndOnEscapeAndEnterPressesTheDefaultButton() {
            String modal = "<Modal showing=\"${dialog}\" onShowing=\"#setDialog\"><Label text=\"Sure?\"/>"
                    + "<Button text=\"OK\" defaultButton=\"true\" onAction=\"#click\"/></Modal>";
            Ui ui = new Ui(modal);
            ui.click(3, 3);
            assertTrue(!ui.c.getDialog(), "a click on the scrim asks to close");
            ui.c.setDialog(true);
            ui.refresh();
            ui.key(ESCAPE);
            assertTrue(!ui.c.getDialog(), "Escape asks to close");
            ui.c.setDialog(true);
            ui.refresh();
            ui.key(ENTER);
            assertEquals(1, ui.c.clicks, "Enter presses the default button");
        }

        @Test
        void aDrawerClosesOnItsScrim() {
            Ui ui = new Ui("<Drawer side=\"left\" showing=\"${drawer}\" onShowing=\"#setDrawer\"><Label text=\"Menu\"/></Drawer>");
            ui.click(ui.w - 5, ui.h / 2);
            assertTrue(!ui.c.getDrawer());
        }

        @Test
        void aSnackBarActionRunsAndItAsksToCloseAfterItsDuration() {
            Ui ui = new Ui("<SnackBar message=\"Saved\" action=\"Undo\" onAction=\"#click\" duration=\"4000\""
                    + " showing=\"${snack}\" onShowing=\"#setSnack\"/>");
            JXNativeNode snack = ui.find("snackbar");
            ui.click(snack.getX() + snack.getWidth() - 20, center(snack)[1]);
            assertEquals(1, ui.c.clicks, "the action runs");
            ui.root.tickOverlays(0);
            ui.root.tickOverlays(5000);
            ui.refresh();
            assertTrue(!ui.c.getSnack(), "it asks to close after its duration");
        }
    }

    // ---- selection, clipboard, undo, hover, tooltips ---------------------------------------------------

    @Nested
    class SelectionAndHover {
        private static final int SHIFT = JXInputEvent.SHIFT;
        private final int CTRL = JXInputEvent.SHORTCUT; // Cmd on macOS, Ctrl elsewhere
        private final String[] clipboard = {null};

        @org.junit.jupiter.api.BeforeEach
        void fakeClipboard() {
            JXNativeNode.setClipboard(new com.jxparallel.ui.input.JXClipboard() {
                @Override
                public String getText() {
                    return clipboard[0];
                }

                @Override
                public void setText(String value) {
                    clipboard[0] = value;
                }
            });
        }

        @org.junit.jupiter.api.AfterEach
        void systemClipboard() {
            JXNativeNode.setClipboard(null);
        }

        private Ui typed(String xml, String text) {
            Ui ui = new Ui(xml);
            ui.click(ui.find(xml.contains("PasswordField") ? "password" : "input"));
            ui.type(text);
            return ui;
        }

        @Test
        void shiftAndTheArrowsSelectAndTypingReplacesTheSelection() {
            Ui ui = typed("<TextField text=\"@{name}\"/>", "hello world");
            ui.key(HOME);
            for (int i = 0; i < 5; i++) {
                ui.key(RIGHT, SHIFT);
            }
            assertEquals(Integer.valueOf(0), ui.find("input").getProperty("anchor"), "the selection is drawn from 0");
            ui.type("X");
            assertEquals("X world", ui.c.getName());
        }

        @Test
        void ctrlASelectsEverythingAndBackspaceClearsIt() {
            Ui ui = typed("<TextField text=\"@{name}\"/>", "hello");
            ui.key(65, CTRL);
            ui.key(BACKSPACE);
            assertEquals("", ui.c.getName());
        }

        @Test
        void ctrlCCopiesCtrlXCutsAndCtrlVPastes() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<TextField text=\"@{other}\"/>");
            ui.click(ui.find("input", 0));
            ui.type("abc");
            ui.key(65, CTRL);
            ui.key(67, CTRL);
            assertEquals("abc", clipboard[0], "Ctrl+C");
            ui.click(ui.find("input", 1));
            ui.key(86, CTRL);
            assertEquals("abc", ui.c.getOther(), "Ctrl+V");
            ui.key(65, CTRL);
            ui.key(88, CTRL);
            assertEquals("", ui.c.getOther(), "Ctrl+X removes it");
            assertEquals("abc", clipboard[0]);
        }

        @Test
        void aPasswordIsNotCopied() {
            Ui ui = typed("<PasswordField text=\"@{secret}\"/>", "s3cr3t");
            ui.key(65, CTRL);
            ui.key(67, CTRL);
            ui.key(88, CTRL);
            assertNull(clipboard[0]);
            assertEquals("s3cr3t", ui.c.getSecret());
        }

        @Test
        void ctrlZUndoesAndCtrlYRedoes() {
            Ui ui = typed("<TextField text=\"@{name}\"/>", "ab");
            ui.key(90, CTRL);
            assertEquals("a", ui.c.getName());
            ui.key(90, CTRL);
            assertEquals("", ui.c.getName());
            ui.key(89, CTRL);
            assertEquals("a", ui.c.getName());
            ui.type("c");
            assertEquals("ac", ui.c.getName(), "typing after an undo goes on from there");
        }

        @Test
        void aDoubleClickSelectsAWordAndATripleClickEverything() {
            Ui ui = typed("<TextField text=\"@{name}\"/>", "hello world");
            JXNativeNode field = ui.find("input");
            int y = center(field)[1];
            int x = field.getX();
            while (JXTextHit.fieldIndex(field, x) < 8) {
                x++;
            }
            ui.clicks(x, y, 2);
            ui.type("X");
            assertEquals("hello X", ui.c.getName(), "a double click selected world");
            ui.clicks(x, y, 3);
            ui.type("Y");
            assertEquals("Y", ui.c.getName(), "a triple click selected everything");
        }

        @Test
        void aDragSelectsTheTextItCrosses() {
            Ui ui = typed("<TextField text=\"@{name}\"/>", "hello");
            JXNativeNode field = ui.find("input");
            int y = center(field)[1];
            ui.press(field.getX() + 2, y);
            ui.moveTo(field.getX() + field.getWidth() - 4, y);
            ui.release();
            ui.type("Z");
            assertEquals("Z", ui.c.getName());
        }

        @Test
        void theNodesUnderThePointerAreHoveredAndATooltipIsFound() {
            Ui ui = new Ui("<Button text=\"Save\" tooltip=\"Save the file\" onAction=\"#click\"/>\n<Label text=\"plain\"/>");
            JXNativeNode button = ui.find("button");
            int[] c = center(button);
            ui.hover(c[0], c[1]);
            assertEquals(Boolean.TRUE, ui.find("button").getProperty("hover"), "drawn hovered");
            assertEquals("Save the file", JXNativeNode.hoveredTooltip().getProperty("tooltip"));
            JXNativeNode label = ui.find("#text");
            ui.hover(center(label)[0], center(label)[1]);
            assertNull(ui.find("button").getProperty("hover"), "no longer hovered");
            assertNull(JXNativeNode.hoveredTooltip());
        }

        @Test
        void aButtonIsDrawnPressedOnlyWhileThePointerIsOnIt() {
            Ui ui = new Ui("<Button text=\"Save\" onAction=\"#click\"/>");
            int[] c = center(ui.find("button"));
            ui.press(c[0], c[1]);
            assertEquals(Boolean.TRUE, ui.find("button").getProperty("pressed"));
            ui.moveTo(ui.w - 3, ui.h - 3);
            assertNull(ui.find("button").getProperty("pressed"), "slid off: drawn up again");
            ui.release();
        }
    }

    // ---- selection of the rows of lists, tables and trees --------------------------------------------

    @Nested
    class RowSelection {
        private static final int SHIFT = JXInputEvent.SHIFT;
        private final int CTRL = JXInputEvent.SHORTCUT; // Cmd on macOS, Ctrl elsewhere
        private static final int PAGE_UP = 266;
        private static final int PAGE_DOWN = 267;
        private static final int KEY_A = 65;
        private static final int SELECTED = 0xFF0096C9;
        private static final int UNFOCUSED = 0xFFD3D3D3;

        private static final String LIST = "<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\""
                + " rows=\"30\" prefHeight=\"240\" selected=\"@{selected}\"><ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView>";
        private static final String MULTIPLE = "<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\""
                + " rows=\"30\" prefHeight=\"240\" selectionMode=\"multiple\" selection=\"@{chosen}\" selected=\"${selected}\""
                + " onSelect=\"#setSelected\"><ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView>";
        private static final String TABLE = "<TableView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\""
                + " rows=\"30\" prefHeight=\"240\" columns=\"Name\" columnWidths=\"-1\" constrained=\"true\" selected=\"@{selected}\">"
                + "<TableRow><TableCell text=\"${r}\" onAction=\"#pick(r)\"/></TableRow></TableView>";

        private double item(JXNativeNode list) {
            return "table".equals(list.getType()) ? JXControlLayout.rowHeight(list) : JXControlLayout.cellHeight(list);
        }

        /** The window y of the middle of the row at {@code index} of the model, as the list is scrolled now. */
        private int rowY(Ui ui, String type, int index) {
            JXNativeNode list = ui.find(type);
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(list);
            return (int) (g.viewY + index * item(list) - g.scrollY + item(list) / 2);
        }

        private int rowX(Ui ui, String type) {
            return ui.find(type).getX() + 60;
        }

        /** A pixel of the row at {@code index} that no text reaches. */
        private int paintedRow(Ui ui, String type, int index) {
            JXNativeNode list = ui.find(type);
            return ui.pixel(list.getX() + list.getWidth() - 60, rowY(ui, type, index));
        }

        private void clickRow(Ui ui, int index, int modifiers) {
            ui.click(rowX(ui, "list"), rowY(ui, "list", index), modifiers);
        }

        private void clickRow(Ui ui, int index) {
            clickRow(ui, index, 0);
        }

        private double[] chosen(Ui ui) {
            return ui.c.getChosen();
        }

        @Test
        void aClickSelectsTheRowAndTheApplicationIsToldItsIndex() {
            Ui ui = new Ui(LIST);
            clickRow(ui, 3);
            assertEquals(3, ui.c.getSelected());
            assertEquals(SELECTED, paintedRow(ui, "list", 3), "the selected row is drawn with the selection colour");
            assertTrue(paintedRow(ui, "list", 4) != SELECTED, "and only that one");
            clickRow(ui, 5);
            assertEquals(5, ui.c.getSelected());
            assertTrue(paintedRow(ui, "list", 3) != SELECTED, "the row before is no longer selected");
        }

        @Test
        void aListThatIsNotFocusedDrawsItsSelectionGrey() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n" + LIST);
            ui.c.setSelected(2);
            ui.refresh();
            ui.click(ui.find("input"));
            assertEquals(UNFOCUSED, paintedRow(ui, "list", 2), "the selection of an unfocused list");
            clickRow(ui, 2);
            assertEquals(SELECTED, paintedRow(ui, "list", 2), "focused: the selection colour");
            assertEquals(1, ui.c.told.size(), "only the setSelected(2) the test made; the click on a selected row tells nothing");
        }

        @Test
        void aClickDoesNotRunTheRowsActionButEnterAndADoubleClickDo() {
            Ui ui = new Ui(LIST);
            clickRow(ui, 3);
            assertTrue(ui.c.picked.isEmpty(), "a single click only selects");
            ui.key(ENTER);
            assertEquals("Row 3", ui.c.picked.get(0), "Enter runs the action of the selected row");
            ui.clicks(rowX(ui, "list"), rowY(ui, "list", 6), 2);
            assertEquals(6, ui.c.getSelected(), "the double click selects");
            assertEquals("Row 6", ui.c.picked.get(1), "and runs the action");
        }

        @Test
        void upDownHomeEndAndThePagesMoveTheSelectionOfTheFocusedList() {
            Ui ui = new Ui(LIST);
            clickRow(ui, 3);
            ui.key(DOWN);
            assertEquals(4, ui.c.getSelected());
            ui.key(UP);
            ui.key(UP);
            assertEquals(2, ui.c.getSelected());
            ui.key(HOME);
            assertEquals(0, ui.c.getSelected());
            int told = ui.c.told.size();
            ui.key(UP);
            assertEquals(0, ui.c.getSelected(), "not above the first row");
            assertEquals(told, ui.c.told.size(), "and nothing to tell");
            ui.key(END);
            assertEquals(199, ui.c.getSelected());
            ui.key(DOWN);
            assertEquals(199, ui.c.getSelected(), "not below the last row");
            int page = (int) Math.floor(JXControlLayout.scrollGeometry(ui.find("list")).viewH / 24);
            ui.key(PAGE_UP);
            assertEquals(199 - page, ui.c.getSelected(), "Page Up moves the rows the viewport shows");
            ui.key(PAGE_DOWN);
            assertEquals(199, ui.c.getSelected());
        }

        @Test
        void theMovedSelectionIsScrolledIntoView() {
            Ui ui = new Ui(LIST);
            clickRow(ui, 0);
            ui.key(END);
            JXNativeNode list = ui.find("list");
            assertEquals("Row 199", ui.deepestTextAt(list.getX() + 40, list.getY() + list.getHeight() - 6),
                    "End shows the last row at the bottom");
            assertTrue(ui.c.getTop() > 150, "the application was told the window moved, to " + ui.c.getTop());
            assertEquals(SELECTED, paintedRow(ui, "list", 199), "and the row is drawn selected there");
            ui.key(HOME);
            assertEquals(0, ui.c.getTop(), "Home goes back to the top");
            for (int i = 0; i < 12; i++) {
                ui.key(DOWN);
            }
            assertTrue(ui.c.getTop() >= 3, "going down past the bottom row scrolls one row at a time, top " + ui.c.getTop());
            int y = rowY(ui, "list", ui.c.getSelected());
            assertTrue(y > list.getY() && y < list.getY() + list.getHeight(), "the selected row is inside the list");
        }

        @Test
        void aSelectedRowOutsideTheWindowIsKeptAndTheKeysMoveFromIt() {
            Ui ui = new Ui(LIST);
            ui.c.setSelected(150);
            ui.refresh();
            for (int k = 0; k < 10; k++) {
                int pixel = paintedRow(ui, "list", k);
                assertTrue(pixel != SELECTED && pixel != UNFOCUSED, "no row of the window is drawn selected, row " + k);
            }
            ui.click(rowX(ui, "list"), rowY(ui, "list", 0)); // focuses the list, selects row 0
            ui.c.setSelected(150);
            ui.refresh();
            ui.key(DOWN);
            assertEquals(151, ui.c.getSelected(), "the keys move from the row, wherever it is");
            assertTrue(ui.c.getTop() >= 142 && ui.c.getTop() <= 151, "and the window follows it, top " + ui.c.getTop());
            assertEquals(SELECTED, paintedRow(ui, "list", 151));
        }

        @Test
        void tabGivesTheFocusToAListWithASelectionAndNotToOneWithout() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n" + LIST);
            ui.click(ui.find("input"));
            ui.key(TAB);
            assertEquals(ui.find("list"), ui.focused, "Tab reaches the list");
            ui.key(DOWN);
            assertEquals(0, ui.c.getSelected());
            Ui plain = new Ui("<TextField text=\"@{name}\"/>\n<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\""
                    + " rows=\"30\" prefHeight=\"240\"><ListItem text=\"${r}\"/></ListView>");
            plain.click(plain.find("input"));
            plain.key(TAB);
            assertEquals(plain.find("input"), plain.focused, "a list with no selection is not in the order, as before");
        }

        @Test
        void ctrlClickTogglesAndShiftClickExtendsARangeInMultipleMode() {
            Ui ui = new Ui(MULTIPLE);
            clickRow(ui, 2);
            assertArrayEquals(new double[] {2}, chosen(ui));
            clickRow(ui, 5, CTRL);
            clickRow(ui, 9, CTRL);
            assertArrayEquals(new double[] {2, 5, 9}, chosen(ui), "Ctrl+click adds");
            clickRow(ui, 5, CTRL);
            assertArrayEquals(new double[] {2, 9}, chosen(ui), "and takes off a row selected");
            assertEquals(SELECTED, paintedRow(ui, "list", 9));
            assertEquals(SELECTED, paintedRow(ui, "list", 2), "every selected row is drawn so");
            clickRow(ui, 4);
            assertArrayEquals(new double[] {4}, chosen(ui), "a click without keys selects that row only");
            clickRow(ui, 7, SHIFT);
            assertArrayEquals(new double[] {4, 5, 6, 7}, chosen(ui), "Shift+click selects from the row clicked last");
            clickRow(ui, 2, SHIFT);
            assertArrayEquals(new double[] {2, 3, 4}, chosen(ui), "the range keeps its start");
            clickRow(ui, 9, SHIFT | CTRL);
            assertArrayEquals(new double[] {2, 3, 4, 5, 6, 7, 8, 9}, chosen(ui), "Ctrl+Shift adds the range");
            assertEquals(9, ui.c.getSelected(), "onSelect hears of the row last clicked");
        }

        @Test
        void shiftWithTheArrowsExtendsAndCtrlASelectsAllInMultipleMode() {
            Ui ui = new Ui(MULTIPLE);
            clickRow(ui, 3);
            ui.key(DOWN, SHIFT);
            ui.key(DOWN, SHIFT);
            assertArrayEquals(new double[] {3, 4, 5}, chosen(ui));
            ui.key(UP, SHIFT);
            assertArrayEquals(new double[] {3, 4}, chosen(ui), "Shift+Up takes the last row back");
            ui.key(UP, SHIFT);
            ui.key(UP, SHIFT);
            assertArrayEquals(new double[] {2, 3}, chosen(ui), "past the start of the range it grows the other way");
            ui.key(KEY_A, CTRL);
            assertEquals(200, chosen(ui).length, "Ctrl+A selects every row of the model, also the ones not shown");
            assertEquals(199, chosen(ui)[199], 0);
            ui.key(DOWN);
            assertEquals(1, chosen(ui).length, "an arrow without Shift collapses the selection to one row");
        }

        @Test
        void inSingleModeShiftCtrlAndCtrlAAreOrdinary() {
            Ui ui = new Ui(LIST);
            clickRow(ui, 2);
            clickRow(ui, 6, CTRL);
            assertEquals(6, ui.c.getSelected(), "Ctrl+click is a click");
            clickRow(ui, 9, SHIFT);
            assertEquals(9, ui.c.getSelected(), "and so is Shift+click");
            ui.key(DOWN, SHIFT);
            assertEquals(10, ui.c.getSelected());
            int told = ui.c.told.size();
            ui.key(KEY_A, CTRL);
            assertEquals(told, ui.c.told.size(), "Ctrl+A selects nothing in single mode");
        }

        @Test
        void aTableSelectsItsRowsToo() {
            Ui ui = new Ui(TABLE);
            ui.click(rowX(ui, "table"), rowY(ui, "table", 4));
            assertEquals(4, ui.c.getSelected());
            assertEquals(SELECTED, paintedRow(ui, "table", 4), "the row of a table is drawn selected");
            ui.key(DOWN);
            assertEquals(5, ui.c.getSelected());
            ui.clicks(rowX(ui, "table"), rowY(ui, "table", 8), 2);
            assertEquals("Row 8", ui.c.picked.get(0), "a double click runs the action of the cell under it");
            ui.key(END);
            JXNativeNode table = ui.find("table");
            assertEquals("Row 199", ui.deepestTextAt(table.getX() + 20, table.getY() + table.getHeight() - 6));
        }

        @Test
        void aTreeSelectsItsRowsAndItsArrowOnlyToggles() {
            Ui ui = new Ui("<Tree prefHeight=\"120\" selected=\"@{selected}\"><TreeItem text=\"src\" expanded=\"${srcOpen}\""
                    + " onToggle=\"#toggleSrc\" onAction=\"#click\"><TreeItem text=\"main\"/></TreeItem><TreeItem text=\"docs\"/></Tree>");
            JXNativeNode row = ui.find("cell");
            int arrow = -1;
            for (int x = row.getX(); x < row.getX() + row.getWidth(); x++) {
                if (JXControlLayout.disclosureAt(row, x)) {
                    arrow = x + 2;
                    break;
                }
            }
            ui.click(arrow, center(row)[1]);
            assertTrue(ui.c.getSrcOpen(), "the arrow expands");
            assertEquals(-1, ui.c.getSelected(), "and does not select");
            ui.click(row.getX() + row.getWidth() - 10, center(row)[1]);
            assertEquals(0, ui.c.getSelected(), "a click on the row selects it");
            assertEquals(0, ui.c.clicks, "and does not run its action");
            ui.key(DOWN);
            assertEquals(1, ui.c.getSelected(), "the rows count the visible items: main, now that src is open");
            assertEquals(3, ui.find("list").getChildren().size(), "src, main and docs are the rows shown");
        }

        @Test
        void aTreeTableSelectsAcrossItsColumns() {
            Ui ui = new Ui("<TreeTable columns=\"Name, Size\" columnWidths=\"-1, 80\" constrained=\"true\" prefHeight=\"150\""
                    + " selected=\"@{selected}\"><TreeItem text=\"docs\" values=\"1 KB\"/><TreeItem text=\"notes\" values=\"2 KB\"/>"
                    + "</TreeTable>");
            JXNativeNode table = ui.find("table");
            int y = rowY(ui, "table", 1);
            ui.click(table.getX() + table.getWidth() - 20, y); // in the second column
            assertEquals(1, ui.c.getSelected());
            assertEquals(SELECTED, ui.pixel(table.getX() + 6 + 40, y - 8), "drawn selected");
        }

        @Test
        void aButtonInsideARowActsAsItselfAndDoesNotSelect() {
            Ui ui = new Ui("<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" rows=\"30\" prefHeight=\"240\""
                    + " selected=\"@{selected}\"><ListItem text=\"${r}\"><Button text=\"x\" onAction=\"#click\"/></ListItem></ListView>");
            ui.click(ui.find("button"));
            assertEquals(1, ui.c.clicks, "the button runs");
            assertEquals(-1, ui.c.getSelected(), "and the row is not selected");
        }

        @Test
        void aListWithoutSelectionPropsKeepsItsOldBehaviour() {
            Ui ui = new Ui("<ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" rows=\"30\" prefHeight=\"240\">"
                    + "<ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView>");
            ui.click(rowX(ui, "list"), rowY(ui, "list", 2));
            assertEquals("Row 2", ui.c.picked.get(0), "a click still runs the action");
            assertTrue(paintedRow(ui, "list", 2) != SELECTED, "and draws no selection");
            ui.key(DOWN);
            assertTrue(ui.c.told.isEmpty());
        }
    }

    // ---- sortable columns of a table -------------------------------------------------------------------------

    @Nested
    class ColumnSorting {
        private static final String SORTED = "<TableView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" rows=\"30\" prefHeight=\"240\""
                + " columns=\"Name, Other\" columnWidths=\"160, 100\" selected=\"@{selected}\""
                + " sortColumn=\"@{sortCol}\" sortAscending=\"@{sortAsc}\" onSort=\"#sorted\">"
                + "<TableRow><TableCell text=\"${r}\"/><TableCell text=\"x\"/></TableRow></TableView>";

        /** A click in the header of the column: {@code index} 0 is the first, 1 the second (160 px wide). */
        private void header(Ui ui, int index) {
            JXNativeNode table = ui.find("table");
            ui.click(table.getX() + 1 + (index == 0 ? 60 : 160 + 40), table.getY() + 1 + JXControlLayout.TABLE_HEADER / 2);
        }

        private String firstRow(Ui ui) {
            JXNativeNode table = ui.find("table");
            return ui.deepestTextAt(table.getX() + 20, table.getY() + 1 + JXControlLayout.TABLE_HEADER + 10);
        }

        /** The arrow of a header: its color where the apex of a triangle of 8 x 4 is, and 3 px below it. */
        private int arrowPixel(Ui ui, int column, int dy) {
            JXNativeNode table = ui.find("table");
            float left = table.getX() + 1 + (column == 0 ? 0 : 160);
            float width = column == 0 ? 160 : 100;
            return ui.pixel((int) (left + width - 16 + 4 + 4), table.getY() + 1 + (JXControlLayout.TABLE_HEADER - 4) / 2 + dy);
        }

        @Test
        void aHeaderCyclesAscendingDescendingAndUnsortedAndTheApplicationSortsItsRows() {
            Ui ui = new Ui(SORTED);
            assertEquals("Row 0", firstRow(ui));
            header(ui, 0);
            assertEquals(0, ui.c.getSortCol());
            assertTrue(ui.c.getSortAsc());
            assertEquals("[0.0, 1.0]", ui.c.sorts.get(0), "onSort gets {column, ascending}");
            assertEquals("Row 0", firstRow(ui), "ascending: the text sorts Row 0, Row 1, Row 10 ...");
            assertEquals("Row 1", ui.deepestTextAt(ui.find("table").getX() + 20, ui.find("table").getY() + 1 + JXControlLayout.TABLE_HEADER + 10 + 24));
            header(ui, 0);
            assertEquals(0, ui.c.getSortCol());
            assertEquals(false, ui.c.getSortAsc());
            assertEquals("Row 99", firstRow(ui), "descending");
            header(ui, 0);
            assertEquals(-1, ui.c.getSortCol(), "the third press leaves the sort");
            assertTrue(ui.c.getSortAsc());
            assertEquals("[-1.0, 1.0]", ui.c.sorts.get(2));
            assertEquals("Row 0", firstRow(ui));
            assertEquals(3, ui.c.sorts.size());
        }

        @Test
        void aPressOnAnotherColumnStartsItAscendingAndTheHeaderDrawsTheArrow() {
            Ui ui = new Ui(SORTED);
            int none = arrowPixel(ui, 0, 1);
            header(ui, 0);
            header(ui, 0); // descending on the first
            assertNotEquals(none, arrowPixel(ui, 0, 1), "the arrow of the sorted column");
            assertEquals(JXPaint.ARROW, arrowPixel(ui, 0, 1), "pointing down: wide at the top");
            header(ui, 1);
            assertEquals(1, ui.c.getSortCol());
            assertTrue(ui.c.getSortAsc(), "another column: ascending");
            assertEquals(JXPaint.ARROW, arrowPixel(ui, 1, 3), "pointing up: wide at the bottom");
            assertEquals(none, arrowPixel(ui, 0, 1), "and the first column has none");
        }

        @Test
        void aTableWithoutSortPropsHasHeadersThatDoNothingAndSortableFalseTurnsThemOff() {
            Ui plain = new Ui("<TableView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" rows=\"30\" prefHeight=\"240\" columns=\"Name\""
                    + " columnWidths=\"160\" selected=\"@{selected}\"><TableRow><TableCell text=\"${r}\"/></TableRow></TableView>");
            JXNativeNode table = plain.find("table");
            plain.click(table.getX() + 60, table.getY() + 1 + JXControlLayout.TABLE_HEADER / 2);
            assertEquals(0, plain.c.sorts.size());
            assertEquals(-1, plain.c.getSelected(), "the header is not a row");
            Ui off = new Ui(SORTED.replace("onSort=\"#sorted\"", "onSort=\"#sorted\" sortable=\"false\""));
            header(off, 0);
            assertEquals(0, off.c.sorts.size());
            assertEquals(-1, off.c.getSortCol());
            Ui on = new Ui(SORTED.replace(" sortColumn=\"@{sortCol}\" sortAscending=\"@{sortAsc}\"", ""));
            header(on, 0);
            assertEquals(1, on.c.sorts.size(), "onSort alone makes the table sortable");
        }

        @Test
        void aClickInTheRowsStillSelectsAndAHeaderPressDoesNot() {
            Ui ui = new Ui(SORTED);
            JXNativeNode table = ui.find("table");
            ui.click(table.getX() + 60, table.getY() + 1 + JXControlLayout.TABLE_HEADER + 10 + 24 * 2);
            assertEquals(2, ui.c.getSelected());
            header(ui, 0);
            assertEquals(2, ui.c.getSelected(), "a header press leaves the selection as it is");
        }
    }

    // ---- hover and keys inside popups and menus ------------------------------------------------------------

    @Nested
    class PopupsAndMenus {
        private static final int ALT = JXInputEvent.ALT;
        private static final int SPACE = 32;
        private static final int F4 = 293;
        private static final int PAGE_UP = 266;
        private static final int PAGE_DOWN = 267;
        private static final int SELECTION = 0xFF0096C9;
        private static final int GREY = 0xFFD3D3D3;
        private static final String FRUITS = "Apple, Banana, Cherry, Date, Elderberry, Fig, Grape";
        private static final String COMBO = "<ComboBox items=\"" + FRUITS + "\" value=\"@{fruit}\" showing=\"${fruitOpen}\""
                + " onShowing=\"#setFruitOpen\"/>";
        /** Open items (0), Save (1), a separator (2), a disabled one (3), a submenu with two items (4), and Quit (5). */
        private static final String ITEMS = "<MenuItem text=\"Open\" onAction=\"#click\"/><MenuItem text=\"Save\" onAction=\"#click\"/>"
                + "<MenuSeparator/><MenuItem text=\"Closed\" disabled=\"true\" onAction=\"#click\"/>"
                + "<Menu text=\"Recent\"><MenuItem text=\"One\" onAction=\"#click\"/><MenuItem text=\"Two\" onAction=\"#toggleBold\"/></Menu>"
                + "<MenuItem text=\"Quit\" onAction=\"#toggleExpanded\"/>";
        private static final String MENU = "<MenuButton text=\"Actions\">" + ITEMS + "</MenuButton>";

        private int[] cell(Ui ui, int i) {
            return center(ui.find("select").getPopupCells()[i]);
        }

        private int highlight(Ui ui) {
            return ui.find("select").highlight;
        }

        private Ui openCombo() {
            Ui ui = new Ui(COMBO);
            ui.click(ui.find("select"));
            assertTrue(ui.c.getFruitOpen());
            return ui;
        }

        private JXNativeNode row(JXNativeNode container, int i) {
            return container.getChildren().get(i);
        }

        private boolean menuOpen(Ui ui, String type) {
            return JXOverlays.isOpen(ui.find(type));
        }

        // ---- the list of a combo box

        @Test
        void hoverHighlightsTheOptionUnderThePointerAndTheChosenOneTurnsGrey() {
            Ui ui = openCombo();
            JXNativeNode combo = ui.find("select");
            int[][] cells = combo.getPopupCells();
            int right = cells[0][0] + cells[0][2] - 8;
            assertEquals(SELECTION, ui.pixel(right, cells[0][1] + 3), "nothing hovered: the chosen option is the blue one");
            int[] c = cell(ui, 3);
            ui.hover(c[0], c[1]);
            assertEquals(3, highlight(ui));
            assertEquals(SELECTION, ui.pixel(right, cells[3][1] + 3), "the hovered option is drawn blue");
            assertEquals(GREY, ui.pixel(right, cells[0][1] + 3), "and the chosen one grey");
            c = cell(ui, 5);
            ui.hover(c[0], c[1]);
            assertEquals(5, highlight(ui));
            assertEquals("Apple", ui.c.getFruit(), "hover chooses nothing");
            ui.click(c[0], c[1]);
            assertEquals("Fig", ui.c.getFruit());
        }

        @Test
        void upDownHomeEndAndThePagesMoveTheHighlightAndStopAtTheEnds() {
            Ui ui = openCombo();
            ui.key(DOWN);
            assertEquals(1, highlight(ui), "from the chosen option, the next one");
            ui.key(DOWN);
            assertEquals(2, highlight(ui));
            ui.key(UP);
            ui.key(UP);
            ui.key(UP);
            assertEquals(0, highlight(ui), "not above the first");
            ui.key(END);
            assertEquals(6, highlight(ui));
            ui.key(DOWN);
            assertEquals(6, highlight(ui), "not below the last");
            ui.key(HOME);
            assertEquals(0, highlight(ui));
            ui.key(PAGE_DOWN);
            assertEquals(6, highlight(ui));
            ui.key(PAGE_UP);
            assertEquals(0, highlight(ui));
            assertEquals("Apple", ui.c.getFruit(), "moving chooses nothing");
            assertTrue(ui.c.getFruitOpen(), "and the list stays open");
        }

        @Test
        void enterAndSpaceChooseTheHighlightedOptionAndClose() {
            Ui ui = openCombo();
            ui.key(DOWN);
            ui.key(DOWN);
            ui.key(ENTER);
            assertEquals("Cherry", ui.c.getFruit());
            assertTrue(!ui.c.getFruitOpen(), "choosing closes the list");
            assertEquals(ui.find("select"), ui.focused, "the combo box keeps the focus");
            ui.type(" "); // closed: Space opens it again
            assertTrue(ui.c.getFruitOpen());
            ui.key(END);
            ui.type(" ");
            assertEquals("Grape", ui.c.getFruit(), "Space chooses too");
            assertTrue(!ui.c.getFruitOpen());
        }

        @Test
        void enterWithNothingHighlightedStillClosesTheList() {
            Ui ui = openCombo();
            ui.key(ENTER);
            assertTrue(!ui.c.getFruitOpen());
            assertEquals("Apple", ui.c.getFruit());
        }

        @Test
        void escapeClosesTheListAndTheComboBoxHasTheFocusAgain() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n" + COMBO);
            ui.click(ui.find("input"));
            ui.c.setFruitOpen(true); // the application opened it: the field keeps the focus
            ui.refresh();
            assertEquals(ui.find("input"), ui.focused);
            ui.key(ESCAPE);
            assertTrue(!ui.c.getFruitOpen(), "Escape closes");
            assertEquals(ui.find("select"), ui.focused, "and the owner of the list has the focus");
            ui.key(DOWN);
            assertEquals("Banana", ui.c.getFruit(), "so the arrows change its value");
        }

        @Test
        void typingALetterJumpsToTheNextOptionThatStartsWithIt() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<ComboBox items=\"Apple, Banana, Blueberry, Cherry\" value=\"@{fruit}\""
                    + " showing=\"${fruitOpen}\" onShowing=\"#setFruitOpen\"/>");
            ui.click(ui.find("select"));
            ui.type("b");
            assertEquals(1, highlight(ui), "the first B after the chosen Apple");
            ui.type("b");
            assertEquals(2, highlight(ui), "the next B");
            ui.type("B");
            assertEquals(1, highlight(ui), "wraps round to the first");
            ui.type("c");
            assertEquals(3, highlight(ui));
            ui.type("z");
            assertEquals(3, highlight(ui), "no option starts with Z: it stays");
            assertEquals("", ui.c.getName(), "the letters were the list's, not a field's");
            assertEquals("Apple", ui.c.getFruit());
        }

        @Test
        void aClosedComboBoxWithTheFocusOpensWithAltDownF4AndSpace() {
            Ui ui = new Ui(COMBO);
            ui.click(ui.find("select"));
            ui.key(ESCAPE);
            assertTrue(!ui.c.getFruitOpen());
            ui.key(DOWN, ALT);
            assertTrue(ui.c.getFruitOpen(), "Alt+Down opens");
            assertEquals(0, highlight(ui), "with the chosen option highlighted");
            ui.key(F4, 0);
            assertTrue(!ui.c.getFruitOpen(), "F4 closes it");
            ui.key(F4, 0);
            assertTrue(ui.c.getFruitOpen(), "and opens it");
            ui.key(ESCAPE);
            ui.type(" ");
            assertTrue(ui.c.getFruitOpen(), "Space opens");
            ui.key(DOWN, ALT);
            assertTrue(!ui.c.getFruitOpen(), "Alt+Down closes an open one");
            ui.key(DOWN);
            assertEquals("Banana", ui.c.getFruit(), "closed, Down changes the value as before");
        }

        @Test
        void theListFollowsTheHighlightPastItsTenRows() {
            Ui ui = new Ui("<ComboBox items=\"1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20\" value=\"@{fruit}\""
                    + " showing=\"${fruitOpen}\" onShowing=\"#setFruitOpen\"/>");
            ui.click(ui.find("select"));
            ui.key(END);
            JXOverlays.Popup po = JXOverlays.popup(ui.find("select"));
            assertEquals(10, po.cells.length);
            assertEquals(19, po.cells[9][5], "the last option is the last row shown");
            assertEquals(10, po.cells[0][5]);
            ui.key(HOME);
            po = JXOverlays.popup(ui.find("select"));
            assertEquals(0, po.cells[0][5], "and back at the top");
            for (int i = 0; i < 12; i++) {
                ui.key(DOWN);
            }
            po = JXOverlays.popup(ui.find("select"));
            assertEquals(12, ui.find("select").highlight);
            assertEquals(3, po.cells[0][5], "going down scrolls one row at a time, the window moved only as far as needed");
        }

        // ---- menus

        @Test
        void hoverHighlightsTheRowsThatCanBeChosen() {
            Ui ui = new Ui(MENU);
            ui.click(ui.find("menubutton"));
            JXNativeNode host = ui.find("menubutton");
            ui.hover(center(row(host, 1))[0], center(row(host, 1))[1]);
            assertEquals(1, host.highlight, "Save");
            ui.hover(center(row(host, 2))[0], center(row(host, 2))[1]);
            assertEquals(1, host.highlight, "a separator is not highlighted");
            ui.hover(center(row(host, 3))[0], center(row(host, 3))[1]);
            assertEquals(1, host.highlight, "nor a disabled row");
            ui.hover(center(row(host, 5))[0], center(row(host, 5))[1]);
            assertEquals(5, host.highlight);
            ui.hover(ui.w - 5, ui.h - 5);
            assertEquals(5, host.highlight, "leaving the menu leaves the highlight");
        }

        @Test
        void theArrowsMoveOverTheRowsThatCanBeChosenAndWrapAround() {
            Ui ui = new Ui(MENU);
            ui.click(ui.find("menubutton"));
            JXNativeNode host = ui.find("menubutton");
            ui.key(DOWN);
            assertEquals(0, host.highlight, "Down from nothing: the first row");
            ui.key(DOWN);
            assertEquals(1, host.highlight);
            ui.key(DOWN);
            assertEquals(4, host.highlight, "the separator and the disabled row are skipped");
            ui.key(DOWN);
            assertEquals(5, host.highlight);
            ui.key(DOWN);
            assertEquals(0, host.highlight, "after the last, the first");
            ui.key(UP);
            assertEquals(5, host.highlight, "before the first, the last");
            ui.key(HOME);
            assertEquals(0, host.highlight);
            ui.key(END);
            assertEquals(5, host.highlight);
        }

        @Test
        void enterAndSpaceRunTheHighlightedItemAndCloseTheMenu() {
            Ui ui = new Ui(MENU);
            ui.click(ui.find("menubutton"));
            ui.key(HOME);
            ui.key(ENTER);
            assertEquals(1, ui.c.clicks, "Enter runs the item");
            assertTrue(!menuOpen(ui, "menubutton"), "and closes the menu");
            ui.click(ui.find("menubutton"));
            ui.key(DOWN);
            ui.key(DOWN);
            ui.type(" ");
            assertEquals(2, ui.c.clicks, "Space runs it too");
            assertTrue(!menuOpen(ui, "menubutton"));
        }

        @Test
        void rightOpensASubmenuLeftClosesItAndEscapeClosesTheWholeMenu() {
            Ui ui = new Ui(MENU);
            ui.click(ui.find("menubutton"));
            JXNativeNode host = ui.find("menubutton");
            ui.key(END);
            ui.key(UP);
            assertEquals(4, host.highlight, "Recent");
            ui.key(RIGHT);
            assertEquals(4, host.openChild, "Right opens the submenu");
            assertEquals(0, row(host, 4).highlight, "with its first item highlighted");
            assertEquals(2, JXOverlays.popup(host).panels.size());
            ui.key(DOWN);
            assertEquals(1, row(host, 4).highlight, "the arrows now move in the submenu");
            assertEquals(4, host.highlight, "the row that opened it keeps its highlight");
            ui.key(LEFT);
            assertEquals(-1, host.openChild, "Left closes the submenu");
            assertEquals(1, JXOverlays.popup(host).panels.size());
            ui.key(RIGHT);
            ui.key(DOWN);
            ui.key(ENTER);
            assertTrue(ui.c.getBold(), "Enter in the submenu runs its item");
            assertTrue(!menuOpen(ui, "menubutton"), "and closes every panel");
            ui.click(ui.find("menubutton"));
            ui.key(HOME);
            ui.key(DOWN);
            ui.key(DOWN);
            ui.key(RIGHT);
            ui.key(ESCAPE);
            assertTrue(!menuOpen(ui, "menubutton"), "Escape closes the menu with its submenu");
            assertEquals(ui.find("menubutton"), ui.focused, "and the focus is the menu button's");
            ui.click(ui.find("menubutton"));
            assertEquals(-1, ui.find("menubutton").highlight, "a menu opened again starts with nothing highlighted");
            assertEquals(-1, ui.find("menubutton").openChild);
        }

        @Test
        void typingALetterJumpsToTheNextRowThatStartsWithIt() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<Column prefHeight=\"150\" background=\"#EEEEEE\"><Label text=\"area\"/>"
                    + "<ContextMenu>" + ITEMS + "</ContextMenu></Column>");
            ui.click(ui.find("input"));
            JXNativeNode area = ui.find("column", 1);
            ui.press(area.getX() + 40, area.getY() + area.getHeight() - 10, 1);
            ui.release();
            JXNativeNode menu = ui.find("contextmenu");
            assertNotNull(JXOverlays.popup(menu), "a right click opens the context menu");
            ui.type("q");
            assertEquals(5, menu.highlight, "Quit");
            ui.type("s");
            assertEquals(1, menu.highlight, "Save");
            ui.type("r");
            assertEquals(4, menu.highlight, "Recent");
            ui.type("z");
            assertEquals(4, menu.highlight);
            assertEquals("", ui.c.getName(), "the letters went to the menu, not to the field that has the focus");
            ui.key(ESCAPE);
            assertNull(JXOverlays.popup(ui.find("contextmenu")));
        }

        @Test
        void aContextMenuTakesTheKeys() {
            Ui ui = new Ui("<Column prefHeight=\"150\" background=\"#EEEEEE\"><Label text=\"area\"/>"
                    + "<ContextMenu>" + ITEMS + "</ContextMenu></Column>");
            JXNativeNode area = ui.find("column", 1);
            ui.press(area.getX() + 40, area.getY() + area.getHeight() - 10, 1);
            ui.release();
            ui.key(DOWN);
            ui.key(DOWN);
            ui.key(ENTER);
            assertEquals(1, ui.c.clicks, "Down, Down, Enter runs the second item");
            assertNull(JXOverlays.popup(ui.find("contextmenu")), "and closes the menu");
        }

        @Test
        void aMenuBarMovesBetweenItsMenusWithLeftAndRight() {
            Ui ui = new Ui("<MenuBar><Menu text=\"File\"><MenuItem text=\"Open\" onAction=\"#click\"/></Menu>"
                    + "<Menu text=\"Edit\"><MenuItem text=\"Copy\" onAction=\"#toggleBold\"/><MenuItem text=\"Paste\"/></Menu></MenuBar>");
            JXNativeNode bar = ui.find("menubar");
            ui.click(bar.getX() + 12, center(bar)[1]);
            assertEquals(0, bar.openChild);
            ui.key(DOWN);
            assertEquals(0, row(bar, 0).highlight, "Down highlights the first item of the open menu");
            ui.key(RIGHT);
            assertEquals(1, bar.openChild, "Right goes to the next menu");
            assertEquals(0, row(bar, 1).highlight, "with its first item highlighted");
            ui.key(RIGHT);
            assertEquals(0, bar.openChild, "and round the end");
            ui.key(LEFT);
            assertEquals(1, bar.openChild, "Left goes back");
            ui.key(DOWN);
            ui.key(UP);
            ui.key(HOME);
            ui.key(ENTER);
            assertTrue(ui.c.getBold(), "Enter runs the item of the second menu");
            assertEquals(-1, bar.openChild, "and closes the bar");
        }

        @Test
        void aClosedMenuButtonWithTheFocusOpensWithEnterSpaceAltDownAndF4() {
            Ui ui = new Ui(MENU);
            ui.click(ui.find("menubutton"));
            ui.key(ESCAPE);
            assertTrue(!menuOpen(ui, "menubutton"));
            ui.key(ENTER);
            assertTrue(menuOpen(ui, "menubutton"), "Enter opens");
            assertEquals(0, ui.find("menubutton").highlight, "with the first item highlighted");
            ui.key(ESCAPE);
            ui.type(" ");
            assertTrue(menuOpen(ui, "menubutton"), "Space opens");
            ui.key(F4, 0);
            assertTrue(!menuOpen(ui, "menubutton"), "F4 closes");
            ui.key(DOWN, ALT);
            assertTrue(menuOpen(ui, "menubutton"), "Alt+Down opens");
            assertEquals(0, ui.c.clicks, "opening runs nothing");
        }

        @Test
        void otherPopupsOpenWithTheKeyboardAndCloseWithEscape() {
            Ui ui = new Ui("<DatePicker value=\"@{date}\" showing=\"${dateOpen}\" onShowing=\"#setDateOpen\"/>\n"
                    + "<TimePicker value=\"@{time}\" showing=\"${timeOpen}\" onShowing=\"#setTimeOpen\"/>");
            ui.click(ui.find("datepicker"));
            ui.key(ESCAPE);
            ui.key(DOWN, ALT);
            assertTrue(ui.c.getDateOpen(), "Alt+Down opens the calendar");
            ui.key(F4, 0);
            assertTrue(!ui.c.getDateOpen(), "F4 closes it");
            ui.key(TAB);
            ui.type(" ");
            assertTrue(ui.c.getTimeOpen(), "Space opens the time picker that has the focus");
            ui.key(ESCAPE);
            assertTrue(!ui.c.getTimeOpen());
            assertEquals(ui.find("timepicker"), ui.focused);
        }

        @Test
        void aPopupThatNothingHoveredOrTypedIsDrawnAsBefore() {
            Ui ui = openCombo();
            JXNativeNode combo = ui.find("select");
            assertEquals(-1, combo.highlight, "a popup opened with the pointer has no highlight");
            ui.hover(5, ui.h - 5);
            assertEquals(-1, combo.highlight);
            ui.key(ESCAPE);
            ui.click(ui.find("select"));
            assertEquals(-1, ui.find("select").highlight, "and one opened again starts clean");
        }
    }

    // ---- empty areas of layout panes ---------------------------------------------------------------

    @Nested
    class EmptyAreas {
        private String screen(String overlay) {
            return "<Stack controller=\"" + CTRL + "\">\n<Column padding=\"10\" gap=\"8\">\n"
                    + "<Button text=\"Below\" onAction=\"#click\" prefWidth=\"120\"/>\n"
                    + "<Scroll fitToWidth=\"true\" prefHeight=\"150\"><Column gap=\"4\">"
                    + "<For items=\"${rows}\" var=\"r\" type=\"java.lang.String\" limit=\"40\">"
                    + "<Button text=\"${r}\" onAction=\"#pick(r)\" prefHeight=\"30\"/></For></Column></Scroll>\n"
                    + "</Column>\n" + overlay + "\n</Stack>";
        }

        private final String closedModals = "<Stack><Modal><Label text=\"a\"/></Modal><Modal><Label text=\"b\"/></Modal></Stack>";

        @Test
        void anOverlayStackHoldingOnlyClosedModalsLetsThePointerThrough() {
            Ui ui = new Ui(400, 400, screen(closedModals));
            int[] c = center(ui.find("button"));
            ui.click(c[0], c[1]);
            assertEquals(1, ui.c.clicks, "the click reaches the button under the Stack");
            ui.hover(c[0], c[1]);
            assertEquals("button", ui.root.hitTest(c[0], c[1]).getType());
            JXNativeNode pane = ui.find("scroll");
            int[] s = center(pane);
            assertTrue(ui.wheel(s[0], s[1], 2), "the wheel reaches the scroll pane under it");
            assertTrue(pane.userScrollY > 0);
            // an empty spot of the window: only the window root is hit, not the overlay Stack above the content
            assertEquals(1, ui.root.hitPath(390, 390).size());
        }

        @Test
        void aFileDroppedThroughTheOverlayReachesWhatIsBelow() {
            Ui ui = new Ui(400, 400, "<Stack controller=\"" + CTRL + "\">\n<Column onDrop=\"#dropped\" prefHeight=\"300\">"
                    + "<Label text=\"x\"/></Column>\n" + closedModals + "\n</Stack>");
            assertTrue(ui.root.dispatchDrop(100, 100, java.util.Arrays.asList("a.txt")));
            assertEquals(1, ui.c.drops.size());
        }

        @Test
        void aStackWithABackgroundOrAHandlerStillTakesItsEmptyArea() {
            Ui ui = new Ui(400, 400, screen("<Stack background=\"#80000000\" prefWidth=\"300\" prefHeight=\"300\"/>"));
            JXNativeNode top = ui.root.hitTest(250, 250);
            assertNotNull(top);
            assertEquals("stack", top.getType(), "a background makes it a box that takes clicks");
            int[] b = c(ui, "button");
            ui.click(b[0], b[1]);
            assertEquals(0, ui.c.clicks, "so the button under it gets none");
            Ui handler = new Ui(400, 400, screen("<Column onAction=\"#click\" prefWidth=\"300\" prefHeight=\"300\"/>"));
            handler.click(250, 250);
            assertEquals(1, handler.c.clicks, "an onAction on an empty container runs on its empty area");
        }

        private int[] c(Ui ui, String type) {
            return center(ui.find(type));
        }

        @Test
        void hoverAndDropOnTheEmptyAreaOfAContainerWithHandlersStillWork() {
            Ui ui = new Ui(400, 400, "<Stack controller=\"" + CTRL + "\">\n<Column onHover=\"#outerHover\" onDrop=\"#dropped\" "
                    + "prefHeight=\"200\" prefWidth=\"200\"><Label text=\"x\"/></Column>\n</Stack>");
            ui.hover(150, 150);
            assertEquals(java.util.Arrays.asList("outer+"), ui.c.hovers);
            assertTrue(ui.root.dispatchDrop(150, 150, java.util.Arrays.asList("a.txt")));
        }

        @Test
        void theWheelOverTheEmptyAreaOfAScrollPaneStillScrollsIt() {
            Ui ui = new Ui("<Scroll prefHeight=\"150\" prefWidth=\"300\"><Column gap=\"4\">"
                    + "<For items=\"${rows}\" var=\"r\" type=\"java.lang.String\" limit=\"40\">"
                    + "<Button text=\"${r}\" prefWidth=\"50\" prefHeight=\"30\"/></For></Column></Scroll>");
            JXNativeNode pane = ui.find("scroll");
            assertTrue(ui.wheel(pane.getX() + pane.getWidth() - 40, pane.getY() + 20, 2), "right of the buttons: the empty area of the column");
            assertTrue(pane.userScrollY > 0);
        }

        @Test
        void anOpenDialogKeepsItsScrimOverTheScreen() {
            Ui ui = new Ui(400, 400, screen("<Modal showing=\"${dialog}\"><Label text=\"in\"/></Modal>"));
            assertTrue(ui.c.getDialog());
            int[] c = c(ui, "button");
            ui.click(c[0], c[1]);
            assertEquals(0, ui.c.clicks, "the scrim takes the click meant for the button");
        }
    }

    // ---- desktop window behaviours: mouse cursor, file drop, keyboard menus, accelerators -----------

    @Nested
    class PickerPopups {
        private static final int PAGE_DOWN = 267;
        private static final int SPACE = 32;

        private Ui date() {
            Ui ui = new Ui("<DatePicker value=\"@{date}\" showing=\"${dateOpen}\" onShowing=\"#setDateOpen\"/>");
            ui.click(ui.find("datepicker"));
            assertTrue(ui.c.getDateOpen());
            return ui;
        }

        /** The top left pixel of the cell of a day in the open calendar. */
        private int[] cellOf(Ui ui, int month, int day) {
            JXNativeNode calendar = JXOverlays.popup(ui.find("datepicker")).calendar;
            for (int y = calendar.getY(); y < calendar.getY() + calendar.getHeight(); y += 2) {
                for (int x = calendar.getX(); x < calendar.getX() + calendar.getWidth(); x += 2) {
                    int[] hit = JXCalendar.hit(calendar, x, y);
                    if (hit[0] == JXCalendar.DAY && hit[2] == month && hit[3] == day) {
                        return new int[] {x, y};
                    }
                }
            }
            throw new AssertionError("no cell for " + month + "/" + day);
        }

        @Test
        void thePointerHighlightsTheDayOfTheCalendarItIsOver() {
            Ui ui = date();
            JXNativeNode picker = ui.find("datepicker");
            int[] at = cellOf(ui, 9, 20);
            assertEquals(JXNativeNode.NO_DATE, picker.highlightDate);
            ui.hover(at[0] + 3, at[1] + 3);
            assertEquals(java.time.LocalDate.of(2026, 9, 20).toEpochDay(), picker.highlightDate);
            assertEquals(JXOverlayPaint.HOVER, ui.pixel(at[0] + 3, at[1] + 3), "the day is drawn highlighted");
            ui.key(ESCAPE);
            assertEquals(JXNativeNode.NO_DATE, picker.highlightDate, "closing clears it");
        }

        @Test
        void theArrowsMoveTheHighlightedDayAndEnterChoosesIt() {
            Ui ui = date();
            JXNativeNode picker = ui.find("datepicker");
            ui.key(RIGHT);
            assertEquals(java.time.LocalDate.of(2026, 9, 16).toEpochDay(), picker.highlightDate, "from the chosen day");
            assertEquals("2026-09-15", ui.c.getDate(), "moving the highlight changes nothing");
            ui.key(DOWN);
            assertEquals(java.time.LocalDate.of(2026, 9, 23).toEpochDay(), picker.highlightDate, "a week");
            ui.key(LEFT);
            ui.key(UP);
            assertEquals(java.time.LocalDate.of(2026, 9, 15).toEpochDay(), picker.highlightDate);
            ui.key(PAGE_DOWN);
            assertEquals(10, picker.viewMonth, "the calendar turned to October");
            ui.key(ENTER);
            assertEquals("2026-10-15", ui.c.getDate());
            assertTrue(!ui.c.getDateOpen(), "Enter chooses and closes");
        }

        @Test
        void enterWithNothingHighlightedStillClosesAndEscapeChangesNothing() {
            Ui ui = date();
            ui.key(ENTER);
            assertTrue(!ui.c.getDateOpen());
            assertEquals("2026-09-15", ui.c.getDate());
            ui.click(ui.find("datepicker"));
            ui.key(RIGHT);
            ui.key(ESCAPE);
            assertEquals("2026-09-15", ui.c.getDate());
        }

        @Test
        void theTimePickerHighlightsUnderThePointerAndMovesWithTheArrows() {
            Ui ui = new Ui("<TimePicker value=\"@{time}\" showing=\"${timeOpen}\" onShowing=\"#setTimeOpen\"/>");
            ui.click(ui.find("timepicker"));
            JXNativeNode picker = ui.find("timepicker");
            int[][] cells = picker.getPopupCells();
            int[] c = center(cells[24 + 3]);
            ui.hover(c[0], c[1]);
            assertEquals(24 + 3, picker.highlight, "the minute cell under the pointer");
            ui.key(ESCAPE);
            ui.click(ui.find("timepicker"));
            assertEquals(-1, picker.highlight, "reopened clean");
            ui.key(DOWN);
            assertEquals(16, picker.highlight, "from the chosen hour 10 a row down is hour 16");
            ui.key(RIGHT);
            assertEquals(17, picker.highlight);
            ui.key(LEFT);
            ui.key(LEFT);
            assertEquals(15, picker.highlight);
            ui.key(UP);
            assertEquals(9, picker.highlight);
            ui.key(ENTER);
            assertEquals("09:30", ui.c.getTime(), "Enter chooses the hour, like a click");
            assertTrue(ui.c.getTimeOpen(), "the popup stays, so the minute can follow");
            ui.key(END);
            assertEquals(cells.length - 1, picker.highlight);
        }

        @Test
        void theColorPickerHighlightsUnderThePointerAndMovesWithTheArrows() {
            Ui ui = new Ui("<ColorPicker value=\"@{color}\" showing=\"${colorOpen}\" onShowing=\"#setColorOpen\"/>");
            ui.click(ui.find("colorpicker"));
            JXNativeNode picker = ui.find("colorpicker");
            int[][] cells = picker.getPopupCells();
            int[] c = center(cells[5]);
            ui.hover(c[0], c[1]);
            assertEquals(5, picker.highlight);
            ui.key(RIGHT);
            assertEquals(6, picker.highlight);
            ui.key(DOWN);
            assertEquals(14, picker.highlight, "eight swatches to a row");
            ui.key(LEFT);
            ui.key(UP);
            assertEquals(5, picker.highlight);
            ui.key(DOWN);
            ui.key(SPACE);
            assertEquals(JXOverlays.hex(JXOverlays.PALETTE[13]), ui.c.getColor());
            ui.key(HOME);
            assertEquals(0, picker.highlight);
        }

        @Test
        void typingHexDigitsInTheColorPickerStillWorks() {
            Ui ui = new Ui("<ColorPicker value=\"@{color}\" showing=\"${colorOpen}\" onShowing=\"#setColorOpen\"/>");
            ui.click(ui.find("colorpicker"));
            for (int i = 0; i < 7; i++) {
                ui.key(BACKSPACE);
            }
            ui.type("#00FF7F");
            assertEquals("#00FF7F", ui.c.getColor());
        }
    }

    @Nested
    class Accelerators {
        private static final int S = 83;
        private static final int C = 67;
        private static final int F5 = 294;
        private static final int CTRL_ = JXInputEvent.CONTROL;

        @Test
        void aButtonRunsOnItsAcceleratorFromAnywhere() {
            Ui ui = new Ui("<Button text=\"Save\" accelerator=\"Ctrl+S\" onAction=\"#save\"/>\n<CheckBox text=\"x\" selected=\"@{agree}\"/>");
            ui.click(ui.find("checkbox"));
            ui.key(S, CTRL_);
            assertEquals(1, ui.c.saves, "Ctrl+S with another control focused");
            ui.key(S, 0);
            ui.key(S, CTRL_ | JXInputEvent.SHIFT);
            assertEquals(1, ui.c.saves, "other modifiers are other shortcuts");
        }

        @Test
        void aMenuItemRunsWithItsMenuClosed() {
            Ui ui = new Ui("<MenuBar><Menu text=\"File\"><MenuItem text=\"Save\" accelerator=\"Ctrl+S\" onAction=\"#save\"/>"
                    + "<MenuItem text=\"Off\" accelerator=\"F5\" disabled=\"true\" onAction=\"#click\"/></Menu>"
                    + "<Menu text=\"Dead\" disabled=\"true\"><MenuItem text=\"Never\" accelerator=\"Ctrl+D\" onAction=\"#click\"/></Menu></MenuBar>");
            ui.key(S, CTRL_);
            assertEquals(1, ui.c.saves);
            ui.key(F5, 0);
            ui.key(68, CTRL_);
            assertEquals(0, ui.c.clicks, "a disabled item and the item of a disabled menu do not run");
            assertTrue(!JXOverlays.isOpen(ui.find("menubar")), "and the menu stays closed");
        }

        @Test
        void aTextFieldKeepsItsOwnShortcutsButNotTheOthers() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<Button text=\"Save\" accelerator=\"Ctrl+S\" onAction=\"#save\"/>"
                    + "\n<Button text=\"Copy\" accelerator=\"Shortcut+C\" onAction=\"#click\"/>\n<Button text=\"Reload\" accelerator=\"F5\" onAction=\"#toggleBold\"/>");
            ui.click(ui.find("input"));
            ui.key(C, JXInputEvent.SHORTCUT);
            assertEquals(0, ui.c.clicks, "Shortcut+C (Cmd+C on macOS) copies in a text field");
            ui.key(S, CTRL_);
            assertEquals(1, ui.c.saves, "Ctrl+S is nobody's there");
            ui.key(F5, 0);
            assertTrue(ui.c.getBold(), "F5 neither");
        }

        @Test
        void ctrlShiftAcceleratorsReachPastATextField() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<Button text=\"Paste plain\" accelerator=\"Shortcut+Shift+V\" onAction=\"#save\"/>"
                    + "\n<Button text=\"Redo\" accelerator=\"Shortcut+Shift+Z\" onAction=\"#click\"/>");
            JXNativeNode.setClipboard(new com.jxparallel.ui.input.JXClipboard() {
                @Override
                public String getText() {
                    return "PASTED";
                }

                @Override
                public void setText(String value) {
                }
            });
            try {
                ui.click(ui.find("input"));
                ui.key(86, JXInputEvent.SHORTCUT | JXInputEvent.SHIFT);
                assertEquals(1, ui.c.saves, "Ctrl+Shift+V runs the button with a field focused");
                assertTrue(ui.c.getName() == null || ui.c.getName().isEmpty(), "and nothing was pasted");
                ui.key(90, JXInputEvent.SHORTCUT | JXInputEvent.SHIFT);
                assertEquals(0, ui.c.clicks, "Ctrl+Shift+Z stays the field's redo");
            } finally {
                JXNativeNode.setClipboard(null);
            }
        }

        @Test
        void anOpenMenuAndAHeldKeyAreLeftAlone() {
            Ui ui = new Ui("<Button text=\"Save\" accelerator=\"Ctrl+S\" onAction=\"#save\"/>\n"
                    + "<MenuButton text=\"Actions\"><MenuItem text=\"Open\" onAction=\"#click\"/></MenuButton>");
            ui.root.routeKey(new JXKeyEvent(S, '\0', CTRL_, true), null);
            assertEquals(0, ui.c.saves, "auto-repeat does not run it again");
            ui.click(ui.find("menubutton"));
            ui.key(S, CTRL_);
            assertEquals(0, ui.c.saves, "an open menu takes every key");
        }

        @Test
        void aDialogOnlyAllowsItsOwnAcceleratorsAndAClosedOneNone() {
            Ui ui = new Ui("<Button text=\"Save\" accelerator=\"Ctrl+S\" onAction=\"#save\"/>\n"
                    + "<Modal showing=\"${dialog}\"><Button text=\"In\" accelerator=\"Ctrl+I\" onAction=\"#click\"/></Modal>");
            assertTrue(ui.c.getDialog(), "the dialog starts open");
            ui.key(S, CTRL_);
            assertEquals(0, ui.c.saves, "behind an open dialog");
            ui.key(73, CTRL_);
            assertEquals(1, ui.c.clicks, "inside it");
            ui.c.setDialog(false);
            ui.refresh();
            ui.key(73, CTRL_);
            assertEquals(1, ui.c.clicks, "a closed dialog's buttons are not in the window");
            ui.key(S, CTRL_);
            assertEquals(1, ui.c.saves);
        }

        @Test
        void theTextOfAnAcceleratorParses() {
            assertEquals(83, JXAccelerator.parse("Ctrl+S").code);
            assertEquals(JXInputEvent.CONTROL, JXAccelerator.parse("ctrl+s").modifiers);
            assertEquals(JXInputEvent.CONTROL | JXInputEvent.SHIFT, JXAccelerator.parse("Ctrl + Shift + Z").modifiers);
            assertEquals(JXInputEvent.SHORTCUT, JXAccelerator.parse("Shortcut+O").modifiers); // Cmd on macOS
            assertEquals(JXInputEvent.ALT, JXAccelerator.parse("Alt+Enter").modifiers);
            assertEquals(257, JXAccelerator.parse("Alt+Enter").code);
            assertEquals(290, JXAccelerator.parse("F1").code);
            assertEquals(301, JXAccelerator.parse("F12").code);
            assertEquals(261, JXAccelerator.parse("Delete").code);
            assertEquals(JXInputEvent.META, JXAccelerator.parse("Cmd+Q").modifiers);
            assertNull(JXAccelerator.parse("Ctrl+"));
            assertNull(JXAccelerator.parse("Ctrl+Sparkle"));
            assertNull(JXAccelerator.parse("Hyper+S"));
            assertNull(JXAccelerator.parse(""));
            assertNull(JXAccelerator.parse(null));
        }
    }

    @Nested
    class MenusByKeyboard {
        private static final int F10 = 299;
        private static final int MENU_KEY = 348;
        private static final String BAR = "<MenuBar><Menu text=\"File\" disabled=\"true\"><MenuItem text=\"Never\" onAction=\"#toggleBold\"/></Menu>"
                + "<Menu text=\"Edit\"><MenuItem text=\"Copy\" onAction=\"#save\"/><MenuItem text=\"Paste\" onAction=\"#click\"/></Menu></MenuBar>";

        @Test
        void f10OpensTheFirstMenuThatCanOpenAndTheArrowsAndEnterWorkInIt() {
            Ui ui = new Ui(BAR);
            JXNativeNode bar = ui.find("menubar");
            ui.key(F10, 0);
            assertTrue(JXOverlays.isOpen(bar), "F10 opens the menu bar");
            assertEquals(1, bar.openChild, "the disabled File menu is skipped");
            assertEquals(0, bar.getChildren().get(1).highlight, "its first row is highlighted");
            ui.key(DOWN);
            ui.key(ENTER);
            assertEquals(1, ui.c.clicks, "Paste ran");
            assertTrue(!JXOverlays.isOpen(bar), "and the menu closed");
        }

        @Test
        void f10AgainAndEscapeCloseTheMenuBar() {
            Ui ui = new Ui(BAR);
            JXNativeNode bar = ui.find("menubar");
            ui.key(F10, 0);
            ui.key(F10, 0);
            assertTrue(!JXOverlays.isOpen(bar), "F10 closes it again");
            ui.key(F10, 0);
            ui.key(ESCAPE);
            assertTrue(!JXOverlays.isOpen(bar), "Escape closes it");
            assertEquals(0, ui.c.saves + ui.c.clicks, "opening and closing run nothing");
        }

        @Test
        void anAltTapOpensTheMenuBarAndAltWithAnotherKeyOrAClickDoesNot() {
            Ui ui = new Ui(BAR);
            JXAltTap alt = new JXAltTap();
            assertTrue(!alt.key(342, true));
            assertTrue(alt.key(342, false), "pressed and released alone is a tap");
            assertTrue(ui.root.toggleMenuBar());
            assertTrue(JXOverlays.isOpen(ui.find("menubar")));
            assertTrue(!alt.key(342, true));
            assertTrue(!alt.key(70, true), "Alt+F");
            assertTrue(!alt.key(70, false));
            assertTrue(!alt.key(342, false), "so the release of Alt is no tap");
            alt.key(346, true);
            alt.cancel();
            assertTrue(!alt.key(346, false), "Alt+click");
        }

        @Test
        void withoutAMenuBarOrWithAnotherPopupOpenF10DoesNothing() {
            Ui none = new Ui("<Button text=\"x\" onAction=\"#click\"/>");
            none.key(F10, 0);
            assertTrue(!none.root.toggleMenuBar());
            Ui ui = new Ui(BAR + "\n<MenuButton text=\"Actions\"><MenuItem text=\"Open\" onAction=\"#click\"/></MenuButton>");
            ui.click(ui.find("menubutton"));
            ui.key(F10, 0);
            assertTrue(!JXOverlays.isOpen(ui.find("menubar")), "a menu button's menu is open: F10 leaves it");
        }

        @Test
        void theMenuKeyAndShiftF10OpenTheContextMenuOfTheFocusedNode() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<ContextMenu><MenuItem text=\"Copy\" onAction=\"#save\"/>"
                    + "<MenuItem text=\"Paste\" onAction=\"#click\"/></ContextMenu>");
            ui.click(ui.find("input"));
            JXNativeNode menu = ui.find("contextmenu");
            ui.key(MENU_KEY, 0);
            assertTrue(JXOverlays.isOpen(menu), "the Menu key opens it");
            assertEquals(0, menu.highlight, "with the first row highlighted");
            ui.key(DOWN);
            ui.key(ENTER);
            assertEquals(1, ui.c.clicks, "Enter runs Paste");
            assertTrue(!JXOverlays.isOpen(menu));
            ui.key(F10, JXInputEvent.SHIFT);
            assertTrue(JXOverlays.isOpen(menu), "Shift+F10 opens it too");
            ui.key(ESCAPE);
            assertTrue(!JXOverlays.isOpen(menu));
            assertEquals(ui.find("input"), ui.focused, "the field keeps the focus");
        }

        @Test
        void aFocusedNodeWithoutAContextMenuOpensNone() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>");
            ui.click(ui.find("input"));
            ui.key(MENU_KEY, 0);
            ui.key(F10, JXInputEvent.SHIFT);
            assertTrue(ui.root.getChildren().size() > 0);
        }
    }

    @Nested
    class GraphicsAndHover {
        @Test
        void aClickOnTheIconInsideAButtonClicksTheButton() {
            Ui ui = new Ui("<Button text=\"Add\" onAction=\"#click\"><Icon name=\"add\"/></Button>");
            JXNativeNode icon = ui.find("icon");
            int[] c = center(icon);
            assertEquals(ui.find("button"), ui.root.hitTest(c[0], c[1]), "the icon does not take the pointer");
            assertEquals(JXCursor.ARROW, ui.root.cursorAt(c[0], c[1]));
            ui.press(c[0], c[1]);
            assertEquals(0, ui.c.clicks, "a press only arms");
            assertEquals(Boolean.TRUE, ui.find("button").getProperty("pressed"), "drawn pressed");
            ui.release();
            assertEquals(1, ui.c.clicks, "the release on the icon runs onAction");
        }

        @Test
        void anIconOnlyButtonAndAHoverOnItsIconWork() {
            Ui ui = new Ui("<Button onAction=\"#click\" cursor=\"hand\"><Icon name=\"add\"/></Button>");
            int[] c = center(ui.find("icon"));
            assertEquals(JXCursor.HAND, ui.root.cursorAt(c[0], c[1]), "the cursor of the button, over its graphic");
            ui.hover(c[0], c[1]);
            assertEquals(Boolean.TRUE, ui.find("button").getProperty("hover"));
            ui.click(c[0], c[1]);
            assertEquals(1, ui.c.clicks);
        }

        @Test
        void onHoverReportsEnterAndLeaveAlongTheHoverPath() {
            Ui ui = new Ui("<Column onHover=\"#outerHover\" prefHeight=\"120\" prefWidth=\"200\">"
                    + "<Label text=\"in\" onHover=\"#innerHover\"/><Label text=\"plain\"/></Column>\n<Label text=\"out\"/>");
            int[] in = center(ui.find("#text", 0));
            int[] plain = center(ui.find("#text", 1));
            int[] out = center(ui.find("#text", 2));
            ui.hover(in[0], in[1]);
            assertEquals(java.util.Arrays.asList("outer+", "inner+"), ui.c.hovers, "outer first");
            ui.hover(in[0] + 1, in[1]);
            assertEquals(2, ui.c.hovers.size(), "moving inside the same node reports nothing");
            ui.hover(plain[0], plain[1]);
            assertEquals(java.util.Arrays.asList("outer+", "inner+", "inner-"), ui.c.hovers,
                    "the parent stays hovered while the pointer is over another child");
            ui.hover(in[0], in[1]);
            ui.hover(out[0], out[1]);
            assertEquals(java.util.Arrays.asList("outer+", "inner+", "inner-", "inner+", "inner-", "outer-"), ui.c.hovers,
                    "leaving: the child, then the parent");
        }

        @Test
        void onHoverIsFalseWhenThePointerLeavesTheWindow() {
            Ui ui = new Ui("<Column onHover=\"#outerHover\" prefHeight=\"120\"><Label text=\"in\"/></Column>");
            int[] in = center(ui.find("#text"));
            ui.hover(in[0], in[1]);
            assertEquals(java.util.Arrays.asList("outer+"), ui.c.hovers);
            ui.hover(-1, -1);
            assertEquals(java.util.Arrays.asList("outer+", "outer-"), ui.c.hovers);
            ui.hover(-1, -1);
            assertEquals(2, ui.c.hovers.size());
        }
    }

    @Nested
    class ClosedLayers {
        @Test
        void theContentOfAClosedDialogOrDrawerIsNotInTheTree() {
            Ui ui = new Ui("<Button text=\"Open\" onAction=\"#click\"/>\n"
                    + "<Modal showing=\"${dialog}\" onShowing=\"#setDialog\"><TextField text=\"@{other}\"/><Button text=\"OK\" onAction=\"#click\"/></Modal>\n"
                    + "<Drawer showing=\"${drawer}\" onShowing=\"#setDrawer\"><TextField text=\"@{notes}\"/></Drawer>");
            ui.c.setDialog(false);
            ui.c.setDrawer(false);
            ui.refresh();
            assertEquals(0, ui.find("dialog").getChildren().size());
            assertEquals(0, ui.find("drawer").getChildren().size());
            assertNull(find(ui.root, "input"), "no field of a closed layer is on the screen");

            ui.c.setDialog(true);
            ui.refresh();
            assertEquals(2, ui.find("dialog").getChildren().size(), "it is built when it opens");
            JXNativeNode field = ui.click(center(ui.find("input"))[0], center(ui.find("input"))[1]);
            assertEquals("input", field.getType());
            ui.type("x");
            assertEquals("x", ui.c.getOther());

            ui.c.setDialog(false);
            ui.refresh();
            assertEquals(0, ui.find("dialog").getChildren().size(), "and dropped when it closes");
            ui.c.setDrawer(true);
            ui.refresh();
            assertEquals(1, ui.find("drawer").getChildren().size());
        }

        @Test
        void aTabKeyAndAnEnterInADialogThatOpenedLateStillWork() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<Modal showing=\"${dialog}\" onShowing=\"#setDialog\">"
                    + "<TextField text=\"@{other}\"/><Button text=\"OK\" defaultButton=\"true\" onAction=\"#click\"/></Modal>");
            ui.c.setDialog(true);
            ui.refresh();
            ui.key(TAB);
            assertEquals("input", ui.focused.getType());
            assertEquals(ui.find("dialog"), ui.find("dialog"));
            ui.key(ENTER);
            assertEquals(1, ui.c.clicks, "the default button of the dialog that was built when it opened");
            ui.key(ESCAPE);
            assertTrue(!ui.c.getDialog());
            ui.refresh();
            assertEquals(0, ui.find("dialog").getChildren().size());
        }

        private JXNativeNode find(JXNativeNode node, String type) {
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
    }

    @Nested
    class FileDrop {
        private final List<String> files = java.util.Arrays.asList("C:\\a.txt", "C:\\b.png");

        @Test
        void theFilesGoToTheNodeUnderThePointerThatHasOnDrop() {
            Ui ui = new Ui("<Column onDrop=\"#dropped\" prefHeight=\"120\"><Label text=\"inside\"/></Column>\n<Label text=\"outside\"/>");
            int[] in = center(ui.find("#text", 0));
            assertTrue(ui.root.dispatchDrop(in[0], in[1], files), "the label has no handler: its column takes the files");
            assertEquals(1, ui.c.drops.size());
            assertEquals(files, ui.c.drops.get(0));
            int[] out = center(ui.find("#text", 1));
            assertTrue(!ui.root.dispatchDrop(out[0], out[1], files), "nothing there takes files");
            assertEquals(1, ui.c.drops.size());
        }

        @Test
        void theNearestAncestorWinsAndADisabledOneIsSkipped() {
            Ui ui = new Ui("<Column onDrop=\"#dropped\"><Row onDrop=\"#droppedInner\"><Label text=\"inner\"/></Row>"
                    + "<Button text=\"b\" disabled=\"true\" onDrop=\"#droppedInner\"/></Column>");
            int[] label = center(ui.find("#text"));
            assertTrue(ui.root.dispatchDrop(label[0], label[1], files));
            assertEquals(1, ui.c.drops2.size(), "the row, nearer than the column");
            assertEquals(0, ui.c.drops.size());
            int[] button = center(ui.find("button"));
            assertTrue(ui.root.dispatchDrop(button[0], button[1], files));
            assertEquals(1, ui.c.drops2.size(), "a disabled node takes nothing");
            assertEquals(1, ui.c.drops.size(), "so the column does");
        }

        @Test
        void anOpenDialogKeepsTheFilesFromWhatIsBehindIt() {
            Ui ui = new Ui("<Column onDrop=\"#dropped\" prefHeight=\"380\"><Label text=\"behind\"/></Column>\n"
                    + "<Modal showing=\"true\"><Label text=\"in the dialog\"/></Modal>");
            int[] behind = {3, ui.h - 3};
            assertTrue(!ui.root.dispatchDrop(behind[0], behind[1], files), "over the scrim");
            assertEquals(0, ui.c.drops.size());
        }

        @Test
        void overADialogOnlyWhatIsInsideItTakesTheFilesNotTheScreenBehindIt() {
            // the Column with onDrop holds the dialog, so it is an ancestor of everything in it
            Ui ui = new Ui("<Column onDrop=\"#dropped\" prefHeight=\"380\"><Label text=\"behind\"/>"
                    + "<Modal showing=\"true\"><Label text=\"plain\"/></Modal></Column>");
            JXNativeNode dialog = ui.find("dialog");
            int[] scrim = {3, ui.h - 3};
            int[] form = center(ui.find("#text", 1));
            assertTrue(dialog.contains(form[0], form[1]));
            assertTrue(!ui.root.dispatchDrop(scrim[0], scrim[1], files), "the scrim");
            assertTrue(!ui.root.dispatchDrop(form[0], form[1], files), "a dialog without onDrop: nobody, not its ancestors");
            assertEquals(0, ui.c.drops.size());

            Ui handled = new Ui("<Column onDrop=\"#dropped\" prefHeight=\"380\"><Label text=\"behind\"/>"
                    + "<Modal showing=\"true\"><Column onDrop=\"#droppedInner\"><Label text=\"zone\"/></Column></Modal></Column>");
            int[] zone = center(handled.find("#text", 1));
            assertTrue(handled.root.dispatchDrop(zone[0], zone[1], files), "the nearest onDrop inside the dialog");
            assertEquals(1, handled.c.drops2.size());
            assertEquals(0, handled.c.drops.size());
            assertTrue(!handled.root.dispatchDrop(3, handled.h - 3, files), "the scrim, even with an onDrop outside");
            assertEquals(0, handled.c.drops.size());

            Ui onDialog = new Ui("<Column onDrop=\"#dropped\" prefHeight=\"380\">"
                    + "<Modal showing=\"true\" onDrop=\"#droppedInner\"><Label text=\"x\"/></Modal></Column>");
            int[] in = center(onDialog.find("#text"));
            assertTrue(onDialog.root.dispatchDrop(in[0], in[1], files), "onDrop on the dialog itself");
            assertEquals(1, onDialog.c.drops2.size());
            assertTrue(!onDialog.root.dispatchDrop(3, onDialog.h - 3, files), "but not on its scrim");
            assertEquals(0, onDialog.c.drops.size());
        }

        @Test
        void aDrawerKeepsTheFilesFromTheScreenBehindItToo() {
            Ui ui = new Ui("<Column onDrop=\"#dropped\" prefHeight=\"380\"><Label text=\"behind\"/>"
                    + "<Drawer side=\"left\" showing=\"true\"><Label text=\"menu\"/>"
                    + "<Column onDrop=\"#droppedInner\"><Label text=\"zone\"/></Column></Drawer></Column>");
            int[] label = center(ui.find("#text", 1));
            assertTrue(!ui.root.dispatchDrop(label[0], label[1], files), "in the drawer, outside its drop zone");
            int[] zone = center(ui.find("#text", 2));
            assertTrue(ui.root.dispatchDrop(zone[0], zone[1], files), "its drop zone");
            assertEquals(1, ui.c.drops2.size());
            assertTrue(!ui.root.dispatchDrop(ui.w - 5, ui.h / 2, files), "the scrim");
            assertEquals(0, ui.c.drops.size());
        }

        @Test
        void noFilesTakeNothing() {
            Ui ui = new Ui("<Column onDrop=\"#dropped\" prefHeight=\"120\"><Label text=\"x\"/></Column>");
            int[] in = center(ui.find("#text"));
            assertTrue(!ui.root.dispatchDrop(in[0], in[1], new java.util.ArrayList<String>()));
        }
    }

    @Nested
    class MouseCursors {
        @Test
        void aTextFieldShowsTheIBeamAndAHyperlinkTheHand() {
            Ui ui = new Ui("<TextField text=\"@{name}\"/>\n<PasswordField text=\"${secret}\"/>\n<TextArea text=\"${notes}\"/>\n"
                    + "<Hyperlink text=\"More\" onAction=\"#click\"/>\n<Label text=\"plain\"/>");
            assertEquals(JXCursor.IBEAM, ui.root.cursorAt(center(ui.find("input"))[0], center(ui.find("input"))[1]));
            assertEquals(JXCursor.IBEAM, ui.root.cursorAt(center(ui.find("password"))[0], center(ui.find("password"))[1]));
            assertEquals(JXCursor.IBEAM, ui.root.cursorAt(center(ui.find("textarea"))[0], center(ui.find("textarea"))[1]));
            assertEquals(JXCursor.HAND, ui.root.cursorAt(center(ui.find("hyperlink"))[0], center(ui.find("hyperlink"))[1]));
            assertEquals(JXCursor.ARROW, ui.root.cursorAt(center(ui.find("#text"))[0], center(ui.find("#text"))[1]));
            assertEquals(JXCursor.ARROW, ui.root.cursorAt(ui.w - 2, ui.h - 2), "the empty window");
            assertEquals(JXCursor.ARROW, ui.root.cursorAt(-1, -1), "the pointer left the window");
        }

        @Test
        void aDisabledHyperlinkKeepsTheArrow() {
            Ui ui = new Ui("<Hyperlink text=\"More\" disabled=\"true\"/>");
            assertEquals(JXCursor.ARROW, ui.root.cursorAt(center(ui.find("hyperlink"))[0], center(ui.find("hyperlink"))[1]));
        }

        @Test
        void theCursorAttributeOfAnyElementWinsAndTheDeepestOneCounts() {
            Ui ui = new Ui("<Button text=\"Go\" cursor=\"hand\" onAction=\"#click\"/>\n"
                    + "<TextField text=\"@{name}\" cursor=\"default\"/>\n"
                    + "<Row cursor=\"crosshair\"><Label text=\"in a crosshair\"/><Label text=\"own\" cursor=\"v-resize\"/></Row>");
            assertEquals(JXCursor.HAND, ui.root.cursorAt(center(ui.find("button"))[0], center(ui.find("button"))[1]));
            assertEquals(JXCursor.ARROW, ui.root.cursorAt(center(ui.find("input"))[0], center(ui.find("input"))[1]),
                    "an explicit cursor replaces the built-in one");
            assertEquals(JXCursor.CROSSHAIR, ui.root.cursorAt(center(ui.find("#text", 0))[0], center(ui.find("#text", 0))[1]),
                    "a label takes the cursor of its row");
            assertEquals(JXCursor.RESIZE_V, ui.root.cursorAt(center(ui.find("#text", 1))[0], center(ui.find("#text", 1))[1]));
        }

        @Test
        void aSplitPaneDividerShowsTheResizeCursorOnlyWhenItCanBeDragged() {
            Ui ui = new Ui("<SplitPane dividerPositions=\"@{split}\" prefHeight=\"100\"><Label text=\"A\"/><Label text=\"B\"/></SplitPane>\n"
                    + "<SplitPane dividerPositions=\"0.5\" prefHeight=\"100\"><Label text=\"A\"/><Label text=\"B\"/></SplitPane>\n"
                    + "<SplitPane orientation=\"vertical\" dividerPositions=\"@{split}\" prefHeight=\"100\"><Label text=\"A\"/><Label text=\"B\"/></SplitPane>");
            float[] d0 = JXControlLayout.splitRects(ui.find("splitpane", 0))[2];
            float[] d1 = JXControlLayout.splitRects(ui.find("splitpane", 1))[2];
            float[] d2 = JXControlLayout.splitRects(ui.find("splitpane", 2))[2];
            assertEquals(JXCursor.RESIZE_H, ui.root.cursorAt((int) (d0[0] + d0[2] / 2), (int) (d0[1] + d0[3] / 2)));
            assertEquals(JXCursor.ARROW, ui.root.cursorAt((int) (d1[0] + d1[2] / 2), (int) (d1[1] + d1[3] / 2)),
                    "no onChange: the divider does not move");
            assertEquals(JXCursor.RESIZE_V, ui.root.cursorAt((int) (d2[0] + d2[2] / 2), (int) (d2[1] + d2[3] / 2)));
            assertEquals(JXCursor.ARROW, ui.root.cursorAt((int) d0[0] - 40, (int) (d0[1] + d0[3] / 2)), "away from the divider");
        }

        @Test
        void aTextFieldInsideAnOpenMenuLayerIsNotUnderThePointerThroughIt() {
            Ui ui = new Ui("<MenuButton text=\"Actions\"><MenuItem text=\"Open\" onAction=\"#click\"/></MenuButton>\n"
                    + "<TextField text=\"@{name}\" prefWidth=\"300\"/>");
            int[] field = center(ui.find("input"));
            assertEquals(JXCursor.IBEAM, ui.root.cursorAt(field[0], field[1]));
            ui.click(ui.find("menubutton"));
            int[] menu = ui.find("menubutton").getPopupBounds();
            assertNotNull(menu, "the menu is open");
            assertEquals(JXCursor.ARROW, ui.root.cursorAt(menu[0] + menu[2] / 2, menu[1] + menu[3] / 2), "over a menu row: the arrow");
        }

        @Test
        void theNamesOfTheCursorsParse() {
            assertEquals(JXCursor.HAND, JXCursor.parse("HAND"));
            assertEquals(JXCursor.HAND, JXCursor.parse("pointer"));
            assertEquals(JXCursor.IBEAM, JXCursor.parse("Text"));
            assertEquals(JXCursor.RESIZE_H, JXCursor.parse("H_RESIZE"));
            assertEquals(JXCursor.RESIZE_H, JXCursor.parse("ew-resize"));
            assertEquals(JXCursor.RESIZE_V, JXCursor.parse("ns-resize"));
            assertEquals(JXCursor.ARROW, JXCursor.parse("default"));
            assertNull(JXCursor.parse("sparkle"));
            assertNull(JXCursor.parse(null));
        }
    }

    @Nested
    class AnimationBehaviour {
        private static final long MS = 1_000_000L;
        private static final long T0 = 7_000 * MS;

        @Test
        void aClickDuringATransitionHitsWhereTheNodeIsDrawn() {
            Ui ui = new Ui("<Button text=\"Go\" prefWidth=\"40\" translateX=\"${volume}\" transition=\"translateX 100ms linear\""
                    + " onAction=\"#click\"/>");
            ui.c.setVolume(0);
            ui.refresh();
            ui.root.tickAnimations(T0);
            ui.root.tickAnimations(T0 + 500 * MS); // settled at 0
            ui.c.setVolume(100);
            ui.refresh(); // the application renders again: the button is kept and slides
            JXNativeNode button = ui.find("button");
            ui.root.tickAnimations(T0 + 600 * MS);
            ui.root.tickAnimations(T0 + 650 * MS); // half way: drawn 50 to the right of where it was laid out
            int[] c = center(button);
            ui.click(c[0], c[1]);
            assertEquals(0, ui.c.clicks, "nothing is where the button was laid out any more");
            ui.click(c[0] + 50, c[1]);
            assertEquals(1, ui.c.clicks, "it is where it is drawn");
            ui.root.tickAnimations(T0 + 700 * MS);
            ui.click(c[0] + 100, c[1]);
            assertEquals(2, ui.c.clicks, "and where it came to rest");
            ui.click(c[0], c[1]);
            assertEquals(2, ui.c.clicks);
        }

        @Test
        void aClickDoesNotRestartTheAnimationOfTheRenderThatFollowsIt() {
            Ui ui = new Ui("<Button text=\"Go\" prefWidth=\"40\" translateX=\"${volume}\" transition=\"translateX 100ms linear\""
                    + " onAction=\"#click\"/>");
            ui.c.setVolume(0);
            ui.refresh();
            ui.root.tickAnimations(T0);
            ui.root.tickAnimations(T0 + 500 * MS);
            ui.c.setVolume(100);
            ui.refresh();
            JXNativeNode button = ui.find("button");
            ui.root.tickAnimations(T0 + 600 * MS);
            ui.root.tickAnimations(T0 + 650 * MS);
            int[] c = center(button);
            ui.click(c[0] + 50, c[1]); // the application renders again after the click
            ui.root.tickAnimations(T0 + 680 * MS);
            assertEquals(80.0, ((Number) button.getProperty("translateX")).doubleValue(), 1e-9, "on its own schedule");
        }
    }
}
