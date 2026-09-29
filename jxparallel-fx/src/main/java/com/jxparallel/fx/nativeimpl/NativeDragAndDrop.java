package com.jxparallel.fx.nativeimpl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.native2d.JXInputEvent;

import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

/**
 * Drag and drop inside the application (between nodes of native windows), as JavaFX delivers it:
 * a DRAG_DETECTED handler calls startDragAndDrop and fills the Dragboard; while the pointer moves,
 * the node under it gets DRAG_ENTERED/DRAG_OVER/DRAG_EXITED and may accept transfer modes; on
 * release an accepting node gets DRAG_DROPPED, and the source gets DRAG_DONE with the mode used.
 * The Dragboard is a JavaFX one over an in-process clipboard, so any DataFormat works.
 */
final class NativeDragAndDrop {
    /** One drag gesture. */
    static final class Session {
        final NativeModel source;
        final Dragboard dragboard;
        final TransferMode[] modes;
        NativeModel over;
        TransferMode accepted;

        Session(NativeModel source, Dragboard dragboard, TransferMode[] modes) {
            this.source = source;
            this.dragboard = dragboard;
            this.modes = modes;
        }
    }

    private NativeDragAndDrop() {
    }

    /** Node.startDragAndDrop: only valid while a DRAG_DETECTED event is being handled, like JavaFX. */
    static Dragboard start(NativeModel source, Object modesArg) {
        NativeScene s = NativeRuntime.windowOf(source);
        if (s == null || !s.dragDetected) {
            throw new IllegalStateException("Cannot start drag and drop outside of DRAG_DETECTED event handler");
        }
        TransferMode[] modes = modes(modesArg);
        Dragboard dragboard = createDragboard(modes);
        s.drag = new Session(source, dragboard, modes);
        return dragboard;
    }

    private static TransferMode[] modes(Object arg) {
        if (arg instanceof Object[]) {
            Object[] values = (Object[]) arg;
            List<TransferMode> out = new ArrayList<>();
            for (Object v : values) {
                Object fx = Fx.fx(v);
                if (fx instanceof TransferMode) {
                    out.add((TransferMode) fx);
                }
            }
            return out.toArray(new TransferMode[0]);
        }
        return TransferMode.ANY;
    }

    /**
     * A Dragboard over an in-process clipboard with a drag view (JavaFX's LocalClipboard refuses
     * drag views). Created through Dragboard.impl_createDragboard (JavaFX 8) or DragboardHelper (9+).
     */
    static Dragboard createDragboard(TransferMode[] modes) {
        try {
            Class<?> tkClipboard = Class.forName("com.sun.javafx.tk.TKClipboard");
            java.util.Map<javafx.scene.input.DataFormat, Object> content = new java.util.LinkedHashMap<>();
            Object[] view = new Object[3];
            Object clipboard = java.lang.reflect.Proxy.newProxyInstance(tkClipboard.getClassLoader(), new Class<?>[]{tkClipboard},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getContentTypes":
                                return new java.util.LinkedHashSet<>(content.keySet());
                            case "putContent":
                                content.clear();
                                for (Object pair : (Object[]) args[0]) {
                                    javafx.util.Pair<?, ?> p = (javafx.util.Pair<?, ?>) pair;
                                    content.put((javafx.scene.input.DataFormat) p.getKey(), p.getValue());
                                }
                                return true;
                            case "getContent":
                                return content.get(args[0]);
                            case "hasContent":
                                return content.containsKey(args[0]);
                            case "getTransferModes":
                                return new java.util.LinkedHashSet<>(Arrays.asList(modes));
                            case "setDragView":
                                view[0] = args[0];
                                return null;
                            case "setDragViewOffsetX":
                                view[1] = args[0];
                                return null;
                            case "setDragViewOffsetY":
                                view[2] = args[0];
                                return null;
                            case "getDragView":
                                return view[0];
                            case "getDragViewOffsetX":
                                return view[1] == null ? 0.0 : view[1];
                            case "getDragViewOffsetY":
                                return view[2] == null ? 0.0 : view[2];
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return "NativeDragboard" + content.keySet();
                            default:
                                return null; // setSecurityContext
                        }
                    });
            try {
                Method create = Dragboard.class.getMethod("impl_createDragboard", tkClipboard);
                return (Dragboard) create.invoke(null, clipboard);
            } catch (NoSuchMethodException newer) {
                Class<?> helper = Class.forName("com.sun.javafx.scene.input.DragboardHelper");
                Method create = helper.getMethod("createDragboard", tkClipboard, boolean.class);
                return (Dragboard) create.invoke(null, clipboard, false);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create a Dragboard", e);
        }
    }

    static boolean active(NativeScene s) {
        return s.drag != null;
    }

    /** The pointer moved during a drag: enter/exit and DRAG_OVER on the node under it. */
    static void over(NativeScene s, List<NativeModel> path, JXInputEvent e) {
        Session drag = s.drag;
        NativeModel target = path.isEmpty() ? null : path.get(path.size() - 1);
        showView(s, e);
        if (target != drag.over) {
            if (drag.over != null) {
                NativeEvents.deliver(drag.over, event(s, drag, drag.over, drag.over, DragEvent.DRAG_EXITED, e));
            }
            drag.over = target;
            drag.accepted = null;
            if (target != null) {
                NativeEvents.deliver(target, event(s, drag, target, target, DragEvent.DRAG_ENTERED, e));
            }
        }
        if (target == null) {
            drag.accepted = null;
            return;
        }
        List<DragEvent> made = new ArrayList<>();
        drag.accepted = null;
        NativeEvents.dispatch(s, NativeEvents.withWindow(s, path), n -> {
            DragEvent event = event(s, drag, n, target, DragEvent.DRAG_OVER, e, null);
            made.add(event);
            return event;
        });
        for (DragEvent event : made) {
            if (event.getAcceptedTransferMode() != null) {
                drag.accepted = event.getAcceptedTransferMode(); // each node sees its own copy; any acceptance counts
            }
        }
    }

    /** Release: DRAG_DROPPED on an accepting target, then DRAG_DONE on the source. */
    static void release(NativeScene s, JXInputEvent e) {
        Session drag = s.drag;
        s.drag = null;
        s.overlays.removeIf(o -> o.kind == NativeOverlay.Kind.DRAG);
        TransferMode done = null;
        if (drag.over != null && drag.accepted != null) {
            List<NativeModel> path = NativeScene.models(s.pathAt(e.getX(), e.getY()));
            if (path.isEmpty()) {
                path = Arrays.asList(drag.over);
            }
            NativeModel target = path.get(path.size() - 1);
            List<DragEvent> made = new ArrayList<>();
            TransferMode mode = drag.accepted;
            NativeEvents.dispatch(s, NativeEvents.withWindow(s, path), n -> {
                DragEvent event = event(s, drag, n, target, DragEvent.DRAG_DROPPED, e, mode);
                made.add(event);
                return event;
            });
            for (DragEvent event : made) {
                if (event.isDropCompleted()) {
                    done = mode;
                }
            }
            NativeEvents.deliver(drag.over, event(s, drag, drag.over, drag.over, DragEvent.DRAG_EXITED, e));
        }
        TransferMode result = done;
        // DRAG_DONE carries the mode used, or null when nothing accepted the drop
        NativeEvents.dispatch(s, NativeEvents.chainOf(s, drag.source), n -> new DragEvent(n, drag.source, DragEvent.DRAG_DONE,
                drag.dragboard, NativeEvents.local(s, n, e.getX()), NativeEvents.localY(s, n, e.getY()), e.getX(), e.getY(),
                result, drag.source, drag.over, null));
        NativeRuntime.pulse();
    }

    private static void showView(NativeScene s, JXInputEvent e) {
        Object view = s.drag.dragboard.getDragView();
        NativeOverlay overlay = null;
        for (NativeOverlay o : s.overlays) {
            if (o.kind == NativeOverlay.Kind.DRAG) {
                overlay = o;
            }
        }
        if (view != null) {
            if (overlay == null) {
                overlay = new NativeOverlay(NativeOverlay.Kind.DRAG, null);
                s.overlays.add(overlay);
            }
            overlay.content = view;
            overlay.x = e.getX();
            overlay.y = e.getY();
            NativeRuntime.pulse();
        }
    }

    private static DragEvent event(NativeScene s, Session drag, NativeModel source, NativeModel target,
                                   javafx.event.EventType<DragEvent> type, JXInputEvent e) {
        return event(s, drag, source, target, type, e, drag.accepted);
    }

    private static DragEvent event(NativeScene s, Session drag, NativeModel source, NativeModel target,
                                   javafx.event.EventType<DragEvent> type, JXInputEvent e, TransferMode mode) {
        TransferMode transfer = mode != null ? mode : drag.modes.length > 0 ? drag.modes[0] : TransferMode.MOVE;
        return new DragEvent(source, target, type, drag.dragboard, NativeEvents.local(s, source, e.getX()),
                NativeEvents.localY(s, source, e.getY()), e.getX(), e.getY(), transfer, drag.source, drag.over, null);
    }
}
