package com.jxparallel.fx.nativeimpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.event.ActionEvent;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.input.KeyCode;
import com.jxparallel.fx.scene.input.KeyEvent;
import com.jxparallel.fx.scene.input.MouseEvent;
import com.jxparallel.fx.scene.layout.*;
import com.jxparallel.ui.native2d.JXCalendar;
import com.jxparallel.ui.native2d.JXControlLayout;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Controls respond to clicks, keys and the wheel the way their JavaFX behaviors do. */
class NativeInteractionTest extends NativeTestSupport {

    // ---- event dispatch ----------------------------------------------------------------------

    @Test
    void filtersRunFromTheRootAndHandlersFromTheTargetAndConsumeStops() throws Exception {
        List<String> order = new ArrayList<>();
        Button[] button = new Button[1];
        VBox[] box = new VBox[1];
        NativeScene s = show(fx(() -> {
            button[0] = new Button("B");
            box[0] = new VBox(button[0]);
            box[0].addEventFilter(MouseEvent.MOUSE_PRESSED, e -> order.add("box filter"));
            button[0].addEventFilter(MouseEvent.MOUSE_PRESSED, e -> order.add("button filter"));
            button[0].addEventHandler(MouseEvent.MOUSE_PRESSED, e -> order.add("button handler"));
            button[0].setOnMousePressed(e -> order.add("button property"));
            box[0].addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                order.add("box handler");
                e.consume();
            });
            // like JavaFX's CompositeEventHandler: the rest of the box's handlers still run,
            // consuming only keeps the event from the scene and the window
            box[0].setOnMousePressed(e -> order.add("box property"));
            return box[0];
        }), 200, 100);
        fx(() -> box[0].getScene().addEventHandler(MouseEvent.MOUSE_PRESSED, e -> order.add("scene (not reached)")));

        click(s, button[0]);

        assertEquals(java.util.Arrays.asList("box filter", "button filter", "button handler", "button property", "box handler",
                "box property"), order);
        close(s);
    }

    @Test
    void aConsumingFilterKeepsTheButtonFromFiring() throws Exception {
        AtomicInteger actions = new AtomicInteger();
        Button[] button = new Button[1];
        NativeScene s = show(fx(() -> {
            button[0] = new Button("B");
            button[0].setOnAction(e -> actions.incrementAndGet());
            button[0].addEventFilter(ActionEvent.ACTION, e -> e.consume());
            return new VBox(button[0]);
        }), 200, 100);

        click(s, button[0]);

        assertEquals(0, actions.get());
        close(s);
    }

    @Test
    void mouseEventsHaveLocalCoordinatesButtonAndClickCountAndTheNodeAsSource() throws Exception {
        AtomicReference<Object> source = new AtomicReference<>();
        List<Double> xs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        Label[] label = new Label[1];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("target");
            label[0].setOnMouseClicked(e -> {
                source.set(e.getSource());
                xs.add(e.getX());
                counts.add(e.getClickCount());
                assertEquals(com.jxparallel.fx.scene.input.MouseButton.PRIMARY, e.getButton());
            });
            VBox box = new VBox(label[0]);
            box.setPadding(new com.jxparallel.fx.geometry.Insets(10, 0, 0, 30));
            return box;
        }), 200, 100);
        JXNativeNode n = node(s, label[0]);

        click(s, n.getX() + 5, n.getY() + 5, 2, 0);

        assertSame(label[0], source.get());
        assertEquals(5.0, xs.get(0), 0.001);
        assertEquals(2, (int) counts.get(0));
        close(s);
    }

    @Test
    void releasingOutsideThePressedNodeIsNotAClick() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        Button[] button = new Button[1];
        NativeScene s = show(fx(() -> {
            button[0] = new Button("B");
            button[0].setOnAction(e -> clicks.incrementAndGet());
            button[0].setOnMouseClicked(e -> clicks.addAndGet(100));
            return new VBox(button[0]);
        }), 300, 200);
        JXNativeNode n = node(s, button[0]);

        press(s, n.getX() + 2, n.getY() + 2, 1, 0);
        release(s, 250, 150, 1, 0);
        settle();

        assertEquals(0, clicks.get());
        close(s);
    }

    @Test
    void enteredAndExitedGoOnlyToTheNodeThatChanged() throws Exception {
        List<String> events = new ArrayList<>();
        Label[] a = new Label[1];
        Label[] b = new Label[1];
        NativeScene s = show(fx(() -> {
            a[0] = new Label("aaaa");
            b[0] = new Label("bbbb");
            a[0].setOnMouseEntered(e -> events.add("enter a"));
            a[0].setOnMouseExited(e -> events.add("exit a"));
            b[0].setOnMouseEntered(e -> events.add("enter b"));
            return new VBox(a[0], b[0]);
        }), 200, 100);
        JXNativeNode na = node(s, a[0]);
        JXNativeNode nb = node(s, b[0]);

        move(s, na.getX() + 2, na.getY() + 2, false);
        move(s, nb.getX() + 2, nb.getY() + 2, false);
        settle();

        assertEquals(java.util.Arrays.asList("enter a", "exit a", "enter b"), events);
        assertTrue(fx(() -> s.hover.contains(model(b[0]))));
        close(s);
    }

    // ---- buttons and toggles -----------------------------------------------------------------

    @Test
    void checkBoxTogglesAndCyclesThroughIndeterminate() throws Exception {
        CheckBox[] check = new CheckBox[1];
        NativeScene s = show(fx(() -> {
            check[0] = new CheckBox("c");
            return new VBox(check[0]);
        }), 200, 100);

        click(s, check[0]);
        assertTrue(fx(() -> check[0].isSelected()));
        click(s, check[0]);
        assertFalse(fx(() -> check[0].isSelected()));

        fx(() -> check[0].setAllowIndeterminate(true));
        click(s, check[0]);
        assertTrue(fx(() -> check[0].isIndeterminate()), "unchecked -> indeterminate");
        click(s, check[0]);
        assertTrue(fx(() -> check[0].isSelected() && !check[0].isIndeterminate()), "indeterminate -> checked");
        click(s, check[0]);
        assertFalse(fx(() -> check[0].isSelected()), "checked -> unchecked");
        close(s);
    }

    @Test
    void radioButtonsInAGroupSelectOneAtATime() throws Exception {
        RadioButton[] r = new RadioButton[2];
        ToggleGroup[] group = new ToggleGroup[1];
        NativeScene s = show(fx(() -> {
            group[0] = new ToggleGroup();
            r[0] = new RadioButton("a");
            r[1] = new RadioButton("b");
            r[0].setToggleGroup(group[0]);
            r[1].setToggleGroup(group[0]);
            r[0].setSelected(true);
            return new VBox(r[0], r[1]);
        }), 200, 100);

        click(s, r[1]);

        assertFalse(fx(() -> r[0].isSelected()));
        assertTrue(fx(() -> r[1].isSelected()));
        assertSame(r[1], fx(() -> group[0].getSelectedToggle()));
        click(s, r[1]);
        assertTrue(fx(() -> r[1].isSelected()), "a selected radio stays selected");
        close(s);
    }

    @Test
    void toggleButtonTogglesAndHyperlinkBecomesVisited() throws Exception {
        ToggleButton[] t = new ToggleButton[1];
        Hyperlink[] h = new Hyperlink[1];
        AtomicInteger actions = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            t[0] = new ToggleButton("t");
            h[0] = new Hyperlink("h");
            h[0].setOnAction(e -> actions.incrementAndGet());
            return new VBox(t[0], h[0]);
        }), 200, 100);

        click(s, t[0]);
        click(s, h[0]);

        assertTrue(fx(() -> t[0].isSelected()));
        assertTrue(fx(() -> h[0].isVisited()));
        assertEquals(1, actions.get());
        close(s);
    }

    @Test
    void disabledControlsIgnoreClicks() throws Exception {
        AtomicInteger actions = new AtomicInteger();
        Button[] b = new Button[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("b");
            b[0].setOnAction(e -> actions.incrementAndGet());
            VBox box = new VBox(b[0]);
            box.setDisable(true);
            return box;
        }), 200, 100);

        click(s, b[0]);

        assertEquals(0, actions.get());
        assertFalse(fx(() -> b[0].isFocused()), "a disabled control does not take the focus");
        close(s);
    }

    // ---- keyboard ----------------------------------------------------------------------------

    @Test
    void tabMovesTheFocusInSceneOrderAndSpaceFiresTheFocusedButton() throws Exception {
        TextField[] f = new TextField[1];
        Button[] b = new Button[1];
        AtomicInteger actions = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            f[0] = new TextField();
            b[0] = new Button("b");
            b[0].setOnAction(e -> actions.incrementAndGet());
            return new VBox(new Label("l"), f[0], b[0]);
        }), 200, 100);

        assertTrue(fx(() -> f[0].isFocused()), "the first focusable node gets the focus when shown");
        key(s, "TAB");
        assertTrue(fx(() -> b[0].isFocused()));
        key(s, "SPACE");
        assertEquals(1, actions.get());
        key(s, "TAB", JXInputEvent.SHIFT);
        assertTrue(fx(() -> f[0].isFocused()));
        close(s);
    }

    @Test
    void enterFiresTheDefaultButtonAndEscapeTheCancelButton() throws Exception {
        List<String> fired = new ArrayList<>();
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField();
            Button ok = new Button("OK");
            ok.setDefaultButton(true);
            ok.setOnAction(e -> fired.add("ok"));
            Button cancel = new Button("Cancel");
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> fired.add("cancel"));
            return new VBox(f[0], ok, cancel);
        }), 200, 100);

        key(s, "ENTER");
        key(s, "ESCAPE");

        assertEquals(java.util.Arrays.asList("ok", "cancel"), fired);
        close(s);
    }

    @Test
    void keyEventsReachTheFocusedNodeAndItsParents() throws Exception {
        List<String> seen = new ArrayList<>();
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField();
            f[0].setOnKeyPressed(e -> seen.add("field " + e.getCode()));
            VBox box = new VBox(f[0]);
            box.addEventHandler(KeyEvent.KEY_PRESSED, e -> seen.add("box " + e.getCode()));
            box.addEventFilter(KeyEvent.KEY_TYPED, e -> {
                if ("x".equals(e.getCharacter())) {
                    e.consume(); // a filter can refuse characters
                }
            });
            return box;
        }), 200, 100);

        key(s, "F2");
        type(s, "axb");

        assertEquals(java.util.Arrays.asList("field F2", "box F2"), seen);
        assertEquals("ab", fx(() -> f[0].getText()));
        close(s);
    }

    @Test
    void textEditingKeys() throws Exception {
        TextField[] f = new TextField[1];
        AtomicInteger actions = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            f[0] = new TextField("hello world");
            f[0].setOnAction(e -> actions.incrementAndGet());
            return new VBox(f[0]);
        }), 300, 100);
        fx(() -> f[0].positionCaret(11));

        key(s, "BACK_SPACE");
        assertEquals("hello worl", fx(() -> f[0].getText()));
        key(s, "BACK_SPACE", JXInputEvent.CONTROL);
        assertEquals("hello ", fx(() -> f[0].getText()));
        key(s, "HOME");
        key(s, "DELETE");
        assertEquals("ello ", fx(() -> f[0].getText()));
        key(s, "END");
        key(s, "LEFT", JXInputEvent.SHIFT);
        key(s, "LEFT", JXInputEvent.SHIFT);
        type(s, "!");
        assertEquals("ell!", fx(() -> f[0].getText()));
        key(s, "A", JXInputEvent.CONTROL);
        type(s, "z");
        assertEquals("z", fx(() -> f[0].getText()));
        key(s, "ENTER");
        assertEquals(1, actions.get());
        close(s);
    }

    @Test
    void clickPlacesTheCaretAndDoubleClickSelectsAWord() throws Exception {
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField("alpha beta");
            return new VBox(f[0]);
        }), 300, 100);
        JXNativeNode n = node(s, f[0]);

        click(s, n.getX() + 8, n.getY() + 10);
        assertEquals(0, (int) fx(() -> f[0].getCaretPosition()));
        click(s, n.getX() + n.getWidth() - 10, n.getY() + 10, 2, 0);
        assertEquals("beta", fx(() -> f[0].getSelectedText()));
        close(s);
    }

    @Test
    void textAreaEnterInsertsALineAndArrowsMoveBetweenLines() throws Exception {
        TextArea[] a = new TextArea[1];
        NativeScene s = show(fx(() -> {
            a[0] = new TextArea("ab");
            return new VBox(a[0]);
        }), 300, 200);
        fx(() -> a[0].positionCaret(1));

        key(s, "ENTER");
        type(s, "c");
        assertEquals("a\ncb", fx(() -> a[0].getText()));
        key(s, "UP");
        type(s, "x");
        assertEquals("ax\ncb", fx(() -> a[0].getText()));
        close(s);
    }

    @Test
    void textFormatterFilterCanRejectAndRewriteChanges() throws Exception {
        TextField[] f = new TextField[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField();
            f[0].setTextFormatter(new TextFormatter<String>(change -> {
                if (!change.getText().matches("[0-9a-z]*")) {
                    return null;
                }
                change.setText(change.getText().toUpperCase());
                return change;
            }));
            return new VBox(f[0]);
        }), 200, 100);

        type(s, "a1-b");

        assertEquals("A1B", fx(() -> f[0].getText()));
        close(s);
    }

    // ---- composite controls ------------------------------------------------------------------

    @Test
    void comboBoxPopupPicksAnItemAndFiresAction() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        AtomicInteger actions = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            combo[0] = new ComboBox<>(FXCollections.observableArrayList("Fortaleza", "Recife", "Natal"));
            combo[0].setOnAction(e -> actions.incrementAndGet());
            return new VBox(combo[0]);
        }), 300, 300);
        JXNativeNode n = node(s, combo[0]);

        click(s, combo[0]);
        assertTrue(fx(() -> combo[0].isShowing()));
        // the popup lists the items below the combo box, 23 px per row
        click(s, n.getX() + 20, n.getY() + n.getHeight() + 1 + 23 + 10);

        assertEquals("Recife", fx(() -> combo[0].getValue()));
        assertEquals(1, actions.get());
        assertFalse(fx(() -> combo[0].isShowing()));
        close(s);
    }

    @Test
    void comboBoxKeysChangeTheValueAndTypingJumpsToAnItem() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        NativeScene s = show(fx(() -> {
            combo[0] = new ComboBox<>(FXCollections.observableArrayList("Ana", "Bia", "Caio"));
            return new VBox(combo[0]);
        }), 300, 300);

        fx(() -> combo[0].requestFocus());
        key(s, "DOWN");
        assertEquals("Ana", fx(() -> combo[0].getValue()));
        key(s, "DOWN");
        assertEquals("Bia", fx(() -> combo[0].getValue()));
        type(s, "c");
        assertEquals("Caio", fx(() -> combo[0].getValue()));
        close(s);
    }

    @Test
    void clickingOutsideAnOpenPopupClosesItWithoutClicking() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        Button[] b = new Button[1];
        AtomicInteger actions = new AtomicInteger();
        NativeScene s = show(fx(() -> {
            combo[0] = new ComboBox<>(FXCollections.observableArrayList("a"));
            b[0] = new Button("b");
            b[0].setOnAction(e -> actions.incrementAndGet());
            HBox box = new HBox(20, combo[0], b[0]);
            return box;
        }), 400, 300);

        click(s, combo[0]);
        click(s, b[0]);

        assertFalse(fx(() -> combo[0].isShowing()));
        assertEquals(0, actions.get(), "the click that closes the popup is consumed");
        click(s, b[0]);
        assertEquals(1, actions.get());
        close(s);
    }

    @Test
    void datePickerCalendarPicksADay() throws Exception {
        DatePicker[] picker = new DatePicker[1];
        NativeScene s = show(fx(() -> {
            picker[0] = new DatePicker(LocalDate.of(2026, 9, 15));
            return new VBox(picker[0]);
        }), 400, 400);
        JXNativeNode n = node(s, picker[0]);

        click(s, n.getX() + n.getWidth() - 5, n.getY() + 5);
        JXNativeNode calendar = fx(() -> {
            s.render();
            for (JXNativeNode c : s.pathAt(n.getX() + 30, n.getY() + n.getHeight() + 80)) {
                if ("calendar".equals(c.getType())) {
                    return c;
                }
            }
            return null;
        });
        assertTrue(calendar != null, "the calendar opened below the picker");
        // find the cell of the 20th and click it
        double[] at = fx(() -> {
            for (int y = calendar.getY(); y < calendar.getY() + calendar.getHeight(); y += 4) {
                for (int x = calendar.getX(); x < calendar.getX() + calendar.getWidth(); x += 4) {
                    int[] hit = JXCalendar.hit(calendar, x, y);
                    if (hit[0] == JXCalendar.DAY && hit[2] == 9 && hit[3] == 20) {
                        return new double[] {x, y};
                    }
                }
            }
            return null;
        });
        click(s, at[0], at[1]);

        assertEquals(LocalDate.of(2026, 9, 20), fx(() -> picker[0].getValue()));
        close(s);
    }

    @Test
    void spinnerArrowsAndKeysStepTheValue() throws Exception {
        Spinner<Integer>[] spinner = new Spinner[1];
        NativeScene s = show(fx(() -> {
            spinner[0] = new Spinner<>(0, 10, 5);
            return new VBox(spinner[0]);
        }), 300, 100);
        JXNativeNode n = node(s, spinner[0]);

        click(s, n.getX() + n.getWidth() - 5, n.getY() + 3);
        assertEquals(6, (int) fx(() -> spinner[0].getValue()));
        click(s, n.getX() + n.getWidth() - 5, n.getY() + n.getHeight() - 3);
        click(s, n.getX() + n.getWidth() - 5, n.getY() + n.getHeight() - 3);
        assertEquals(4, (int) fx(() -> spinner[0].getValue()));
        fx(() -> spinner[0].requestFocus());
        key(s, "UP");
        assertEquals(5, (int) fx(() -> spinner[0].getValue()));
        assertEquals("5", node(s, spinner[0]).getProperty("value"));
        close(s);
    }

    @Test
    void sliderFollowsThePointer() throws Exception {
        Slider[] slider = new Slider[1];
        NativeScene s = show(fx(() -> {
            slider[0] = new Slider(0, 100, 0);
            slider[0].setPrefWidth(214);
            return new HBox(slider[0]);
        }), 300, 100);
        JXNativeNode n = node(s, slider[0]);

        press(s, n.getX() + 7 + 100, n.getY() + 5, 1, 0);
        release(s, n.getX() + 7 + 100, n.getY() + 5, 1, 0);

        assertEquals(50.0, fx(() -> slider[0].getValue()), 0.5);
        close(s);
    }

    @Test
    void titledPaneCollapsesAndAccordionKeepsOneOpen() throws Exception {
        TitledPane[] panes = new TitledPane[2];
        Accordion[] acc = new Accordion[1];
        NativeScene s = show(fx(() -> {
            panes[0] = new TitledPane("A", new Label("a"));
            panes[1] = new TitledPane("B", new Label("b"));
            acc[0] = new Accordion(panes[0], panes[1]);
            return new VBox(acc[0]);
        }), 300, 300);

        JXNativeNode a = node(s, panes[0]);
        click(s, a.getX() + 40, a.getY() + 10);
        assertSame(panes[0], fx(() -> acc[0].getExpandedPane()));
        JXNativeNode b = node(s, panes[1]);
        click(s, b.getX() + 40, b.getY() + 10);
        assertSame(panes[1], fx(() -> acc[0].getExpandedPane()));
        assertFalse(fx(() -> panes[0].isExpanded()));
        click(s, b.getX() + 40, node(s, panes[1]).getY() + 10);
        assertNull(fx(() -> acc[0].getExpandedPane()), "clicking the open pane closes it");
        close(s);
    }

    @Test
    void tabHeadersSelectAndCloseTabs() throws Exception {
        TabPane[] tabs = new TabPane[1];
        List<String> events = new ArrayList<>();
        NativeScene s = show(fx(() -> {
            Tab one = new Tab("Um", new Label("1"));
            Tab two = new Tab("Dois", new Label("2"));
            two.setOnSelectionChanged(e -> events.add("two selection changed"));
            two.setOnClosed(e -> events.add("two closed"));
            tabs[0] = new TabPane(one, two);
            tabs[0].setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
            return new VBox(tabs[0]);
        }), 400, 300);
        JXNativeNode n = node(s, tabs[0]);
        float[] positions = fx(() -> JXControlLayout.tabPositions(n));

        click(s, n.getX() + positions[1] + 5, n.getY() + 15);
        assertEquals(1, (int) fx(() -> tabs[0].getSelectionModel().getSelectedIndex()));
        click(s, n.getX() + positions[2] - 10, n.getY() + 15);
        assertEquals(1, (int) fx(() -> tabs[0].getTabs().size()));
        assertEquals(java.util.Arrays.asList("two selection changed", "two closed"), events);
        close(s);
    }

    @Test
    void listViewSelectionWithControlAndShift() throws Exception {
        ListView<String>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>(FXCollections.observableArrayList("a", "b", "c", "d"));
            list[0].getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            return new VBox(list[0]);
        }), 300, 300);
        JXNativeNode n = node(s, list[0]);
        double x = n.getX() + 20;
        double y0 = n.getY() + 1 + 11;

        click(s, x, y0);
        click(s, x, y0 + 2 * 23, 1, JXInputEvent.SHIFT);
        assertEquals(java.util.Arrays.asList(0, 1, 2), fx(() -> new ArrayList<>(list[0].getSelectionModel().getSelectedIndices())));
        click(s, x, y0 + 23, 1, JXInputEvent.CONTROL);
        assertFalse(fx(() -> list[0].getSelectionModel().isSelected(1)));
        fx(() -> list[0].requestFocus());
        key(s, "END");
        assertEquals("d", fx(() -> list[0].getSelectionModel().getSelectedItem()));
        close(s);
    }

    @Test
    void wheelScrollsAListAndTheVisibleCellsFollow() throws Exception {
        ListView<Integer>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                items.add(i);
            }
            list[0] = new ListView<>(FXCollections.observableArrayList(items));
            list[0].setPrefHeight(117);
            return new VBox(list[0]);
        }), 300, 300);
        JXNativeNode n = node(s, list[0]);

        wheel(s, n.getX() + 20, n.getY() + 20, -2);

        JXNativeNode after = node(s, list[0]);
        assertEquals(6, after.getProperty("first"), "two notches scroll six rows");
        assertEquals("6", after.getChildren().get(0).getProperty("value"));
        assertTrue(after.getChildren().size() <= 7, "only the visible cells exist");
        close(s);
    }

    @Test
    void tableHeaderClickSortsTheItems() throws Exception {
        TableView<NativeControlsTest.Person>[] table = new TableView[1];
        NativeScene s = show(fx(() -> {
            table[0] = new TableView<>(FXCollections.observableArrayList(new NativeControlsTest.Person("Carla", 1),
                    new NativeControlsTest.Person("Ana", 2), new NativeControlsTest.Person("Bia", 3)));
            TableColumn<NativeControlsTest.Person, String> name = new TableColumn<>("Nome");
            name.setCellValueFactory(new com.jxparallel.fx.scene.control.cell.PropertyValueFactory<>("name"));
            table[0].getColumns().add(name);
            return new VBox(table[0]);
        }), 300, 300);
        JXNativeNode n = node(s, table[0]);

        click(s, n.getX() + 10, n.getY() + 10);
        assertEquals("Ana", fx(() -> table[0].getItems().get(0).getName()));
        click(s, n.getX() + 10, n.getY() + 10);
        assertEquals("Carla", fx(() -> table[0].getItems().get(0).getName()));
        click(s, n.getX() + 10, n.getY() + 1 + 24 + 30);
        assertEquals("Bia", fx(() -> table[0].getSelectionModel().getSelectedItem().getName()));
        close(s);
    }

    @Test
    void scrollPaneWheelAndThumbDrag() throws Exception {
        ScrollPane[] scroll = new ScrollPane[1];
        NativeScene s = show(fx(() -> {
            Pane content = new Pane();
            content.setPrefSize(100, 1000);
            scroll[0] = new ScrollPane(content);
            scroll[0].setPrefSize(200, 200);
            return new VBox(scroll[0]);
        }), 300, 300);
        JXNativeNode n = node(s, scroll[0]);

        wheel(s, n.getX() + 50, n.getY() + 50, -1);
        double afterWheel = fx(() -> scroll[0].getVvalue());
        assertEquals(40.0 / (1000 - 198), afterWheel, 0.0001, "one notch is 40 px");

        JXControlLayout.ScrollGeometry g = fx(() -> JXControlLayout.scrollGeometry(node(s, scroll[0])));
        press(s, g.vx + 5, g.vThumbY + 5, 1, 0);
        move(s, g.vx + 5, g.vThumbY + 5 + 1000, true);
        release(s, g.vx + 5, g.vThumbY + 5 + 1000, 1, 0);
        assertEquals(1.0, fx(() -> scroll[0].getVvalue()), 0.0001);
        close(s);
    }

    @Test
    void paginationButtonsChangeThePage() throws Exception {
        Pagination[] pages = new Pagination[1];
        NativeScene s = show(fx(() -> {
            pages[0] = new Pagination(5, 0);
            pages[0].setPageFactory(i -> new Label("p" + i));
            return new VBox(pages[0]);
        }), 400, 300);
        JXNativeNode n = node(s, pages[0]);
        float[][] buttons = fx(() -> JXControlLayout.pageButtons(n));

        click(s, buttons[3][0] + 5, buttons[3][1] + 5);
        assertEquals(2, (int) fx(() -> pages[0].getCurrentPageIndex()));
        click(s, buttons[buttons.length - 1][0] + 5, buttons[buttons.length - 1][1] + 5);
        assertEquals(3, (int) fx(() -> pages[0].getCurrentPageIndex()));
        close(s);
    }

    @Test
    void contextMenuOpensOnSecondaryClickAndRunsTheItem() throws Exception {
        AtomicInteger actions = new AtomicInteger();
        Label[] label = new Label[1];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("menu here");
            MenuItem item = new MenuItem("Copiar");
            item.setOnAction(e -> actions.incrementAndGet());
            ContextMenu menu = new ContextMenu(item);
            TextField f = new TextField();
            f.setContextMenu(menu);
            return new VBox(f);
        }), 300, 300);
        TextField field = fx(() -> (TextField) ((VBox) s.rootModel().jxOwner()).getChildren().get(0));
        JXNativeNode n = node(s, field);

        rightClick(s, n.getX() + 10, n.getY() + 10);
        assertEquals(1, (int) fx(() -> (int) s.overlays.stream().filter(o -> o.kind == NativeOverlay.Kind.MENU).count()));
        click(s, n.getX() + 20, n.getY() + 10 + 1 + 12);

        assertEquals(1, actions.get());
        assertTrue(fx(() -> s.overlays.isEmpty()));
        close(s);
    }
}
