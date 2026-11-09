package com.jxparallel.ui.native2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            String xml = "<Stack controller=\"" + CTRL + "\">\n<Column padding=\"10\" gap=\"8\">\n" + body + "\n</Column>\n</Stack>";
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
            pointerX = x;
            pointerY = y;
            JXNativeNode hit = root.pressPointer(new JXPointerEvent(x, y, button));
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
        }

        JXNativeNode click(int x, int y) {
            JXNativeNode hit = press(x, y);
            release();
            return hit;
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
                root.routeKey(new JXKeyEvent(code, '\0'), focused);
            }
            refresh();
        }

        /** Typing: GLFW sends the key, then the character. */
        void type(String text) {
            for (char ch : text.toCharArray()) {
                root.routeKey(new JXKeyEvent(Character.toUpperCase(ch), '\0'), focused);
                refresh();
                if (focused != null) {
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
}
