package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXControlLayout;
import com.jxparallel.ui.native2d.JXInputEvent;
import com.jxparallel.ui.native2d.JXNativeNode;


/**
 * One native window showing a Stage's scene. The tree is built, reconciled and laid out on the
 * JavaFX application thread (where the application changes its nodes), under a lock the window's
 * painting also takes; input arrives from the display thread and is handled on the application
 * thread against the same tree, so hit testing always matches the models.
 */
final class NativeScene {
    final NativeModel stage;
    final NativeSurface window;
    final Object lock = new Object();
    private JXNativeNode tree;
    int width;
    int height;
    boolean showing;
    boolean windowFocused = true;
    /** Node with the keyboard focus. */
    NativeModel focus;
    /** Nodes under the pointer, root first. */
    final List<NativeModel> hover = new ArrayList<>();
    /** Node that got the last primary press, and whether the pointer is still over it. */
    NativeModel pressed;
    boolean pressInside;
    double pressX;
    double pressY;
    int pressButton;
    boolean dragDetected;
    /** Models and deepest node under the press; released and dragged events go to them. */
    List<NativeModel> pressPath;
    JXNativeNode pressNode;
    double pointerX;
    double pointerY;
    /** Node whose tooltip is scheduled or shown. */
    NativeModel tooltipOwner;
    /** Scroll bar thumb drag: {vertical 1/0, pointer at press, scroll offset at press}, and its control. */
    double[] barDrag;
    NativeModel barDragModel;
    /** Drag and drop in progress from this window. */
    NativeDragAndDrop.Session drag;
    /** Popups shown over the scene: combo lists, calendars, tooltips, context menus. */
    final List<NativeOverlay> overlays = new ArrayList<>();
    /** Focus traversal order of the last render. */
    List<NativeModel> focusOrder = new ArrayList<>();
    /** First node of each model in the last layout. */
    final Map<NativeModel, JXNativeNode> nodes = new IdentityHashMap<>();
    Object nestedKey;
    private boolean rendering;

    NativeScene(NativeModel stage, NativeSurface window) {
        this.stage = stage;
        this.window = window;
    }

    NativeModel sceneModel() {
        return NativeElements.model(Native.value(stage, "scene"));
    }

    NativeModel rootModel() {
        NativeModel scene = sceneModel();
        return scene == null ? null : NativeElements.model(Native.value(scene, "root"));
    }

    private JXElement lastRoot;
    private JXElement lastRootWrapped;

    /** Builds, reconciles and lays out the tree, repeating while virtual lists need other cells. */
    void render() {
        if (rendering || !showing) {
            return;
        }
        rendering = true;
        try {
            for (int pass = 0; pass < 4; pass++) {
                if (!renderOnce()) {
                    break;
                }
            }
            NativeRuntime.updateGeometry(this);
            window.requestRender();
        } finally {
            rendering = false;
        }
        if (firstRender == 0) {
            firstRender = System.nanoTime();
        }
        if (!tabPanes.isEmpty() && !prebuildScheduled) {
            prebuildScheduled = true;
            long wait = PREBUILD_AFTER_MS - (System.nanoTime() - firstRender) / 1_000_000;
            if (wait > 0) {
                // not while the window opens: its first frames come first
                IDLE.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        javafx.application.Platform.runLater(NativeScene.this::prebuildHiddenTab);
                    }
                }, wait);
            } else {
                javafx.application.Platform.runLater(this::prebuildHiddenTab);
            }
        }
    }

    private static final long PREBUILD_AFTER_MS = 300;
    private static final java.util.Timer IDLE = new java.util.Timer("JX idle", true);
    private long firstRender;

    /** Tab panes shown in the last render with tabs whose content has no native nodes yet. */
    final java.util.Set<NativeModel> tabPanes = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean prebuildScheduled;

    /**
     * Builds the content of one tab that is not shown, when the application is idle: JavaFX builds
     * every tab's content with the scene, so its first switch to a tab finds the nodes ready; here
     * the first frame comes without them, and they are made afterwards, one tab per idle turn.
     */
    private void prebuildHiddenTab() {
        prebuildScheduled = false;
        if (!showing || rendering) {
            return;
        }
        for (java.util.Iterator<NativeModel> it = tabPanes.iterator(); it.hasNext(); ) {
            NativeModel pane = it.next();
            JXNativeNode node = nodes.get(pane);
            if (node == null) {
                it.remove(); // not shown any more
                continue;
            }
            List<Object> tabs = Native.list(pane, "tabs");
            int selected = NativeRuntime.selectedTab(pane);
            for (int i = 0; i < tabs.size(); i++) {
                NativeModel tab = NativeElements.model(tabs.get(i));
                NativeModel content = tab == null || i == selected ? null : NativeElements.model(Native.value(tab, "content"));
                if (content != null && !node.holds(content)) {
                    JXElement element = NativeElements.child(content, new NativeElements.Context(this), tab);
                    synchronized (lock) {
                        node.retainAhead(element);
                    }
                    prebuildScheduled = true;
                    javafx.application.Platform.runLater(this::prebuildHiddenTab); // the next one on another turn
                    return;
                }
            }
            it.remove(); // every tab is ready
        }
    }

    /** One build and layout; true when a virtual list or table asked for a different set of cells. */
    private boolean renderOnce() {
        NativeElements.Context ctx = new NativeElements.Context(this);
        NativeModel root = rootModel();
        List<JXElement> layers = new ArrayList<>();
        if (root != null) {
            root.state.remove("renderParent");
            JXElement rootElement = NativeElements.toElement(root, ctx);
            if (rootElement != lastRoot) {
                // the root filling the window, rebuilt only with the root's element: an unchanged
                // screen then reaches the reconcile as the very same element and is skipped
                lastRoot = rootElement;
                lastRootWrapped = asRoot(rootElement);
            }
            layers.add(lastRootWrapped);
        }
        for (NativeOverlay overlay : overlays) {
            JXElement e = overlay.element(this);
            if (e != null) {
                layers.add(e);
            }
        }
        JXElement top = JXElement.of("anchor", JXProps.builder().build(), layers.toArray(new JXElement[0]));
        boolean again;
        synchronized (lock) {
            if (tree == null || !tree.reconcile(top)) {
                tree = JXNativeNode.createBackendNode(top);
                window.setRoot(tree, lock);
            }
            tree.layoutForBackend(Math.max(1, width), Math.max(1, height));
            nodes.clear();
            index(tree);
            again = virtualFlows();
        }
        focusOrder = ctx.focusOrder;
        if (focus != null && !focusOrder.contains(focus) && !nodes.containsKey(focus)) {
            NativeRuntime.setFocus(this, null);
        }
        return again;
    }

    /** The scene root fills the window whatever its max size says, like Scene does with its root. */
    private static JXElement asRoot(JXElement e) {
        JXProps.Builder b = JXProps.builder();
        for (Map.Entry<String, Object> entry : e.getProps().asMap().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("max") && !key.startsWith("min") && !key.equals("margin")) {
                b.set(key, entry.getValue());
            }
        }
        b.set("leftAnchor", 0.0).set("topAnchor", 0.0).set("rightAnchor", 0.0).set("bottomAnchor", 0.0);
        return JXElement.of(e.getType(), b.build(), e.getChildren().toArray(new JXElement[0]));
    }

    private void index(JXNativeNode node) {
        Object m = node.getProperty("model");
        if (m instanceof NativeModel && !nodes.containsKey(m)) {
            nodes.put((NativeModel) m, node);
        }
        for (JXNativeNode child : node.getChildren()) {
            index(child);
        }
    }

    /** Records how many cells each list and table can show; true if one now needs other cells. */
    private boolean virtualFlows() {
        boolean again = false;
        for (Map.Entry<NativeModel, JXNativeNode> e : nodes.entrySet()) {
            JXNativeNode node = e.getValue();
            String type = node.getType();
            if (!"list".equals(type) && !"table".equals(type)) {
                continue;
            }
            NativeModel m = e.getKey();
            int visible = JXControlLayout.visibleCount(node);
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(node);
            double viewHeight = g.viewH;
            Object before = m.state.get("visible");
            Object beforeView = m.state.get("viewHeight");
            m.state.put("viewHeight", (double) viewHeight);
            if ("list".equals(type)) {
                double cell = JXControlLayout.cellHeight(node);
                Object beforeCell = m.state.get("cellHeight");
                m.state.put("cellHeight", cell);
                if (beforeCell == null || ((Number) beforeCell).doubleValue() != cell) {
                    again = true;
                }
            }
            if (before == null || ((Number) before).intValue() < visible) {
                m.state.put("visible", visible);
                again = true;
            }
            if (beforeView == null || ((Number) beforeView).doubleValue() != viewHeight) {
                again = true;
            }
        }
        return again;
    }

    /** Nodes at window (x, y), root first, with their models. */
    List<JXNativeNode> pathAt(double x, double y) {
        synchronized (lock) {
            return tree == null ? new ArrayList<>() : tree.hitPath((int) Math.floor(x), (int) Math.floor(y));
        }
    }

    /** Models along a node path, root first, without repeats. */
    static List<NativeModel> models(List<JXNativeNode> path) {
        List<NativeModel> out = new ArrayList<>();
        for (JXNativeNode node : path) {
            Object m = node.getProperty("model");
            if (m instanceof NativeModel && (out.isEmpty() || out.get(out.size() - 1) != m)) {
                out.add((NativeModel) m);
            }
        }
        return out;
    }

    JXNativeNode node(NativeModel m) {
        return nodes.get(m);
    }

    void input(JXInputEvent event) {
        NativeEvents.handle(this, event);
    }
}
