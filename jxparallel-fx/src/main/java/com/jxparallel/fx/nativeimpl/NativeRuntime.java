package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.native2d.JXNativeNode;
import com.jxparallel.ui.native2d.JXWindow;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

/**
 * The native runtime: native windows for Stages ({@link NativeScene}), and the parts of the node
 * and window API that are more than bean accesses (showing and hiding, focus, geometry, lookup,
 * snapshots, tooltips, context menus, tab and page selection). Everything runs on the JavaFX
 * application thread, which keeps running (without JavaFX windows) as the event loop, so
 * Platform.runLater, Task and nested event loops behave as they do on JavaFX.
 */
public final class NativeRuntime {
    static final List<NativeScene> SCENES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean PULSE = new AtomicBoolean();
    /** Scene models created so far, so a node can find its scene before its stage is shown. */
    private static final Map<NativeModel, Boolean> SCENE_MODELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static javafx.stage.Stage eventSource;

    private NativeRuntime() {
    }

    static void register() {
        Native.onChange(model -> pulse());
        Native.register("Stage.show()", (self, m, a) -> {
            show(m);
            return null;
        });
        Native.register("Window.show()", (self, m, a) -> {
            show(m);
            return null;
        });
        Native.register("Stage.showAndWait()", (self, m, a) -> {
            showAndWait(m);
            return null;
        });
        Native.register("Stage.close()", (self, m, a) -> {
            hide(m);
            return null;
        });
        Native.register("Window.hide()", (self, m, a) -> {
            hide(m);
            return null;
        });
        Native.register("Window.isShowing()", (self, m, a) -> sceneOf(m) != null);
        Native.register("Window.showingProperty()", (self, m, a) -> Fx.jx(Native.property(m, "showing", boolean.class)));
        Native.register("Stage.initModality(Modality)", (self, m, a) -> set(m, "modality", a[0]));
        Native.register("Stage.initOwner(Window)", (self, m, a) -> set(m, "owner", a[0]));
        Native.register("Stage.initStyle(StageStyle)", (self, m, a) -> set(m, "style", a[0]));
        Native.register("Stage.setScene(Scene)", (self, m, a) -> setScene(m, a[0]));
        Native.register("Window.setScene(Scene)", (self, m, a) -> setScene(m, a[0]));
        Native.register("Window.sizeToScene()", (self, m, a) -> {
            NativeScene s = sceneOf(m);
            if (s != null) {
                double[] size = preferredSize(s.rootModel());
                s.window.setSize((int) Math.ceil(size[0]), (int) Math.ceil(size[1]));
            }
            return null;
        });
        Native.register("Window.centerOnScreen()", (self, m, a) -> null);
        Native.register("Stage.toFront()", (self, m, a) -> null);
        Native.register("Stage.toBack()", (self, m, a) -> null);
        Native.register("Window.requestFocus()", (self, m, a) -> null);
        Native.register("Scene.getFocusOwner()", (self, m, a) -> {
            NativeScene s = sceneForSceneModel(m);
            return s == null || s.focus == null ? null : Fx.jx(s.focus);
        });
        Native.register("Scene.lookup(String)", (self, m, a) -> Fx.jx(lookup(NativeElements.model(Native.value(m, "root")), (String) a[0])));
        Native.register("Node.lookup(String)", (self, m, a) -> Fx.jx(lookup(m, (String) a[0])));
        Native.register("Parent.lookup(String)", (self, m, a) -> Fx.jx(lookup(m, (String) a[0])));
        Native.register("Parent.getChildrenUnmodifiable()", (self, m, a) ->
                Fx.jx(javafx.collections.FXCollections.unmodifiableObservableList(m.children)));
        Native.register("Node.getScene()", (self, m, a) -> Fx.jx(sceneModelOf(m)));
        Native.register("Node.requestFocus()", (self, m, a) -> {
            NativeScene s = windowOf(m);
            if (s != null && focusable(m)) {
                setFocus(s, m);
            } else {
                m.state.put("wantsFocus", true);
            }
            return null;
        });
        Native.register("Node.isFocused()", (self, m, a) -> {
            NativeScene s = windowOf(m);
            return s != null && s.focus == m;
        });
        Native.register("Node.getLayoutBounds()", (self, m, a) -> Fx.jx(new BoundingBox(0, 0, width(m), height(m))));
        Native.register("Node.getBoundsInLocal()", (self, m, a) -> Fx.jx(new BoundingBox(0, 0, width(m), height(m))));
        Native.register("Node.getBoundsInParent()", (self, m, a) -> {
            double[] xy = position(m);
            double[] parent = position(NativeCss.parentOf(m));
            return Fx.jx(new BoundingBox(xy[0] - parent[0], xy[1] - parent[1], width(m), height(m)));
        });
        Native.register("Node.localToScene(double,double)", (self, m, a) -> {
            double[] xy = position(m);
            return Fx.jx(new Point2D(xy[0] + (Double) a[0], xy[1] + (Double) a[1]));
        });
        Native.register("Node.sceneToLocal(double,double)", (self, m, a) -> {
            double[] xy = position(m);
            return Fx.jx(new Point2D((Double) a[0] - xy[0], (Double) a[1] - xy[1]));
        });
        Native.register("Node.localToScreen(double,double)", (self, m, a) -> {
            double[] xy = position(m);
            return Fx.jx(new Point2D(xy[0] + (Double) a[0], xy[1] + (Double) a[1]));
        });
        Native.register("Region.getWidth()", (self, m, a) -> width(m));
        Native.register("Region.getHeight()", (self, m, a) -> height(m));
        Native.register("Region.widthProperty()", (self, m, a) -> Fx.jx(geometryProperty(m, "width")));
        Native.register("Region.heightProperty()", (self, m, a) -> Fx.jx(geometryProperty(m, "height")));
        for (String noop : new String[]{"Parent.requestLayout()", "Parent.layout()", "Node.applyCss()", "Node.autosize()",
                "Node.toFront()", "Node.toBack()"}) {
            Native.register(noop, (self, m, a) -> {
                pulse();
                return null;
            });
        }
        Native.register("Node.snapshot(SnapshotParameters,WritableImage)", (self, m, a) -> Fx.jx(snapshot(m)));
        Native.register("Node.startDragAndDrop(TransferMode[])", (self, m, a) -> Fx.jx(NativeDragAndDrop.start(m, a[0])));
        Native.register("Tooltip.install(Node,Tooltip)", (self, m, a) -> {
            NativeModel node = NativeElements.model(a[0]);
            if (node != null) {
                node.state.put("tooltip", Fx.fx(a[1]));
            }
            return null;
        });
        Native.register("Tooltip.uninstall(Node,Tooltip)", (self, m, a) -> {
            NativeModel node = NativeElements.model(a[0]);
            if (node != null) {
                node.state.remove("tooltip");
            }
            return null;
        });
        Native.register("ContextMenu.show(Node,double,double)", (self, m, a) -> {
            NativeModel owner = NativeElements.model(a[0]);
            NativeScene s = owner == null ? null : windowOf(owner);
            if (s != null) {
                NativeEvents.showMenu(s, m, s.pointerX, s.pointerY);
            }
            return null;
        });
        Native.register("ContextMenu.hide()", (self, m, a) -> {
            for (NativeScene s : SCENES) {
                s.overlays.removeIf(o -> o.content == m);
            }
            pulse();
            return null;
        });
        Native.register("Cell.updateItem(Object,boolean)", (self, m, a) -> {
            Native.property(m, "item", Object.class).setValue(Fx.fx(a[0]));
            Native.property(m, "empty", boolean.class).setValue((Boolean) a[1]);
            return null;
        });
        Native.register("IndexedCell.updateIndex(int)", (self, m, a) -> {
            Native.property(m, "index", int.class).setValue((Integer) a[0]);
            return null;
        });
        Native.register("Cell.isEmpty()", (self, m, a) -> {
            Object empty = Native.value(m, "empty");
            return empty == null || Boolean.TRUE.equals(empty); // a cell is empty until it gets an item
        });
        Native.register("TableView.getSelectionModel()", (self, m, a) -> Fx.jx(selectionOf(m)));
        Native.register("ListView.scrollTo(int)", (self, m, a) -> {
            scrollTo(m, (Integer) a[0], NativeCells.LIST_CELL);
            return null;
        });
        Native.register("TableView.scrollTo(int)", (self, m, a) -> {
            scrollTo(m, (Integer) a[0], NativeCells.TABLE_ROW);
            return null;
        });
        Native.register("MapValueFactory.call(CellDataFeatures)", (self, m, a) -> {
            NativeModel features = NativeElements.model(a[0]);
            Object item = features == null ? null : Native.value(features, "value");
            Object key = Native.value(m, "key");
            Object value = item instanceof Map ? ((Map<?, ?>) item).get(key) : null;
            return Fx.jx(value instanceof javafx.beans.value.ObservableValue ? value : new javafx.beans.property.ReadOnlyObjectWrapper<>(value));
        });
        Native.register("ComboBoxBase.show()", (self, m, a) -> {
            NativeScene s = windowOf(m);
            if (s != null) {
                NativeEvents.openPopup(s, m);
            }
            return null;
        });
        Native.register("ComboBoxBase.hide()", (self, m, a) -> {
            NativeScene s = windowOf(m);
            if (s != null) {
                NativeEvents.closePopups(s);
            }
            return null;
        });
        Native.register("ComboBoxBase.isShowing()", (self, m, a) -> {
            NativeScene s = windowOf(m);
            return s != null && popupOwner(s) == m;
        });
        Native.register("ChoiceBox.show()", (self, m, a) -> {
            NativeScene s = windowOf(m);
            if (s != null) {
                NativeEvents.openPopup(s, m);
            }
            return null;
        });
        Native.register("ChoiceBox.hide()", (self, m, a) -> {
            NativeScene s = windowOf(m);
            if (s != null) {
                NativeEvents.closePopups(s);
            }
            return null;
        });
        Native.register("TextInputControl.selectAll()", (self, m, a) -> {
            NativeText.selectAll(m);
            return null;
        });
        Native.register("TextInputControl.positionCaret(int)", (self, m, a) -> {
            NativeText.moveCaret(m, (Integer) a[0], false);
            return null;
        });
        Native.register("TextInputControl.end()", (self, m, a) -> {
            NativeText.moveCaret(m, NativeElements.string(m, "text").length(), false);
            return null;
        });
        Native.register("TextInputControl.home()", (self, m, a) -> {
            NativeText.moveCaret(m, 0, false);
            return null;
        });
        Native.register("TextInputControl.getCaretPosition()", (self, m, a) -> NativeText.caret(m));
        Native.register("TextInputControl.getSelectedText()", (self, m, a) -> NativeText.selectedText(m));
        Native.register("TextInputControl.appendText(String)", (self, m, a) -> {
            String text = NativeElements.string(m, "text");
            Native.property(m, "text", String.class).setValue(text + a[0]);
            return null;
        });
        Native.register("TextInputControl.insertText(int,String)", (self, m, a) -> {
            String text = NativeElements.string(m, "text");
            int at = Math.max(0, Math.min((Integer) a[0], text.length()));
            Native.property(m, "text", String.class).setValue(text.substring(0, at) + a[1] + text.substring(at));
            return null;
        });
        Native.register("TextInputControl.copy()", (self, m, a) -> {
            NativeText.copy(m);
            return null;
        });
        Native.register("TextInputControl.cut()", (self, m, a) -> {
            NativeText.cut(m);
            return null;
        });
        Native.register("TextInputControl.paste()", (self, m, a) -> {
            NativeText.paste(m);
            return null;
        });
        Native.register("TextInputControl.deselect()", (self, m, a) -> {
            NativeText.moveCaret(m, NativeText.caret(m), false);
            return null;
        });
        Native.register("TextInputControl.selectRange(int,int)", (self, m, a) -> {
            m.state.put("anchor", a[0]);
            m.state.put("caret", a[1]);
            pulse();
            return null;
        });
        Native.register("ButtonBase.fire()", (self, m, a) -> {
            NativeBehavior.fire(m);
            return null;
        });
        Native.register("Button.fire()", (self, m, a) -> {
            NativeBehavior.fire(m);
            return null;
        });
        Native.register("CheckBox.fire()", (self, m, a) -> {
            NativeBehavior.toggle(m);
            return null;
        });
        Native.register("ToggleButton.fire()", (self, m, a) -> {
            NativeBehavior.toggle(m);
            return null;
        });
        Native.register("RadioButton.fire()", (self, m, a) -> {
            NativeBehavior.toggle(m);
            return null;
        });
        Native.register("Hyperlink.fire()", (self, m, a) -> {
            NativeBehavior.fire(m);
            return null;
        });
        Native.register("RadioMenuItem.setSelected(boolean)", (self, m, a) -> {
            NativeBehavior.setSelected(m, (Boolean) a[0]);
            return null;
        });
        Native.register("ToggleButton.setSelected(boolean)", (self, m, a) -> {
            NativeBehavior.setSelected(m, (Boolean) a[0]);
            return null;
        });
        Native.register("ComboBoxBase.setValue(Object)", (self, m, a) -> {
            Object before = Native.value(m, "value");
            Object value = Fx.fx(a[0]);
            Native.property(m, "value", Object.class).setValue(value);
            if (before != value && (before == null || !before.equals(value))) {
                NativeEvents.fireAction(m); // ComboBox fires ACTION when its value changes, whoever changed it
            }
            return null;
        });
        NativeEvents.register();
        NativeDialogs.register();
    }

    // ---- text metrics ------------------------------------------------------------------------

    private static final java.util.Map<String, float[]> LINE_METRICS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean metricsInstalled;

    /**
     * Makes native text lines as tall as JavaFX's (hinted DirectWrite metrics of the System font),
     * measured by the running JavaFX on the application thread. Common sizes are measured up front,
     * so the display thread (which paints while holding the scene lock) never waits for it; other
     * sizes asked off the application thread fall back to the scaled font metrics.
     */
    static void installLineMetrics() {
        if (metricsInstalled || !Platform.isFxApplicationThread()) {
            return;
        }
        metricsInstalled = true;
        for (int size = 6; size <= 48; size++) {
            measureLine(size, false);
            measureLine(size, true);
        }
        com.jxparallel.ui.text.JXTextEngine.setLineMetrics(new com.jxparallel.ui.text.JXTextEngine.LineMetrics() {
            @Override
            public float[] measure(float size, boolean bold) {
                float[] known = LINE_METRICS.get(size + (bold ? "b" : ""));
                if (known != null || !Platform.isFxApplicationThread()) {
                    return known;
                }
                return measureLine(size, bold);
            }

            @Override
            public float width(String text, float size, boolean bold) {
                return Platform.isFxApplicationThread() ? measureWidth(text, size, bold) : Float.NaN;
            }
        });
    }

    private static javafx.scene.text.Text widthProbe;

    /** Width of text in the System font as JavaFX lays it out (one reused text node in a scene). */
    static float measureWidth(String value, float size, boolean bold) {
        if (widthProbe == null) {
            widthProbe = new javafx.scene.text.Text();
            new javafx.scene.Scene(new javafx.scene.Group(widthProbe));
        }
        widthProbe.setFont(javafx.scene.text.Font.font("System",
                bold ? javafx.scene.text.FontWeight.BOLD : javafx.scene.text.FontWeight.NORMAL, size));
        widthProbe.setText(value);
        return (float) widthProbe.getLayoutBounds().getWidth();
    }

    /** A styled Label in a scene, as JavaFX sizes it (out of a scene, text reports unhinted metrics). */
    static float[] measureLine(float size, boolean bold) {
        javafx.scene.text.Font font = javafx.scene.text.Font.font("System",
                bold ? javafx.scene.text.FontWeight.BOLD : javafx.scene.text.FontWeight.NORMAL, size);
        javafx.scene.control.Label label = new javafx.scene.control.Label("Hg");
        label.setFont(font);
        javafx.scene.text.Text text = new javafx.scene.text.Text("Hg");
        text.setFont(font);
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(label, text);
        new javafx.scene.Scene(root);
        root.applyCss();
        root.layout();
        float[] metrics = {(float) text.getBaselineOffset(), (float) Math.ceil(label.prefHeight(-1))};
        LINE_METRICS.put(size + (bold ? "b" : ""), metrics);
        return metrics;
    }

    // ---- scheduling --------------------------------------------------------------------------

    /** Coalesces model changes into one render of every window, on the application thread. */
    static void pulse() {
        if (SCENES.isEmpty() || !PULSE.compareAndSet(false, true)) {
            return;
        }
        Runnable pulse = () -> {
            PULSE.set(false);
            for (NativeScene s : SCENES) {
                try {
                    s.render();
                } catch (RuntimeException e) {
                    report(e);
                }
            }
        };
        try {
            Platform.runLater(pulse);
        } catch (IllegalStateException toolkitGone) {
            PULSE.set(false);
        }
    }

    static void report(Throwable e) {
        Thread current = Thread.currentThread();
        Thread.UncaughtExceptionHandler handler = current.getUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(current, e);
        } else {
            e.printStackTrace();
        }
    }

    // ---- stages ------------------------------------------------------------------------------

    static void sceneCreated(NativeModel scene) {
        SCENE_MODELS.put(scene, Boolean.TRUE);
    }

    private static Object set(NativeModel m, String name, Object value) {
        Native.property(m, name, Object.class).setValue(Fx.fx(value));
        return null;
    }

    private static Object setScene(NativeModel stage, Object jxScene) {
        NativeModel scene = NativeElements.model(jxScene);
        Native.property(stage, "scene", Object.class).setValue(scene);
        if (scene != null) {
            Native.property(scene, "window", Object.class).setValue(stage);
            sceneCreated(scene);
        }
        pulse();
        return null;
    }

    static NativeScene sceneOf(NativeModel stage) {
        for (NativeScene s : SCENES) {
            if (s.stage == stage) {
                return s;
            }
        }
        return null;
    }

    private static NativeScene sceneForSceneModel(NativeModel scene) {
        for (NativeScene s : SCENES) {
            if (s.sceneModel() == scene) {
                return s;
            }
        }
        return null;
    }

    /** The shown window a node is in, or null. */
    static NativeScene windowOf(NativeModel node) {
        NativeModel root = rootOf(node);
        for (NativeScene s : SCENES) {
            if (s.rootModel() == root || s.nodes.containsKey(node)) {
                return s;
            }
        }
        return null;
    }

    private static NativeModel rootOf(NativeModel node) {
        NativeModel m = node;
        for (int guard = 0; guard < 10000; guard++) {
            NativeModel parent = NativeCss.parentOf(m);
            if (parent == null) {
                return m;
            }
            m = parent;
        }
        return m;
    }

    /** The scene model of a node: of the window showing it, or any scene whose root is its root. */
    static NativeModel sceneModelOf(NativeModel node) {
        NativeScene s = windowOf(node);
        if (s != null) {
            return s.sceneModel();
        }
        NativeModel root = rootOf(node);
        synchronized (SCENE_MODELS) {
            for (NativeModel scene : SCENE_MODELS.keySet()) {
                if (NativeElements.model(Native.value(scene, "root")) == root) {
                    return scene;
                }
            }
        }
        return null;
    }

    static void show(NativeModel stage) {
        if (sceneOf(stage) != null) {
            return;
        }
        NativeModel scene = NativeElements.model(Native.value(stage, "scene"));
        if (scene != null) {
            Native.property(scene, "window", Object.class).setValue(stage);
            sceneCreated(scene);
        }
        NativeModel root = scene == null ? null : NativeElements.model(Native.value(scene, "root"));
        double[] pref = preferredSize(root);
        double w = explicit(stage, "width", explicit(scene, "width", pref[0]));
        double h = explicit(stage, "height", explicit(scene, "height", pref[1]));
        NativeSurface window = NativeSurface.create(NativeElements.string(stage, "title"));
        window.setSize((int) Math.ceil(Math.max(w, 1)), (int) Math.ceil(Math.max(h, 1)));
        window.setResizable(!Boolean.FALSE.equals(Native.value(stage, "resizable")));
        Object style = Native.value(stage, "style");
        if (style == StageStyle.UNDECORATED || style == StageStyle.TRANSPARENT) {
            window.setDecorated(false);
        }
        if (Boolean.TRUE.equals(Native.value(stage, "alwaysOnTop"))) {
            window.setFloating(true);
        }
        if (Boolean.TRUE.equals(stage.state.get("noFocus"))) {
            window.setFocusOnShow(false);
        }
        double minW = NativeElements.number(stage, "minWidth", -1);
        double minH = NativeElements.number(stage, "minHeight", -1);
        if (minW > 0 || minH > 0) {
            window.setMinSize((int) minW, (int) minH);
        }
        if (stage.values.containsKey("x") && stage.values.containsKey("y")
                && !Double.isNaN(NativeElements.number(stage, "x", Double.NaN)) && !Double.isNaN(NativeElements.number(stage, "y", Double.NaN))) {
            window.setPosition((int) NativeElements.number(stage, "x", 0), (int) NativeElements.number(stage, "y", 0));
        }
        NativeScene s = new NativeScene(stage, window);
        s.width = (int) Math.ceil(w);
        s.height = (int) Math.ceil(h);
        s.showing = true;
        SCENES.add(s);
        window.setInputListener(event -> NativeEvents.post(s, event));
        fireWindowEvent(stage, WindowEvent.WINDOW_SHOWING, "onShowing");
        Native.property(stage, "showing", boolean.class).setValue(true);
        Native.property(stage, "width", double.class).setValue((double) s.width);
        Native.property(stage, "height", double.class).setValue((double) s.height);
        s.render();
        window.open();
        focusFirst(s);
        s.render();
        fireWindowEvent(stage, WindowEvent.WINDOW_SHOWN, "onShown");
    }

    private static double explicit(NativeModel m, String name, double fallback) {
        if (m == null) {
            return fallback;
        }
        double v = NativeElements.number(m, name, -1);
        return v > 0 && !Double.isNaN(v) ? v : fallback;
    }

    /** The preferred size of a scene root, as Scene uses when it has no size. */
    static double[] preferredSize(NativeModel root) {
        if (root == null) {
            return new double[] {320, 240};
        }
        JXElement element = NativeElements.toElement(root, new NativeElements.Context(null));
        JXNativeNode node = JXNativeNode.createBackendNode(element);
        return new double[] {Math.ceil(node.getPrefWidth()), Math.ceil(node.getPrefHeight())};
    }

    static void showAndWait(NativeModel stage) {
        show(stage);
        NativeScene s = sceneOf(stage);
        if (s == null) {
            return;
        }
        Object key = new Object();
        s.nestedKey = key;
        NestedLoop.enter(key);
    }

    static void hide(NativeModel stage) {
        NativeScene s = sceneOf(stage);
        if (s == null) {
            return;
        }
        fireWindowEvent(stage, WindowEvent.WINDOW_HIDING, "onHiding");
        s.showing = false;
        SCENES.remove(s);
        s.window.dispose();
        Native.property(stage, "showing", boolean.class).setValue(false);
        fireWindowEvent(stage, WindowEvent.WINDOW_HIDDEN, "onHidden");
        if (s.nestedKey != null) {
            NestedLoop.exit(s.nestedKey);
            s.nestedKey = null;
        }
        if (SCENES.isEmpty() && Platform.isImplicitExit() && !NativeDialogs.notificationsShowing()) {
            Platform.exit();
        }
    }

    /** Delivers a window event to the stage's handler; true if it was consumed. */
    static boolean fireWindowEvent(NativeModel stage, javafx.event.EventType<WindowEvent> type, String handler) {
        Object h = Native.value(stage, handler);
        if (!(h instanceof EventHandler)) {
            return false;
        }
        // the event names the native stage, so getSource() is the application's stage
        WindowEvent event = (WindowEvent) new WindowEvent(eventSource(), type).copyFor(stage, stage);
        try {
            @SuppressWarnings("unchecked")
            EventHandler<WindowEvent> eh = (EventHandler<WindowEvent>) h;
            eh.handle(event);
        } catch (RuntimeException e) {
            report(e);
        }
        return event.isConsumed();
    }

    /** WindowEvent needs a JavaFX window as its source; a hidden stage stands in for the native one. */
    private static javafx.stage.Stage eventSource() {
        if (eventSource == null) {
            eventSource = new javafx.stage.Stage();
        }
        return eventSource;
    }

    /** True while a modal window other than {@code s} (and not owned by it) is showing. */
    static boolean blocked(NativeScene s) {
        for (NativeScene other : SCENES) {
            if (other == s) {
                continue;
            }
            Object modality = Native.value(other.stage, "modality");
            if (modality == Modality.APPLICATION_MODAL) {
                return !ownedBy(s.stage, other.stage);
            }
            if (modality == Modality.WINDOW_MODAL && ownedBy(other.stage, s.stage)) {
                return true;
            }
        }
        return false;
    }

    /** True if {@code owner} is {@code window} or one of its owners. */
    private static boolean ownedBy(NativeModel window, NativeModel owner) {
        for (NativeModel w = window; w != null; w = NativeElements.model(Native.value(w, "owner"))) {
            if (w == owner) {
                return true;
            }
        }
        return false;
    }

    // ---- focus -------------------------------------------------------------------------------

    static final java.util.Set<String> FOCUSABLE = new java.util.HashSet<>(java.util.Arrays.asList(
            "Button", "ToggleButton", "CheckBox", "RadioButton", "Hyperlink", "TextField", "PasswordField",
            "CustomTextField", "CustomPasswordField", "TextArea", "ComboBox", "ChoiceBox", "Spinner", "DatePicker",
            "Slider", "ListView", "TableView", "TabPane", "TitledPane", "Pagination", "ColorPicker", "MenuButton"));

    /** Takes part in focus traversal: a focusable control, visible, enabled, not opted out. */
    static boolean focusable(NativeModel m) {
        if (!FOCUSABLE.contains(m.type) && !Boolean.TRUE.equals(m.values.containsKey("focusTraversable")
                && Boolean.TRUE.equals(Native.value(m, "focusTraversable")))) {
            return false;
        }
        if (m.values.containsKey("focusTraversable") && Boolean.FALSE.equals(Native.value(m, "focusTraversable"))) {
            return false;
        }
        return !disabled(m) && !Boolean.FALSE.equals(Native.value(m, "visible"));
    }

    /** Disabled itself or through an ancestor, like Node.isDisabled. */
    static boolean disabled(NativeModel m) {
        for (NativeModel c = m; c != null; c = NativeCss.parentOf(c)) {
            if (Boolean.TRUE.equals(Native.value(c, "disable"))) {
                return true;
            }
        }
        return false;
    }

    static void setFocus(NativeScene s, NativeModel m) {
        NativeModel before = s.focus;
        if (before == m) {
            return;
        }
        s.focus = m;
        if (before != null) {
            Native.property(before, "focused", boolean.class).setValue(false);
            onFocusLost(s, before);
        }
        if (m != null) {
            Native.property(m, "focused", boolean.class).setValue(true);
            if (NativeText.isText(m) && !m.state.containsKey("caret")) {
                NativeText.moveCaret(m, NativeElements.string(m, "text").length(), false);
            }
        }
        pulse();
    }

    /** Text fields commit on focus loss; open popups of the control close. */
    private static void onFocusLost(NativeScene s, NativeModel before) {
        if (NativeText.isText(before)) {
            NativeText.commit(before);
        }
        NativeModel editorOwner = (NativeModel) before.state.get("editor");
        if (editorOwner != null) {
            NativeText.commit(editorOwner);
        }
        if (popupOwner(s) == before) {
            NativeEvents.closePopups(s);
        }
    }

    private static void focusFirst(NativeScene s) {
        for (NativeModel m : s.focusOrder) {
            if (Boolean.TRUE.equals(m.state.remove("wantsFocus"))) {
                setFocus(s, m);
                return;
            }
        }
        for (NativeModel m : s.focusOrder) {
            if (focusable(m)) {
                setFocus(s, m);
                return;
            }
        }
    }

    /** Moves the focus to the next (or previous) node in traversal order. */
    static void traverse(NativeScene s, boolean forward) {
        List<NativeModel> order = new ArrayList<>();
        for (NativeModel m : s.focusOrder) {
            if (focusable(m)) {
                order.add(m);
            }
        }
        if (order.isEmpty()) {
            return;
        }
        int at = order.indexOf(s.focus);
        int next = at < 0 ? 0 : (at + (forward ? 1 : -1) + order.size()) % order.size();
        setFocus(s, order.get(next));
    }

    // ---- geometry ----------------------------------------------------------------------------

    /** After a layout: sizes the application can read or listen to. */
    static void updateGeometry(NativeScene s) {
        for (Map.Entry<NativeModel, JXNativeNode> e : s.nodes.entrySet()) {
            NativeModel m = e.getKey();
            JXNativeNode node = e.getValue();
            putIfChanged(m, "x", node.getX());
            putIfChanged(m, "y", node.getY());
            setIfListened(m, "width", node.getWidth());
            setIfListened(m, "height", node.getHeight());
        }
        String title = NativeElements.string(s.stage, "title");
        if (!title.equals(s.window.getTitle())) {
            s.window.setTitle(title);
        }
    }

    /** Stores a laid-out coordinate, boxing only when it moved (every node, every frame). */
    private static void putIfChanged(NativeModel m, String name, double value) {
        Object old = m.state.get(name);
        if (!(old instanceof Double) || (Double) old != value) {
            m.state.put(name, boxed(value));
        }
    }

    /** Laid-out coordinates are whole pixels: rows moving while scrolling reuse these boxes. */
    private static final Double[] PIXELS = new Double[4096];

    static {
        for (int i = 0; i < PIXELS.length; i++) {
            PIXELS[i] = (double) i;
        }
    }

    static Double boxed(double value) {
        int i = (int) value;
        return i == value && i >= 0 && i < PIXELS.length ? PIXELS[i] : Double.valueOf(value);
    }

    private static void setIfListened(NativeModel m, String name, double value) {
        putIfChanged(m, name, value);
        Object property = m.values.get(name);
        if (property instanceof javafx.beans.property.Property) {
            @SuppressWarnings("unchecked")
            javafx.beans.property.Property<Object> p = (javafx.beans.property.Property<Object>) property;
            Object old = p.getValue();
            if (!(old instanceof Number) || ((Number) old).doubleValue() != value) {
                p.setValue(value);
            }
        }
    }

    private static javafx.beans.property.Property<Object> geometryProperty(NativeModel m, String name) {
        javafx.beans.property.Property<Object> p = Native.property(m, name, double.class);
        Object v = m.state.get(name);
        if (v instanceof Number) {
            p.setValue(((Number) v).doubleValue());
        }
        return p;
    }

    static double width(NativeModel m) {
        return NativeCells.number(m.state.get("width"), NativeElements.number(m, "prefWidth", 0));
    }

    static double height(NativeModel m) {
        return NativeCells.number(m.state.get("height"), NativeElements.number(m, "prefHeight", 0));
    }

    /** Window coordinates of a node's top left corner. */
    static double[] position(NativeModel m) {
        if (m == null) {
            return new double[] {0, 0};
        }
        return new double[] {NativeCells.number(m.state.get("x"), 0), NativeCells.number(m.state.get("y"), 0)};
    }

    private static void scrollTo(NativeModel m, int index, double itemHeight) {
        double cell = NativeCells.number(m.state.get("cellHeight"), itemHeight);
        m.state.put("scrollY", Math.max(0, index * cell));
        pulse();
    }

    /** Pixels of a node as it is drawn now (NanoVG in a hidden window), for snapshots and drag views. */
    private static javafx.scene.image.WritableImage snapshot(NativeModel m) {
        int w = (int) Math.max(1, Math.ceil(width(m)));
        int h = (int) Math.max(1, Math.ceil(height(m)));
        javafx.scene.image.WritableImage image = new javafx.scene.image.WritableImage(w, h);
        try {
            NativeScene s = windowOf(m);
            JXElement element = NativeElements.toElement(m, new NativeElements.Context(s));
            int[] argb = JXWindow.captureNanoVG(element, w, h);
            image.getPixelWriter().setPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
        } catch (RuntimeException | LinkageError e) {
            // no OpenGL here (tests, remote sessions): a blank image of the right size
        }
        return image;
    }

    // ---- lookup ------------------------------------------------------------------------------

    /** First node under {@code start} (itself included) matching a CSS selector, like Node.lookup. */
    static NativeModel lookup(NativeModel start, String selector) {
        if (start == null || selector == null) {
            return null;
        }
        NativeCss.Selector parsed = NativeCss.Selector.parse(selector.trim());
        if (parsed == null) {
            return null;
        }
        return lookup(start, parsed, new NativeCss(null), 0);
    }

    private static NativeModel lookup(NativeModel m, NativeCss.Selector selector, NativeCss css, int depth) {
        if (depth > 200) {
            return null;
        }
        if (selector.matches(m, css)) {
            return m;
        }
        for (NativeModel child : childrenOf(m)) {
            NativeModel found = lookup(child, selector, css, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Child nodes of a node: its children and the nodes its properties hold (content, graphic, panes...). */
    /**
     * Gives a shown scroll pane or titled pane a skin, as JavaFX does on its first pulse, so code
     * reading {@code getSkin()} or waiting on {@code skinProperty()} (DeviceConfig reads the scroll
     * pane skin's {@code viewRect}) works in native mode. Called when its element is built.
     */
    static void installSkin(NativeModel m) {
        if (Native.value(m, "skin") == null && m.jxOwner() != null) {
            Object skin = null;
            if (m.is(javafx.scene.control.ScrollPane.class)) {
                skin = new com.jxparallel.fx.sun.scene.control.skin.ScrollPaneSkin((com.jxparallel.fx.scene.control.ScrollPane) m.jxOwner());
            } else if (m.is(javafx.scene.control.TitledPane.class)) {
                skin = new com.jxparallel.fx.sun.scene.control.skin.TitledPaneSkin((com.jxparallel.fx.scene.control.TitledPane) m.jxOwner());
            }
            if (skin != null) {
                Native.property(m, "skin", Object.class).setValue(skin); // the JX skin: no JavaFX one exists
            }
        }
    }

    static List<NativeModel> childrenOf(NativeModel m) {
        List<NativeModel> out = new ArrayList<>();
        for (Object c : m.children) {
            NativeModel child = NativeElements.model(c);
            if (child != null) {
                out.add(child);
            }
        }
        for (String name : new String[]{"content", "graphic", "top", "left", "center", "right", "bottom", "left", "right"}) {
            NativeModel child = NativeElements.model(m.values.get(name) instanceof javafx.beans.value.ObservableValue
                    ? ((javafx.beans.value.ObservableValue<?>) m.values.get(name)).getValue() : m.values.get(name));
            if (child != null && !out.contains(child)) {
                out.add(child);
            }
        }
        for (String list : new String[]{"panes", "tabs", "buttons", "items"}) {
            Object values = m.values.get(list);
            if (values instanceof List && !"items".equals(list)) {
                for (Object o : (List<?>) values) {
                    NativeModel child = NativeElements.model(o);
                    if (child != null) {
                        out.add(child);
                    }
                }
            }
        }
        return out;
    }

    // ---- controls ----------------------------------------------------------------------------

    /** The control whose popup (combo list or calendar) is open in a window, or null. */
    static NativeModel popupOwner(NativeScene s) {
        if (s == null) {
            return null;
        }
        for (NativeOverlay o : s.overlays) {
            if (o.kind == NativeOverlay.Kind.COMBO || o.kind == NativeOverlay.Kind.CALENDAR) {
                return o.owner;
            }
        }
        return null;
    }

    /** Selected tab index of a TabPane; the first tab unless the selection model says otherwise. */
    static int selectedTab(NativeModel tabPane) {
        javafx.scene.control.SingleSelectionModel<Object> sel = tabSelection(tabPane);
        if (sel.getSelectedIndex() < 0 && !Native.list(tabPane, "tabs").isEmpty()) {
            sel.select(0);
        }
        return sel.getSelectedIndex();
    }

    /**
     * The selection of a tab pane, created on first use and starting on the first tab like JavaFX.
     * It drives the tabs: their {@code selected} flag and SELECTION_CHANGED events follow it,
     * whether a header click, a key or application code moved it.
     */
    @SuppressWarnings("unchecked")
    static javafx.scene.control.SingleSelectionModel<Object> tabSelection(NativeModel tabPane) {
        Object model = tabPane.values.get("selectionModel");
        if (model instanceof javafx.scene.control.SingleSelectionModel) {
            return (javafx.scene.control.SingleSelectionModel<Object>) model;
        }
        NativeSelection.Single sel = new NativeSelection.Single(tabPane, "tabs", false);
        tabPane.values.put("selectionModel", sel);
        sel.selectedIndexProperty().addListener((o, before, after) -> tabChanged(tabPane, before.intValue(), after.intValue()));
        if (!Native.list(tabPane, "tabs").isEmpty()) {
            sel.select(0);
        }
        return sel;
    }

    private static void tabChanged(NativeModel tabPane, int before, int after) {
        List<Object> tabs = Native.list(tabPane, "tabs");
        for (int i = 0; i < tabs.size(); i++) {
            NativeModel tab = NativeElements.model(tabs.get(i));
            if (tab != null) {
                Native.property(tab, "selected", boolean.class).setValue(i == after);
            }
        }
        fireTabEvent(before >= 0 && before < tabs.size() ? NativeElements.model(tabs.get(before)) : null);
        fireTabEvent(after >= 0 && after < tabs.size() ? NativeElements.model(tabs.get(after)) : null);
        pulse();
    }

    static void selectTab(NativeModel tabPane, int index) {
        if (index >= 0 && index < Native.list(tabPane, "tabs").size()) {
            tabSelection(tabPane).select(index);
        }
    }

    private static void fireTabEvent(NativeModel tab) {
        if (tab == null) {
            return;
        }
        Object h = Native.value(tab, "onSelectionChanged");
        if (h instanceof EventHandler) {
            try {
                @SuppressWarnings("unchecked")
                EventHandler<javafx.event.Event> eh = (EventHandler<javafx.event.Event>) h;
                eh.handle(new javafx.event.Event(tab, tab, javafx.scene.control.Tab.SELECTION_CHANGED_EVENT));
            } catch (RuntimeException e) {
                report(e);
            }
        }
    }

    /** The page node of a Pagination, from its page factory (cached per index). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static NativeModel page(NativeModel pagination) {
        int index = (int) NativeElements.number(pagination, "currentPageIndex", 0);
        Object factory = Native.value(pagination, "pageFactory");
        if (!(factory instanceof javafx.util.Callback)) {
            return null;
        }
        if (Integer.valueOf(index).equals(pagination.state.get("pageIndex")) && pagination.state.get("pageFactory") == factory) {
            return (NativeModel) pagination.state.get("page");
        }
        NativeModel page;
        try {
            page = NativeElements.model(((javafx.util.Callback) factory).call(index));
        } catch (RuntimeException e) {
            report(e);
            page = null;
        }
        pagination.state.put("pageIndex", index);
        pagination.state.put("pageFactory", factory);
        pagination.state.put("page", page);
        return page;
    }

    static List<Object> orderedButtons(NativeModel buttonBar) {
        return Native.list(buttonBar, "buttons");
    }

    /** The table's selection model, a JavaFX TableViewSelectionModel over the native items. */
    static Object selectionOf(NativeModel table) {
        Object existing = table.values.get("selectionModel");
        if (existing instanceof javafx.scene.control.TableView.TableViewSelectionModel) {
            return existing;
        }
        NativeSelection.TableSelection sel = new NativeSelection.TableSelection(table);
        table.values.put("selectionModel", sel);
        return sel;
    }

    /** Enters and exits nested event loops (JavaFX 9+ Platform API, else the JavaFX 8 toolkit). */
    static final class NestedLoop {
        private NestedLoop() {
        }

        static void enter(Object key) {
            try {
                Platform.class.getMethod("enterNestedEventLoop", Object.class).invoke(null, key);
            } catch (NoSuchMethodException e) {
                com.sun.javafx.tk.Toolkit.getToolkit().enterNestedEventLoop(key);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static void exit(Object key) {
            try {
                Platform.class.getMethod("exitNestedEventLoop", Object.class, Object.class).invoke(null, key, null);
            } catch (NoSuchMethodException e) {
                com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(key, null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
