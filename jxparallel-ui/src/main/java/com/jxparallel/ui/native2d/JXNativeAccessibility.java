package com.jxparallel.ui.native2d;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jxparallel.ui.accessibility.JXAccessibilityEvent;
import com.jxparallel.ui.accessibility.JXAccessibilityRole;
import com.jxparallel.ui.accessibility.JXAccessibleAction;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.accessibility.JXAccessibleState;

/**
 * The accessibility tree of a live node tree, the actions an assistive technology can run on it and the events found
 * between two snapshots. One instance per window; display thread only (the snapshots it makes are immutable and may go
 * anywhere). Needs no window: tests lay a tree out with {@code layoutForBackend} and take a snapshot.
 *
 * <p>Ids are stable while a node lives: a node that reconciles in place keeps its id; the window itself is
 * {@link #ROOT_ID}. Parts a control draws without a node of their own (the tabs of a tab bar, the buttons of a
 * pagination, the options of an open combo box, the header of a table) get ids derived from their owner.
 */
public final class JXNativeAccessibility {
    public static final long ROOT_ID = 1;
    private static final long SYNTHETIC = 1L << 32;
    private static final int OPTION = 1;
    private static final int TAB = 2;
    private static final int PAGE = 3;

    /** What an id stands for: a node, or a part of one at a rectangle. */
    private static final class Target {
        final JXNativeNode node;
        final int kind;
        final int index;
        final int[] rect;

        Target(JXNativeNode node, int kind, int index, int[] rect) {
            this.node = node;
            this.kind = kind;
            this.index = index;
            this.rect = rect;
        }
    }

    private IdentityHashMap<JXNativeNode, Long> ids = new IdentityHashMap<JXNativeNode, Long>();
    private long nextId = 2;
    private Map<Long, Target> targets = new HashMap<Long, Target>();
    private JXAccessibleNode last;
    private JXNativeNode root;
    private Consumer<JXNativeNode> focuser = JXNativeNode::setFocusOwner;

    // state of the snapshot being built
    private IdentityHashMap<JXNativeNode, Long> seen;
    private Map<String, JXNativeNode> labels;
    private double ux = 1;
    private double uy = 1;
    private int sx;
    private int sy;

    /** What gives a node the focus when an assistive technology asks: the window sets its own (it also keeps the node). */
    public void setFocuser(Consumer<JXNativeNode> focuser) {
        this.focuser = focuser == null ? JXNativeNode::setFocusOwner : focuser;
    }

    /** The snapshot of the last {@link #snapshot} call, or null. */
    public JXAccessibleNode getLast() {
        return last;
    }

    /** A snapshot without a window: title empty, one screen pixel per logical pixel, the window as big as the tree. */
    public static JXAccessibleNode snapshot(JXNativeNode root) {
        return new JXNativeAccessibility().snapshot(root, "", root.getX() + root.getWidth(), root.getY() + root.getHeight(), 1, 1, 0, 0);
    }

    /**
     * The tree of {@code content}, laid out, under a root node named {@code title}. {@code unitsX} and {@code unitsY} are
     * screen pixels per logical pixel and {@code (screenX, screenY)} the screen position of the content's origin, which
     * give each node its screen bounds; with an open dialog or drawer the tree holds only it (and open snack bars).
     */
    public JXAccessibleNode snapshot(JXNativeNode content, String title, int windowWidth, int windowHeight, double unitsX,
            double unitsY, int screenX, int screenY) {
        this.root = content;
        this.ux = unitsX;
        this.uy = unitsY;
        this.sx = screenX;
        this.sy = screenY;
        this.seen = new IdentityHashMap<JXNativeNode, Long>();
        this.labels = new HashMap<String, JXNativeNode>();
        Map<Long, Target> old = targets;
        targets = new HashMap<Long, Target>();
        JXAccessibleNode result;
        try {
            List<JXAccessibleNode> kids = new ArrayList<JXAccessibleNode>();
            JXAccessibleNode.Builder b = new JXAccessibleNode.Builder(JXAccessibilityRole.ROOT).id(ROOT_ID).name(title)
                    .type("window").bounds(0, 0, windowWidth, windowHeight).screenBounds(sx, sy,
                            (int) Math.round(windowWidth * ux), (int) Math.round(windowHeight * uy));
            if (content != null) {
                collectLabels(content);
                int[] clip = {0, 0, windowWidth, windowHeight};
                List<JXNativeNode> layers = JXOverlays.openLayers(content);
                if (layers.isEmpty()) {
                    add(kids, visit(content, null, 0, clip, null));
                } else {
                    add(kids, visit(layers.get(layers.size() - 1), null, 0, clip, null));
                    for (JXOverlays.Entry e : JXOverlays.collect(content)) {
                        if ("snackbar".equals(e.node.getType())) {
                            add(kids, visit(e.node, null, 0, clip, null));
                        }
                    }
                }
            }
            result = b.children(kids).build();
        } catch (RuntimeException e) {
            targets = old;
            throw e;
        }
        ids = seen;
        seen = null;
        labels = null;
        last = result;
        return result;
    }

    private static void add(List<JXAccessibleNode> list, JXAccessibleNode node) {
        if (node != null) {
            list.add(node);
        }
    }

    private void collectLabels(JXNativeNode n) {
        Object target = n.getProps().get("labelFor");
        if (target != null) {
            labels.put(String.valueOf(target), n);
        }
        for (JXNativeNode c : n.getChildren()) {
            collectLabels(c);
        }
    }

    private long idOf(JXNativeNode n) {
        Long id = seen.get(n);
        if (id == null) {
            id = ids.get(n);
            if (id == null) {
                id = Long.valueOf(nextId++);
            }
            seen.put(n, id);
        }
        return id.longValue();
    }

    private static boolean flag(JXNativeNode n, String prop) {
        return Boolean.TRUE.equals(n.getProperty(prop));
    }

    private static String text(JXNativeNode n, String prop) {
        return JXPaint.text(n, prop);
    }

    private static boolean has(JXNativeNode n, String prop) {
        Object v = n.getProperty(prop);
        return v != null && !String.valueOf(v).isEmpty();
    }

    private static String number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static boolean isTreeRow(JXNativeNode row) {
        if (JXControlLayout.tree(row)) {
            return true;
        }
        return "tablerow".equals(row.getType()) && !row.getChildren().isEmpty() && JXControlLayout.tree(row.getChildren().get(0));
    }

    private static JXNativeNode treeCell(JXNativeNode row) {
        return JXControlLayout.tree(row) ? row : "tablerow".equals(row.getType()) && !row.getChildren().isEmpty()
                && JXControlLayout.tree(row.getChildren().get(0)) ? row.getChildren().get(0) : null;
    }

    // ---- the walk ----------------------------------------------------------------------------------

    private JXAccessibleNode visit(JXNativeNode n, JXNativeNode parent, int pos, int[] clip, List<JXNativeNode> scrollers) {
        String t = n.getType();
        if (flag(n, "hidden") || flag(n, "accessibleHidden")) {
            return null;
        }
        if (JXOverlays.floating(t) && !JXOverlays.isOpen(n)) {
            return null;
        }
        if ("badge".equals(t) && JXControlLayout.badgeText(n) == null) {
            return null;
        }
        JXAccessibilityRole role = roleOf(n, parent);
        JXAccessibleNode.Builder b = new JXAccessibleNode.Builder(role).id(idOf(n)).type(t);
        boolean disabled = flag(n, "disabled");
        boolean listRow = parent != null && ("list".equals(parent.getType()) || "table".equals(parent.getType()));
        boolean selectable = listRow && JXSelection.enabled(parent);
        int first = listRow ? Math.max(0, (int) JXPaint.number(parent, "first", 0)) : 0;

        // bounds, clipping
        int x = n.getX();
        int y = n.getY();
        int w = n.getWidth();
        int h = n.getHeight();
        bounds(b, x, y, w, h);
        boolean visible = w > 0 && h > 0 && x < clip[0] + clip[2] && y < clip[1] + clip[3] && x + w > clip[0] && y + h > clip[1];
        b.state(JXAccessibleState.OFFSCREEN, !visible);

        // name, description
        String name = nameOf(n, role);
        JXNativeNode label = labelOf(n);
        if (label != null && !has(n, "accessibleName")) {
            String l = contentText(label, 0);
            if (!l.isEmpty()) {
                name = l;
            }
            b.labelledBy(idOf(label));
        }
        b.name(name);
        String description = has(n, "accessibleDescription") ? text(n, "accessibleDescription") : text(n, "tooltip");
        b.description(description);

        // focus, disabled
        boolean focusable = n.focusable() && w > 0 && h > 0;
        b.state(JXAccessibleState.FOCUSABLE, focusable);
        b.state(JXAccessibleState.FOCUSED, flag(n, "focused"));
        b.state(JXAccessibleState.DISABLED, disabled);
        b.state(JXAccessibleState.MODAL, JXOverlays.modal(t));
        if (focusable) {
            b.action(JXAccessibleAction.FOCUS);
        }
        boolean clickable = !disabled && (JXNativeNode.clickedOnRelease(t) || n.getProps().containsKey("onClick")
                || n.getProps().get("onAction") != null && !"snackbar".equals(t) && !"menu".equals(t));
        boolean change = !disabled && n.getProperty("onChange") instanceof Consumer;
        boolean readOnly = flag(n, "readOnly") || Boolean.FALSE.equals(n.getProps().get("editable")) && !"select".equals(t);

        values(b, n, t, change, readOnly);
        states(b, n, t);

        // popups, menus
        boolean host = JXOverlays.canOpen(n) && !JXOverlays.floating(t) && !"menubar".equals(t);
        boolean open = host && JXOverlays.isOpen(n);
        if (host) {
            b.state(open ? JXAccessibleState.EXPANDED : JXAccessibleState.COLLAPSED, true);
            if (!disabled) {
                b.action(JXAccessibleAction.DEFAULT).action(open ? JXAccessibleAction.COLLAPSE : JXAccessibleAction.EXPAND);
            }
            clickable = false;
        }
        if ("titled".equals(t) && n.collapsible()) {
            b.state(n.collapsed() ? JXAccessibleState.COLLAPSED : JXAccessibleState.EXPANDED, true);
            if (!disabled) {
                b.action(JXAccessibleAction.DEFAULT).action(n.collapsed() ? JXAccessibleAction.EXPAND : JXAccessibleAction.COLLAPSE);
                clickable = false;
            }
        }
        if ("menu".equals(t) && parent != null) {
            boolean expanded = parent.openChild == pos;
            b.state(expanded ? JXAccessibleState.EXPANDED : JXAccessibleState.COLLAPSED, true);
            if (!disabled) {
                b.action(JXAccessibleAction.DEFAULT).action(expanded ? JXAccessibleAction.COLLAPSE : JXAccessibleAction.EXPAND);
            }
        }
        if ("menuitem".equals(t) && JXOverlays.separator(n)) {
            clickable = false;
        }
        if (clickable) {
            b.action(JXAccessibleAction.DEFAULT);
        }
        if ("snackbar".equals(t) && !text(n, "action").isEmpty()) {
            b.action(JXAccessibleAction.DEFAULT);
        }
        if (change && !readOnly && (JXChangeValue.settable(t))) {
            b.action(JXAccessibleAction.SET_VALUE);
        }

        // list rows, tables, trees
        int count = 0;
        if (listRow) {
            count = JXSelection.count(parent);
            int index = first + pos;
            if ("table".equals(parent.getType()) || "tablerow".equals(t)) {
                b.row(index, count);
            } else {
                b.item(index, count);
            }
            b.state(JXAccessibleState.SELECTABLE, selectable && !disabled);
            boolean sel = selectable ? JXSelection.isSelected(parent, index) : flag(n, "selected");
            b.state(JXAccessibleState.SELECTED, sel);
            if (selectable && !disabled) {
                b.action(JXAccessibleAction.SELECT);
                b.action(JXAccessibleAction.DEFAULT);
            }
        } else if (parent != null && "tablerow".equals(parent.getType())) {
            b.column(pos, parent.getChildren().size());
        }
        JXNativeNode tc = treeCell(n);
        if (tc != null) {
            b.level(Math.max(0, (int) JXPaint.number(tc, "indent", 0)) + 1);
            String d = text(tc, "disclosure");
            if (!"leaf".equals(d)) {
                b.state("expanded".equals(d) ? JXAccessibleState.EXPANDED : JXAccessibleState.COLLAPSED, true);
                if (tc.getProperty("onToggle") instanceof Runnable && !disabled) {
                    b.action("expanded".equals(d) ? JXAccessibleAction.COLLAPSE : JXAccessibleAction.EXPAND);
                }
            }
        }
        if (scrollers != null && !scrollers.isEmpty()) {
            b.action(JXAccessibleAction.SCROLL_INTO_VIEW);
        }
        b.content(isContent(n, role, name));

        // children
        int[] childClip = clip;
        List<JXNativeNode> childScrollers = scrollers;
        if ("scroll".equals(t) || "list".equals(t) || "table".equals(t)) {
            JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(n);
            childClip = intersect(clip, new int[] {Math.round(g.viewX), Math.round(g.viewY), Math.round(g.viewW), Math.round(g.viewH)});
            childScrollers = new ArrayList<JXNativeNode>(scrollers == null ? new ArrayList<JXNativeNode>() : scrollers);
            childScrollers.add(n);
        }
        List<JXAccessibleNode> kids = new ArrayList<JXAccessibleNode>();
        boolean shown = "menu".equals(t) ? parent != null && parent.openChild == pos : open;
        children(b, kids, n, t, shown, childClip, childScrollers);
        if ("list".equals(t)) {
            b.item(-1, JXSelection.count(n));
        } else if ("table".equals(t)) {
            b.row(-1, JXSelection.count(n));
            b.column(-1, JXPaint.strings(n.getProperty("columns")).length);
        }
        return b.children(kids).build();
    }

    private static int[] intersect(int[] a, int[] b) {
        int x0 = Math.max(a[0], b[0]);
        int y0 = Math.max(a[1], b[1]);
        int x1 = Math.min(a[0] + a[2], b[0] + b[2]);
        int y1 = Math.min(a[1] + a[3], b[1] + b[3]);
        return new int[] {x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)};
    }

    private void bounds(JXAccessibleNode.Builder b, int x, int y, int w, int h) {
        b.bounds(x, y, w, h);
        b.screenBounds(sx + (int) Math.round(x * ux), sy + (int) Math.round(y * uy), (int) Math.round(w * ux), (int) Math.round(h * uy));
    }

    private JXNativeNode labelOf(JXNativeNode n) {
        Object key = n.getProps().get("key");
        return key == null ? null : labels.get(String.valueOf(key));
    }

    private boolean isContent(JXNativeNode n, JXAccessibilityRole role, String name) {
        if (has(n, "accessibleName") || has(n, "accessibleRole")) {
            return true;
        }
        switch (role) {
            case GROUP:
                return !name.isEmpty() && !"column".equals(n.getType()) && !"row".equals(n.getType()) && !"stack".equals(n.getType());
            case SEPARATOR:
                return false;
            case IMAGE:
                return !name.isEmpty();
            default:
                return true;
        }
    }

    // ---- roles, names ------------------------------------------------------------------------------

    static JXAccessibilityRole parseRole(String s) {
        if (s == null) {
            return null;
        }
        String k = s.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (JXAccessibilityRole r : JXAccessibilityRole.values()) {
            if (r.name().equals(k)) {
                return r;
            }
        }
        return null;
    }

    private JXAccessibilityRole roleOf(JXNativeNode n, JXNativeNode parent) {
        JXAccessibilityRole given = parseRole(text(n, "accessibleRole"));
        if (given != null) {
            return given;
        }
        String t = n.getType();
        switch (t) {
            case "button":
            case "menubutton":
            case "buttonbar":
                return "buttonbar".equals(t) ? JXAccessibilityRole.GROUP : JXAccessibilityRole.BUTTON;
            case "toggle":
                return JXAccessibilityRole.TOGGLE_BUTTON;
            case "chip":
                String kind = text(n, "kind");
                return "filter".equals(kind) || "choice".equals(kind) ? JXAccessibilityRole.TOGGLE_BUTTON : JXAccessibilityRole.BUTTON;
            case "checkbox":
                return JXAccessibilityRole.CHECK_BOX;
            case "radio":
                return JXAccessibilityRole.RADIO_BUTTON;
            case "switch":
                return JXAccessibilityRole.SWITCH;
            case "hyperlink":
                return JXAccessibilityRole.LINK;
            case "#text":
            case "badge":
                return JXAccessibilityRole.TEXT;
            case "input":
            case "password":
            case "textarea":
                return JXAccessibilityRole.TEXT_FIELD;
            case "select":
                return JXAccessibilityRole.COMBO_BOX;
            case "datepicker":
                return JXAccessibilityRole.DATE_PICKER;
            case "timepicker":
                return JXAccessibilityRole.TIME_PICKER;
            case "colorpicker":
                return JXAccessibilityRole.COLOR_PICKER;
            case "spinner":
                return JXAccessibilityRole.SPINNER;
            case "slider":
                return JXAccessibilityRole.SLIDER;
            case "progress":
            case "indicator":
                return JXAccessibilityRole.PROGRESS_BAR;
            case "scrollbar":
                return JXAccessibilityRole.SCROLL_BAR;
            case "scroll":
                return JXAccessibilityRole.SCROLL_PANE;
            case "splitpane":
                return JXAccessibilityRole.SPLIT_PANE;
            case "list": {
                for (JXNativeNode c : n.getChildren()) {
                    if (isTreeRow(c)) {
                        return JXAccessibilityRole.TREE;
                    }
                }
                return JXAccessibilityRole.LIST;
            }
            case "table": {
                for (JXNativeNode c : n.getChildren()) {
                    if (isTreeRow(c)) {
                        return JXAccessibilityRole.TREE;
                    }
                }
                return JXAccessibilityRole.TABLE;
            }
            case "tablerow":
                return isTreeRow(n) ? JXAccessibilityRole.TREE_ITEM : JXAccessibilityRole.ROW;
            case "cell":
                if (parent != null && "tablerow".equals(parent.getType())) {
                    return JXAccessibilityRole.CELL;
                }
                return JXControlLayout.tree(n) ? JXAccessibilityRole.TREE_ITEM : JXAccessibilityRole.LIST_ITEM;
            case "tabs":
            case "tabbar":
                return JXAccessibilityRole.TAB_LIST;
            case "menubar":
                return JXAccessibilityRole.MENU_BAR;
            case "contextmenu":
                return JXAccessibilityRole.MENU;
            case "menu":
                return JXAccessibilityRole.MENU_ITEM;
            case "menuitem":
                return JXOverlays.separator(n) ? JXAccessibilityRole.SEPARATOR : JXAccessibilityRole.MENU_ITEM;
            case "icon":
            case "image":
            case "avatar":
                return JXAccessibilityRole.IMAGE;
            case "separator":
                return JXAccessibilityRole.SEPARATOR;
            case "dialog":
            case "drawer":
                return JXAccessibilityRole.DIALOG;
            case "tooltip":
                return JXAccessibilityRole.TOOLTIP;
            case "snackbar":
                return JXAccessibilityRole.STATUS;
            default:
                return JXAccessibilityRole.GROUP;
        }
    }

    private String nameOf(JXNativeNode n, JXAccessibilityRole role) {
        if (has(n, "accessibleName")) {
            return text(n, "accessibleName");
        }
        String t = n.getType();
        switch (t) {
            case "#text":
                return text(n, "value");
            case "cell":
                return text(n, "value").isEmpty() ? contentText(n, 0) : text(n, "value");
            case "tablerow":
                return contentText(n, 0);
            case "input":
            case "password":
            case "textarea":
            case "select":
            case "spinner":
            case "datepicker":
            case "timepicker":
            case "colorpicker":
                return has(n, "label") ? text(n, "label") : text(n, "prompt");
            case "dialog":
            case "drawer":
                return has(n, "title") ? text(n, "title") : has(n, "label") ? text(n, "label") : contentText(n, 0);
            case "snackbar":
                return text(n, "message");
            case "icon":
                return text(n, "name");
            case "image":
                return text(n, "alt");
            case "avatar":
                return text(n, "initials");
            case "badge": {
                String b = JXControlLayout.badgeText(n);
                return b == null || b.isEmpty() ? "notification" : b;
            }
            case "button":
            case "toggle":
            case "hyperlink":
            case "chip":
            case "checkbox":
            case "radio":
            case "switch":
            case "menubutton":
            case "menu":
            case "menuitem":
            case "tooltip": {
                String l = text(n, "label");
                return l.isEmpty() ? contentText(n, 0) : l;
            }
            default:
                return text(n, "label");
        }
    }

    /** The text a node shows: its own, or that of the nodes inside it. */
    private static String contentText(JXNativeNode n, int depth) {
        String own = "#text".equals(n.getType()) || "cell".equals(n.getType()) ? JXPaint.text(n, "value")
                : "icon".equals(n.getType()) ? JXPaint.text(n, "name") : JXPaint.text(n, "label");
        if (!own.isEmpty() || depth > 4) {
            return own;
        }
        StringBuilder sb = new StringBuilder();
        for (JXNativeNode c : n.getChildren()) {
            if (flag(c, "hidden") || flag(c, "accessibleHidden")) {
                continue;
            }
            String s = contentText(c, depth + 1);
            if (!s.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }

    // ---- values and states -------------------------------------------------------------------------

    private void values(JXAccessibleNode.Builder b, JXNativeNode n, String t, boolean change, boolean readOnly) {
        switch (t) {
            case "input":
            case "textarea":
                b.value(text(n, "value"));
                b.state(JXAccessibleState.READ_ONLY, readOnly);
                b.state(JXAccessibleState.MULTILINE, "textarea".equals(t));
                break;
            case "password":
                b.state(JXAccessibleState.PASSWORD, true); // the text is never read
                b.state(JXAccessibleState.READ_ONLY, readOnly);
                break;
            case "select":
            case "datepicker":
                b.value(text(n, "value"));
                break;
            case "timepicker":
                b.value(JXOverlays.timeText(n));
                break;
            case "colorpicker": {
                String hex = JXOverlays.normalizeHex(text(n, "value"));
                b.value(hex == null ? "" : hex);
                break;
            }
            case "spinner": {
                b.value(text(n, "value"));
                double now = Double.NaN;
                try {
                    now = Double.parseDouble(text(n, "value").trim());
                } catch (NumberFormatException e) {
                    // not a number yet
                }
                b.range(JXPaint.number(n, "min", -Double.MAX_VALUE), JXPaint.number(n, "max", Double.MAX_VALUE), now);
                break;
            }
            case "slider":
            case "scrollbar": {
                double min = JXPaint.number(n, "min", 0.0);
                double max = JXPaint.number(n, "max", 100.0);
                double now = JXPaint.number(n, "value", min);
                b.range(min, max, now);
                b.value(number(now));
                break;
            }
            case "progress":
            case "indicator": {
                double p = JXPaint.number(n, "progress", -1.0);
                if (p < 0) {
                    b.state(JXAccessibleState.BUSY, true);
                } else {
                    double pct = Math.min(1, p) * 100;
                    b.range(0, 100, pct);
                    b.value(number(Math.round(pct)) + "%");
                }
                break;
            }
            case "stepper": {
                int steps = JXControlLayout.stepCount(n);
                int current = (int) JXPaint.number(n, "current", 0);
                b.value("Step " + (current + 1) + " of " + steps);
                break;
            }
            case "pagination":
                b.value("Page " + ((int) JXPaint.number(n, "current", 0) + 1) + " of " + Math.max(1, (int) JXPaint.number(n, "pageCount", 1)));
                break;
            case "tabs":
            case "tabbar": {
                String[] titles = JXPaint.strings(n.getProperty("titles"));
                int sel = (int) JXPaint.number(n, "selected", 0);
                b.value(sel >= 0 && sel < titles.length ? titles[sel] : "");
                break;
            }
            default:
                break;
        }
    }

    private void states(JXAccessibleNode.Builder b, JXNativeNode n, String t) {
        switch (t) {
            case "checkbox":
                b.state(JXAccessibleState.MIXED, flag(n, "indeterminate"));
                b.state(JXAccessibleState.CHECKED, flag(n, "checked") && !flag(n, "indeterminate"));
                break;
            case "radio":
            case "switch":
            case "menuitem":
                b.state(JXAccessibleState.CHECKED, flag(n, "checked"));
                if ("radio".equals(t) && !flag(n, "checked") && !flag(n, "disabled")) {
                    b.action(JXAccessibleAction.SELECT);
                }
                break;
            case "toggle":
                b.state(JXAccessibleState.CHECKED, flag(n, "selected"));
                break;
            case "chip": {
                String k = text(n, "kind");
                if ("filter".equals(k) || "choice".equals(k)) {
                    b.state(JXAccessibleState.CHECKED, flag(n, "selected"));
                }
                break;
            }
            default:
                break;
        }
    }

    // ---- children ----------------------------------------------------------------------------------

    private void children(JXAccessibleNode.Builder b, List<JXAccessibleNode> kids, JXNativeNode n, String t, boolean open,
            int[] clip, List<JXNativeNode> scrollers) {
        List<JXNativeNode> real = n.getChildren();
        switch (t) {
            case "tabs":
            case "tabbar": {
                String[] titles = JXPaint.strings(n.getProperty("titles"));
                int sel = (int) JXPaint.number(n, "selected", 0);
                float[] edges = "tabs".equals(t) ? JXControlLayout.tabPositions(n) : JXControlLayout.tabbarEdges(n);
                boolean disabled = flag(n, "disabled");
                for (int i = 0; i < titles.length; i++) {
                    int tx = n.getX() + Math.round(edges[i]);
                    int tw = Math.round(edges[i + 1] - edges[i]);
                    int ty = "tabs".equals(t) ? n.getY() + JXControlLayout.TAB_TOP : n.getY();
                    int th = "tabs".equals(t) ? JXControlLayout.TAB_HEADER - JXControlLayout.TAB_TOP : n.getHeight();
                    long id = SYNTHETIC * (i + 1) + idOf(n);
                    targets.put(Long.valueOf(id), new Target(n, TAB, i, new int[] {tx, ty, tw, th}));
                    JXAccessibleNode.Builder tb = new JXAccessibleNode.Builder(JXAccessibilityRole.TAB).id(id).name(titles[i]).type("tab")
                            .item(i, titles.length).state(JXAccessibleState.SELECTED, i == sel)
                            .state(JXAccessibleState.SELECTABLE, true).state(JXAccessibleState.DISABLED, disabled);
                    bounds(tb, tx, ty, tw, th);
                    if (!disabled) {
                        tb.action(JXAccessibleAction.DEFAULT).action(JXAccessibleAction.SELECT);
                    }
                    kids.add(tb.build());
                }
                break;
            }
            case "pagination": {
                int current = (int) JXPaint.number(n, "current", 0);
                float[][] buttons = JXControlLayout.pageButtons(n);
                for (int i = 0; i < buttons.length; i++) {
                    float[] r = buttons[i];
                    int page = (int) r[4];
                    String nm = page == -1 ? "Previous page" : page == -2 ? "Next page" : "Page " + (page + 1);
                    long id = SYNTHETIC * (i + 1) + idOf(n);
                    int[] rect = {Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3])};
                    targets.put(Long.valueOf(id), new Target(n, PAGE, i, rect));
                    JXAccessibleNode.Builder pb = new JXAccessibleNode.Builder(JXAccessibilityRole.BUTTON).id(id).name(nm).type("page")
                            .state(JXAccessibleState.SELECTED, page == current).action(JXAccessibleAction.DEFAULT);
                    bounds(pb, rect[0], rect[1], rect[2], rect[3]);
                    kids.add(pb.build());
                }
                break;
            }
            case "table": {
                String[] columns = JXPaint.strings(n.getProperty("columns"));
                if (columns.length > 0) {
                    float[] widths = JXControlLayout.columnWidths(n);
                    JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(n);
                    List<JXAccessibleNode> headers = new ArrayList<JXAccessibleNode>();
                    double cx = g.viewX - g.scrollX;
                    for (int i = 0; i < columns.length; i++) {
                        int cw = i < widths.length ? Math.round(widths[i]) : 0;
                        JXAccessibleNode.Builder hb = new JXAccessibleNode.Builder(JXAccessibilityRole.CELL).name(columns[i]).type("header")
                                .column(i, columns.length);
                        bounds(hb, (int) Math.round(cx), n.getY() + 1, cw, JXControlLayout.TABLE_HEADER);
                        headers.add(hb.build());
                        cx += cw;
                    }
                    JXAccessibleNode.Builder row = new JXAccessibleNode.Builder(JXAccessibilityRole.ROW).name("Header").type("header")
                            .children(headers).row(-1, -1);
                    bounds(row, n.getX() + 1, n.getY() + 1, n.getWidth() - 2, JXControlLayout.TABLE_HEADER);
                    kids.add(row.build());
                }
                break;
            }
            default:
                break;
        }
        if ("select".equals(t) && open) {
            JXOverlays.Popup po = JXOverlays.popup(n);
            String[] options = JXPaint.strings(n.getProperty("options"));
            if (po != null) {
                String value = text(n, "value");
                for (int[] cell : po.cells) {
                    int index = cell[5];
                    if (index < 0 || index >= options.length) {
                        continue;
                    }
                    long id = SYNTHETIC * (index + 1) + idOf(n) + (1L << 62);
                    targets.put(Long.valueOf(id), new Target(n, OPTION, index, new int[] {cell[0], cell[1], cell[2], cell[3]}));
                    JXAccessibleNode.Builder ob = new JXAccessibleNode.Builder(JXAccessibilityRole.LIST_ITEM).id(id).name(options[index])
                            .type("option").item(index, options.length).state(JXAccessibleState.SELECTED, options[index].equals(value))
                            .state(JXAccessibleState.SELECTABLE, true).action(JXAccessibleAction.DEFAULT).action(JXAccessibleAction.SELECT);
                    bounds(ob, cell[0], cell[1], cell[2], cell[3]);
                    kids.add(ob.build());
                }
            }
            return;
        }
        if (JXOverlays.popupHost(t)) {
            return;
        }
        if ("titled".equals(t) && n.collapsed()) {
            return;
        }
        if (("menubutton".equals(t) || "menu".equals(t)) && !open) {
            return;
        }
        for (int i = 0; i < real.size(); i++) {
            add(kids, visit(real.get(i), n, i, clip, scrollers));
        }
    }

    // ---- events ------------------------------------------------------------------------------------

    /** What changed between two snapshots, coalesced: at most one event of a kind per node. Focus goes last. */
    public static List<JXAccessibilityEvent> diff(JXAccessibleNode before, JXAccessibleNode after) {
        List<JXAccessibilityEvent> out = new ArrayList<JXAccessibilityEvent>();
        if (before == null || after == null) {
            return out;
        }
        Map<Long, JXAccessibleNode> old = new HashMap<Long, JXAccessibleNode>();
        for (JXAccessibleNode n : before.flatten()) {
            if (n.getId() != 0) {
                old.put(Long.valueOf(n.getId()), n);
            }
        }
        long focusBefore = 0;
        long focusAfter = 0;
        for (JXAccessibleNode n : before.flatten()) {
            if (n.is(JXAccessibleState.FOCUSED) && focusBefore == 0) {
                focusBefore = n.getId();
            }
        }
        for (JXAccessibleNode n : after.flatten()) {
            if (n.is(JXAccessibleState.FOCUSED) && focusAfter == 0) {
                focusAfter = n.getId();
            }
            JXAccessibleNode o = old.get(Long.valueOf(n.getId()));
            if (o == null) {
                continue;
            }
            if (!childIds(o).equals(childIds(n))) {
                out.add(JXAccessibilityEvent.of(JXAccessibilityEvent.Kind.STRUCTURE_CHANGED, n.getId(), ""));
            }
            if (!o.getName().equals(n.getName())) {
                out.add(JXAccessibilityEvent.of(JXAccessibilityEvent.Kind.NAME_CHANGED, n.getId(), n.getName()));
            }
            if (!o.getValue().equals(n.getValue()) && !n.is(JXAccessibleState.PASSWORD) || !same(o.getNow(), n.getNow())) {
                out.add(JXAccessibilityEvent.of(JXAccessibilityEvent.Kind.VALUE_CHANGED, n.getId(), n.getValue()));
            }
            for (JXAccessibleState s : JXAccessibleState.values()) {
                boolean was = o.is(s);
                boolean is = n.is(s);
                if (was == is || s == JXAccessibleState.FOCUSED) {
                    continue;
                }
                if (s == JXAccessibleState.SELECTED) {
                    out.add(new JXAccessibilityEvent(JXAccessibilityEvent.Kind.SELECTION_CHANGED, n.getId(), n.getName(), s, is, false));
                } else {
                    out.add(new JXAccessibilityEvent(JXAccessibilityEvent.Kind.STATE_CHANGED, n.getId(), "", s, is, false));
                }
            }
        }
        if (focusBefore != focusAfter) {
            out.add(JXAccessibilityEvent.of(JXAccessibilityEvent.Kind.FOCUS_CHANGED, focusAfter, ""));
        }
        return out;
    }

    private static boolean same(double a, double b) {
        return Double.isNaN(a) && Double.isNaN(b) || a == b;
    }

    private static List<Long> childIds(JXAccessibleNode n) {
        List<Long> ids = new ArrayList<Long>();
        for (JXAccessibleNode c : n.getChildren()) {
            ids.add(Long.valueOf(c.getId()));
        }
        return ids;
    }

    // ---- actions -----------------------------------------------------------------------------------

    /**
     * Carries out {@code action} on the node with this id of the last snapshot, through the code the pointer and keyboard
     * use. Display thread only. False when the node is gone, does not list the action or refuses it now.
     */
    public boolean perform(long id, JXAccessibleAction action, Object argument) {
        JXAccessibleNode snap = last == null ? null : last.findById(id);
        Target target = targets.get(Long.valueOf(id));
        JXNativeNode node = target != null ? target.node : null;
        if (id == ROOT_ID || snap == null || !snap.supports(action)) {
            return false;
        }
        if (node == null) {
            for (Map.Entry<JXNativeNode, Long> e : ids.entrySet()) {
                if (e.getValue().longValue() == id) {
                    node = e.getKey();
                }
            }
        }
        if (node == null) {
            return false;
        }
        JXNativeNode.revision++;
        switch (action) {
            case FOCUS:
                focuser.accept(node);
                return true;
            case DEFAULT:
            case SELECT:
                return click(node, target);
            case EXPAND:
            case COLLAPSE:
                return expand(node, action == JXAccessibleAction.EXPAND);
            case SET_VALUE:
                return JXChangeValue.set(node, argument);
            case SCROLL_INTO_VIEW:
                return reveal(node);
            default:
                return false;
        }
    }

    private boolean click(JXNativeNode node, Target target) {
        int cx;
        int cy;
        if (target != null && target.rect != null) {
            cx = target.rect[0] + target.rect[2] / 2;
            cy = target.rect[1] + target.rect[3] / 2;
            if (target.kind == OPTION && !JXOverlays.isOpen(node)) {
                return false;
            }
        } else if ("snackbar".equals(node.getType())) {
            cx = Math.max(node.getX(), JXOverlays.snackbarActionX(node) + 1);
            cy = node.getY() + node.getHeight() / 2;
        } else if ("titled".equals(node.getType())) {
            cx = node.getX() + JXTitledLayout.textOffset(node) + 2;
            cy = node.getY() + JXTitledLayout.titleHeight() / 2;
        } else {
            cx = node.getX() + node.getWidth() / 2;
            cy = node.getY() + node.getHeight() / 2;
        }
        return clickAt(node, cx, cy);
    }

    private boolean clickAt(JXNativeNode node, int cx, int cy) {
        JXNativeNode target = node;
        boolean reachable = root != null && root.hitPath(cx, cy).contains(node);
        if (reachable) {
            JXNativeNode hit = root.pressPointer(new JXPointerEvent(cx, cy, 0, 1, 0));
            root.releasePointer(new JXPointerEvent(cx, cy, 0));
            if (hit != null) {
                focuser.accept(hit);
            }
            return true;
        }
        // covered or scrolled out: the node still gets its click, as a handler call does
        target.dispatchPointer(new JXPointerEvent(cx, cy, 0));
        return true;
    }

    private boolean expand(JXNativeNode node, boolean open) {
        String t = node.getType();
        if (JXOverlays.canOpen(node) && !JXOverlays.floating(t)) {
            JXOverlays.request(node, open);
            return true;
        }
        if ("titled".equals(t)) {
            if (node.collapsed() == open) {
                return click(node, null);
            }
            return true;
        }
        JXNativeNode tc = treeCell(node);
        if (tc != null) {
            String d = JXPaint.text(tc, "disclosure");
            if ("expanded".equals(d) != open && !"leaf".equals(d)) {
                float[] pad = JXPaint.insetsOr(tc, 3, 7);
                int cx = (int) (tc.getX() + pad[3] + Math.max(0, JXPaint.number(tc, "indent", 0)) * JXControlLayout.TREE_INDENT
                        + JXControlLayout.TREE_ARROW / 2);
                return clickAt(tc, cx, tc.getY() + tc.getHeight() / 2);
            }
            return true;
        }
        if ("menu".equals(t)) {
            return click(node, null);
        }
        return false;
    }

    private boolean reveal(JXNativeNode node) {
        List<JXNativeNode> path = new ArrayList<JXNativeNode>();
        if (root == null || !path(root, node, path)) {
            return false;
        }
        for (int i = path.size() - 2; i >= 0; i--) {
            JXNativeNode scroller = path.get(i);
            String t = scroller.getType();
            JXNativeNode child = path.get(i + 1);
            if ("list".equals(t) || "table".equals(t)) {
                int pos = scroller.getChildren().indexOf(child);
                if (pos >= 0) {
                    JXSelection.reveal(root, scroller, Math.max(0, (int) JXPaint.number(scroller, "first", 0)) + pos);
                }
            } else if ("scroll".equals(t)) {
                JXControlLayout.ScrollGeometry g = JXControlLayout.scrollGeometry(scroller);
                double top = node.getY() - g.viewY + g.scrollY;
                double y = g.scrollY;
                if (top < g.scrollY) {
                    y = top;
                } else if (top + node.getHeight() > g.scrollY + g.viewH) {
                    y = top + node.getHeight() - g.viewH;
                }
                double left = node.getX() - g.viewX + g.scrollX;
                double x = g.scrollX;
                if (left < g.scrollX) {
                    x = left;
                } else if (left + node.getWidth() > g.scrollX + g.viewW) {
                    x = left + node.getWidth() - g.viewW;
                }
                JXScrolling.scrollTo(path, i, x, y);
            }
        }
        return true;
    }

    private static boolean path(JXNativeNode from, JXNativeNode target, List<JXNativeNode> out) {
        out.add(from);
        if (from == target) {
            return true;
        }
        for (JXNativeNode c : from.getChildren()) {
            if (path(c, target, out)) {
                return true;
            }
        }
        out.remove(out.size() - 1);
        return false;
    }

    /** SET_VALUE: the same {@code onChange} calls the keyboard and pointer make, with a value they could have made. */
    static final class JXChangeValue {
        private JXChangeValue() {
        }

        static boolean settable(String type) {
            switch (type) {
                case "input":
                case "password":
                case "textarea":
                case "select":
                case "spinner":
                case "slider":
                case "scrollbar":
                case "datepicker":
                case "timepicker":
                case "colorpicker":
                    return true;
                default:
                    return false;
            }
        }

        @SuppressWarnings("unchecked")
        static boolean set(JXNativeNode n, Object value) {
            Object h = n.getProperty("onChange");
            if (!(h instanceof Consumer) || flag(n, "disabled") || value == null) {
                return false;
            }
            Consumer<Object> onChange = (Consumer<Object>) h;
            String t = n.getType();
            switch (t) {
                case "input":
                case "password":
                case "textarea": {
                    String s = String.valueOf(value);
                    if (!"textarea".equals(t)) {
                        s = s.replace("\r", "").replace('\n', ' ');
                    }
                    n.caret = s.length();
                    n.anchor = -1;
                    onChange.accept(s);
                    return true;
                }
                case "slider":
                case "scrollbar":
                case "spinner": {
                    if (!(value instanceof Number)) {
                        return false;
                    }
                    double min = JXPaint.number(n, "min", "spinner".equals(t) ? -Double.MAX_VALUE : 0.0);
                    double max = JXPaint.number(n, "max", "spinner".equals(t) ? Double.MAX_VALUE : 100.0);
                    double v = Math.max(min, Math.min(max, ((Number) value).doubleValue()));
                    onChange.accept(Double.valueOf(v));
                    return true;
                }
                case "select": {
                    String s = String.valueOf(value);
                    if (!java.util.Arrays.asList(JXPaint.strings(n.getProperty("options"))).contains(s)) {
                        return false;
                    }
                    onChange.accept(s);
                    return true;
                }
                case "datepicker": {
                    try {
                        onChange.accept(LocalDate.parse(String.valueOf(value).trim()).toString());
                        return true;
                    } catch (java.time.format.DateTimeParseException e) {
                        return false;
                    }
                }
                case "timepicker": {
                    int minutes = JXOverlays.parseTime(String.valueOf(value));
                    if (minutes < 0) {
                        return false;
                    }
                    onChange.accept(JXOverlays.formatTime(minutes / 60, minutes % 60));
                    return true;
                }
                case "colorpicker": {
                    String hex = JXOverlays.normalizeHex(String.valueOf(value));
                    if (hex == null) {
                        return false;
                    }
                    n.hexDraft = hex;
                    onChange.accept(hex);
                    return true;
                }
                default:
                    return false;
            }
        }
    }
}
