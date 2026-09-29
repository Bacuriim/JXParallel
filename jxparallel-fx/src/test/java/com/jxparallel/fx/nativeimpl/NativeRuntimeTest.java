package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.scene.Scene;
import com.jxparallel.fx.scene.control.Button;
import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.scene.control.TextField;
import com.jxparallel.fx.scene.control.Tooltip;
import com.jxparallel.fx.scene.layout.StackPane;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.fx.stage.Stage;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Windows, focus, geometry and lookup in native mode. */
class NativeRuntimeTest extends NativeTestSupport {

    @Test
    void stageLifecycleEventsAndShowingProperty() throws Exception {
        List<String> events = new ArrayList<>();
        Stage stage = fx(() -> {
            Stage st = new Stage();
            st.setTitle("Ciclo");
            st.setScene(new Scene(new VBox(new Label("x")), 300, 200));
            st.setOnShowing(e -> events.add("showing"));
            st.setOnShown(e -> events.add(e.getSource() == st ? "shown" : "wrong source"));
            st.setOnHiding(e -> events.add("hiding"));
            st.setOnHidden(e -> events.add("hidden"));
            st.showingProperty().addListener((o, a, b) -> events.add("showing=" + b));
            st.show();
            return st;
        });
        NativeScene s = fx(() -> NativeRuntime.sceneOf(model(stage)));
        assertTrue(fx(() -> stage.isShowing()));
        assertEquals(300.0, fx(() -> stage.getWidth()));
        assertEquals("Ciclo", fx(() -> s.window.getTitle()));
        fx(() -> stage.close());
        assertFalse(fx(() -> stage.isShowing()));
        assertEquals(java.util.Arrays.asList("showing", "showing=true", "shown", "hiding", "showing=false", "hidden"), events);
        assertTrue(((NativeSurface.Headless) s.window).disposed);
    }

    @Test
    void closeRequestCanBeVetoed() throws Exception {
        Stage stage = fx(() -> {
            Stage st = new Stage();
            st.setScene(new Scene(new VBox(), 100, 100));
            st.setOnCloseRequest(e -> e.consume());
            st.show();
            return st;
        });
        NativeScene s = fx(() -> NativeRuntime.sceneOf(model(stage)));
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.CLOSE_REQUEST, 0, 0)));
        assertTrue(fx(() -> stage.isShowing()), "the handler consumed the request");
        fx(() -> stage.setOnCloseRequest(null));
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.CLOSE_REQUEST, 0, 0)));
        assertFalse(fx(() -> stage.isShowing()));
    }

    @Test
    void stageWithoutSizeTakesItsRootsPreferredSize() throws Exception {
        Stage stage = fx(() -> {
            Stage st = new Stage();
            StackPane root = new StackPane(new Label("x"));
            root.setPrefSize(250, 130);
            st.setScene(new Scene(root));
            st.show();
            return st;
        });
        NativeScene s = fx(() -> NativeRuntime.sceneOf(model(stage)));
        assertEquals(250, s.width);
        assertEquals(130, s.height);
        close(s);
    }

    @Test
    void resizingTheWindowRelaysOutAndUpdatesWidthListeners() throws Exception {
        List<Double> widths = new ArrayList<>();
        VBox[] root = new VBox[1];
        NativeScene s = show(fx(() -> {
            root[0] = new VBox(new Label("x"));
            root[0].widthProperty().addListener((o, a, b) -> widths.add(b.doubleValue()));
            return root[0];
        }), 300, 200);
        fx(() -> NativeEvents.handle(s, JXInputEvent.window(JXInputEvent.Kind.RESIZE, 500, 250)));
        settle();
        assertEquals(500, node(s, root[0]).getWidth());
        assertEquals(500.0, fx(() -> root[0].getWidth()));
        assertTrue(widths.contains(500.0));
        close(s);
    }

    @Test
    void nodesFindTheirSceneAndLookupFindsById() throws Exception {
        Label[] label = new Label[1];
        VBox[] root = new VBox[1];
        Scene scene = fx(() -> {
            label[0] = new Label("x");
            label[0].setId("nome");
            label[0].getStyleClass().add("destaque");
            root[0] = new VBox(new VBox(label[0]));
            return new Scene(root[0], 100, 100);
        });
        assertSame(scene, fx(() -> label[0].getScene()), "before the stage is shown");
        assertSame(label[0], fx(() -> scene.lookup("#nome")));
        assertSame(label[0], fx(() -> root[0].lookup(".destaque")));
        assertNull(fx(() -> root[0].lookup("#outro")));
        assertEquals(1, (int) fx(() -> root[0].lookupAll("Label").size()));
    }

    @Test
    void requestFocusAndFocusedProperty() throws Exception {
        TextField[] a = new TextField[1];
        TextField[] b = new TextField[1];
        List<String> events = new ArrayList<>();
        NativeScene s = show(fx(() -> {
            a[0] = new TextField("a");
            b[0] = new TextField("b");
            b[0].focusedProperty().addListener((o, was, is) -> events.add("b focused=" + is));
            return new VBox(a[0], b[0]);
        }), 200, 100);
        fx(() -> b[0].requestFocus());
        assertTrue(fx(() -> b[0].isFocused()));
        assertFalse(fx(() -> a[0].isFocused()));
        assertSame(b[0], fx(() -> b[0].getScene().getFocusOwner()));
        fx(() -> a[0].requestFocus());
        assertEquals(java.util.Arrays.asList("b focused=true", "b focused=false"), events);
        close(s);
    }

    @Test
    void layoutBoundsAndLocalToSceneAfterLayout() throws Exception {
        Label[] label = new Label[1];
        NativeScene s = show(fx(() -> {
            label[0] = new Label("x");
            VBox box = new VBox(label[0]);
            box.setPadding(new com.jxparallel.fx.geometry.Insets(7, 0, 0, 11));
            return box;
        }), 200, 100);
        settle();
        assertEquals(11.0, fx(() -> label[0].localToScene(0, 0).getX()));
        assertEquals(7.0, fx(() -> label[0].localToScene(0, 0).getY()));
        JXNativeNode n = node(s, label[0]);
        assertEquals((double) n.getWidth(), fx(() -> label[0].getLayoutBounds().getWidth()));
        close(s);
    }

    @Test
    void tooltipAppearsAfterTheDelayAndHidesOnExit() throws Exception {
        Button[] button = new Button[1];
        NativeScene s = show(fx(() -> {
            button[0] = new Button("b");
            button[0].setTooltip(new Tooltip("Dica"));
            return new VBox(button[0], new Label("fora"));
        }), 200, 100);
        JXNativeNode n = node(s, button[0]);
        move(s, n.getX() + 3, n.getY() + 3, false);
        assertFalse(fx(() -> s.overlays.stream().anyMatch(o -> o.kind == NativeOverlay.Kind.TOOLTIP)), "not before the delay");
        Thread.sleep(NativeEvents.TOOLTIP_DELAY + 300);
        settle();
        assertEquals("Dica", fx(() -> s.overlays.stream().filter(o -> o.kind == NativeOverlay.Kind.TOOLTIP)
                .findFirst().map(o -> o.content).orElse(null)));
        fx(() -> NativeEvents.handle(s, JXInputEvent.pointer(JXInputEvent.Kind.EXIT, 0, 0, 0, 0, 0, false, java.util.Collections.emptyList())));
        assertTrue(fx(() -> s.overlays.isEmpty()));
        close(s);
    }

    @Test
    void changingTheSceneRootReplacesTheTree() throws Exception {
        Stage stage = fx(() -> {
            Stage st = new Stage();
            st.setScene(new Scene(new VBox(new Label("antes")), 200, 100));
            st.show();
            return st;
        });
        NativeScene s = fx(() -> NativeRuntime.sceneOf(model(stage)));
        Label after = fx(() -> new Label("depois"));
        fx(() -> stage.getScene().setRoot(new StackPane(after)));
        settle();
        assertNotNull(node(s, after));
        close(s);
    }

    @Test
    void handlerExceptionsAreReportedAndDoNotStopTheWindow() throws Exception {
        Button[] b = new Button[1];
        AtomicReference<Throwable> reported = new AtomicReference<>();
        NativeScene s = show(fx(() -> {
            b[0] = new Button("b");
            b[0].setOnAction(e -> {
                throw new IllegalStateException("boom");
            });
            return new VBox(b[0]);
        }), 200, 100);
        Thread.UncaughtExceptionHandler before = fx(() -> Thread.currentThread().getUncaughtExceptionHandler());
        fx(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> reported.set(e)));
        try {
            click(s, b[0]);
        } finally {
            fx(() -> Thread.currentThread().setUncaughtExceptionHandler(before));
        }
        assertEquals("boom", reported.get().getMessage());
        assertTrue(fx(() -> s.showing));
        close(s);
    }

    @Test
    void modalityDecidesWhichWindowsAreBlocked() throws Exception {
        Stage main = fx(() -> {
            Stage st = new Stage();
            st.setScene(new Scene(new VBox(), 100, 100));
            st.show();
            return st;
        });
        Stage modal = fx(() -> {
            Stage st = new Stage();
            st.initModality(com.jxparallel.fx.stage.Modality.WINDOW_MODAL);
            st.initOwner(main);
            st.setScene(new Scene(new VBox(), 100, 100));
            st.show();
            return st;
        });
        NativeScene mainScene = fx(() -> NativeRuntime.sceneOf(model(main)));
        NativeScene modalScene = fx(() -> NativeRuntime.sceneOf(model(modal)));
        assertTrue(fx(() -> NativeRuntime.blocked(mainScene)), "the owner is blocked by its window-modal child");
        assertFalse(fx(() -> NativeRuntime.blocked(modalScene)));
        close(modalScene);
        assertFalse(fx(() -> NativeRuntime.blocked(mainScene)));
        close(mainScene);
    }
    @Test
    void shownScrollAndTitledPanesGetASkinWithTheViewRect() throws Exception {
        com.jxparallel.fx.scene.control.ScrollPane scroll = fx(() -> new com.jxparallel.fx.scene.control.ScrollPane(new Label("in")));
        com.jxparallel.fx.scene.control.TitledPane titled = fx(() -> new com.jxparallel.fx.scene.control.TitledPane("T", new Label("x")));
        List<Object> skins = new ArrayList<>();
        fx(() -> scroll.skinProperty().addListener((o, a, b) -> skins.add(b)));
        assertNull(fx(() -> scroll.getSkin()), "no skin before the pane is shown, like JavaFX");
        NativeScene scene = show(new VBox(scroll, titled), 300, 200);
        settle();
        Object skin = fx(() -> scroll.getSkin());
        assertTrue(skin instanceof com.jxparallel.fx.sun.scene.control.skin.ScrollPaneSkin);
        assertEquals(1, skins.size(), "skinProperty listeners hear it once");
        assertSame(skin, skins.get(0));
        java.lang.reflect.Field viewRect = com.jxparallel.fx.sun.scene.control.skin.ScrollPaneSkin.class.getDeclaredField("viewRect");
        viewRect.setAccessible(true);
        StackPane pane = (StackPane) viewRect.get(skin);
        assertNotNull(pane, "what DeviceConfig reads by reflection to turn caching off");
        fx(() -> pane.setCache(false));
        assertSame(scroll, fx(() -> ((com.jxparallel.fx.sun.scene.control.skin.ScrollPaneSkin) skin).getSkinnable()));
        assertSame(scroll, fx(() -> ((com.jxparallel.fx.sun.scene.control.skin.ScrollPaneSkin) skin).getNode()));
        fx(() -> ((com.jxparallel.fx.sun.scene.control.skin.ScrollPaneSkin) skin).dispose());
        assertTrue(fx(() -> titled.getSkin()) instanceof com.jxparallel.fx.sun.scene.control.skin.TitledPaneSkin);
        close(scene);
    }

    @Test
    void javaFxDrawsNothingInNativeModeSoItUsesItsSoftwarePipeline() {
        assertTrue(com.jxparallel.fx.Fx.NATIVE);
        assertEquals("sw", System.getProperty("prism.order"), "no Direct3D device beside the native renderer's");
    }
}
