package com.jxparallel.fx.nativeimpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.geometry.HPos;
import com.jxparallel.fx.geometry.Orientation;
import com.jxparallel.fx.geometry.VPos;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.layout.*;
import com.jxparallel.fx.util.StringConverter;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Text formatters and commits, CSS pseudo-classes, grid constraints, flow and tile panes, static layout getters. */
class NativeTextAndLayoutTest extends NativeTestSupport {

    /** Integer text, like JavaFX's IntegerStringConverter (which the JX API does not mirror). */
    static final class IntegerStringConverter extends StringConverter<Integer> {
        @Override
        public String toString(Integer value) {
            return value == null ? "" : value.toString();
        }

        @Override
        public Integer fromString(String text) {
            return text == null || text.trim().isEmpty() ? null : Integer.valueOf(text.trim());
        }
    }

    // ---- text --------------------------------------------------------------------------------

    @Test
    void formatterFiltersSeeTheControlTextAndCaret() throws Exception {
        TextField[] f = new TextField[1];
        List<String> seen = new ArrayList<>();
        NativeScene s = show(fx(() -> {
            f[0] = new TextField("12");
            f[0].setTextFormatter(new TextFormatter<Object>(change -> {
                seen.add(change.getControlText() + "|" + change.getControlNewText() + "|" + change.getControlCaretPosition()
                        + "|" + change.getControlAnchor());
                return change.getControlNewText().matches("\\d*") ? change : null;
            }));
            return new VBox(f[0]);
        }), 300, 100);
        fx(() -> f[0].requestFocus());
        fx(() -> f[0].positionCaret(2));
        type(s, "3");
        type(s, "x");
        assertEquals("123", fx(() -> f[0].getText()), "only digits get through");
        assertEquals("12|123|2|2", seen.get(0));
        assertEquals("123|123x|3|3", seen.get(1));
        close(s);
    }

    @Test
    void formatterValuesCommitOnEnterAndKeepTheLastGoodOne() throws Exception {
        TextField[] f = new TextField[1];
        TextFormatter<Integer>[] formatter = new TextFormatter[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField();
            formatter[0] = new TextFormatter<>(new IntegerStringConverter(), 5);
            f[0].setTextFormatter(formatter[0]);
            return new VBox(f[0]);
        }), 300, 100);
        fx(() -> f[0].requestFocus());
        key(s, "A", JXInputEvent.CONTROL);
        type(s, "42");
        key(s, "ENTER");
        assertEquals(42, (int) fx(() -> formatter[0].getValue()));
        key(s, "A", JXInputEvent.CONTROL);
        type(s, "abc");
        key(s, "ENTER");
        assertEquals(42, (int) fx(() -> formatter[0].getValue()), "text that does not convert keeps the value");
        close(s);
    }

    @Test
    void typedTextCommitsIntoDatePickersComboBoxesAndSpinners() throws Exception {
        DatePicker[] picker = new DatePicker[2];
        ComboBox<Integer>[] combo = new ComboBox[1];
        Spinner<Integer>[] spinner = new Spinner[1];
        NativeScene s = show(fx(() -> {
            picker[0] = new DatePicker(LocalDate.of(2026, 1, 2));
            picker[1] = new DatePicker();
            picker[1].setConverter(new StringConverter<LocalDate>() {
                @Override
                public String toString(LocalDate d) {
                    return d == null ? "" : d.toString();
                }

                @Override
                public LocalDate fromString(String text) {
                    return text.isEmpty() ? null : LocalDate.parse(text);
                }
            });
            combo[0] = new ComboBox<>(FXCollections.observableArrayList(1, 2));
            combo[0].setEditable(true);
            combo[0].setConverter(new IntegerStringConverter());
            spinner[0] = new Spinner<>(0, 100, 10);
            spinner[0].setEditable(true);
            return new VBox(picker[0], picker[1], combo[0], spinner[0]);
        }), 400, 300);
        String typed = NativeText.defaultDateFormat().format(LocalDate.of(2026, 3, 4));
        fx(() -> picker[0].requestFocus());
        key(s, "A", JXInputEvent.CONTROL);
        type(s, typed);
        key(s, "ENTER");
        assertEquals(LocalDate.of(2026, 3, 4), fx(() -> picker[0].getValue()));
        key(s, "A", JXInputEvent.CONTROL);
        type(s, "não é data");
        key(s, "ENTER");
        assertEquals(LocalDate.of(2026, 3, 4), fx(() -> picker[0].getValue()), "an invalid date keeps the value");
        assertEquals(typed, fx(() -> picker[0].getEditor().getText()), "and the editor shows it again");
        key(s, "A", JXInputEvent.CONTROL);
        key(s, "DELETE");
        key(s, "ENTER");
        assertNull(fx(() -> picker[0].getValue()), "an empty editor clears the date");

        fx(() -> picker[1].requestFocus());
        type(s, "2026-12-25");
        key(s, "ENTER");
        assertEquals(LocalDate.of(2026, 12, 25), fx(() -> picker[1].getValue()), "the picker's converter parses");

        fx(() -> combo[0].requestFocus());
        type(s, "77");
        key(s, "ENTER");
        assertEquals(77, (int) fx(() -> combo[0].getValue()), "the combo box converter makes an Integer");

        fx(() -> spinner[0].requestFocus());
        key(s, "A", JXInputEvent.CONTROL);
        type(s, "x");
        key(s, "ENTER");
        assertEquals(10, (int) fx(() -> spinner[0].getValue()));
        assertEquals("10", fx(() -> spinner[0].getEditor().getText()), "invalid spinner text is reset");
        close(s);
    }

    @Test
    void wordBoundariesAndSelection() throws Exception {
        assertEquals(6, NativeText.nextWord("hello world", 0));
        assertEquals(11, NativeText.nextWord("hello world", 6));
        assertEquals(11, NativeText.nextWord("hello world", 11));
        assertEquals(6, NativeText.previousWord("hello world", 11));
        assertEquals(0, NativeText.previousWord("hello world", 6));
        assertEquals(0, NativeText.previousWord("hello world", 0));
        assertEquals(0, NativeText.previousWord("hello  world", 1));
        fx(() -> {
            TextField f = new TextField("alpha beta");
            NativeModel m = model(f);
            NativeText.selectWord(m, 7);
            assertEquals("beta", f.getSelectedText());
            NativeText.selectWord(m, 5);
            assertEquals("alpha", f.getSelectedText(), "at the end of a word, that word");
            NativeText.moveCaret(m, 99, false);
            assertEquals(10, f.getCaretPosition(), "clamped to the text");
            NativeText.moveCaret(m, -5, false);
            assertEquals(0, f.getCaretPosition());
            f.setEditable(false);
            NativeText.replaceSelection(m, "x");
            assertEquals("alpha beta", f.getText(), "a read-only field does not change");
            NativeText.deleteBackward(m, false);
            NativeText.deleteForward(m, false);
            assertEquals("alpha beta", f.getText());
            f.setEditable(true);
            NativeText.moveCaret(m, 0, false);
            NativeText.deleteBackward(m, false);
            assertEquals("alpha beta", f.getText(), "nothing before the start");
            NativeText.moveCaret(m, 10, false);
            NativeText.deleteForward(m, false);
            assertEquals("alpha beta", f.getText(), "nothing after the end");
            assertFalse(NativeText.isText(model(new Label("x"))));
            assertTrue(NativeText.isText(model(new PasswordField())));
            return null;
        });
    }

    // ---- CSS pseudo-classes ------------------------------------------------------------------

    private static boolean matches(NativeScene s, Object node, String selector) {
        return NativeCss.Selector.parse(selector).matches(model(node), new NativeCss(s));
    }

    @Test
    void pseudoClassesFollowTheControlState() throws Exception {
        Button[] b = new Button[2];
        CheckBox[] check = new CheckBox[1];
        TitledPane[] titled = new TitledPane[1];
        ComboBox<String>[] combo = new ComboBox[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("ok");
            b[0].setDefaultButton(true);
            b[1] = new Button("off");
            b[1].setDisable(true);
            check[0] = new CheckBox("c");
            check[0].setSelected(true);
            titled[0] = new TitledPane("t", new Label("x"));
            titled[0].setExpanded(false);
            combo[0] = new ComboBox<>(FXCollections.observableArrayList("a"));
            return new VBox(b[0], b[1], check[0], titled[0], combo[0]);
        }), 300, 300);
        JXNativeNode n = node(s, b[0]);
        fx(() -> {
            assertTrue(matches(s, b[0], ".button:default"));
            assertFalse(matches(s, b[1], ".button:default"));
            assertTrue(matches(s, b[1], ":disabled"));
            assertFalse(matches(s, b[0], ":disabled"));
            assertTrue(matches(s, check[0], ":selected"));
            assertTrue(matches(s, check[0], ":checked"));
            assertTrue(matches(s, titled[0], ":collapsed"));
            assertFalse(matches(s, titled[0], ":expanded"));
            assertFalse(matches(s, b[0], ":unknown-state"));
            assertFalse(matches(s, combo[0], ":showing"));
            return null;
        });
        move(s, n.getX() + 3, n.getY() + 3, false);
        assertTrue(fx(() -> matches(s, b[0], ":hover")));
        press(s, n.getX() + 3, n.getY() + 3, 1, 0);
        assertTrue(fx(() -> matches(s, b[0], ":pressed")));
        assertTrue(fx(() -> matches(s, b[0], ":armed")));
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.FOCUS_GAINED, 0, 0)));
        assertTrue(fx(() -> matches(s, b[0], ":focused")), "pressed and focused in a focused window");
        release(s, n.getX() + 3, n.getY() + 3, 1, 0);
        assertFalse(fx(() -> matches(s, b[0], ":pressed")));
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.FOCUS_LOST, 0, 0)));
        assertFalse(fx(() -> matches(s, b[0], ":focused")), "not focused while the window is not");
        click(s, combo[0]);
        assertTrue(fx(() -> matches(s, combo[0], ":showing")));
        close(s);
        assertFalse(fx(() -> NativeCss.Selector.parse(":hover").matches(model(b[0]), new NativeCss(null))), "no scene, no hover");
    }

    @Test
    void cellPseudoClasses() throws Exception {
        fx(() -> {
            ListCell<String> cell = new ListCell<>();
            NativeModel m = model(cell);
            NativeCss css = new NativeCss(null);
            Native.property(m, "index", int.class).setValue(3);
            Native.property(m, "empty", boolean.class).setValue(true);
            assertTrue(NativeCss.Selector.parse(":odd").matches(m, css));
            assertFalse(NativeCss.Selector.parse(":even").matches(m, css));
            assertTrue(NativeCss.Selector.parse(":empty").matches(m, css));
            assertFalse(NativeCss.Selector.parse(":filled").matches(m, css));
            Native.property(m, "index", int.class).setValue(4);
            Native.property(m, "empty", boolean.class).setValue(false);
            assertTrue(NativeCss.Selector.parse(":even").matches(m, css));
            assertTrue(NativeCss.Selector.parse(":filled").matches(m, css));
            return null;
        });
    }

    // ---- layouts -----------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void gridColumnAndRowConstraintsReachTheLayout() throws Exception {
        GridPane[] grid = new GridPane[1];
        NativeScene s = show(fx(() -> {
            grid[0] = new GridPane();
            ColumnConstraints c = new ColumnConstraints(40, 60, 80);
            c.setHgrow(Priority.ALWAYS);
            c.setHalignment(HPos.RIGHT);
            c.setFillWidth(false);
            ColumnConstraints percent = new ColumnConstraints();
            percent.setPercentWidth(25);
            RowConstraints r = new RowConstraints(20, 30, 40);
            r.setVgrow(Priority.SOMETIMES);
            r.setValignment(VPos.BOTTOM);
            r.setFillHeight(false);
            RowConstraints rowPercent = new RowConstraints();
            rowPercent.setPercentHeight(50);
            grid[0].getColumnConstraints().addAll(c, percent);
            grid[0].getRowConstraints().addAll(r, rowPercent);
            grid[0].add(new Label("x"), 0, 0);
            return new VBox(grid[0]);
        }), 400, 300);
        JXNativeNode n = node(s, grid[0]);
        List<Map<String, Object>> columns = (List<Map<String, Object>>) n.getProperty("columns");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) n.getProperty("rows");
        assertEquals(40.0, columns.get(0).get("minWidth"));
        assertEquals(60.0, columns.get(0).get("prefWidth"));
        assertEquals(80.0, columns.get(0).get("maxWidth"));
        assertEquals("always", columns.get(0).get("hgrow"));
        assertEquals("RIGHT", columns.get(0).get("halignment"));
        assertEquals(false, columns.get(0).get("fillWidth"));
        assertEquals(25.0, columns.get(1).get("percentWidth"));
        assertNull(columns.get(1).get("hgrow"));
        assertEquals(20.0, rows.get(0).get("minHeight"));
        assertEquals(30.0, rows.get(0).get("prefHeight"));
        assertEquals(40.0, rows.get(0).get("maxHeight"));
        assertEquals("sometimes", rows.get(0).get("vgrow"));
        assertEquals("BOTTOM", rows.get(0).get("valignment"));
        assertEquals(false, rows.get(0).get("fillHeight"));
        assertEquals(50.0, rows.get(1).get("percentHeight"));
        assertNull(rows.get(1).get("valignment"));
        close(s);
    }

    @Test
    void flowAndTilePanes() throws Exception {
        FlowPane[] flow = new FlowPane[1];
        TilePane[] tile = new TilePane[1];
        NativeScene s = show(fx(() -> {
            flow[0] = new FlowPane(Orientation.VERTICAL);
            flow[0].setHgap(3);
            flow[0].setVgap(4);
            flow[0].setPrefWrapLength(120);
            flow[0].getChildren().addAll(new Label("a"), new Label("b"));
            tile[0] = new TilePane(5, 6);
            tile[0].getChildren().addAll(new Label("c"));
            return new VBox(flow[0], tile[0]);
        }), 400, 300);
        JXNativeNode f = node(s, flow[0]);
        assertEquals("flow", f.getType());
        assertEquals("vertical", f.getProperty("orientation"));
        assertEquals(3.0, f.getProperty("hgap"));
        assertEquals(4.0, f.getProperty("vgap"));
        assertEquals(120.0, f.getProperty("prefWrapLength"));
        JXNativeNode t = node(s, tile[0]);
        assertEquals("tile", t.getType());
        assertEquals(5.0, t.getProperty("hgap"));
        assertEquals(6.0, t.getProperty("vgap"));
        FlowPane plain = fx(() -> new FlowPane());
        fx(() -> ((VBox) s.rootModel().jxOwner()).getChildren().add(plain));
        assertNull(node(s, plain).getProperty("prefWrapLength"), "unset wrap length stays JavaFX's default");
        close(s);
    }

    @Test
    void staticLayoutGettersAndSetters() throws Exception {
        fx(() -> {
            Label label = new Label("x");
            GridPane.setColumnIndex(label, 2);
            GridPane.setRowSpan(label, 3);
            assertEquals(2, (int) GridPane.getColumnIndex(label));
            assertEquals(3, (int) GridPane.getRowSpan(label));
            assertNull(GridPane.getRowIndex(label), "unset constraints are null, like JavaFX");
            VBox.setVgrow(label, Priority.ALWAYS);
            assertEquals(Priority.ALWAYS, VBox.getVgrow(label));
            HBox.setMargin(label, new com.jxparallel.fx.geometry.Insets(1, 2, 3, 4));
            assertEquals(4.0, HBox.getMargin(label).getLeft());
            AnchorPane.setTopAnchor(label, 7.0);
            assertEquals(7.0, AnchorPane.getTopAnchor(label));
            return null;
        });
    }

    @Test
    void defaultValuesOfUnsetPrimitives() {
        assertEquals(false, Native.zero(boolean.class));
        assertEquals(0.0, Native.zero(double.class));
        assertEquals(0.0f, Native.zero(float.class));
        assertEquals(0L, Native.zero(long.class));
        assertEquals(0, Native.zero(int.class));
        assertEquals(0, Native.zero(short.class));
        assertEquals(0, Native.zero(byte.class));
        assertEquals('\0', Native.zero(char.class));
        assertNull(Native.zero(String.class));
    }

    @Test
    void genericPropertiesHaveTheirValueTypes() throws Exception {
        fx(() -> {
            Pagination pages = new Pagination(5, 2);
            AtomicInteger changes = new AtomicInteger();
            pages.currentPageIndexProperty().addListener((o, a, b) -> changes.incrementAndGet());
            pages.currentPageIndexProperty().set(3);
            assertEquals(3, pages.getCurrentPageIndex());
            Label label = new Label("x");
            label.wrapTextProperty().set(true);
            assertTrue(label.isWrapText());
            label.opacityProperty().set(0.25);
            assertEquals(0.25, label.getOpacity());
            label.textProperty().set("y");
            assertEquals("y", label.getText());
            assertEquals(1, changes.get());
            return null;
        });
    }
    @Test
    void unsetValuesReportJavaFxDefaults() throws Exception {
        fx(() -> {
            assertEquals(100.0, new Slider().getMax(), "Slider max");
            assertEquals(10.0, new Slider().getBlockIncrement());
            assertTrue(new TitledPane().isExpanded(), "a titled pane starts expanded");
            assertTrue(new TitledPane().isCollapsible());
            assertEquals(-1.0, new ProgressBar().getProgress(), "a no-argument progress bar is indeterminate");
            assertFalse(new ListView<String>().isEditable(), "lists are not editable by default");
            assertTrue(new TextField().isEditable());
            assertFalse(new ComboBox<String>().isEditable());
            assertEquals(10, new ComboBox<String>().getVisibleRowCount());
            assertEquals(12, new TextField().getPrefColumnCount());
            assertTrue(new com.jxparallel.fx.stage.Stage().isResizable(), "stages are resizable");
            assertEquals(100.0, new Slider().maxProperty().get(), "a property created later starts at the default too");
            Slider set = new Slider();
            set.setMax(5);
            assertEquals(5.0, set.getMax(), "a set value wins");
            assertEquals(null, Native.javafxDefault(javafx.scene.control.Slider.class, "padding"), "objects come from CSS, not here");
            assertEquals(null, Native.javafxDefault(Object.class, "max"), "only JavaFX classes");
            assertEquals(null, Native.javafxDefault(javafx.scene.Scene.class, "width"), "no prototype without a no-argument constructor");
            assertEquals(null, Native.javafxDefault(javafx.scene.control.Slider.class, "noSuchThing"));
            return null;
        });
    }
}
