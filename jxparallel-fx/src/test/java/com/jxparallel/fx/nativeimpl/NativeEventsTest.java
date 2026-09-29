package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.event.ActionEvent;
import com.jxparallel.fx.event.EventHandler;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.input.KeyCode;
import com.jxparallel.fx.scene.input.KeyEvent;
import com.jxparallel.fx.scene.input.MouseEvent;
import com.jxparallel.fx.scene.Scene;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Event dispatch details: consumption on one node, accelerators, posting from the display thread, window events. */
class NativeEventsTest extends NativeTestSupport {

    @Test
    void consumingStopsTheNextNodeButNotTheOtherHandlersOfTheSameNode() throws Exception {
        List<String> seen = new ArrayList<>();
        Button[] b = new Button[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("b");
            b[0].addEventHandler(ActionEvent.ACTION, e -> {
                seen.add("first handler");
                e.consume();
            });
            b[0].addEventHandler(ActionEvent.ACTION, e -> seen.add("second handler"));
            b[0].setOnAction(e -> seen.add("onAction"));
            b[0].addEventFilter(MouseEvent.MOUSE_PRESSED, e -> seen.add("filter 1"));
            b[0].addEventFilter(MouseEvent.MOUSE_PRESSED, e -> seen.add("filter 2"));
            VBox box = new VBox(b[0]);
            box.addEventHandler(ActionEvent.ACTION, e -> seen.add("parent"));
            return box;
        }), 300, 100);
        click(s, b[0]);
        assertEquals(Arrays.asList("filter 1", "filter 2", "first handler", "second handler", "onAction"), seen,
                "every handler of the button runs, the parent does not");
        close(s);
    }

    @Test
    void aConsumingFilterStopsAfterTheSameNodesOtherFilters() throws Exception {
        List<String> seen = new ArrayList<>();
        Button[] b = new Button[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("b");
            b[0].setOnMousePressed(e -> seen.add("button"));
            VBox box = new VBox(b[0]);
            box.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                seen.add("filter 1");
                e.consume();
            });
            box.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> seen.add("filter 2"));
            return box;
        }), 300, 100);
        JXNativeNode n = node(s, b[0]);
        press(s, n.getX() + 5, n.getY() + 5, 1, 0);
        release(s, n.getX() + 5, n.getY() + 5, 1, 0);
        assertEquals(Arrays.asList("filter 1", "filter 2"), seen);
        close(s);
    }

    @Test
    void acceleratorsRunBeforeTheControlsKeys() throws Exception {
        AtomicInteger saved = new AtomicInteger();
        TextField[] f = new TextField[1];
        Scene[] scene = new Scene[1];
        NativeScene s = show(fx(() -> {
            f[0] = new TextField("abc");
            return new VBox(f[0]);
        }), 300, 100);
        fx(() -> {
            scene[0] = f[0].getScene();
            scene[0].getAccelerators().put(javafx.scene.input.KeyCombination.keyCombination("Ctrl+S"), saved::incrementAndGet);
            f[0].requestFocus();
        });
        key(s, "S", JXInputEvent.CONTROL);
        assertEquals(1, saved.get());
        key(s, "S");
        assertEquals(1, saved.get(), "without Ctrl no match");
        key(s, "A", JXInputEvent.CONTROL);
        assertEquals("abc", fx(() -> f[0].getSelectedText()), "other keys reach the control");
        fx(() -> scene[0].getAccelerators().put(javafx.scene.input.KeyCombination.keyCombination("Ctrl+A"), saved::incrementAndGet));
        key(s, "END");
        key(s, "A", JXInputEvent.CONTROL);
        assertEquals(2, saved.get());
        assertEquals("", fx(() -> f[0].getSelectedText()), "an accelerator takes the key from the control");
        close(s);
    }

    @Test
    void sceneAndWindowHandlersCanBeRemoved() throws Exception {
        List<String> seen = new ArrayList<>();
        Button[] b = new Button[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("b");
            return new VBox(b[0]);
        }), 300, 100);
        EventHandler<KeyEvent> sceneFilter = e -> seen.add("scene filter " + e.getCode());
        EventHandler<KeyEvent> windowHandler = e -> seen.add("window handler " + e.getCode());
        fx(() -> {
            b[0].getScene().addEventFilter(KeyEvent.KEY_PRESSED, sceneFilter);
            b[0].getScene().getWindow().addEventHandler(KeyEvent.KEY_PRESSED, windowHandler);
            b[0].requestFocus();
        });
        key(s, "F2");
        assertEquals(Arrays.asList("scene filter F2", "window handler F2"), seen);
        fx(() -> {
            b[0].getScene().removeEventFilter(KeyEvent.KEY_PRESSED, sceneFilter);
            b[0].getScene().getWindow().removeEventHandler(KeyEvent.KEY_PRESSED, windowHandler);
        });
        key(s, "F3");
        assertEquals(2, seen.size(), "removed handlers hear nothing");
        close(s);
    }

    @Test
    void fireEventDeliversAlongTheNodesChain() throws Exception {
        List<Object> seen = new ArrayList<>();
        Button[] b = new Button[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("b");
            b[0].setOnAction(e -> seen.add(e.getSource()));
            VBox box = new VBox(b[0]);
            box.addEventHandler(ActionEvent.ACTION, e -> seen.add(e.getSource()));
            return box;
        }), 300, 100);
        fx(() -> b[0].fireEvent(new ActionEvent()));
        assertEquals(2, seen.size());
        assertEquals(b[0], seen.get(0), "each node sees itself as the source");
        close(s);
    }

    @Test
    void movesPostedFromTheDisplayThreadAreCoalescedAndKeepTheirOrder() throws Exception {
        List<String> seen = new ArrayList<>();
        Button[] b = new Button[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("a wide button");
            b[0].setPrefWidth(200);
            b[0].setOnMouseMoved(e -> seen.add("move " + (int) e.getSceneX()));
            b[0].setOnMousePressed(e -> seen.add("press " + (int) e.getSceneX()));
            return new VBox(b[0]);
        }), 300, 100);
        JXNativeNode n = node(s, b[0]);
        double y = n.getY() + 5;
        fx(() -> {
            // on the application thread the posted runnables wait until this one returns
            for (int x = 10; x <= 50; x += 10) {
                NativeEvents.post(s, JXInputEvent.pointer(JXInputEvent.Kind.MOVE, x, y, 0, 0, 0, false, Collections.<JXNativeNode>emptyList()));
            }
            NativeEvents.post(s, JXInputEvent.pointer(JXInputEvent.Kind.MOVE, 60, y, 0, 0, 0, false, Collections.<JXNativeNode>emptyList()));
            NativeEvents.post(s, JXInputEvent.pointer(JXInputEvent.Kind.PRESS, 70, y, JXInputEvent.BUTTON_PRIMARY, 0, 1, true,
                    Collections.<JXNativeNode>emptyList()));
            NativeEvents.post(s, JXInputEvent.pointer(JXInputEvent.Kind.RELEASE, 70, y, JXInputEvent.BUTTON_PRIMARY, 0, 1, false,
                    Collections.<JXNativeNode>emptyList()));
        });
        settle();
        assertEquals(Arrays.asList("move 60", "press 70"), seen, "only the latest move, and before the press");
        close(s);
    }

    @Test
    void resizeFocusAndExitEvents() throws Exception {
        List<String> seen = new ArrayList<>();
        Button[] b = new Button[1];
        NativeScene s = show(fx(() -> {
            b[0] = new Button("b");
            b[0].setOnMouseEntered(e -> seen.add("entered"));
            b[0].setOnMouseExited(e -> seen.add("exited"));
            return new VBox(b[0]);
        }), 300, 100);
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.RESIZE, 420, 260)));
        assertEquals(420.0, fx(() -> b[0].getScene().getWindow().getWidth()));
        assertEquals(260.0, fx(() -> b[0].getScene().getWindow().getHeight()));
        assertEquals(420.0, fx(() -> b[0].getScene().getWidth()));
        assertEquals(260.0, fx(() -> b[0].getScene().getHeight()));
        assertEquals(420, s.width);
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.FOCUS_GAINED, 0, 0)));
        assertTrue(fx(() -> b[0].getScene().getWindow().isFocused()));
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.FOCUS_LOST, 0, 0)));
        assertFalse(fx(() -> b[0].getScene().getWindow().isFocused()));
        JXNativeNode n = node(s, b[0]);
        move(s, n.getX() + 5, n.getY() + 5, false);
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.EXIT, 0, 0)));
        assertEquals(Arrays.asList("entered", "exited"), seen, "leaving the window exits the hovered nodes");
        close(s);
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.RESIZE, 10, 10)));
        assertEquals(420, s.width, "a hidden window ignores events");
    }

    @Test
    void theWheelOverAnOpenComboListScrollsIt() throws Exception {
        ComboBox<String>[] combo = new ComboBox[1];
        NativeScene s = show(fx(() -> {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                items.add("item " + i);
            }
            combo[0] = new ComboBox<>(FXCollections.observableArrayList(items));
            return new VBox(combo[0]);
        }), 300, 400);
        click(s, combo[0]);
        JXNativeNode n = node(s, combo[0]);
        double listY = n.getY() + n.getHeight() + 30;
        wheel(s, n.getX() + 20, listY, -1);
        assertEquals(3 * 23.0, fx(() -> NativeCells.number(model(combo[0]).state.get("popupScrollY"), 0)), 0.001);
        move(s, n.getX() + 20, n.getY() + n.getHeight() + 1 + 23 + 5, false);
        int hover = fx(() -> (Integer) model(combo[0]).state.get("popupHover"));
        assertEquals(1 + 3, hover, "the row under the pointer, counting the scrolled rows");
        close(s);
    }

    @Test
    void unknownKeyNamesAreUndefined() {
        assertEquals(javafx.scene.input.KeyCode.UNDEFINED, NativeEvents.code(null));
        assertEquals(javafx.scene.input.KeyCode.UNDEFINED, NativeEvents.code("NOT_A_KEY"));
        assertEquals(javafx.scene.input.KeyCode.ENTER, NativeEvents.code("ENTER"));
        assertEquals(KeyCode.ENTER.getName(), javafx.scene.input.KeyCode.ENTER.getName());
    }
}
