package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.geometry.Orientation;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.layout.*;
import com.jxparallel.ui.native2d.JXControlLayout;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXTextHit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The control behaviors the interaction tests leave out: keys on every kind of control, keys of
 * open popups, the editing keys of fields and areas, scroll bars, slider orientations and ticks,
 * table sorting details and menu items.
 */
class NativeBehaviorTest extends NativeTestSupport {

    private static void focus(Control c) throws Exception {
        fx(() -> c.requestFocus());
        settle();
    }

    private static List<String> items(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add("item " + i);
        }
        return out;
    }

    // ---- keys on buttons and toggles ---------------------------------------------------------

    @Test
    void enterAndSpaceFireButtonsAndToggleToggles() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        Object[] c = new Object[6];
        NativeScene s = show(fx(() -> {
            Button b = new Button("b");
            b.setOnAction(e -> fired.incrementAndGet());
            Hyperlink h = new Hyperlink("h");
            h.setOnAction(e -> fired.incrementAndGet());
            CheckBox check = new CheckBox("c");
            ToggleButton toggle = new ToggleButton("t");
            RadioButton radio = new RadioButton("r");
            TitledPane titled = new TitledPane("T", new Label("x"));
            c[0] = b;
            c[1] = h;
            c[2] = check;
            c[3] = toggle;
            c[4] = radio;
            c[5] = titled;
            return new VBox(b, h, check, toggle, radio, titled);
        }), 300, 300);
        Button b = (Button) c[0];
        Hyperlink h = (Hyperlink) c[1];

        focus(b);
        key(s, "ENTER");
        key(s, "SPACE");
        assertEquals(2, fired.get());
        focus(h);
        key(s, "ENTER");
        assertEquals(3, fired.get());
        assertTrue(fx(() -> h.isVisited()));
        key(s, "SPACE");
        assertEquals(4, fired.get());
        for (int i = 2; i <= 4; i++) {
            focus((Control) c[i]);
            key(s, "SPACE");
        }
        assertTrue(fx(() -> ((CheckBox) c[2]).isSelected()));
        assertTrue(fx(() -> ((ToggleButton) c[3]).isSelected()));
        assertTrue(fx(() -> ((RadioButton) c[4]).isSelected()));
        focus((Control) c[5]);
        key(s, "SPACE");
        assertFalse(fx(() -> ((TitledPane) c[5]).isExpanded()));
        key(s, "F4");
        assertFalse(fx(() -> ((TitledPane) c[5]).isExpanded()), "F4 opens only popups");
        close(s);
    }

    @Test
    void arrowsMoveTheFocusBetweenButtons() throws Exception {
        Button[] b = new Button[3];
        NativeScene s = show(fx(() -> {
            for (int i = 0; i < 3; i++) {
                b[i] = new Button("b" + i);
            }
            return new VBox(b[0], b[1], b[2]);
        }), 300, 200);
        focus(b[1]);
        key(s, "DOWN");
        assertTrue(fx(() -> b[2].isFocused()));
        key(s, "UP");
        key(s, "LEFT");
        assertTrue(fx(() -> b[0].isFocused()));
        key(s, "RIGHT");
        assertTrue(fx(() -> b[1].isFocused()));
        key(s, "TAB", JXInputEvent.SHIFT);
        assertTrue(fx(() -> b[0].isFocused()));
        close(s);
    }

    // ---- popups ------------------------------------------------------------------------------

    @Test
    void openComboListFollowsTheArrowsAndScrollsToTheHoveredRow() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        AtomicInteger cancels = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            combo[0] = new ComboBox<>(FXCollections.observableArrayList(items(30)));
            combo[0].setVisibleRowCount(5);
            Button cancel = new Button("cancel");
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> cancels.incrementAndGet());
            return new VBox(combo[0], cancel);
        }), 300, 400);
        NativeModel m = model(combo[0]);
        focus(combo[0]);

        key(s, "F4");
        assertTrue(fx(() -> combo[0].isShowing()));
        for (int i = 0; i < 7; i++) {
            key(s, "DOWN");
        }
        assertEquals(6, fx(() -> m.state.get("popupHover")));
        assertEquals(7 * 23.0 - 5 * 23.0, fx(() -> m.state.get("popupScrollY")), "the hovered row is the last visible one");
        for (int i = 0; i < 6; i++) {
            key(s, "UP");
        }
        assertEquals(0, fx(() -> m.state.get("popupHover")));
        assertEquals(0.0, fx(() -> m.state.get("popupScrollY")));
        key(s, "UP");
        assertEquals(0, fx(() -> m.state.get("popupHover")), "stays on the first row");
        key(s, "DOWN");
        key(s, "ENTER");
        assertEquals("item 1", fx(() -> combo[0].getValue()));
        assertFalse(fx(() -> combo[0].isShowing()));

        key(s, "DOWN", JXInputEvent.ALT);
        assertTrue(fx(() -> combo[0].isShowing()), "Alt+Down opens");
        key(s, "ESCAPE");
        assertFalse(fx(() -> combo[0].isShowing()));
        assertEquals(0, cancels.get(), "Escape closed the popup, it did not cancel");
        key(s, "ESCAPE");
        assertEquals(1, cancels.get());

        key(s, "SPACE");
        assertTrue(fx(() -> combo[0].isShowing()));
        key(s, "SPACE");
        assertFalse(fx(() -> combo[0].isShowing()), "Space without a hovered row just closes");
        assertEquals("item 1", fx(() -> combo[0].getValue()));
        key(s, "F4");
        key(s, "TAB");
        assertFalse(fx(() -> combo[0].isShowing()));
        assertFalse(fx(() -> combo[0].isFocused()), "Tab closes and moves on");
        close(s);
    }

    @Test
    void closedComboKeysJumpToTheEnds() throws Exception {
        ChoiceBox<String>[] choice = new ChoiceBox[1];
        NativeScene s = show(fx(() -> {
            choice[0] = new ChoiceBox<>(FXCollections.observableArrayList("Ana", "Bia", "Caio", "Davi"));
            return new VBox(choice[0]);
        }), 300, 300);
        focus(choice[0]);
        key(s, "END");
        assertEquals("Davi", fx(() -> choice[0].getValue()));
        key(s, "DOWN");
        assertEquals("Davi", fx(() -> choice[0].getValue()), "past the end nothing changes");
        key(s, "HOME");
        assertEquals("Ana", fx(() -> choice[0].getValue()));
        key(s, "UP");
        key(s, "RIGHT");
        assertEquals("Ana", fx(() -> choice[0].getValue()));
        type(s, "b");
        assertEquals("Bia", fx(() -> choice[0].getValue()));
        type(s, "\u0007");
        type(s, "z");
        assertEquals("Bia", fx(() -> choice[0].getValue()), "control characters and misses are ignored");
        close(s);
    }

    @Test
    void datePickerF4OpensAndEscapeClosesTheCalendar() throws Exception {
        DatePicker[] picker = new DatePicker[1];
        NativeScene s = show(fx(() -> {
            picker[0] = new DatePicker();
            return new VBox(picker[0]);
        }), 400, 400);
        focus(picker[0]);
        key(s, "F4");
        assertTrue(fx(() -> picker[0].isShowing()));
        key(s, "DOWN");
        assertTrue(fx(() -> picker[0].isShowing()), "arrows do not close the calendar");
        key(s, "ESCAPE");
        assertFalse(fx(() -> picker[0].isShowing()));
        JXNativeNode n = node(s, picker[0]);
        click(s, n.getX() + 10, n.getY() + 10);
        assertFalse(fx(() -> picker[0].isShowing()), "a click on the text edits, it does not open");
        click(s, n.getX() + n.getWidth() - 5, n.getY() + 10);
        assertTrue(fx(() -> picker[0].isShowing()));
        click(s, n.getX() + n.getWidth() - 5, n.getY() + 10);
        assertFalse(fx(() -> picker[0].isShowing()), "the button toggles");
        close(s);
    }

    @Test
    void editableComboTakesTypedTextAndPickedItems() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        AtomicInteger actions = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            combo[0] = new ComboBox<>(FXCollections.observableArrayList("Fortaleza", "Recife"));
            combo[0].setEditable(true);
            combo[0].setOnAction(e -> actions.incrementAndGet());
            return new VBox(combo[0]);
        }), 300, 300);
        JXNativeNode n = node(s, combo[0]);
        click(s, n.getX() + 10, n.getY() + 10);
        assertFalse(fx(() -> combo[0].isShowing()), "the text part edits");
        type(s, "Natal");
        key(s, "ENTER");
        assertEquals("Natal", fx(() -> combo[0].getValue()));
        assertEquals(1, actions.get());
        click(s, n.getX() + n.getWidth() - 5, n.getY() + 10);
        assertTrue(fx(() -> combo[0].isShowing()));
        click(s, n.getX() + 20, n.getY() + n.getHeight() + 1 + 23 + 10);
        assertEquals("Recife", fx(() -> combo[0].getValue()));
        assertEquals("Recife", fx(() -> combo[0].getEditor().getText()), "the editor shows the picked item");
        assertEquals(6, (int) fx(() -> combo[0].getEditor().getCaretPosition()));
        assertEquals(2, actions.get());
        close(s);
    }

    // ---- text editing keys -------------------------------------------------------------------

    @Test
    void wordMovesSelectionCollapseAndWordDeletes() throws Exception {
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField("hello big world");
            return new VBox(f[0]);
        }), 300, 100);
        focus(f[0]);
        fx(() -> f[0].positionCaret(15));
        key(s, "LEFT", JXInputEvent.CONTROL);
        assertEquals(NativeText.previousWord("hello big world", 15), (int) fx(() -> f[0].getCaretPosition()));
        key(s, "LEFT", JXInputEvent.CONTROL);
        int word = fx(() -> f[0].getCaretPosition());
        key(s, "RIGHT", JXInputEvent.CONTROL);
        assertEquals(NativeText.nextWord("hello big world", word), (int) fx(() -> f[0].getCaretPosition()));

        key(s, "HOME");
        key(s, "RIGHT", JXInputEvent.SHIFT);
        key(s, "RIGHT", JXInputEvent.SHIFT);
        assertEquals("he", fx(() -> f[0].getSelectedText()));
        key(s, "RIGHT");
        assertEquals(2, (int) fx(() -> f[0].getCaretPosition()), "Right collapses to the end of the selection");
        assertEquals("", fx(() -> f[0].getSelectedText()));
        key(s, "RIGHT", JXInputEvent.SHIFT);
        key(s, "LEFT");
        assertEquals(2, (int) fx(() -> f[0].getCaretPosition()), "Left collapses to the start");
        key(s, "RIGHT");
        assertEquals(3, (int) fx(() -> f[0].getCaretPosition()));
        key(s, "END", JXInputEvent.SHIFT);
        assertEquals("lo big world", fx(() -> f[0].getSelectedText()));
        key(s, "HOME", JXInputEvent.SHIFT);
        assertEquals("hel", fx(() -> f[0].getSelectedText()));

        key(s, "HOME");
        key(s, "DELETE", JXInputEvent.CONTROL);
        String left = fx(() -> f[0].getText());
        assertTrue(left.length() < "hello big world".length() - 1, "Ctrl+Delete removes a word: " + left);
        assertFalse(left.startsWith("h"));
        key(s, "A");
        assertEquals(left, fx(() -> f[0].getText()), "a key press alone types nothing");
        close(s);
    }

    @Test
    void clipboardKeys() throws Exception {
        assertTrue(NativeText.clipboard != NativeText.SYSTEM, "headless tests never touch the user's clipboard");
        TextField[] f = new TextField[1];
        PasswordField[] p = new PasswordField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField("abc");
            p[0] = new PasswordField();
            p[0].setText("secret");
            return new VBox(f[0], p[0]);
        }), 300, 100);
        focus(f[0]);
        key(s, "A", JXInputEvent.CONTROL);
        key(s, "C", JXInputEvent.CONTROL);
        assertEquals("abc", NativeText.clipboard.get());
        key(s, "END");
        key(s, "V", JXInputEvent.CONTROL);
        assertEquals("abcabc", fx(() -> f[0].getText()));
        key(s, "LEFT", JXInputEvent.SHIFT);
        key(s, "X", JXInputEvent.CONTROL);
        assertEquals("abcab", fx(() -> f[0].getText()));
        assertEquals("c", NativeText.clipboard.get());
        key(s, "HOME");
        key(s, "INSERT", JXInputEvent.SHIFT);
        assertEquals("cabcab", fx(() -> f[0].getText()));
        key(s, "RIGHT", JXInputEvent.SHIFT);
        key(s, "INSERT", JXInputEvent.CONTROL);
        assertEquals("a", NativeText.clipboard.get());
        key(s, "INSERT");
        key(s, "C");
        key(s, "X");
        key(s, "V");
        assertEquals("cabcab", fx(() -> f[0].getText()), "without modifiers these keys do nothing");
        key(s, "C", JXInputEvent.CONTROL);
        key(s, "END");
        key(s, "C", JXInputEvent.CONTROL);
        assertEquals("a", NativeText.clipboard.get(), "copying nothing keeps the clipboard");
        focus(p[0]);
        key(s, "A", JXInputEvent.CONTROL);
        key(s, "C", JXInputEvent.CONTROL);
        key(s, "X", JXInputEvent.CONTROL);
        assertEquals("a", NativeText.clipboard.get(), "a password is never copied");
        assertEquals("secret", fx(() -> p[0].getText()), "nor cut");
        close(s);
    }

    @Test
    void textAreaLineKeysTabAndControlEnter() throws Exception {
        TextArea[] a = new TextArea[1];
        Button[] other = new Button[1];
        AtomicInteger defaults = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            a[0] = new TextArea("ab\ncd\nef");
            other[0] = new Button("ok");
            other[0].setDefaultButton(true);
            other[0].setOnAction(e -> defaults.incrementAndGet());
            return new VBox(a[0], other[0]);
        }), 300, 300);
        focus(a[0]);
        fx(() -> a[0].positionCaret(4));
        key(s, "HOME");
        assertEquals(3, (int) fx(() -> a[0].getCaretPosition()), "start of the line");
        key(s, "END");
        assertEquals(5, (int) fx(() -> a[0].getCaretPosition()), "end of the line, before its break");
        key(s, "HOME", JXInputEvent.CONTROL);
        assertEquals(0, (int) fx(() -> a[0].getCaretPosition()));
        key(s, "DOWN");
        assertEquals(3, (int) fx(() -> a[0].getCaretPosition()));
        key(s, "END", JXInputEvent.CONTROL);
        assertEquals(8, (int) fx(() -> a[0].getCaretPosition()));
        key(s, "END");
        assertEquals(8, (int) fx(() -> a[0].getCaretPosition()), "the last line ends at the text's end");
        key(s, "TAB");
        assertEquals("ab\ncd\nef\t", fx(() -> a[0].getText()));
        key(s, "ENTER", JXInputEvent.CONTROL);
        assertEquals(1, defaults.get(), "Ctrl+Enter is not a new line: it reaches the default button");
        assertEquals("ab\ncd\nef\t", fx(() -> a[0].getText()));
        key(s, "TAB", JXInputEvent.CONTROL);
        assertTrue(fx(() -> other[0].isFocused()), "Ctrl+Tab leaves the area");
        close(s);
    }

    @Test
    void spinnerEnterCommitsAndReachesTheDefaultButton() throws Exception {
        Spinner<Integer>[] spinner = new Spinner[1];
        AtomicInteger defaults = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            spinner[0] = new Spinner<>(0, 10, 5);
            spinner[0].setEditable(true);
            Button ok = new Button("ok");
            ok.setDefaultButton(true);
            ok.setOnAction(e -> defaults.incrementAndGet());
            return new VBox(spinner[0], ok);
        }), 300, 200);
        focus(spinner[0]);
        key(s, "A", JXInputEvent.CONTROL);
        type(s, "7");
        key(s, "ENTER");
        assertEquals(7, (int) fx(() -> spinner[0].getValue()));
        assertEquals(1, defaults.get());
        key(s, "DOWN");
        assertEquals(6, (int) fx(() -> spinner[0].getValue()));
        key(s, "LEFT");
        assertEquals(6, (int) fx(() -> spinner[0].getValue()), "left moves the caret, not the value");
        JXNativeNode n = node(s, spinner[0]);
        click(s, n.getX() + 8, n.getY() + 10);
        assertEquals(0, (int) fx(() -> spinner[0].getEditor().getCaretPosition()), "a click in the text places the caret");
        close(s);
    }

    @Test
    void tripleClickSelectsAllAndDraggingSelects() throws Exception {
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField("alpha beta");
            return new VBox(f[0]);
        }), 300, 100);
        JXNativeNode n = node(s, f[0]);
        click(s, n.getX() + 12, n.getY() + 10, 3, 0);
        assertEquals("alpha beta", fx(() -> f[0].getSelectedText()));
        press(s, n.getX() + 7, n.getY() + 10, 1, 0);
        move(s, n.getX() + n.getWidth() - 3, n.getY() + 10, true);
        release(s, n.getX() + n.getWidth() - 3, n.getY() + 10, 1, 0);
        assertEquals("alpha beta", fx(() -> f[0].getSelectedText()));
        press(s, n.getX() + n.getWidth() - 3, n.getY() + 10, 1, 0);
        release(s, n.getX() + n.getWidth() - 3, n.getY() + 10, 1, 0);
        click(s, n.getX() + 7, n.getY() + 10, 1, JXInputEvent.SHIFT);
        assertEquals("alpha beta", fx(() -> f[0].getSelectedText()), "Shift+click extends from the caret");
        close(s);
    }

    // ---- arrows on sliders, tabs and lists ---------------------------------------------------

    @Test
    void sliderKeysFollowTheOrientation() throws Exception {
        Slider[] h = new Slider[1];
        Slider[] v = new Slider[1];
        NativeScene s = show(fx(() -> {
            h[0] = new Slider(0, 100, 50);
            h[0].setBlockIncrement(10);
            v[0] = new Slider(0, 100, 50);
            v[0].setOrientation(Orientation.VERTICAL);
            v[0].setBlockIncrement(5);
            return new HBox(h[0], v[0]);
        }), 400, 300);
        focus(h[0]);
        key(s, "RIGHT");
        assertEquals(60.0, fx(() -> h[0].getValue()), 1e-9);
        key(s, "LEFT");
        key(s, "LEFT");
        assertEquals(40.0, fx(() -> h[0].getValue()), 1e-9);
        key(s, "HOME");
        assertEquals(0.0, fx(() -> h[0].getValue()), 1e-9);
        key(s, "LEFT");
        assertEquals(0.0, fx(() -> h[0].getValue()), 1e-9, "clamped to the minimum");
        key(s, "END");
        assertEquals(100.0, fx(() -> h[0].getValue()), 1e-9);
        focus(v[0]);
        key(s, "UP");
        assertEquals(55.0, fx(() -> v[0].getValue()), 1e-9, "up increments a vertical slider");
        key(s, "DOWN");
        key(s, "DOWN");
        assertEquals(45.0, fx(() -> v[0].getValue()), 1e-9);
        key(s, "RIGHT");
        assertEquals(45.0, fx(() -> v[0].getValue()), 1e-9, "the other axis does nothing");
        close(s);
    }

    @Test
    void verticalSliderClicksAndSnapsToTicks() throws Exception {
        Slider[] v = new Slider[1];
        Slider[] snap = new Slider[1];
        NativeScene s = show(fx(() -> {
            v[0] = new Slider(0, 100, 0);
            v[0].setOrientation(Orientation.VERTICAL);
            v[0].setPrefHeight(214);
            v[0].setMaxHeight(214);
            snap[0] = new Slider(0, 100, 0);
            snap[0].setPrefWidth(214);
            snap[0].setSnapToTicks(true);
            snap[0].setMajorTickUnit(25);
            snap[0].setMinorTickCount(0);
            return new HBox(v[0], snap[0]);
        }), 500, 300);
        JXNativeNode n = node(s, v[0]);
        click(s, n.getX() + 5, n.getY() + 7 + 50);
        assertEquals(75.0, fx(() -> v[0].getValue()), 0.5, "top is the maximum");
        press(s, n.getX() + 5, n.getY() + 7 + 50, 1, 0);
        move(s, n.getX() + 5, n.getY() + 7 + 150, true);
        release(s, n.getX() + 5, n.getY() + 7 + 150, 1, 0);
        assertEquals(25.0, fx(() -> v[0].getValue()), 0.5, "dragging follows the pointer");
        click(s, n.getX() + 5, n.getY() + n.getHeight() - 1);
        assertEquals(0.0, fx(() -> v[0].getValue()), 1e-9, "past the end of the track is the minimum");
        JXNativeNode t = node(s, snap[0]);
        click(s, t.getX() + 7 + 200 * 0.3, t.getY() + 5);
        assertEquals(25.0, fx(() -> snap[0].getValue()), 1e-9, "30% snaps to the 25 tick");
        click(s, t.getX() + 7 + 200 * 0.4, t.getY() + 5);
        assertEquals(50.0, fx(() -> snap[0].getValue()), 1e-9);
        close(s);
    }

    @Test
    void tabPaneArrowsWrapAround() throws Exception {
        TabPane[] tabs = new TabPane[1];
        NativeScene s = show(fx(() -> {
            tabs[0] = new TabPane(new Tab("a", new Label("a")), new Tab("b", new Label("b")), new Tab("c", new Label("c")));
            return new VBox(tabs[0]);
        }), 300, 200);
        focus(tabs[0]);
        key(s, "RIGHT");
        assertEquals(1, (int) fx(() -> tabs[0].getSelectionModel().getSelectedIndex()));
        key(s, "LEFT");
        key(s, "LEFT");
        assertEquals(2, (int) fx(() -> tabs[0].getSelectionModel().getSelectedIndex()), "from the first to the last");
        key(s, "RIGHT");
        assertEquals(0, (int) fx(() -> tabs[0].getSelectionModel().getSelectedIndex()));
        key(s, "DOWN");
        assertEquals(0, (int) fx(() -> tabs[0].getSelectionModel().getSelectedIndex()));
        close(s);
    }

    @Test
    void listKeysPageAndKeepTheSelectionVisible() throws Exception {
        ListView<String>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>(FXCollections.observableArrayList(items(40)));
            list[0].setPrefHeight(117);
            list[0].getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            return new VBox(list[0]);
        }), 300, 300);
        NativeModel m = model(list[0]);
        focus(list[0]);
        key(s, "DOWN");
        assertEquals(0, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        int page = fx(() -> Math.max(1, (int) NativeCells.number(m.state.get("visible"), 10) - 2));
        key(s, "PAGE_DOWN");
        assertEquals(page, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        key(s, "PAGE_UP");
        assertEquals(0, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        key(s, "PAGE_UP");
        assertEquals(0, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        key(s, "END");
        assertEquals(39, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        double view = fx(() -> NativeCells.number(m.state.get("viewHeight"), 0));
        assertEquals(40 * 23 - view, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001,
                "the last row sits at the bottom of the viewport");
        key(s, "PAGE_DOWN");
        assertEquals(39, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        key(s, "DOWN");
        assertEquals(39, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        key(s, "UP", JXInputEvent.SHIFT);
        key(s, "UP", JXInputEvent.SHIFT);
        assertEquals(3, (int) fx(() -> list[0].getSelectionModel().getSelectedIndices().size()), "Shift extends from the anchor");
        key(s, "HOME");
        assertEquals(0.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001);
        key(s, "RIGHT");
        assertEquals(0, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()), "sideways keys do nothing");
        close(s);
    }

    @Test
    void keysOnAnEmptyListDoNothing() throws Exception {
        ListView<String>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>();
            return new VBox(list[0]);
        }), 300, 300);
        focus(list[0]);
        key(s, "DOWN");
        assertEquals(-1, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()));
        close(s);
    }

    // ---- scroll bars -------------------------------------------------------------------------

    @Test
    void listScrollBarArrowsTrackAndThumb() throws Exception {
        ListView<String>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>(FXCollections.observableArrayList(items(100)));
            list[0].setPrefHeight(117);
            return new VBox(list[0]);
        }), 300, 300);
        NativeModel m = model(list[0]);
        JXControlLayout.ScrollGeometry g = fx(() -> JXControlLayout.scrollGeometry(node(s, list[0])));
        click(s, g.vx + 5, g.vy + g.vh - 5);
        assertEquals(23.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001, "the down arrow steps one row");
        click(s, g.vx + 5, g.vy + 5);
        assertEquals(0.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001);
        click(s, g.vx + 5, g.vy + 5);
        assertEquals(0.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001, "not above the top");
        click(s, g.vx + 5, g.vy + g.vh - 20);
        assertEquals(g.viewH, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001, "the track pages by the view");
        JXControlLayout.ScrollGeometry paged = fx(() -> JXControlLayout.scrollGeometry(node(s, list[0])));
        click(s, g.vx + 5, paged.vThumbY - 2);
        assertEquals(0.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001, "above the thumb pages up");
        press(s, g.vx + 5, g.vThumbY + 2, 1, 0);
        move(s, g.vx + 5, g.vThumbY + 2 + 5000, true);
        release(s, g.vx + 5, g.vThumbY + 2 + 5000, 1, 0);
        assertEquals(g.maxScrollY(), fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001);
        assertEquals(-1, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()), "bar clicks select nothing");
        close(s);
    }

    @Test
    void horizontalScrollBarAndShiftWheel() throws Exception {
        ScrollPane[] scroll = new ScrollPane[1];
        NativeScene s = show(fx(() -> {
            Pane content = new Pane();
            content.setPrefSize(1000, 100);
            scroll[0] = new ScrollPane(content);
            scroll[0].setPrefSize(200, 200);
            return new VBox(scroll[0]);
        }), 300, 300);
        JXControlLayout.ScrollGeometry g = fx(() -> JXControlLayout.scrollGeometry(node(s, scroll[0])));
        double max = g.maxScrollX();
        click(s, g.hx + g.hw - 5, g.hy + 5);
        assertEquals(20 / max, fx(() -> scroll[0].getHvalue()), 1e-6, "the right arrow steps 20 px");
        click(s, g.hx + 5, g.hy + 5);
        assertEquals(0.0, fx(() -> scroll[0].getHvalue()), 1e-6);
        click(s, g.hx + g.hw - 20, g.hy + 5);
        assertEquals(g.viewW / max, fx(() -> scroll[0].getHvalue()), 1e-6, "the track pages");
        JXControlLayout.ScrollGeometry paged = fx(() -> JXControlLayout.scrollGeometry(node(s, scroll[0])));
        click(s, paged.hThumbX - 2, g.hy + 5);
        assertEquals(0.0, fx(() -> scroll[0].getHvalue()), 1e-6);
        press(s, g.hThumbX + 2, g.hy + 5, 1, 0);
        move(s, g.hThumbX + 2 + 5000, g.hy + 5, true);
        release(s, g.hThumbX + 2 + 5000, g.hy + 5, 1, 0);
        assertEquals(1.0, fx(() -> scroll[0].getHvalue()), 1e-6);
        fx(() -> scroll[0].setHvalue(0));
        fx(() -> NativeEvents.handle(s, JXInputEvent.scroll(g.viewX + 50, g.viewY + 50, 0, -1, JXInputEvent.SHIFT,
                java.util.Collections.<JXNativeNode>emptyList())));
        settle();
        assertEquals(40 / max, fx(() -> scroll[0].getHvalue()), 1e-6, "Shift turns the wheel sideways");
        fx(() -> scroll[0].setHmin(10));
        fx(() -> scroll[0].setHmax(20));
        click(s, g.hx + 5, g.hy + 5);
        assertEquals(10.0, fx(() -> scroll[0].getHvalue()), 1e-6,
                "the old value is below the new range: the view is at its start, and so is the value");
        click(s, g.hx + g.hw - 5, g.hy + 5);
        assertEquals(10 + 20 / max * 10, fx(() -> scroll[0].getHvalue()), 1e-6, "values use hmin and hmax");
        close(s);
    }

    @Test
    void wheelScrollsATextAreaByThreeLines() throws Exception {
        TextArea[] a = new TextArea[1];
        NativeScene s = show(fx(() -> {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                text.append("line ").append(i).append('\n');
            }
            a[0] = new TextArea(text.toString());
            a[0].setPrefHeight(100);
            return new VBox(a[0]);
        }), 300, 300);
        JXNativeNode n = node(s, a[0]);
        double line = fx(() -> JXTextHit.lineHeight(n));
        wheel(s, n.getX() + 20, n.getY() + 20, -1);
        assertEquals(3 * line, fx(() -> NativeElements.number(model(a[0]), "scrollTop", 0)), 0.001);
        wheel(s, n.getX() + 20, n.getY() + 20, 5);
        assertEquals(0.0, fx(() -> NativeElements.number(model(a[0]), "scrollTop", 0)), 0.001, "not above the top");
        wheel(s, n.getX() + 20, n.getY() + 20, -1000);
        double bottom = fx(() -> NativeElements.number(model(a[0]), "scrollTop", 0));
        assertTrue(bottom > 0 && bottom < 41 * line, "stops at the last line: " + bottom);
        close(s);
    }

    // ---- composite controls ------------------------------------------------------------------

    @Test
    void paginationArrowsStopAtTheEnds() throws Exception {
        Pagination[] pages = new Pagination[1];
        NativeScene s = show(fx(() -> {
            pages[0] = new Pagination(3, 0);
            pages[0].setPageFactory(i -> new Label("p" + i));
            return new VBox(pages[0]);
        }), 400, 300);
        float[][] b = fx(() -> JXControlLayout.pageButtons(node(s, pages[0])));
        click(s, b[0][0] + 5, b[0][1] + 5);
        assertEquals(0, (int) fx(() -> pages[0].getCurrentPageIndex()), "no page before the first");
        click(s, b[3][0] + 5, b[3][1] + 5);
        assertEquals(2, (int) fx(() -> pages[0].getCurrentPageIndex()));
        click(s, b[4][0] + 5, b[4][1] + 5);
        assertEquals(2, (int) fx(() -> pages[0].getCurrentPageIndex()), "no page after the last");
        click(s, b[0][0] + 5, b[0][1] + 5);
        assertEquals(1, (int) fx(() -> pages[0].getCurrentPageIndex()));
        close(s);
    }

    @Test
    void aTabCanRefuseToCloseAndClosingSelectsItsNeighbour() throws Exception {
        TabPane[] tabs = new TabPane[1];
        NativeScene s = show(fx(() -> {
            Tab keep = new Tab("Fica", new Label("1"));
            keep.setOnCloseRequest(e -> e.consume());
            Tab a = new Tab("A", new Label("2"));
            Tab b = new Tab("B", new Label("3"));
            tabs[0] = new TabPane(keep, a, b);
            tabs[0].setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
            return new VBox(tabs[0]);
        }), 400, 300);
        JXNativeNode n = node(s, tabs[0]);
        float[] x = fx(() -> JXControlLayout.tabPositions(n));
        click(s, n.getX() + x[1] - 10, n.getY() + 15);
        assertEquals(3, (int) fx(() -> tabs[0].getTabs().size()), "the close request was consumed");
        click(s, n.getX() + x[3] - 10, n.getY() + 15);
        assertEquals(2, (int) fx(() -> tabs[0].getTabs().size()));
        assertEquals(1, (int) fx(() -> tabs[0].getSelectionModel().getSelectedIndex()), "the last tab closed: the new last is selected");
        close(s);
    }

    @Test
    void tableSortingUsesComparatorsAndSkipsUnsortableColumns() throws Exception {
        TableView<NativeControlsTest.Person>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = new TableView<>(FXCollections.observableArrayList(new NativeControlsTest.Person("bia", 2),
                    new NativeControlsTest.Person(null, 3), new NativeControlsTest.Person("Ana", 1)));
            TableColumn<NativeControlsTest.Person, String> name = new TableColumn<>("Nome");
            name.setCellValueFactory(new com.jxparallel.fx.scene.control.cell.PropertyValueFactory<>("name"));
            name.setPrefWidth(100);
            TableColumn<NativeControlsTest.Person, String> ci = new TableColumn<>("CI");
            ci.setCellValueFactory(new com.jxparallel.fx.scene.control.cell.PropertyValueFactory<>("name"));
            ci.setComparator(String.CASE_INSENSITIVE_ORDER);
            ci.setPrefWidth(100);
            TableColumn<NativeControlsTest.Person, String> fixed = new TableColumn<>("Fixa");
            fixed.setSortable(false);
            fixed.setPrefWidth(100);
            table[0].getColumns().add(name);
            table[0].getColumns().add(ci);
            table[0].getColumns().add(fixed);
            return new VBox(table[0]);
        }), 400, 300);
        JXNativeNode n = node(s, table[0]);
        click(s, n.getX() + 10, n.getY() + 10);
        assertEquals(null, fx(() -> table[0].getItems().get(0).getName()), "empty values first");
        assertEquals("Ana", fx(() -> table[0].getItems().get(1).getName()), "then natural order: upper case first");
        fx(() -> table[0].getItems().remove(0));
        settle();
        click(s, n.getX() + 1 + 100 + 10, n.getY() + 10);
        assertEquals("Ana", fx(() -> table[0].getItems().get(0).getName()));
        click(s, n.getX() + 1 + 100 + 10, n.getY() + 10);
        assertEquals("bia", fx(() -> table[0].getItems().get(0).getName()), "second click: descending");
        click(s, n.getX() + 1 + 200 + 10, n.getY() + 10);
        assertEquals("bia", fx(() -> table[0].getItems().get(0).getName()), "an unsortable column keeps the order");
        click(s, n.getX() + 1 + 350, n.getY() + 10);
        assertEquals("bia", fx(() -> table[0].getItems().get(0).getName()), "past the last column nothing happens");
        close(s);
    }

    @Test
    void checkAndRadioMenuItems() throws Exception {
        CheckMenuItem[] check = new CheckMenuItem[1];
        RadioMenuItem[] radio = new RadioMenuItem[2];
        AtomicInteger actions = new AtomicInteger();
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            check[0] = new CheckMenuItem("Negrito");
            check[0].setOnAction(e -> actions.incrementAndGet());
            ToggleGroup group = new ToggleGroup();
            radio[0] = new RadioMenuItem("Um");
            radio[1] = new RadioMenuItem("Dois");
            radio[0].setToggleGroup(group);
            radio[1].setToggleGroup(group);
            radio[0].setSelected(true);
            f[0] = new TextField();
            f[0].setContextMenu(new ContextMenu(check[0], radio[0], radio[1]));
            return new VBox(f[0]);
        }), 300, 300);
        JXNativeNode n = node(s, f[0]);
        rightClick(s, n.getX() + 10, n.getY() + 10);
        click(s, n.getX() + 20, n.getY() + 10 + 1 + 12);
        assertTrue(fx(() -> check[0].isSelected()));
        assertEquals(1, actions.get());
        rightClick(s, n.getX() + 10, n.getY() + 10);
        click(s, n.getX() + 20, n.getY() + 10 + 1 + 12 + 2 * 24);
        assertTrue(fx(() -> radio[1].isSelected()));
        assertFalse(fx(() -> radio[0].isSelected()), "one radio item per group");
        close(s);
    }

    @Test
    void secondaryButtonAndReleasesOutsideDoNothing() throws Exception {
        CheckBox[] c = new CheckBox[1];
        NativeScene s = show(fx(() -> {
            c[0] = new CheckBox("c");
            return new VBox(c[0]);
        }), 300, 100);
        JXNativeNode n = node(s, c[0]);
        rightClick(s, n.getX() + 5, n.getY() + 5);
        assertFalse(fx(() -> c[0].isSelected()));
        press(s, n.getX() + 5, n.getY() + 5, 1, 0);
        release(s, 290, 90, 1, 0);
        settle();
        assertFalse(fx(() -> c[0].isSelected()));
        assertNotEquals(null, n);
        close(s);
    }
}
