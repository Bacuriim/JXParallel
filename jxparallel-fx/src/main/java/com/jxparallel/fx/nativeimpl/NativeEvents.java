package com.jxparallel.fx.nativeimpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.native2d.JXCalendar;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.WindowEvent;

/**
 * Input of native windows, with JavaFX's event semantics: events are real JavaFX events whose
 * source and target are native nodes; filters run from the window down to the target, handlers
 * (added ones, then the {@code onXxx} property) from the target up, and a consumed event stops.
 * What a control does by itself (a button fires, a text field edits) runs after the event went
 * through unconsumed ({@link NativeBehavior}).
 */
final class NativeEvents {
    /** Pixels the pointer moves while pressed before a drag is detected (JavaFX uses a similar hysteresis). */
    static final double DRAG_THRESHOLD = 5;
    static final long TOOLTIP_DELAY = 1000;
    private static final Map<String, String> HANDLER_PROPERTIES = new HashMap<>();

    static {
        String[][] names = {
            {"MOUSE_PRESSED", "onMousePressed"}, {"MOUSE_RELEASED", "onMouseReleased"}, {"MOUSE_CLICKED", "onMouseClicked"},
            {"MOUSE_MOVED", "onMouseMoved"}, {"MOUSE_DRAGGED", "onMouseDragged"}, {"MOUSE_ENTERED", "onMouseEntered"},
            {"MOUSE_EXITED", "onMouseExited"}, {"DRAG_DETECTED", "onDragDetected"}, {"KEY_PRESSED", "onKeyPressed"},
            {"KEY_RELEASED", "onKeyReleased"}, {"KEY_TYPED", "onKeyTyped"}, {"SCROLL", "onScroll"}, {"ACTION", "onAction"},
            {"DRAG_OVER", "onDragOver"}, {"DRAG_ENTERED", "onDragEntered"}, {"DRAG_EXITED", "onDragExited"},
            {"DRAG_DROPPED", "onDragDropped"}, {"DRAG_DONE", "onDragDone"}, {"CONTEXTMENUREQUESTED", "onContextMenuRequested"},
        };
        for (String[] n : names) {
            HANDLER_PROPERTIES.put(n[0], n[1]);
        }
    }

    private NativeEvents() {
    }

    static void register() {
        for (String owner : new String[]{"Scene", "Window"}) {
            Native.register(owner + ".addEventFilter(EventType,EventHandler)", (self, m, a) -> add(m, "eventFilters", a));
            Native.register(owner + ".addEventHandler(EventType,EventHandler)", (self, m, a) -> add(m, "eventHandlers", a));
            Native.register(owner + ".removeEventFilter(EventType,EventHandler)", (self, m, a) -> remove(m, "eventFilters", a));
            Native.register(owner + ".removeEventHandler(EventType,EventHandler)", (self, m, a) -> remove(m, "eventHandlers", a));
        }
        Native.register("Scene.getAccelerators()", (self, m, a) -> {
            Object map = m.state.get("accelerators");
            if (map == null) {
                map = FXCollections.observableHashMap();
                m.state.put("accelerators", map);
            }
            return map; // the API hands out JavaFX's ObservableMap itself
        });
        Native.register("Node.fireEvent(Event)", (self, m, a) -> {
            Object event = Fx.fx(a[0]);
            if (event instanceof Event) {
                NativeScene s = NativeRuntime.windowOf(m);
                dispatch(s, chainOf(s, m), n -> ((Event) event).copyFor(n, m));
            }
            return null;
        });
    }

    private static Object add(NativeModel m, String kind, Object[] a) {
        Native.list(m, kind).add(new Object[]{Fx.fx(a[0]), Fx.fx(a[1])});
        return null;
    }

    private static Object remove(NativeModel m, String kind, Object[] a) {
        Object type = Fx.fx(a[0]);
        Object handler = Fx.fx(a[1]);
        Native.list(m, kind).removeIf(o -> ((Object[]) o)[0] == type && ((Object[]) o)[1] == handler);
        return null;
    }

    // ---- from the display thread -------------------------------------------------------------

    private static final Map<NativeScene, AtomicReference<JXInputEvent>> PENDING_MOVES = Collections.synchronizedMap(new HashMap<>());

    /** Hands a raw event to the application thread; pointer moves are coalesced to the latest. */
    static void post(NativeScene s, JXInputEvent event) {
        if (event.getKind() == JXInputEvent.Kind.MOVE) {
            AtomicReference<JXInputEvent> slot = PENDING_MOVES.computeIfAbsent(s, k -> new AtomicReference<>());
            if (slot.getAndSet(event) == null) {
                Platform.runLater(() -> {
                    JXInputEvent latest = slot.getAndSet(null);
                    if (latest != null) {
                        handle(s, latest);
                    }
                });
            }
            return;
        }
        Platform.runLater(() -> {
            AtomicReference<JXInputEvent> slot = PENDING_MOVES.get(s);
            JXInputEvent move = slot == null ? null : slot.getAndSet(null);
            if (move != null) {
                handle(s, move); // keep the order: a pending move comes before this event
            }
            handle(s, event);
        });
    }

    static void handle(NativeScene s, JXInputEvent e) {
        if (!s.showing) {
            return;
        }
        List<NativeModel> hoverBefore = new ArrayList<>(s.hover);
        NativeModel pressedBefore = s.pressed;
        boolean insideBefore = s.pressInside;
        boolean windowBefore = s.windowFocused;
        try {
            dispatchInput(s, e);
        } finally {
            invalidateChangedStates(s, hoverBefore, pressedBefore, insideBefore, windowBefore);
        }
    }

    /**
     * Elements show hover, pressed and focus states (and CSS may style descendants by them): the
     * nodes whose state this event changed, and what is below them, are rebuilt.
     */
    private static void invalidateChangedStates(NativeScene s, List<NativeModel> hoverBefore, NativeModel pressedBefore,
                                                boolean insideBefore, boolean windowBefore) {
        if (!hoverBefore.equals(s.hover)) {
            for (NativeModel m : hoverBefore) {
                if (!s.hover.contains(m)) {
                    NativeElements.invalidateSubtree(m);
                }
            }
            for (NativeModel m : s.hover) {
                if (!hoverBefore.contains(m)) {
                    NativeElements.invalidateSubtree(m);
                }
            }
        }
        if (pressedBefore != s.pressed || insideBefore != s.pressInside) {
            if (pressedBefore != null) {
                NativeElements.invalidateSubtree(pressedBefore);
            }
            if (s.pressed != null) {
                NativeElements.invalidateSubtree(s.pressed);
            }
        }
        if (windowBefore != s.windowFocused && s.focus != null) {
            NativeElements.invalidateSubtree(s.focus);
        }
    }

    private static void dispatchInput(NativeScene s, JXInputEvent e) {
        try {
            switch (e.getKind()) {
                case RESIZE:
                    s.width = (int) e.getX();
                    s.height = (int) e.getY();
                    Native.property(s.stage, "width", double.class).setValue(e.getX());
                    Native.property(s.stage, "height", double.class).setValue(e.getY());
                    NativeModel scene = s.sceneModel();
                    if (scene != null) {
                        Native.property(scene, "width", double.class).setValue(e.getX());
                        Native.property(scene, "height", double.class).setValue(e.getY());
                    }
                    s.render();
                    return;
                case CLOSE_REQUEST:
                    if (NativeRuntime.blocked(s)) {
                        return;
                    }
                    if (!NativeRuntime.fireWindowEvent(s.stage, WindowEvent.WINDOW_CLOSE_REQUEST, "onCloseRequest")) {
                        NativeRuntime.hide(s.stage);
                    }
                    return;
                case FOCUS_GAINED:
                case FOCUS_LOST:
                    s.windowFocused = e.getKind() == JXInputEvent.Kind.FOCUS_GAINED;
                    Native.property(s.stage, "focused", boolean.class).setValue(s.windowFocused);
                    if (!s.windowFocused) {
                        hideTooltip(s);
                    }
                    s.render();
                    return;
                default:
                    break;
            }
            if (NativeRuntime.blocked(s)) {
                return; // a modal window is showing
            }
            switch (e.getKind()) {
                case PRESS:
                    press(s, e);
                    break;
                case RELEASE:
                    release(s, e);
                    break;
                case MOVE:
                    move(s, e);
                    break;
                case EXIT:
                    exit(s);
                    break;
                case SCROLL:
                    scroll(s, e);
                    break;
                case KEY_PRESS:
                case KEY_RELEASE:
                    key(s, e);
                    break;
                case CHAR:
                    typed(s, e);
                    break;
                default:
                    break;
            }
        } catch (RuntimeException ex) {
            NativeRuntime.report(ex);
        }
        NativeRuntime.pulse();
    }

    // ---- pointer -----------------------------------------------------------------------------

    private static void press(NativeScene s, JXInputEvent e) {
        s.pointerX = e.getX();
        s.pointerY = e.getY();
        hideTooltip(s);
        List<JXNativeNode> nodes = s.pathAt(e.getX(), e.getY());
        JXNativeNode deepest = nodes.isEmpty() ? null : nodes.get(nodes.size() - 1);
        if (overlayClick(s, nodes, e)) {
            return;
        }
        if (closeAutoHiding(s)) {
            return; // like PopupControl, the click that closes a popup is consumed
        }
        List<NativeModel> path = NativeScene.models(nodes);
        NativeModel target = path.isEmpty() ? null : path.get(path.size() - 1);
        s.pressed = target;
        s.pressInside = true;
        s.pressX = e.getX();
        s.pressY = e.getY();
        s.pressButton = e.getButton();
        s.dragDetected = false;
        s.pressPath = path;
        s.pressNode = deepest;
        if (e.getButton() == JXInputEvent.BUTTON_PRIMARY || e.getButton() == JXInputEvent.BUTTON_SECONDARY) {
            for (int i = path.size() - 1; i >= 0; i--) {
                if (NativeRuntime.focusable(path.get(i))) {
                    NativeRuntime.setFocus(s, path.get(i));
                    break;
                }
            }
        }
        if (target == null) {
            return;
        }
        boolean consumed = mouse(s, path, MouseEvent.MOUSE_PRESSED, e, e.getClickCount());
        if (!consumed) {
            NativeBehavior.pressed(s, path, deepest, e);
        }
        if (e.getButton() == JXInputEvent.BUTTON_SECONDARY) {
            boolean handled = dispatch(s, withWindow(s, path), n -> new ContextMenuEvent(n, target, ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    local(s, n, e.getX()), localY(s, n, e.getY()), e.getX(), e.getY(), false, null));
            if (!handled) {
                for (int i = path.size() - 1; i >= 0; i--) {
                    Object menu = Native.value(path.get(i), "contextMenu");
                    if (NativeElements.model(menu) != null) {
                        showMenu(s, NativeElements.model(menu), e.getX(), e.getY());
                        break;
                    }
                }
            }
        }
    }

    private static void release(NativeScene s, JXInputEvent e) {
        s.pointerX = e.getX();
        s.pointerY = e.getY();
        if (NativeDragAndDrop.active(s)) {
            NativeDragAndDrop.release(s, e);
            s.pressed = null;
            return;
        }
        NativeModel target = s.pressed;
        List<NativeModel> pressPath = s.pressPath;
        if (target == null || pressPath == null) {
            s.pressed = null;
            return;
        }
        List<NativeModel> now = NativeScene.models(s.pathAt(e.getX(), e.getY()));
        boolean inside = now.contains(target);
        s.pressInside = inside;
        boolean consumed = mouse(s, pressPath, MouseEvent.MOUSE_RELEASED, e, e.getClickCount());
        if (!consumed) {
            NativeBehavior.released(s, pressPath, s.pressNode, e, inside);
        }
        if (inside && !s.dragDetected) {
            mouse(s, pressPath, MouseEvent.MOUSE_CLICKED, e, e.getClickCount());
        }
        s.pressed = null;
        s.pressPath = null;
        s.pressNode = null;
    }

    private static void move(NativeScene s, JXInputEvent e) {
        s.pointerX = e.getX();
        s.pointerY = e.getY();
        List<JXNativeNode> nodes = s.pathAt(e.getX(), e.getY());
        List<NativeModel> path = NativeScene.models(nodes);
        overlayHover(s, nodes);
        updateHover(s, path, e);
        if (e.isButtonsDown() && s.pressed != null) {
            s.pressInside = path.contains(s.pressed);
            if (!s.dragDetected && Math.hypot(e.getX() - s.pressX, e.getY() - s.pressY) > DRAG_THRESHOLD) {
                s.dragDetected = true;
                mouse(s, s.pressPath, MouseEvent.DRAG_DETECTED, e, 0);
            }
            if (NativeDragAndDrop.active(s)) {
                NativeDragAndDrop.over(s, path, e);
                return;
            }
            boolean consumed = mouse(s, s.pressPath, MouseEvent.MOUSE_DRAGGED, e, 0);
            if (!consumed) {
                NativeBehavior.dragged(s, s.pressPath, s.pressNode, e);
            }
            return;
        }
        if (!path.isEmpty()) {
            mouse(s, path, MouseEvent.MOUSE_MOVED, e, 0);
        }
        tooltip(s, path, e);
    }

    private static void exit(NativeScene s) {
        updateHover(s, Collections.emptyList(), null);
        hideTooltip(s);
    }

    /** Fires MOUSE_EXITED on nodes the pointer left and MOUSE_ENTERED on nodes it entered (each only on itself). */
    private static void updateHover(NativeScene s, List<NativeModel> path, JXInputEvent e) {
        List<NativeModel> before = new ArrayList<>(s.hover);
        if (before.equals(path)) {
            return;
        }
        s.hover.clear();
        s.hover.addAll(path);
        double x = e == null ? s.pointerX : e.getX();
        double y = e == null ? s.pointerY : e.getY();
        for (int i = before.size() - 1; i >= 0; i--) {
            NativeModel m = before.get(i);
            if (!path.contains(m)) {
                deliver(m, mouseEvent(s, m, m, MouseEvent.MOUSE_EXITED, x, y, 0, 0, 0, false));
            }
        }
        for (NativeModel m : path) {
            if (!before.contains(m)) {
                deliver(m, mouseEvent(s, m, m, MouseEvent.MOUSE_ENTERED, x, y, 0, 0, 0, false));
            }
        }
        NativeRuntime.pulse();
    }

    private static void scroll(NativeScene s, JXInputEvent e) {
        List<JXNativeNode> nodes = s.pathAt(e.getX(), e.getY());
        for (JXNativeNode n : nodes) {
            if (n.getProperty("popupOf") != null && "list".equals(n.getType())) {
                NativeModel combo = (NativeModel) n.getProperty("popupOf");
                double y = NativeCells.number(combo.state.get("popupScrollY"), 0) - e.getScrollY() * NativeCells.LIST_CELL * 3;
                combo.state.put("popupScrollY", y);
                return;
            }
        }
        List<NativeModel> path = NativeScene.models(nodes);
        NativeModel target = path.isEmpty() ? null : path.get(path.size() - 1);
        if (target == null) {
            return;
        }
        double deltaX = e.getScrollX() * 40;
        double deltaY = e.getScrollY() * 40;
        boolean consumed = dispatch(s, withWindow(s, path), n -> new ScrollEvent(n, target, ScrollEvent.SCROLL,
                local(s, n, e.getX()), localY(s, n, e.getY()), e.getX(), e.getY(), e.isShiftDown(), e.isControlDown(),
                e.isAltDown(), e.isMetaDown(), false, false, deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.CHARACTERS, e.getScrollX() * 3,
                ScrollEvent.VerticalTextScrollUnits.LINES, e.getScrollY() * 3, 0, null));
        if (!consumed) {
            NativeBehavior.scroll(s, nodes, e);
        }
    }

    // ---- keys --------------------------------------------------------------------------------

    private static void key(NativeScene s, JXInputEvent e) {
        KeyCode code = code(e.getKey());
        boolean press = e.getKind() == JXInputEvent.Kind.KEY_PRESS;
        NativeModel target = s.focus != null ? s.focus : s.rootModel();
        if (target == null) {
            return;
        }
        List<NativeModel> chain = chainOf(s, target);
        EventType<KeyEvent> type = press ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
        KeyEvent[] made = new KeyEvent[1];
        boolean consumed = dispatch(s, chain, n -> made[0] = new KeyEvent(n, target, type, KeyEvent.CHAR_UNDEFINED,
                code.getName(), code, e.isShiftDown(), e.isControlDown(), e.isAltDown(), e.isMetaDown()));
        if (!press || consumed) {
            return;
        }
        if (accelerator(s, made[0])) {
            return;
        }
        NativeBehavior.key(s, target, code, e);
    }

    private static void typed(NativeScene s, JXInputEvent e) {
        NativeModel target = s.focus != null ? s.focus : s.rootModel();
        if (target == null) {
            return;
        }
        String character = new String(Character.toChars(e.getCodepoint()));
        boolean consumed = dispatch(s, chainOf(s, target), n -> new KeyEvent(n, target, KeyEvent.KEY_TYPED, character, "",
                KeyCode.UNDEFINED, e.isShiftDown(), e.isControlDown(), e.isAltDown(), e.isMetaDown()));
        if (!consumed && !e.isControlDown()) {
            NativeBehavior.typed(s, target, character);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean accelerator(NativeScene s, KeyEvent event) {
        NativeModel scene = s.sceneModel();
        Object map = scene == null ? null : scene.state.get("accelerators");
        if (!(map instanceof ObservableMap)) {
            return false;
        }
        for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) map).entrySet()) {
            Object combination = Fx.fx(entry.getKey());
            if (combination instanceof KeyCombination && ((KeyCombination) combination).match(event)) {
                Object action = entry.getValue();
                if (action instanceof Runnable) {
                    ((Runnable) action).run();
                    return true;
                }
            }
        }
        return false;
    }

    static KeyCode code(String name) {
        if (name == null) {
            return KeyCode.UNDEFINED;
        }
        try {
            return KeyCode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return KeyCode.UNDEFINED;
        }
    }

    // ---- dispatch ----------------------------------------------------------------------------

    /** The chain of a node: window, scene, then the node's ancestors from the root down to it. */
    static List<NativeModel> chainOf(NativeScene s, NativeModel target) {
        List<NativeModel> chain = new ArrayList<>();
        for (NativeModel m = target; m != null; m = NativeCss.parentOf(m)) {
            chain.add(0, m);
            if (chain.size() > 10000) {
                break;
            }
        }
        return withWindow(s, chain);
    }

    /** Prepends the stage and the scene to a node path, the start of every JavaFX dispatch chain. */
    static List<NativeModel> withWindow(NativeScene s, List<NativeModel> path) {
        List<NativeModel> chain = new ArrayList<>();
        if (s != null) {
            chain.add(s.stage);
            NativeModel scene = s.sceneModel();
            if (scene != null) {
                chain.add(scene);
            }
        }
        for (NativeModel m : path) {
            if (!chain.contains(m)) {
                chain.add(m);
            }
        }
        return chain;
    }

    /**
     * Capturing then bubbling phase of JavaFX's dispatch along {@code chain} (root first); {@code make}
     * creates the event as a node sees it. Returns true if a handler consumed it.
     */
    static boolean dispatch(NativeScene s, List<NativeModel> chain, Function<NativeModel, Event> make) {
        for (NativeModel m : chain) {
            Event event = make.apply(m);
            handlers(m, "eventFilters", event);
            if (event.isConsumed()) {
                return true;
            }
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            NativeModel m = chain.get(i);
            Event event = make.apply(m);
            if (deliver(m, event)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delivers an event to one node: its handlers, then its property handler (onAction...). As in
     * JavaFX's CompositeEventHandler, all of them run even if one consumes the event; consuming
     * only keeps it from the next node. Returns true if it was consumed.
     */
    static boolean deliver(NativeModel m, Event event) {
        handlers(m, "eventHandlers", event);
        property(m, event);
        return event.isConsumed();
    }

    @SuppressWarnings("unchecked")
    private static void handlers(NativeModel m, String kind, Event event) {
        Object list = m.values.get(kind);
        if (!(list instanceof List) || ((List<?>) list).isEmpty()) {
            return;
        }
        for (Object o : new ArrayList<>((List<Object>) list)) {
            Object[] entry = (Object[]) o;
            if (matches((EventType<?>) entry[0], event.getEventType()) && entry[1] instanceof EventHandler) {
                call((EventHandler<Event>) entry[1], event);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void property(NativeModel m, Event event) {
        String name = HANDLER_PROPERTIES.get(event.getEventType().getName());
        Object handler = name == null ? null : Native.value(m, name);
        if (handler instanceof EventHandler) {
            call((EventHandler<Event>) handler, event);
        }
    }

    private static void call(EventHandler<Event> handler, Event event) {
        try {
            handler.handle(event);
        } catch (RuntimeException e) {
            NativeRuntime.report(e);
        }
    }

    static boolean matches(EventType<?> registered, EventType<?> actual) {
        for (EventType<?> t = actual; t != null; t = t.getSuperType()) {
            if (t == registered) {
                return true;
            }
        }
        return false;
    }

    private static boolean mouse(NativeScene s, List<NativeModel> path, EventType<MouseEvent> type, JXInputEvent e, int clicks) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        NativeModel target = path.get(path.size() - 1);
        return dispatch(s, withWindow(s, path), n -> mouseEvent(s, n, target, type, e.getX(), e.getY(), e.getButton(),
                clicks, e.getModifiers(), e.isButtonsDown() || type == MouseEvent.MOUSE_PRESSED));
    }

    static MouseEvent mouseEvent(NativeScene s, NativeModel source, NativeModel target, EventType<MouseEvent> type,
                                 double x, double y, int button, int clicks, int modifiers, boolean down) {
        MouseButton b = button == JXInputEvent.BUTTON_SECONDARY ? MouseButton.SECONDARY
                : button == JXInputEvent.BUTTON_MIDDLE ? MouseButton.MIDDLE : MouseButton.PRIMARY;
        if (type == MouseEvent.MOUSE_MOVED || type == MouseEvent.MOUSE_ENTERED || type == MouseEvent.MOUSE_EXITED) {
            b = MouseButton.NONE;
        }
        return new MouseEvent(source, target, type, local(s, source, x), localY(s, source, y), x, y, b, clicks,
                (modifiers & JXInputEvent.SHIFT) != 0, (modifiers & JXInputEvent.CONTROL) != 0,
                (modifiers & JXInputEvent.ALT) != 0, (modifiers & JXInputEvent.META) != 0,
                down && b == MouseButton.PRIMARY, down && b == MouseButton.MIDDLE, down && b == MouseButton.SECONDARY,
                false, b == MouseButton.SECONDARY && type == MouseEvent.MOUSE_PRESSED, true, null);
    }

    /** x relative to a node (window coordinates for the stage and scene). */
    static double local(NativeScene s, NativeModel node, double x) {
        JXNativeNode n = s == null ? null : s.node(node);
        return n == null ? x : x - n.getX();
    }

    static double localY(NativeScene s, NativeModel node, double y) {
        JXNativeNode n = s == null ? null : s.node(node);
        return n == null ? y : y - n.getY();
    }

    /** Fires an ActionEvent at a node (onAction and ActionEvent handlers), as ButtonBase.fire does. */
    static void fireAction(NativeModel m) {
        NativeScene s = NativeRuntime.windowOf(m);
        List<NativeModel> chain = s == null ? Collections.singletonList(m) : chainOf(s, m);
        dispatch(s, chain, n -> new ActionEvent(n, m));
    }

    // ---- popups ------------------------------------------------------------------------------

    /** Opens the list of a combo/choice box or the calendar of a date picker. */
    static void openPopup(NativeScene s, NativeModel control) {
        closePopups(s);
        if (control.is(javafx.scene.control.DatePicker.class)) {
            NativeOverlay calendar = new NativeOverlay(NativeOverlay.Kind.CALENDAR, control);
            Object value = Native.value(control, "value");
            LocalDate shown = value instanceof LocalDate ? (LocalDate) value : LocalDate.now();
            calendar.year = shown.getYear();
            calendar.month = shown.getMonthValue();
            s.overlays.add(calendar);
        } else {
            NativeOverlay list = new NativeOverlay(NativeOverlay.Kind.COMBO, control);
            List<Object> items = Native.list(control, "items");
            int index = items.indexOf(Native.value(control, "value"));
            int rows = (int) NativeElements.number(control, "visibleRowCount", 10);
            control.state.put("popupScrollY", Math.max(0, (index - rows / 2) * NativeCells.LIST_CELL));
            control.state.put("popupHover", index);
            s.overlays.add(list);
        }
        Native.property(control, "showing", boolean.class).setValue(true);
        fireSimple(control, "onShowing", javafx.scene.control.ComboBoxBase.ON_SHOWING);
        fireSimple(control, "onShown", javafx.scene.control.ComboBoxBase.ON_SHOWN);
        NativeRuntime.pulse();
    }

    static void closePopups(NativeScene s) {
        for (NativeOverlay o : new ArrayList<>(s.overlays)) {
            if (o.kind == NativeOverlay.Kind.COMBO || o.kind == NativeOverlay.Kind.CALENDAR || o.kind == NativeOverlay.Kind.MENU) {
                s.overlays.remove(o);
                if (o.owner != null && o.kind != NativeOverlay.Kind.MENU) {
                    Native.property(o.owner, "showing", boolean.class).setValue(false);
                    fireSimple(o.owner, "onHiding", javafx.scene.control.ComboBoxBase.ON_HIDING);
                    fireSimple(o.owner, "onHidden", javafx.scene.control.ComboBoxBase.ON_HIDDEN);
                }
            }
        }
        NativeRuntime.pulse();
    }

    private static boolean closeAutoHiding(NativeScene s) {
        for (NativeOverlay o : s.overlays) {
            if (o.kind == NativeOverlay.Kind.COMBO || o.kind == NativeOverlay.Kind.CALENDAR || o.kind == NativeOverlay.Kind.MENU) {
                closePopups(s);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void fireSimple(NativeModel m, String property, EventType<Event> type) {
        Object h = Native.value(m, property);
        if (h instanceof EventHandler) {
            call((EventHandler<Event>) h, new Event(m, m, type));
        }
    }

    static void showMenu(NativeScene s, NativeModel menu, double x, double y) {
        closePopups(s);
        NativeOverlay overlay = new NativeOverlay(NativeOverlay.Kind.MENU, null);
        overlay.content = menu;
        overlay.x = x;
        overlay.y = y;
        s.overlays.add(overlay);
        Native.property(menu, "showing", boolean.class).setValue(true);
        NativeRuntime.pulse();
    }

    /** A press inside a popup: pick the combo item, the calendar day or the menu item. */
    private static boolean overlayClick(NativeScene s, List<JXNativeNode> nodes, JXInputEvent e) {
        JXNativeNode popup = null;
        JXNativeNode cell = null;
        for (JXNativeNode n : nodes) {
            if (Boolean.TRUE.equals(n.getProperty("overlay"))) {
                popup = n;
            }
            if (popup != null && "cell".equals(n.getType())) {
                cell = n;
            }
        }
        if (popup == null) {
            return false;
        }
        Object owner = popup.getProperty("popupOf");
        if ("list".equals(popup.getType()) && owner instanceof NativeModel) {
            NativeModel combo = (NativeModel) owner;
            if (cell != null && cell.getProperty("popupIndex") instanceof Integer) {
                int index = (Integer) cell.getProperty("popupIndex");
                NativeBehavior.selectComboItem(combo, index);
                closePopups(s);
            }
            return true;
        }
        if ("calendar".equals(popup.getType()) && owner instanceof NativeModel) {
            NativeModel picker = (NativeModel) owner;
            int[] hit = JXCalendar.hit(popup, e.getX(), e.getY());
            NativeOverlay overlay = overlayOf(s, picker);
            if (hit[0] == JXCalendar.PREVIOUS && overlay != null) {
                LocalDate d = LocalDate.of(overlay.year, overlay.month, 1).minusMonths(1);
                overlay.year = d.getYear();
                overlay.month = d.getMonthValue();
            } else if (hit[0] == JXCalendar.NEXT && overlay != null) {
                LocalDate d = LocalDate.of(overlay.year, overlay.month, 1).plusMonths(1);
                overlay.year = d.getYear();
                overlay.month = d.getMonthValue();
            } else if (hit[0] == JXCalendar.DAY) {
                Native.property(picker, "value", Object.class).setValue(LocalDate.of(hit[1], hit[2], hit[3]));
                NativeText.syncDatePicker(picker, NativeText.editor(picker), true);
                closePopups(s);
                fireAction(picker);
            }
            NativeRuntime.pulse();
            return true;
        }
        if ("popup".equals(popup.getType()) && owner instanceof NativeModel) {
            if (cell != null && cell.getProperty("model") instanceof NativeModel) {
                NativeModel item = (NativeModel) cell.getProperty("model");
                if (!Boolean.TRUE.equals(Native.value(item, "disable"))) {
                    closePopups(s);
                    NativeBehavior.fireMenuItem(item);
                }
            }
            return true;
        }
        return true;
    }

    private static NativeOverlay overlayOf(NativeScene s, NativeModel owner) {
        for (NativeOverlay o : s.overlays) {
            if (o.owner == owner) {
                return o;
            }
        }
        return null;
    }

    private static void overlayHover(NativeScene s, List<JXNativeNode> nodes) {
        NativeModel combo = null;
        int index = -1;
        NativeModel menu = null;
        int menuIndex = -1;
        for (JXNativeNode n : nodes) {
            Object owner = n.getProperty("popupOf");
            if (owner instanceof NativeModel && "list".equals(n.getType())) {
                combo = (NativeModel) owner;
            }
            if (owner instanceof NativeModel && "popup".equals(n.getType())) {
                menu = (NativeModel) owner;
            }
            if (n.getProperty("popupIndex") instanceof Integer) {
                index = (Integer) n.getProperty("popupIndex");
            }
            if (n.getProperty("menuIndex") instanceof Integer) {
                menuIndex = (Integer) n.getProperty("menuIndex");
            }
        }
        if (combo != null && !Integer.valueOf(index).equals(combo.state.get("popupHover"))) {
            combo.state.put("popupHover", index);
            NativeRuntime.pulse();
        }
        if (menu != null && !Integer.valueOf(menuIndex).equals(menu.state.get("hover"))) {
            menu.state.put("hover", menuIndex);
            NativeRuntime.pulse();
        }
    }

    // ---- tooltips ----------------------------------------------------------------------------

    private static java.util.Timer tooltipTimer;

    private static void tooltip(NativeScene s, List<NativeModel> path, JXInputEvent e) {
        NativeModel owner = null;
        NativeModel tip = null;
        for (int i = path.size() - 1; i >= 0 && tip == null; i--) {
            NativeModel m = path.get(i);
            tip = NativeElements.model(Native.value(m, "tooltip"));
            if (tip == null) {
                tip = NativeElements.model(m.state.get("tooltip"));
            }
            owner = m;
        }
        NativeModel current = s.tooltipOwner;
        if (tip == null) {
            if (current != null) {
                hideTooltip(s);
            }
            return;
        }
        if (owner == current) {
            return;
        }
        hideTooltip(s);
        s.tooltipOwner = owner;
        NativeModel shownTip = tip;
        NativeModel shownOwner = owner;
        double x = e.getX();
        double y = e.getY() + 20;
        if (tooltipTimer == null) {
            tooltipTimer = new java.util.Timer("JX tooltips", true);
        }
        tooltipTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (s.tooltipOwner == shownOwner && s.showing) {
                        NativeOverlay overlay = new NativeOverlay(NativeOverlay.Kind.TOOLTIP, shownOwner);
                        overlay.content = NativeElements.string(shownTip, "text");
                        overlay.x = x;
                        overlay.y = y;
                        s.overlays.add(overlay);
                        NativeRuntime.pulse();
                    }
                });
            }
        }, TOOLTIP_DELAY);
    }

    static void hideTooltip(NativeScene s) {
        s.tooltipOwner = null;
        if (s.overlays.removeIf(o -> o.kind == NativeOverlay.Kind.TOOLTIP)) {
            NativeRuntime.pulse();
        }
    }
}
