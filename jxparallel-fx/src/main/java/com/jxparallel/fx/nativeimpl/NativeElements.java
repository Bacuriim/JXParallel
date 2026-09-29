package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.jxparallel.fx.Fx;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Turns native-mode JX nodes into the element tree jxparallel-ui renders: each JavaFX class maps to
 * a native element type, and the JavaFX property values the native layout and renderer understand
 * are copied under their native names. Every element carries its model ({@code model} prop), so
 * the runtime maps what the pointer hits back to the JX node. Classes without a native element are
 * listed in {@link #unsupported()} and drawn as an empty pane of their preferred size, so the rest
 * of the screen still renders.
 */
public final class NativeElements {
    private static final Set<String> UNSUPPORTED = new ConcurrentSkipListSet<>();
    /** ARGB pixels per JavaFX image, read once. */
    private static final Map<Image, int[]> PIXELS = new WeakHashMap<>();

    private NativeElements() {
    }

    public static Set<String> unsupported() {
        return UNSUPPORTED;
    }

    /** What rendering needs from the window showing the tree: focus, pointer state, cell pools. */
    static class Context {
        final NativeScene scene;
        final List<NativeModel> focusOrder = new ArrayList<>();
        final NativeCss css;

        Context(NativeScene scene) {
            this.scene = scene;
            this.css = new NativeCss(scene);
        }

        boolean focused(NativeModel m) {
            return scene != null && scene.focus == m && scene.windowFocused;
        }

        boolean hover(NativeModel m) {
            return scene != null && scene.hover.contains(m);
        }

        boolean pressed(NativeModel m) {
            return scene != null && scene.pressed == m && scene.pressInside;
        }
    }

    /** The element of a node outside any window (screen checks): no focus or pointer state. */
    public static JXElement toElement(NativeModel m) {
        return toElement(m, new Context(null));
    }

    static JXElement toElement(NativeModel m, Context ctx) {
        NativeRuntime.installLineMetrics();
        return toElement(m, ctx, null);
    }

    // ---- incremental rendering -------------------------------------------------------------------

    /**
     * Bumped when every element must be rebuilt (the scene's stylesheets changed). Otherwise a node's
     * element is rebuilt only when the node, its state or something below it changed, like JavaFX
     * syncing only its dirty nodes; the reconcile then skips unchanged elements by identity.
     */
    private static long epoch;

    /**
     * The list or table whose cells are being set up for the element it is building: their changes
     * (index, item, selection) are part of that element, so invalidation stops below it instead of
     * making it stale at once and rebuilding every visible cell on the next frame.
     */
    static NativeModel building;

    /** The node and its ancestors (and the control an editor belongs to) must rebuild their elements. */
    static void invalidate(NativeModel m) {
        int depth = 0;
        for (NativeModel c = m; c != null && c != building && depth < 10_000; depth++) {
            c.version++;
            Object owner = c.state.get("editorOf");
            if (owner instanceof NativeModel && owner != c) {
                invalidate((NativeModel) owner); // a spinner or combo box shows its editor's text
            }
            c = NativeCss.parentOf(c);
        }
    }

    /**
     * Also every node below: for changes CSS matching sees through descendant selectors (classes,
     * ids, pseudo-class states) and for inherited ones (disabled), and for a node that moved.
     */
    static void invalidateSubtree(NativeModel m) {
        invalidate(m);
        invalidateBelow(m, 0);
    }

    private static void invalidateBelow(NativeModel m, int depth) {
        if (depth > 10_000) {
            return;
        }
        for (NativeModel child : NativeRuntime.childrenOf(m)) {
            child.version++;
            invalidateBelow(child, depth + 1);
        }
        // the rows and cells of a list or table, kept in its cell pool rather than as children
        Object pool = m.state.get("cells");
        if (pool instanceof Map) {
            for (Object cell : ((Map<?, ?>) pool).values()) {
                if (cell instanceof NativeModel) {
                    ((NativeModel) cell).version++;
                    invalidateBelow((NativeModel) cell, depth + 1);
                }
            }
        }
    }

    /** Every element is rebuilt on the next frame. */
    static void invalidateAll() {
        epoch++;
    }

    /** {@code expanded}: for a TitledPane in an Accordion, which decides it; {@code null} elsewhere. */
    private static JXElement toElement(NativeModel m, Context ctx, Boolean expanded) {
        if (m.element != null && m.elementVersion == m.version && m.elementEpoch == epoch && m.elementScene == ctx.scene
                && java.util.Objects.equals(m.elementExpanded, expanded)) {
            if (!m.elementFocus.isEmpty()) {
                ctx.focusOrder.addAll(m.elementFocus); // nothing changed here or below: the same element
            }
            return m.element;
        }
        long version = m.version; // a change while building (a synced editor) makes the result stale at once
        long builtEpoch = epoch;
        int focusStart = ctx.focusOrder.size();
        JXElement element = build(m, ctx, expanded);
        m.element = element;
        m.elementVersion = version;
        m.elementEpoch = builtEpoch;
        m.elementScene = ctx.scene;
        m.elementExpanded = expanded;
        m.elementFocus = focusStart == ctx.focusOrder.size() ? java.util.Collections.<NativeModel>emptyList()
                : new ArrayList<>(ctx.focusOrder.subList(focusStart, ctx.focusOrder.size()));
        return element;
    }

    private static JXElement build(NativeModel m, Context ctx, Boolean expanded) {
        String fx = m.type;
        if (ctx.scene != null && ("ScrollPane".equals(fx) || "TitledPane".equals(fx))) {
            NativeRuntime.installSkin(m); // shown for the first time (only rebuilt nodes get here)
        }
        JXProps.Builder p = JXProps.builder();
        p.set("model", m).set("key", m); // keyed: a node that moves among its siblings keeps its native node
        common(m, p, ctx);
        if (NativeRuntime.focusable(m)) {
            ctx.focusOrder.add(m);
            if (ctx.focused(m)) {
                p.set("focused", true);
            }
        }
        if (ctx.hover(m)) {
            p.set("hover", true);
        }
        if (ctx.pressed(m)) {
            p.set("pressed", true);
        }
        JXElement element = build(m, fx, p, ctx, expanded);
        return element;
    }

    private static JXElement build(NativeModel m, String fx, JXProps.Builder p, Context ctx, Boolean expanded) {
        switch (fx) {
            case "Label":
            case "ListCell":
            case "TableCell":
            case "TreeCell":
            case "IndexedCell":
            case "Cell":
            case "DateCell":
            case "Tooltip": {
                labeled(m, p);
                NativeModel graphic = model(Native.value(m, "graphic"));
                if (graphic != null || !"Label".equals(fx)) {
                    p.set("value", string(m, "text"));
                    if ("Label".equals(fx) || "Tooltip".equals(fx)) {
                        p.set("paintBackground", false);
                        p.set("padding", insetsOr(m, 0));
                    } else {
                        // a cell: selection and stripes from its list or table
                        int index = (int) number(m, "index", -1);
                        p.set("selected", Boolean.TRUE.equals(Native.value(m, "selected")))
                                .set("odd", index % 2 == 1)
                                .set("listFocused", Boolean.TRUE.equals(m.state.get("listFocused")));
                        if ("TableCell".equals(fx)) {
                            p.set("tableCell", true).set("paintBackground", false).set("padding", insetsOr(m, 2, 3));
                        }
                    }
                    finish(m, p, ctx);
                    return graphic == null ? JXElement.of("cell", p.build())
                            : JXElement.of("cell", p.build(), child(graphic, ctx, m));
                }
                p.set("value", string(m, "text"));
                finish(m, p, ctx);
                return JXElement.of("#text", p.build());
            }
            case "Text": {
                p.set("value", string(m, "text"));
                font(m, p);
                Object fill = Native.value(m, "fill");
                if (fill instanceof Color) {
                    p.set("textFill", argb((Color) fill));
                } else {
                    p.set("textFill", 0xFF000000); // Text's default fill is black, not Modena's text color
                }
                if (Boolean.TRUE.equals(Native.value(m, "underline"))) {
                    p.set("underline", true);
                }
                finish(m, p, ctx);
                return JXElement.of("#text", p.build());
            }
            case "TextFlow": {
                finish(m, p, ctx);
                return container("textflow", m, p, ctx);
            }
            case "Button":
            case "ToggleButton": {
                labeled(m, p);
                p.set("label", string(m, "text"));
                if (Boolean.TRUE.equals(Native.value(m, "defaultButton"))) {
                    p.set("defaultButton", true);
                }
                if ("ToggleButton".equals(fx) && Boolean.TRUE.equals(Native.value(m, "selected"))) {
                    p.set("selected", true);
                }
                finish(m, p, ctx);
                NativeModel graphic = model(Native.value(m, "graphic"));
                String type = "Button".equals(fx) ? "button" : "toggle";
                return graphic == null ? JXElement.of(type, p.build()) : JXElement.of(type, p.build(), child(graphic, ctx, m));
            }
            case "Hyperlink":
                labeled(m, p);
                p.set("label", string(m, "text")).set("visited", Boolean.TRUE.equals(Native.value(m, "visited")));
                finish(m, p, ctx);
                return JXElement.of("hyperlink", p.build());
            case "CheckBox":
                labeled(m, p);
                p.set("label", string(m, "text")).set("checked", Boolean.TRUE.equals(Native.value(m, "selected")))
                        .set("indeterminate", Boolean.TRUE.equals(Native.value(m, "indeterminate")));
                finish(m, p, ctx);
                return JXElement.of("checkbox", p.build());
            case "RadioButton":
                labeled(m, p);
                p.set("label", string(m, "text")).set("checked", Boolean.TRUE.equals(Native.value(m, "selected")));
                finish(m, p, ctx);
                return JXElement.of("radio", p.build());
            case "TextField":
            case "PasswordField":
            case "CustomTextField":
            case "CustomPasswordField": {
                textInput(m, p, ctx);
                Object align = Native.value(m, "alignment");
                if (align instanceof Enum) {
                    String name = ((Enum<?>) align).name();
                    p.set("textAlignment", name.endsWith("RIGHT") ? "RIGHT" : name.endsWith("CENTER") ? "CENTER" : "LEFT");
                }
                p.set("prefColumnCount", number(m, "prefColumnCount", 12));
                finish(m, p, ctx);
                String type = fx.contains("Password") ? "password" : "input";
                List<JXElement> sides = new ArrayList<>();
                NativeModel left = model(Native.value(m, "left"));
                NativeModel right = model(Native.value(m, "right"));
                if (left != null) {
                    sides.add(withSide(child(left, ctx, m), "left"));
                }
                if (right != null) {
                    sides.add(withSide(child(right, ctx, m), "right"));
                }
                return JXElement.of(type, p.build(), sides.toArray(new JXElement[0]));
            }
            case "TextArea":
                textInput(m, p, ctx);
                p.set("wrapText", Boolean.TRUE.equals(Native.value(m, "wrapText")));
                p.set("scrollTop", number(m, "scrollTop", 0));
                p.set("prefColumnCount", number(m, "prefColumnCount", 40));
                p.set("prefRowCount", number(m, "prefRowCount", 10));
                finish(m, p, ctx);
                return JXElement.of("textarea", p.build());
            case "ComboBox":
            case "ChoiceBox": {
                boolean editable = "ComboBox".equals(fx) && Boolean.TRUE.equals(Native.value(m, "editable"));
                List<Object> options = new ArrayList<>();
                for (Object item : Native.list(m, "items")) {
                    options.add(NativeCells.display(m, item));
                }
                Object value = Native.value(m, "value");
                p.set("options", options.toArray());
                p.set("showing", NativeRuntime.popupOwner(ctx.scene) == m);
                if (editable) {
                    NativeModel editor = NativeText.editor(m);
                    p.set("editable", true).set("value", string(editor, "text"));
                    caret(editor, p, ctx, m);
                } else {
                    String shown = value == null ? "" : NativeCells.display(m, value);
                    p.set("value", shown);
                }
                p.set("prompt", string(m, "promptText"));
                finish(m, p, ctx);
                return JXElement.of("select", p.build());
            }
            case "Spinner": {
                NativeModel editor = NativeText.editor(m);
                watchFactory(m);
                NativeText.syncSpinner(m, editor);
                p.set("value", string(editor, "text"));
                caret(editor, p, ctx, m);
                finish(m, p, ctx);
                return JXElement.of("spinner", p.build());
            }
            case "DatePicker": {
                NativeModel editor = NativeText.editor(m);
                NativeText.syncDatePicker(m, editor);
                p.set("value", string(editor, "text")).set("prompt", string(m, "promptText"));
                p.set("showing", NativeRuntime.popupOwner(ctx.scene) == m);
                caret(editor, p, ctx, m);
                finish(m, p, ctx);
                return JXElement.of("datepicker", p.build());
            }
            case "ProgressBar":
                p.set("progress", number(m, "progress", -1));
                finish(m, p, ctx);
                return JXElement.of("progress", p.build());
            case "ProgressIndicator":
                p.set("progress", number(m, "progress", -1));
                finish(m, p, ctx);
                return JXElement.of("indicator", p.build());
            case "Slider":
                p.set("min", number(m, "min", 0)).set("max", number(m, "max", 100)).set("value", number(m, "value", 0));
                Object orientation = Native.value(m, "orientation");
                if (orientation instanceof Enum) {
                    p.set("orientation", ((Enum<?>) orientation).name().toLowerCase());
                }
                finish(m, p, ctx);
                return JXElement.of("slider", p.build());
            case "Separator": {
                Object o = Native.value(m, "orientation");
                p.set("orientation", o instanceof Enum ? ((Enum<?>) o).name().toLowerCase() : "horizontal");
                finish(m, p, ctx);
                return JXElement.of("separator", p.build());
            }
            case "TitledPane": {
                labeled(m, p);
                p.set("label", string(m, "text"))
                        .set("expanded", expanded != null ? expanded : !Boolean.FALSE.equals(Native.value(m, "expanded")))
                        .set("collapsible", !Boolean.FALSE.equals(Native.value(m, "collapsible")));
                finish(m, p, ctx);
                NativeModel content = model(Native.value(m, "content"));
                return content == null ? JXElement.of("titled", p.build())
                        : JXElement.of("titled", p.build(), child(content, ctx, m));
            }
            case "Accordion": {
                // Like Accordion: only the expanded pane is open, whatever the panes' own expanded flag says.
                NativeModel open = model(Native.value(m, "expandedPane"));
                List<JXElement> panes = new ArrayList<>();
                for (Object o : Native.list(m, "panes")) {
                    NativeModel pane = model(o);
                    if (pane != null) {
                        pane.state.put("renderParent", m);
                        panes.add(toElement(pane, ctx, pane == open));
                    }
                }
                finish(m, p, ctx);
                return JXElement.of("accordion", p.build(), panes.toArray(new JXElement[0]));
            }
            case "ScrollPane": {
                p.set("hvalue", fraction(m, "h")).set("vvalue", fraction(m, "v"))
                        .set("fitToWidth", Boolean.TRUE.equals(Native.value(m, "fitToWidth")))
                        .set("fitToHeight", Boolean.TRUE.equals(Native.value(m, "fitToHeight")));
                Object hbar = Native.value(m, "hbarPolicy");
                Object vbar = Native.value(m, "vbarPolicy");
                if (hbar instanceof Enum) {
                    p.set("hbarPolicy", ((Enum<?>) hbar).name());
                }
                if (vbar instanceof Enum) {
                    p.set("vbarPolicy", ((Enum<?>) vbar).name());
                }
                finish(m, p, ctx);
                NativeModel content = model(Native.value(m, "content"));
                return content == null ? JXElement.of("scroll", p.build()) : JXElement.of("scroll", p.build(), child(content, ctx, m));
            }
            case "ListView":
                finish(m, p, ctx);
                return NativeCells.list(m, p, ctx);
            case "TableView":
                finish(m, p, ctx);
                return NativeCells.table(m, p, ctx);
            case "TabPane": {
                List<Object> tabs = Native.list(m, "tabs");
                String[] titles = new String[tabs.size()];
                boolean[] closable = new boolean[tabs.size()];
                for (int i = 0; i < tabs.size(); i++) {
                    NativeModel tab = model(tabs.get(i));
                    if (tab != null) {
                        tab.state.put("renderParent", m); // every tab's title is drawn here: its changes rebuild the pane
                    }
                    titles[i] = tab == null ? "" : string(tab, "text");
                    closable[i] = tab == null || !Boolean.FALSE.equals(Native.value(tab, "closable"));
                }
                int selected = NativeRuntime.selectedTab(m);
                if (ctx.scene != null && tabs.size() > 1) {
                    ctx.scene.tabPanes.add(m); // its other tabs are built when the application is idle
                }
                // like TabPaneSkin keeping every tab's content: switching back finds its native nodes as they were
                p.set("titles", titles).set("closable", closable).set("selected", selected).set("retainChildren", true);
                Object policy = Native.value(m, "tabClosingPolicy");
                p.set("closingPolicy", policy instanceof Enum ? ((Enum<?>) policy).name() : "SELECTED_TAB");
                finish(m, p, ctx);
                NativeModel tab = selected >= 0 && selected < tabs.size() ? model(tabs.get(selected)) : null;
                NativeModel content = tab == null ? null : model(Native.value(tab, "content"));
                if (tab != null) {
                    tab.state.put("renderParent", m);
                }
                return content == null ? JXElement.of("tabs", p.build()) : JXElement.of("tabs", p.build(), child(content, ctx, tab));
            }
            case "Pagination": {
                int count = (int) number(m, "pageCount", 1);
                p.set("pageCount", count).set("current", (int) number(m, "currentPageIndex", 0))
                        .set("maxPageIndicatorCount", (int) number(m, "maxPageIndicatorCount", 10));
                finish(m, p, ctx);
                NativeModel page = NativeRuntime.page(m);
                return page == null ? JXElement.of("pagination", p.build()) : JXElement.of("pagination", p.build(), child(page, ctx, m));
            }
            case "ImageView": {
                Object image = Native.value(m, "image");
                if (image instanceof Image) {
                    Image img = (Image) image;
                    int[] pixels = pixels(img);
                    if (pixels == null && img.getProgress() < 1 && m.state.get("awaitedImage") != img) {
                        // loading in the background: show it when it arrives
                        m.state.put("awaitedImage", img);
                        img.progressProperty().addListener(o -> {
                            if (img.getProgress() >= 1) {
                                NativeElements.invalidate(m);
                                NativeRuntime.pulse();
                            }
                        });
                    }
                    p.set("pixels", pixels).set("imageWidth", img.getWidth()).set("imageHeight", img.getHeight());
                }
                p.set("fitWidth", number(m, "fitWidth", 0)).set("fitHeight", number(m, "fitHeight", 0))
                        .set("preserveRatio", Boolean.TRUE.equals(Native.value(m, "preserveRatio")));
                finish(m, p, ctx);
                return JXElement.of("image", p.build());
            }
            case "ButtonBar": {
                finish(m, p, ctx);
                List<JXElement> buttons = new ArrayList<>();
                for (Object o : NativeRuntime.orderedButtons(m)) {
                    NativeModel b = model(o);
                    if (b != null && isManaged(b)) {
                        buttons.add(child(b, ctx, m));
                    }
                }
                return JXElement.of("buttonbar", p.build(), buttons.toArray(new JXElement[0]));
            }
            case "VBox":
                p.set("gap", number(m, "spacing", 0));
                alignment(m, p);
                p.set("fillWidth", Native.value(m, "fillWidth"));
                finish(m, p, ctx);
                return container("column", m, p, ctx);
            case "HBox":
                p.set("gap", number(m, "spacing", 0));
                alignment(m, p);
                p.set("fillHeight", Native.value(m, "fillHeight"));
                finish(m, p, ctx);
                return container("row", m, p, ctx);
            case "StackPane":
                alignment(m, p);
                finish(m, p, ctx);
                return container("stack", m, p, ctx);
            case "BorderPane": {
                finish(m, p, ctx);
                List<JXElement> slots = new ArrayList<>();
                for (String slot : new String[]{"top", "left", "center", "right", "bottom"}) {
                    NativeModel c = model(Native.value(m, slot));
                    if (c != null && isManaged(c)) {
                        slots.add(withSide(child(c, ctx, m), null, slot));
                    }
                }
                if (slots.isEmpty()) {
                    return container("border", m, p, ctx);
                }
                return JXElement.of("border", p.build(), slots.toArray(new JXElement[0]));
            }
            case "GridPane":
                p.set("hgap", number(m, "hgap", 0)).set("vgap", number(m, "vgap", 0));
                alignment(m, p);
                gridConstraints(m, p);
                finish(m, p, ctx);
                return container("grid", m, p, ctx);
            case "AnchorPane":
                finish(m, p, ctx);
                return container("anchor", m, p, ctx);
            case "Pane":
            case "Region":
            case "Group":
            case "Parent":
                finish(m, p, ctx);
                return container("pane", m, p, ctx);
            case "FlowPane":
                p.set("hgap", number(m, "hgap", 0)).set("vgap", number(m, "vgap", 0));
                alignment(m, p);
                Object flowOrientation = Native.value(m, "orientation");
                if (flowOrientation instanceof Enum) {
                    p.set("orientation", ((Enum<?>) flowOrientation).name().toLowerCase());
                }
                if (m.values.containsKey("prefWrapLength")) {
                    p.set("prefWrapLength", number(m, "prefWrapLength", 400));
                }
                finish(m, p, ctx);
                return container("flow", m, p, ctx);
            case "TilePane":
                p.set("hgap", number(m, "hgap", 0)).set("vgap", number(m, "vgap", 0));
                alignment(m, p);
                finish(m, p, ctx);
                return container("tile", m, p, ctx);
            default:
                if (m.is(javafx.scene.layout.Pane.class)) {
                    finish(m, p, ctx);
                    return container("pane", m, p, ctx); // application subclasses of Pane and custom regions
                }
                UNSUPPORTED.add(m.fxType.getName());
                finish(m, p, ctx);
                return container("pane", m, p, ctx);
        }
    }

    /** CSS last: stylesheets and inline styles override values set in code, as in JavaFX. */
    private static void finish(NativeModel m, JXProps.Builder p, Context ctx) {
        ctx.css.apply(m, p);
    }

    static JXElement child(NativeModel child, Context ctx, NativeModel parent) {
        child.state.put("renderParent", parent);
        return toElement(child, ctx);
    }

    private static JXElement container(String type, NativeModel m, JXProps.Builder p, Context ctx) {
        List<JXElement> children = new ArrayList<>();
        for (Object child : m.children) {
            // Like JavaFX: an invisible node keeps its place in the layout unless it is also unmanaged.
            if (child instanceof NativeModel && isManaged((NativeModel) child)) {
                children.add(child((NativeModel) child, ctx, m));
            }
        }
        return JXElement.of(type, p.build(), children.toArray(new JXElement[0]));
    }

    /** The spinner shows its value factory's value, which changes without the spinner knowing: listen. */
    private static void watchFactory(NativeModel spinner) {
        javafx.scene.control.SpinnerValueFactory<?> factory = NativeImpls.factory(spinner);
        if (factory != null && spinner.state.get("watchedFactory") != factory) {
            spinner.state.put("watchedFactory", factory);
            factory.valueProperty().addListener(o -> {
                if (spinner.state.get("watchedFactory") == factory) {
                    NativeElements.invalidate(spinner);
                    NativeRuntime.pulse();
                }
            });
        }
    }

    static boolean isManaged(NativeModel m) {
        return !Boolean.FALSE.equals(Native.value(m, "managed"));
    }

    private static JXElement withSide(JXElement e, String side) {
        return withSide(e, side, null);
    }

    /** A copy of the element with one more prop ({@code side} of a text field, or {@code position} in a border). */
    private static JXElement withSide(JXElement e, String side, String position) {
        JXProps.Builder b = JXProps.builder();
        for (Map.Entry<String, Object> entry : e.getProps().asMap().entrySet()) {
            b.set(entry.getKey(), entry.getValue());
        }
        if (side != null) {
            b.set("side", side);
        }
        if (position != null) {
            b.set("position", position);
        }
        return JXElement.of(e.getType(), b.build(), e.getChildren().toArray(new JXElement[0]));
    }

    /** Text, caret and selection of a text input (the caret only while it has the focus). */
    private static void textInput(NativeModel m, JXProps.Builder p, Context ctx) {
        p.set("value", string(m, "text")).set("prompt", string(m, "promptText"));
        font(m, p);
        caret(m, p, ctx, m);
    }

    private static void caret(NativeModel text, JXProps.Builder p, Context ctx, NativeModel focusOwner) {
        if (ctx.focused(focusOwner)) {
            int length = string(text, "text").length();
            int caret = Math.min(length, NativeText.caret(text));
            int anchor = Math.min(length, NativeText.anchor(text));
            p.set("caret", caret).set("anchor", anchor);
        }
    }

    /** Font, text fill, underline and wrapping of a Labeled. */
    private static void labeled(NativeModel m, JXProps.Builder p) {
        font(m, p);
        Object fill = Native.value(m, "textFill");
        if (fill instanceof Color) {
            p.set("textFill", argb((Color) fill));
        }
        if (Boolean.TRUE.equals(Native.value(m, "underline"))) {
            p.set("underline", true);
        }
        if (Boolean.TRUE.equals(Native.value(m, "wrapText"))) {
            p.set("wrapText", true);
        }
        Object align = Native.value(m, "alignment");
        if (align instanceof Enum) {
            p.set("alignment", ((Enum<?>) align).name());
        }
        Object textAlign = Native.value(m, "textAlignment");
        if (textAlign instanceof Enum && !"LEFT".equals(((Enum<?>) textAlign).name())) {
            p.set("textAlignment", ((Enum<?>) textAlign).name());
        }
    }

    /** The last font asked about and its answer: nodes share a few fonts, and this runs per rebuilt label. */
    private static Font lastFont;
    private static boolean lastBold;

    private static boolean bold(Font f) {
        if (f != lastFont) {
            String style = (f.getStyle() + " " + f.getName()).toLowerCase();
            lastBold = style.contains("bold") || style.contains("black") || style.contains("heavy") || style.contains("semibold");
            lastFont = f;
        }
        return lastBold;
    }

    private static void font(NativeModel m, JXProps.Builder p) {
        Object font = Native.value(m, "font");
        if (font instanceof Font) {
            Font f = (Font) font;
            if (f.getSize() != 12) {
                p.set("fontSize", f.getSize());
            }
            if (bold(f)) {
                p.set("bold", true);
            }
        }
    }

    // Constraint keys, built once: common() runs for every rebuilt element (every cell while scrolling).
    private static final String[] SIZES = {"minWidth", "prefWidth", "maxWidth", "minHeight", "prefHeight", "maxHeight"};
    private static final String[] ANCHOR_KEYS = {"AnchorPane.topAnchor", "AnchorPane.rightAnchor",
        "AnchorPane.bottomAnchor", "AnchorPane.leftAnchor"};
    private static final String[] ANCHOR_PROPS = {"topAnchor", "rightAnchor", "bottomAnchor", "leftAnchor"};
    private static final String[] MARGIN_KEYS = {"VBox.margin", "HBox.margin", "StackPane.margin", "BorderPane.margin",
        "GridPane.margin", "FlowPane.margin", "TilePane.margin"};
    private static final String[] ALIGNMENT_KEYS = {"StackPane.alignment", "BorderPane.alignment", "TilePane.alignment"};

    /** Sizes, padding, visibility and what the parent layout attached to this node. */
    private static void common(NativeModel m, JXProps.Builder p, Context ctx) {
        for (String size : SIZES) {
            double v = number(m, size, -1);
            if (v != -1) {
                // JavaFX USE_PREF_SIZE (-Infinity) means "same as pref"; the native layout reads it the same way.
                p.set(size, v);
            }
        }
        Object padding = Native.value(m, "padding");
        if (padding instanceof Insets) {
            p.set("padding", insets((Insets) padding));
        }
        if (Boolean.FALSE.equals(Native.value(m, "visible"))) {
            p.set("hidden", true);
        }
        if (Boolean.TRUE.equals(Native.value(m, "disable"))) {
            p.set("disabled", true);
        }
        if (Boolean.TRUE.equals(Native.value(m, "mouseTransparent"))) {
            p.set("mouseTransparent", true);
        }
        double opacity = number(m, "opacity", 1);
        if (opacity < 1) {
            p.set("opacity", opacity);
        }
        Object effect = Native.value(m, "effect");
        if (effect instanceof javafx.scene.effect.GaussianBlur) {
            p.set("blur", ((javafx.scene.effect.GaussianBlur) effect).getRadius());
        } else if (effect instanceof javafx.scene.effect.BoxBlur) {
            p.set("blur", ((javafx.scene.effect.BoxBlur) effect).getWidth() / 2);
        }
        if (Native.value(m, "clip") != null) {
            p.set("clip", true);
        }
        double x = number(m, "layoutX", 0);
        double y = number(m, "layoutY", 0);
        if (x != 0 || y != 0) {
            p.set("layoutX", x).set("layoutY", y);
        }
        for (int i = 0; i < ANCHOR_KEYS.length; i++) {
            Object v = Native.constraint(m, ANCHOR_KEYS[i]);
            if (v instanceof Number) {
                p.set(ANCHOR_PROPS[i], ((Number) v).doubleValue());
            }
        }
        for (String key : MARGIN_KEYS) {
            Object margin = Native.constraint(m, key);
            if (margin instanceof Insets) {
                p.set("margin", insets((Insets) margin));
            }
        }
        for (String key : ALIGNMENT_KEYS) {
            Object a = Native.constraint(m, key);
            if (a instanceof javafx.geometry.Pos) {
                p.set("halignment", ((javafx.geometry.Pos) a).getHpos().name()).set("valignment", ((javafx.geometry.Pos) a).getVpos().name());
            }
        }
        Object vgrow = Native.constraint(m, "VBox.vgrow");
        if (vgrow == null) {
            vgrow = Native.constraint(m, "GridPane.vgrow");
        }
        if (vgrow != null) {
            p.set("vgrow", vgrow.toString().toLowerCase());
        }
        Object hgrow = Native.constraint(m, "HBox.hgrow");
        if (hgrow == null) {
            hgrow = Native.constraint(m, "GridPane.hgrow");
        }
        if (hgrow != null) {
            p.set("hgrow", hgrow.toString().toLowerCase());
        }
        grid(m, p);
    }

    private static void grid(NativeModel m, JXProps.Builder p) {
        Object column = Native.constraint(m, "GridPane.columnIndex");
        Object row = Native.constraint(m, "GridPane.rowIndex");
        if (column instanceof Number) {
            p.set("column", ((Number) column).intValue());
        }
        if (row instanceof Number) {
            p.set("row", ((Number) row).intValue());
        }
        Object columnSpan = Native.constraint(m, "GridPane.columnSpan");
        Object rowSpan = Native.constraint(m, "GridPane.rowSpan");
        if (columnSpan instanceof Number) {
            p.set("columnSpan", ((Number) columnSpan).intValue());
        }
        if (rowSpan instanceof Number) {
            p.set("rowSpan", ((Number) rowSpan).intValue());
        }
        Object halign = Native.constraint(m, "GridPane.halignment");
        Object valign = Native.constraint(m, "GridPane.valignment");
        if (halign instanceof Enum) {
            p.set("halignment", ((Enum<?>) halign).name());
        }
        if (valign instanceof Enum) {
            p.set("valignment", ((Enum<?>) valign).name());
        }
        Object fillW = Native.constraint(m, "GridPane.fillWidth");
        Object fillH = Native.constraint(m, "GridPane.fillHeight");
        if (fillW instanceof Boolean) {
            p.set("cellFillWidth", fillW);
        }
        if (fillH instanceof Boolean) {
            p.set("cellFillHeight", fillH);
        }
    }

    /** Column and row constraints of a GridPane, in the native grid's format. */
    private static void gridConstraints(NativeModel m, JXProps.Builder p) {
        List<Object> columns = new ArrayList<>();
        for (Object c : Native.list(m, "columnConstraints")) {
            if (c instanceof javafx.scene.layout.ColumnConstraints) {
                javafx.scene.layout.ColumnConstraints cc = (javafx.scene.layout.ColumnConstraints) c;
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                putSize(map, "minWidth", cc.getMinWidth());
                putSize(map, "prefWidth", cc.getPrefWidth());
                putSize(map, "maxWidth", cc.getMaxWidth());
                if (cc.getPercentWidth() >= 0) {
                    map.put("percentWidth", cc.getPercentWidth());
                }
                if (cc.getHgrow() != null) {
                    map.put("hgrow", cc.getHgrow().name().toLowerCase());
                }
                if (cc.getHalignment() != null) {
                    map.put("halignment", cc.getHalignment().name());
                }
                map.put("fillWidth", cc.isFillWidth());
                columns.add(map);
            }
        }
        List<Object> rows = new ArrayList<>();
        for (Object r : Native.list(m, "rowConstraints")) {
            if (r instanceof javafx.scene.layout.RowConstraints) {
                javafx.scene.layout.RowConstraints rc = (javafx.scene.layout.RowConstraints) r;
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                putSize(map, "minHeight", rc.getMinHeight());
                putSize(map, "prefHeight", rc.getPrefHeight());
                putSize(map, "maxHeight", rc.getMaxHeight());
                if (rc.getPercentHeight() >= 0) {
                    map.put("percentHeight", rc.getPercentHeight());
                }
                if (rc.getVgrow() != null) {
                    map.put("vgrow", rc.getVgrow().name().toLowerCase());
                }
                if (rc.getValignment() != null) {
                    map.put("valignment", rc.getValignment().name());
                }
                map.put("fillHeight", rc.isFillHeight());
                rows.add(map);
            }
        }
        if (!columns.isEmpty()) {
            p.set("columns", columns);
        }
        if (!rows.isEmpty()) {
            p.set("rows", rows);
        }
    }

    private static void putSize(java.util.Map<String, Object> map, String key, double value) {
        if (value != -1) { // USE_COMPUTED_SIZE
            map.put(key, value);
        }
    }

    private static void alignment(NativeModel m, JXProps.Builder p) {
        Object a = Native.value(m, "alignment");
        if (a instanceof Enum) {
            p.set("alignment", ((Enum<?>) a).name());
        }
    }

    static double[] insets(Insets i) {
        return new double[]{i.getTop(), i.getRight(), i.getBottom(), i.getLeft()};
    }

    private static double[] insetsOr(NativeModel m, double fallback) {
        return insetsOr(m, fallback, fallback);
    }

    private static double[] insetsOr(NativeModel m, double vertical, double horizontal) {
        Object padding = Native.value(m, "padding");
        if (padding instanceof Insets) {
            return insets((Insets) padding);
        }
        // a control's default padding, shared: props are never changed, and every visible cell asks for one
        return DEFAULT_PADDINGS.computeIfAbsent(vertical * 1000 + horizontal, k -> new double[]{vertical, horizontal, vertical, horizontal});
    }

    private static final Map<Double, double[]> DEFAULT_PADDINGS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Sets a layout constraint as the layouts' static setters do ("HBox.hgrow"). */
    static void constraintSet(NativeModel m, String key, Object value) {
        m.constraints.put(key, value);
        Native.changed(m);
    }

    /** The native model of a node value: stored as the model itself or as its JX object. */
    static NativeModel model(Object value) {
        if (value instanceof NativeModel) {
            return (NativeModel) value;
        }
        Object fx = value == null ? null : Fx.fx(value);
        return fx instanceof NativeModel ? (NativeModel) fx : null;
    }

    static String string(NativeModel m, String name) {
        Object v = Native.value(m, name);
        return v == null ? "" : v.toString();
    }

    /** A scroll pane's h/v value as the layout's 0..1 fraction of its hmin..hmax (vmin..vmax) range. */
    static double fraction(NativeModel m, String axis) {
        double min = number(m, axis + "min", 0);
        double max = number(m, axis + "max", 1);
        double value = number(m, axis + "value", min);
        return max > min ? (value - min) / (max - min) : 0;
    }

    static double number(NativeModel m, String name, double fallback) {
        Object v = Native.value(m, name);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    static int argb(Color c) {
        return (int) Math.round(c.getOpacity() * 255) << 24 | (int) Math.round(c.getRed() * 255) << 16
                | (int) Math.round(c.getGreen() * 255) << 8 | (int) Math.round(c.getBlue() * 255);
    }

    /** ARGB pixels of a JavaFX image, read once per image (images are immutable once loaded). */
    static int[] pixels(Image image) {
        synchronized (PIXELS) {
            int[] cached = PIXELS.get(image);
            if (cached != null) {
                return cached;
            }
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            PixelReader reader = image.getPixelReader();
            if (reader == null || w <= 0 || h <= 0) {
                return null;
            }
            int[] argb = new int[w * h];
            reader.getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
            PIXELS.put(image, argb);
            return argb;
        }
    }
}
