package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.layout.*;
import com.jxparallel.ui.native2d.JXControlLayout;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact edges of the behaviors: nested controls, scroll bar borders, drag and slider arithmetic, key results. */
class NativeBehaviorEdgesTest extends NativeTestSupport {

    private static List<String> items(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add("item " + i);
        }
        return out;
    }

    @Test
    void controlsInsideListCellsTakeThePressAndDoNotSelectTheRow() throws Exception {
        ListView<String>[] list = new ListView[1];
        List<Control> controls = new ArrayList<>();
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>(FXCollections.observableArrayList("button", "check", "field", "combo", "spinner", "slider",
                    "date", "link", "toggle", "radio"));
            list[0].setPrefHeight(300);
            list[0].setCellFactory(v -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        return;
                    }
                    Control c;
                    switch (item) {
                        case "button": c = new Button("b"); break;
                        case "check": c = new CheckBox("c"); break;
                        case "field": c = new TextField("t"); break;
                        case "combo": c = new ComboBox<>(FXCollections.observableArrayList("x")); break;
                        case "spinner": c = new Spinner<Integer>(0, 5, 1); break;
                        case "slider": c = new Slider(); break;
                        case "date": c = new DatePicker(); break;
                        case "link": c = new Hyperlink("h"); break;
                        case "toggle": c = new ToggleButton("t"); break;
                        default: c = new RadioButton("r"); break;
                    }
                    controls.add(c);
                    setGraphic(c);
                }
            });
            list[0].setFixedCellSize(28);
            return new VBox(list[0]);
        }), 400, 400);
        node(s, list[0]);
        for (Control c : new ArrayList<>(controls)) {
            JXNativeNode n = node(s, c);
            press(s, n.getX() + 3, n.getY() + n.getHeight() / 2.0, 1, 0);
            release(s, n.getX() + 3, n.getY() + n.getHeight() / 2.0, 1, 0);
            settle();
            fx(() -> NativeEvents.closePopups(s));
            assertEquals(-1, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()),
                    "a press on the " + c.getClass().getSimpleName() + " stays with it");
        }
        close(s);
    }

    @Test
    void arrowKeysEditTextInsteadOfMovingTheFocus() throws Exception {
        TextArea[] area = new TextArea[1];
        TextField[] field = new TextField[1];
        AtomicInteger defaults = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            area[0] = new TextArea("ab\ncd");
            field[0] = new TextField("xy");
            Button ok = new Button("ok");
            ok.setDefaultButton(true);
            ok.setOnAction(e -> defaults.incrementAndGet());
            return new VBox(area[0], field[0], ok);
        }), 300, 300);
        fx(() -> field[0].requestFocus());
        fx(() -> field[0].positionCaret(1));
        for (String k : new String[]{"LEFT", "RIGHT", "HOME", "END"}) {
            key(s, k);
            assertTrue(fx(() -> field[0].isFocused()), k + " keeps the focus in the field");
        }
        key(s, "ENTER");
        assertEquals(1, defaults.get(), "Enter in a field reaches the default button once");
        fx(() -> area[0].requestFocus());
        for (String k : new String[]{"UP", "DOWN"}) {
            key(s, k);
            assertTrue(fx(() -> area[0].isFocused()), k + " moves between lines");
        }
        fx(() -> field[0].requestFocus());
        key(s, "UP");
        assertFalse(fx(() -> field[0].isFocused()), "up in a one-line field moves the focus, as in JavaFX");
        close(s);
    }

    @Test
    void scrollBarBordersAndDragArithmetic() throws Exception {
        ListView<String>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>(FXCollections.observableArrayList(items(200)));
            list[0].setPrefHeight(117);
            return new VBox(list[0]);
        }), 300, 300);
        NativeModel m = model(list[0]);
        JXControlLayout.ScrollGeometry g = fx(() -> JXControlLayout.scrollGeometry(node(s, list[0])));
        double mid = g.vy + g.vh / 2;
        click(s, g.vx - 0.5, 5 + g.vy);
        assertEquals(0, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()), "left of the bar is the row");
        fx(() -> list[0].getSelectionModel().clearSelection());
        click(s, g.vx + g.vw - 0.5, g.vy + g.vh - 11);
        assertEquals(23.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001, "the down arrow's first pixel");
        click(s, g.vx + g.vw - 0.5, g.vy + g.vh - 11);
        click(s, g.vx, g.vy + 10.5);
        assertEquals(23.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001, "the up arrow's last pixel");
        JXControlLayout.ScrollGeometry after = fx(() -> JXControlLayout.scrollGeometry(node(s, list[0])));
        assertTrue(after.vThumbY > g.vy + 11, "scrolled one row, the thumb starts just below the arrow");
        click(s, g.vx, g.vy + 11);
        assertEquals(0.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.001,
                "one pixel below the arrow is the track above the thumb: a page up");
        assertEquals(-1, (int) fx(() -> list[0].getSelectionModel().getSelectedIndex()), "bar presses select nothing");
        fx(() -> m.state.put("scrollY", 0.0));
        settle();
        JXControlLayout.ScrollGeometry top = fx(() -> JXControlLayout.scrollGeometry(node(s, list[0])));
        double track = top.vh - 22 - top.vThumbH;
        double max = top.maxScrollY();
        press(s, top.vx + 5, top.vThumbY + 1, 1, 0);
        move(s, top.vx + 5, top.vThumbY + 1 + 10, true);
        assertEquals(10 / track * max, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.01, "the thumb follows the pointer");
        move(s, top.vx + 5, top.vThumbY + 1 - 50, true);
        assertEquals(0.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.01, "not above the top");
        release(s, top.vx + 5, top.vThumbY + 1 - 50, 1, 0);
        move(s, top.vx + 5, mid + 40, true);
        assertEquals(0.0, fx(() -> NativeCells.number(m.state.get("scrollY"), 0)), 0.01, "the drag ended with the release");
        close(s);
    }

    @Test
    void scrollPaneBarsUseTheValueRange() throws Exception {
        ScrollPane[] scroll = new ScrollPane[1];
        NativeScene s = show(fx(() -> {
            Pane content = new Pane();
            content.setPrefSize(100, 1000);
            scroll[0] = new ScrollPane(content);
            scroll[0].setPrefSize(200, 200);
            scroll[0].setVmin(100);
            scroll[0].setVmax(200);
            scroll[0].setVvalue(100);
            return new VBox(scroll[0]);
        }), 300, 300);
        JXControlLayout.ScrollGeometry g = fx(() -> JXControlLayout.scrollGeometry(node(s, scroll[0])));
        click(s, g.vx + 5, g.vy + g.vh - 5);
        assertEquals(100 + 20 / g.maxScrollY() * 100, fx(() -> scroll[0].getVvalue()), 1e-6);
        close(s);
    }

    @Test
    void sliderArithmeticWithAMinimum() throws Exception {
        Slider[] slider = new Slider[1];
        NativeScene s = show(fx(() -> {
            slider[0] = new Slider(10, 110, 10);
            slider[0].setPrefWidth(214);
            slider[0].setMaxWidth(214);
            return new HBox(slider[0]);
        }), 300, 100);
        JXNativeNode n = node(s, slider[0]);
        click(s, n.getX() + 7 + 50, n.getY() + 5);
        assertEquals(35.0, fx(() -> slider[0].getValue()), 1e-9, "a quarter of 200 px over 10..110");
        click(s, n.getX() + 7 + 150, n.getY() + 5);
        assertEquals(85.0, fx(() -> slider[0].getValue()), 1e-9);
        fx(() -> {
            slider[0].setSnapToTicks(true);
            slider[0].setMajorTickUnit(20);
            slider[0].setMinorTickCount(1);
        });
        click(s, n.getX() + 7 + 42, n.getY() + 5);
        assertEquals(30.0, fx(() -> slider[0].getValue()), 1e-9, "31 snaps to the 10 px tick at 30");
        close(s);
    }

    @Test
    void comboListScrollsUpWhenTheHoveredRowLeavesTheTop() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        NativeScene s = show(fx(() -> {
            combo[0] = new ComboBox<>(FXCollections.observableArrayList(items(30)));
            combo[0].setVisibleRowCount(4);
            combo[0].setValue("item 20");
            return new VBox(combo[0]);
        }), 300, 400);
        NativeModel m = model(combo[0]);
        fx(() -> combo[0].requestFocus());
        key(s, "F4");
        assertEquals(20, fx(() -> m.state.get("popupHover")), "opens on the value");
        assertEquals((20 - 2) * 23.0, fx(() -> m.state.get("popupScrollY")), "centred in the list");
        for (int i = 0; i < 3; i++) {
            key(s, "UP");
        }
        assertEquals(17 * 23.0, fx(() -> m.state.get("popupScrollY")), "the hovered row is the first shown");
        for (int i = 0; i < 20; i++) {
            key(s, "DOWN");
        }
        assertEquals(29, fx(() -> m.state.get("popupHover")), "stops at the last row");
        assertEquals((30 - 4) * 23.0, fx(() -> m.state.get("popupScrollY")));
        key(s, "LEFT");
        assertTrue(fx(() -> combo[0].isShowing()), "other keys leave the list open");
        close(s);
    }

    @Test
    void defaultAndCancelButtonsSkipDisabledOnesAndNeedNoFocusOrder() throws Exception {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        AtomicInteger cancels = new AtomicInteger();
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField();
            Button off = new Button("off");
            off.setDefaultButton(true);
            off.setDisable(true);
            off.setOnAction(e -> first.incrementAndGet());
            Button on = new Button("on");
            on.setDefaultButton(true);
            on.setFocusTraversable(false);
            on.setOnAction(e -> second.incrementAndGet());
            Button cancel = new Button("cancel");
            cancel.setCancelButton(true);
            cancel.setFocusTraversable(false);
            cancel.setOnAction(e -> cancels.incrementAndGet());
            return new VBox(f[0], off, on, cancel);
        }), 300, 200);
        fx(() -> f[0].requestFocus());
        key(s, "ENTER");
        key(s, "ESCAPE");
        assertEquals(0, first.get(), "a disabled default button is skipped");
        assertEquals(1, second.get(), "a default button outside the focus order still fires");
        assertEquals(1, cancels.get());
        close(s);
    }

    @Test
    void aTitledPaneThatCannotCollapseIgnoresClicksAndSpace() throws Exception {
        TitledPane[] pane = new TitledPane[1];
        NativeScene s = show(fx(() -> {
            pane[0] = new TitledPane("fixo", new Label("x"));
            pane[0].setCollapsible(false);
            return new VBox(pane[0]);
        }), 300, 200);
        JXNativeNode n = node(s, pane[0]);
        click(s, n.getX() + 30, n.getY() + 10);
        assertTrue(fx(() -> pane[0].isExpanded()));
        fx(() -> pane[0].requestFocus());
        key(s, "SPACE");
        assertTrue(fx(() -> pane[0].isExpanded()));
        click(s, n.getX() + 30, n.getY() + 40);
        assertTrue(fx(() -> pane[0].isExpanded()), "a click below the title is not a toggle");
        close(s);
    }

    @Test
    void aLoneRadioButtonTogglesLikeJavaFx() throws Exception {
        fx(() -> {
            RadioButton alone = new RadioButton("r");
            alone.fire();
            assertTrue(alone.isSelected());
            alone.fire();
            assertFalse(alone.isSelected(), "RadioButton.fire outside a group toggles");
            return null;
        });
    }

    @Test
    void descendingSortPutsEmptyValuesLast() throws Exception {
        TableView<NativeControlsTest.Person>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = new TableView<>(FXCollections.observableArrayList(new NativeControlsTest.Person(null, 1),
                    new NativeControlsTest.Person("b", 2), new NativeControlsTest.Person("a", 3)));
            TableColumn<NativeControlsTest.Person, Object> age = new TableColumn<>("Idade");
            age.setCellValueFactory(new com.jxparallel.fx.scene.control.cell.PropertyValueFactory<>("age"));
            TableColumn<NativeControlsTest.Person, Object> name = new TableColumn<>("Nome");
            name.setCellValueFactory(new com.jxparallel.fx.scene.control.cell.PropertyValueFactory<>("name"));
            age.setPrefWidth(100);
            name.setPrefWidth(100);
            table[0].getColumns().add(age);
            table[0].getColumns().add(name);
            return new VBox(table[0]);
        }), 400, 300);
        JXNativeNode n = node(s, table[0]);
        click(s, n.getX() + 1 + 100 + 10, n.getY() + 10);
        click(s, n.getX() + 1 + 100 + 10, n.getY() + 10);
        assertEquals("b", fx(() -> table[0].getItems().get(0).getName()));
        assertEquals(null, fx(() -> table[0].getItems().get(2).getName()), "descending: empty last");
        click(s, n.getX() + 10, n.getY() + 10);
        assertEquals(1, (int) fx(() -> table[0].getItems().get(0).ageProperty().get()), "another column starts ascending");
        close(s);
    }
}
