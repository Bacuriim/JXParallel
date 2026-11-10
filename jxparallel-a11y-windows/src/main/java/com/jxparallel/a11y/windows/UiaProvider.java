package com.jxparallel.a11y.windows;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.jxparallel.ui.accessibility.JXAccessibilityRole;
import com.jxparallel.ui.accessibility.JXAccessibleAction;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.accessibility.JXAccessibleState;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * The COM object a UI Automation client talks to for one node: a set of interface pointers (each a 16-byte block holding
 * the address of a static vtable and the key of the provider) that all answer from the node's last snapshot.
 *
 * <p>Interfaces: IRawElementProviderSimple, IRawElementProviderFragment, IRawElementProviderFragmentRoot (the window
 * only), and the patterns Invoke, Toggle, Value, RangeValue, SelectionItem and ExpandCollapse where the node supports
 * them. Blocks are never freed while the process lives (a client may still hold one); the number of providers is bounded by
 * what clients asked for.
 */
final class UiaProvider {
    static final int SIMPLE = 0;
    static final int FRAGMENT = 1;
    static final int ROOT = 2;
    static final int INVOKE = 3;
    static final int TOGGLE = 4;
    static final int VALUE = 5;
    static final int RANGE = 6;
    static final int SELITEM = 7;
    static final int EXPAND = 8;
    private static final int KINDS = 9;

    private static final byte[][] IID = {
        guid("D6DD68D1-86FD-4332-8666-9ABEDEA2D24C"), // IRawElementProviderSimple
        guid("F7063DA8-8359-439C-9297-BBC5299A7D87"), // IRawElementProviderFragment
        guid("620CE2A5-AB8F-40A9-86CB-DE3C75599B58"), // IRawElementProviderFragmentRoot
        guid("54FCB24B-E18E-47A2-B4D3-ECCBE77599A2"), // IInvokeProvider
        guid("56D00BD0-C4F4-433C-A836-1A52A57E0892"), // IToggleProvider
        guid("C7935180-6FB3-4201-B174-7DF73ADBF64A"), // IValueProvider
        guid("36DC7AEF-33E6-4691-AFE1-2BE7274B3D33"), // IRangeValueProvider
        guid("2ACAD808-B2D4-452D-A407-91FF1AD167B2"), // ISelectionItemProvider
        guid("D847D3A5-CAB0-4A98-8C32-ECB45C59AD24"), // IExpandCollapseProvider
    };
    private static final byte[] IID_UNKNOWN = guid("00000000-0000-0000-C000-000000000046");

    private static final ConcurrentHashMap<Long, UiaProvider> LIVE = new ConcurrentHashMap<Long, UiaProvider>();
    private static final AtomicLong KEYS = new AtomicLong(1);
    private static final Memory[] VTABLES = new Memory[KINDS];

    final UiaWindow window;
    final long id;
    private final long key = KEYS.getAndIncrement();
    private final Memory[] blocks = new Memory[KINDS];
    private final AtomicInteger refs = new AtomicInteger();

    UiaProvider(UiaWindow window, long id) {
        this.window = window;
        this.id = id;
        LIVE.put(Long.valueOf(key), this);
    }

    /** The interface pointer of one kind, made on first use. */
    Pointer iface(int kind) {
        synchronized (blocks) {
            if (blocks[kind] == null) {
                Memory m = new Memory(16);
                m.setPointer(0, vtable(kind));
                m.setLong(8, key);
                blocks[kind] = m;
            }
            return blocks[kind];
        }
    }

    private static UiaProvider of(Pointer self) {
        return LIVE.get(Long.valueOf(self.getLong(8)));
    }

    private JXAccessibleNode node() {
        return window.host.find(id);
    }

    // ---- what the node supports --------------------------------------------------------------------

    private static boolean toggles(JXAccessibilityRole r) {
        return r == JXAccessibilityRole.TOGGLE_BUTTON || r == JXAccessibilityRole.CHECK_BOX || r == JXAccessibilityRole.SWITCH;
    }

    private static boolean has(JXAccessibleNode n, int kind) {
        JXAccessibilityRole r = n.getRole();
        switch (kind) {
            case INVOKE:
                return n.supports(JXAccessibleAction.DEFAULT) && !toggles(r) && (r == JXAccessibilityRole.BUTTON
                        || r == JXAccessibilityRole.LINK || r == JXAccessibilityRole.MENU_ITEM || r == JXAccessibilityRole.STATUS)
                        && !n.is(JXAccessibleState.EXPANDED) && !n.is(JXAccessibleState.COLLAPSED);
            case TOGGLE:
                return toggles(r);
            case VALUE:
                return r == JXAccessibilityRole.TEXT_FIELD || r == JXAccessibilityRole.COMBO_BOX
                        || r == JXAccessibilityRole.DATE_PICKER || r == JXAccessibilityRole.TIME_PICKER
                        || r == JXAccessibilityRole.COLOR_PICKER;
            case RANGE:
                return n.hasRange() && (r == JXAccessibilityRole.SLIDER || r == JXAccessibilityRole.SCROLL_BAR
                        || r == JXAccessibilityRole.SPINNER || r == JXAccessibilityRole.PROGRESS_BAR);
            case SELITEM:
                return r == JXAccessibilityRole.TAB || r == JXAccessibilityRole.LIST_ITEM || r == JXAccessibilityRole.RADIO_BUTTON
                        || r == JXAccessibilityRole.TREE_ITEM;
            case EXPAND:
                return n.is(JXAccessibleState.EXPANDED) || n.is(JXAccessibleState.COLLAPSED);
            default:
                return false;
        }
    }

    private boolean supports(int kind) {
        if (kind <= FRAGMENT) {
            return true;
        }
        if (kind == ROOT) {
            return id == 1;
        }
        JXAccessibleNode n = node();
        return n != null && has(n, kind);
    }

    private static int patternKind(int patternId) {
        switch (patternId) {
            case UiaNative.PAT_INVOKE:
                return INVOKE;
            case UiaNative.PAT_TOGGLE:
                return TOGGLE;
            case UiaNative.PAT_VALUE:
                return VALUE;
            case UiaNative.PAT_RANGE:
                return RANGE;
            case UiaNative.PAT_SELITEM:
                return SELITEM;
            case UiaNative.PAT_EXPAND:
                return EXPAND;
            default:
                return -1;
        }
    }

    static int controlType(JXAccessibleNode n) {
        switch (n.getRole()) {
            case ROOT: return UiaNative.CT_WINDOW;
            case GROUP: return UiaNative.CT_GROUP;
            case BUTTON: case TOGGLE_BUTTON: case SWITCH: return UiaNative.CT_BUTTON;
            case CHECK_BOX: return UiaNative.CT_CHECKBOX;
            case RADIO_BUTTON: return UiaNative.CT_RADIOBUTTON;
            case LINK: return UiaNative.CT_HYPERLINK;
            case TEXT: return UiaNative.CT_TEXT;
            case TEXT_FIELD: return UiaNative.CT_EDIT;
            case COMBO_BOX: case DATE_PICKER: case TIME_PICKER: case COLOR_PICKER: return UiaNative.CT_COMBOBOX;
            case SPINNER: return UiaNative.CT_SPINNER;
            case SLIDER: return UiaNative.CT_SLIDER;
            case PROGRESS_BAR: return UiaNative.CT_PROGRESSBAR;
            case SCROLL_BAR: return UiaNative.CT_SCROLLBAR;
            case SCROLL_PANE: case SPLIT_PANE: case DIALOG: return UiaNative.CT_PANE;
            case LIST: return UiaNative.CT_LIST;
            case LIST_ITEM: return UiaNative.CT_LISTITEM;
            case TREE: return UiaNative.CT_TREE;
            case TREE_ITEM: return UiaNative.CT_TREEITEM;
            case TABLE: return UiaNative.CT_GROUP;
            case ROW: return UiaNative.CT_DATAITEM;
            case CELL: return UiaNative.CT_TEXT;
            case TAB_LIST: return UiaNative.CT_TAB;
            case TAB: return UiaNative.CT_TABITEM;
            case MENU_BAR: return UiaNative.CT_MENUBAR;
            case MENU: return UiaNative.CT_MENU;
            case MENU_ITEM: return UiaNative.CT_MENUITEM;
            case IMAGE: return UiaNative.CT_IMAGE;
            case SEPARATOR: return UiaNative.CT_SEPARATOR;
            case TOOLTIP: return UiaNative.CT_TOOLTIP;
            case STATUS: return UiaNative.CT_STATUSBAR;
            default: return UiaNative.CT_CUSTOM;
        }
    }

    static int toggleState(JXAccessibleNode n) {
        return n.is(JXAccessibleState.MIXED) ? 2 : n.is(JXAccessibleState.CHECKED) ? 1 : 0;
    }

    static int expandState(JXAccessibleNode n) {
        return n.is(JXAccessibleState.EXPANDED) ? 1 : 0;
    }

    // ---- results -----------------------------------------------------------------------------------

    private Pointer fragmentOf(long otherId) {
        UiaProvider p = window.provider(otherId);
        p.refs.incrementAndGet();
        return p.iface(FRAGMENT);
    }

    private static int gone() {
        return UiaNative.UIA_E_ELEMENTNOTAVAILABLE;
    }

    private int navigate(int direction, Pointer out) {
        JXAccessibleNode n = node();
        if (n == null) {
            out.setPointer(0, null);
            return gone();
        }
        JXAccessibleNode target = null;
        JXAccessibleNode parent = n.getParent();
        switch (direction) {
            case 0: // parent
                target = parent;
                break;
            case 1: // next sibling
            case 2: // previous sibling
                if (parent != null) {
                    int i = n.getIndexInParent() + (direction == 1 ? 1 : -1);
                    List<JXAccessibleNode> siblings = parent.getChildren();
                    target = i >= 0 && i < siblings.size() ? siblings.get(i) : null;
                }
                break;
            case 3: // first child
                target = n.getChildren().isEmpty() ? null : n.getChildren().get(0);
                break;
            case 4: // last child
                target = n.getChildren().isEmpty() ? null : n.getChildren().get(n.getChildren().size() - 1);
                break;
            default:
                break;
        }
        // the window's root has no parent here: its parent is the window that hosts it
        out.setPointer(0, target == null ? null : fragmentOf(target.getId()));
        return UiaNative.S_OK;
    }

    private int property(int propertyId, Pointer variant) {
        JXAccessibleNode n = node();
        UiaNative.variantEmpty(variant);
        if (n == null) {
            return UiaNative.S_OK;
        }
        boolean root = id == 1;
        switch (propertyId) {
            case UiaNative.P_NAME:
                UiaNative.variantString(variant, n.getName());
                break;
            case UiaNative.P_CONTROL_TYPE:
                UiaNative.variantInt(variant, controlType(n));
                break;
            case UiaNative.P_ENABLED:
                UiaNative.variantBool(variant, !n.is(JXAccessibleState.DISABLED));
                break;
            case UiaNative.P_FOCUSABLE:
                UiaNative.variantBool(variant, n.is(JXAccessibleState.FOCUSABLE));
                break;
            case UiaNative.P_HAS_FOCUS:
                UiaNative.variantBool(variant, n.is(JXAccessibleState.FOCUSED));
                break;
            case UiaNative.P_IS_CONTROL:
            case UiaNative.P_IS_CONTENT:
                UiaNative.variantBool(variant, root || n.isContent());
                break;
            case UiaNative.P_IS_PASSWORD:
                UiaNative.variantBool(variant, n.is(JXAccessibleState.PASSWORD));
                break;
            case UiaNative.P_OFFSCREEN:
                UiaNative.variantBool(variant, n.is(JXAccessibleState.OFFSCREEN));
                break;
            case UiaNative.P_AUTOMATION_ID:
                UiaNative.variantString(variant, "jx-" + id);
                break;
            case UiaNative.P_CLASS_NAME:
                UiaNative.variantString(variant, n.getType().isEmpty() ? "JXNode" : "JX-" + n.getType());
                break;
            case UiaNative.P_FRAMEWORK_ID:
                UiaNative.variantString(variant, "JXParallel");
                break;
            case UiaNative.P_HELP_TEXT:
                if (!n.getDescription().isEmpty()) {
                    UiaNative.variantString(variant, n.getDescription());
                }
                break;
            default:
                break; // VT_EMPTY: UI Automation uses its default (or the host window's)
        }
        return UiaNative.S_OK;
    }

    private int pattern(int patternId, Pointer out) {
        out.setPointer(0, null);
        int kind = patternKind(patternId);
        if (kind >= 0 && supports(kind)) {
            refs.incrementAndGet();
            out.setPointer(0, iface(kind));
        }
        return UiaNative.S_OK;
    }

    private int perform(JXAccessibleAction action, Object argument) {
        JXAccessibleNode n = node();
        if (n == null) {
            return gone();
        }
        return window.host.perform(id, action, argument) ? UiaNative.S_OK : UiaNative.UIA_E_NOTSUPPORTED;
    }

    private int bounds(Pointer out) {
        JXAccessibleNode n = node();
        if (n == null) {
            return gone();
        }
        int[] b = n.getScreenBounds();
        out.setDouble(0, b[0]);
        out.setDouble(8, b[1]);
        out.setDouble(16, b[2]);
        out.setDouble(24, b[3]);
        return UiaNative.S_OK;
    }

    private static JXAccessibleNode deepest(JXAccessibleNode n, double x, double y) {
        List<JXAccessibleNode> kids = n.getChildren();
        for (int i = kids.size() - 1; i >= 0; i--) {
            JXAccessibleNode c = kids.get(i);
            int[] b = c.getScreenBounds();
            if (x >= b[0] && y >= b[1] && x < b[0] + b[2] && y < b[1] + b[3]) {
                JXAccessibleNode inner = deepest(c, x, y);
                return inner != null ? inner : c;
            }
        }
        return null;
    }

    // ---- the vtables -------------------------------------------------------------------------------

    private interface Body {
        int run(UiaProvider p);
    }

    /** Runs a method body for the provider behind {@code self}; an exception is a failed call, not a crashed process. */
    private static int guard(Pointer self, Body body) {
        try {
            UiaProvider p = of(self);
            return p == null ? UiaNative.UIA_E_ELEMENTNOTAVAILABLE : body.run(p);
        } catch (Throwable t) {
            System.err.println("uia: provider call failed: " + t);
            return UiaNative.E_FAIL;
        }
    }

    private static synchronized Pointer vtable(int kind) {
        if (VTABLES[kind] == null) {
            VTABLES[kind] = build(kind);
        }
        return VTABLES[kind];
    }

    private static Memory table(Pointer... slots) {
        Memory m = new Memory((long) slots.length * Native.POINTER_SIZE);
        for (int i = 0; i < slots.length; i++) {
            m.setPointer((long) i * Native.POINTER_SIZE, slots[i]);
        }
        return m;
    }

    private static Pointer[] unknown(int kind) {
        UiaNative.F3 queryInterface = (self, riid, out) -> guard(self, p -> {
            byte[] wanted = riid.getByteArray(0, 16);
            out.setPointer(0, null);
            int match = java.util.Arrays.equals(wanted, IID_UNKNOWN) ? SIMPLE : -1;
            for (int k = 0; k < KINDS && match < 0; k++) {
                if (java.util.Arrays.equals(wanted, IID[k])) {
                    match = k;
                }
            }
            if (match < 0 || !p.supports(match)) {
                return UiaNative.E_NOINTERFACE;
            }
            p.refs.incrementAndGet();
            out.setPointer(0, p.iface(match));
            return UiaNative.S_OK;
        });
        UiaNative.F1 addRef = self -> {
            UiaProvider p = of(self);
            return p == null ? 1 : p.refs.incrementAndGet();
        };
        UiaNative.F1 release = self -> {
            UiaProvider p = of(self);
            return p == null ? 0 : Math.max(0, p.refs.decrementAndGet());
        };
        return new Pointer[] {UiaNative.function(queryInterface), UiaNative.function(addRef), UiaNative.function(release)};
    }

    private static Memory build(int kind) {
        Pointer[] base = unknown(kind);
        Pointer[] own;
        switch (kind) {
            case SIMPLE:
                own = new Pointer[] {
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        out.setInt(0, UiaNative.PROVIDER_OPTIONS_SERVER_SIDE);
                        return UiaNative.S_OK;
                    })),
                    UiaNative.function((UiaNative.F3i) (self, patternId, out) -> guard(self, p -> p.pattern(patternId, out))),
                    UiaNative.function((UiaNative.F3i) (self, propertyId, variant) -> guard(self, p -> p.property(propertyId, variant))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        out.setPointer(0, p.id == 1 ? p.window.hostProvider() : null);
                        return UiaNative.S_OK;
                    }))};
                break;
            case FRAGMENT:
                own = new Pointer[] {
                    UiaNative.function((UiaNative.F3i) (self, direction, out) -> guard(self, p -> p.navigate(direction, out))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        // the window's root gets its runtime id from the HWND that hosts it
                        out.setPointer(0, p.id == 1 ? null
                                : UiaNative.intArray(3, (int) p.id, (int) (p.id >>> 32)));
                        return UiaNative.S_OK;
                    })),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> p.bounds(out))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        out.setPointer(0, null);
                        return UiaNative.S_OK;
                    })),
                    UiaNative.function((UiaNative.F1) self -> guard(self, p -> p.perform(JXAccessibleAction.FOCUS, null))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        out.setPointer(0, p.window.root().iface(ROOT));
                        p.window.root().refs.incrementAndGet();
                        return UiaNative.S_OK;
                    }))};
                break;
            case ROOT:
                own = new Pointer[] {
                    UiaNative.function((UiaNative.FDD) (self, x, y, out) -> guard(self, p -> {
                        JXAccessibleNode root = p.node();
                        JXAccessibleNode hit = root == null ? null : deepest(root, x, y);
                        out.setPointer(0, hit == null ? null : p.fragmentOf(hit.getId()));
                        return UiaNative.S_OK;
                    })),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        long focused = p.window.host.getFocusedId();
                        out.setPointer(0, focused == 0 ? null : p.fragmentOf(focused));
                        return UiaNative.S_OK;
                    }))};
                break;
            case INVOKE:
                own = new Pointer[] {UiaNative.function((UiaNative.F1) self -> guard(self, p -> p.perform(JXAccessibleAction.DEFAULT, null)))};
                break;
            case TOGGLE:
                own = new Pointer[] {
                    UiaNative.function((UiaNative.F1) self -> guard(self, p -> p.perform(JXAccessibleAction.DEFAULT, null))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        JXAccessibleNode n = p.node();
                        if (n == null) {
                            return gone();
                        }
                        out.setInt(0, toggleState(n));
                        return UiaNative.S_OK;
                    }))};
                break;
            case VALUE:
                own = new Pointer[] {
                    UiaNative.function((UiaNative.F2) (self, text) -> guard(self, p -> {
                        JXAccessibleNode n = p.node();
                        if (n != null && n.is(JXAccessibleState.READ_ONLY)) {
                            return UiaNative.E_ACCESSDENIED;
                        }
                        return p.perform(JXAccessibleAction.SET_VALUE, text == null ? "" : text.getWideString(0));
                    })),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        JXAccessibleNode n = p.node();
                        if (n == null) {
                            return gone();
                        }
                        if (n.is(JXAccessibleState.PASSWORD)) {
                            out.setPointer(0, null);
                            return UiaNative.E_ACCESSDENIED; // a password's text is never given out
                        }
                        out.setPointer(0, UiaNative.bstr(n.getValue()));
                        return UiaNative.S_OK;
                    })),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        JXAccessibleNode n = p.node();
                        out.setInt(0, n == null || n.is(JXAccessibleState.READ_ONLY) || !n.supports(JXAccessibleAction.SET_VALUE) ? -1 : 0);
                        return UiaNative.S_OK;
                    }))};
                break;
            case RANGE:
                own = new Pointer[] {
                    UiaNative.function((UiaNative.FD) (self, v) -> guard(self, p -> p.perform(JXAccessibleAction.SET_VALUE, Double.valueOf(v)))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> p.real(out, 0))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        JXAccessibleNode n = p.node();
                        out.setInt(0, n == null || !n.supports(JXAccessibleAction.SET_VALUE) ? -1 : 0);
                        return UiaNative.S_OK;
                    })),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> p.real(out, 2))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> p.real(out, 1))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> p.real(out, 3))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> p.real(out, 4)))};
                break;
            case SELITEM:
                own = new Pointer[] {
                    UiaNative.function((UiaNative.F1) self -> guard(self, p -> p.perform(JXAccessibleAction.SELECT, null))),
                    UiaNative.function((UiaNative.F1) self -> guard(self, p -> UiaNative.UIA_E_NOTSUPPORTED)),
                    UiaNative.function((UiaNative.F1) self -> guard(self, p -> UiaNative.UIA_E_NOTSUPPORTED)),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        JXAccessibleNode n = p.node();
                        out.setInt(0, n != null && (n.is(JXAccessibleState.SELECTED) || n.is(JXAccessibleState.CHECKED)) ? -1 : 0);
                        return UiaNative.S_OK;
                    })),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        out.setPointer(0, null);
                        return UiaNative.S_OK;
                    }))};
                break;
            default: // EXPAND
                own = new Pointer[] {
                    UiaNative.function((UiaNative.F1) self -> guard(self, p -> p.perform(JXAccessibleAction.EXPAND, null))),
                    UiaNative.function((UiaNative.F1) self -> guard(self, p -> p.perform(JXAccessibleAction.COLLAPSE, null))),
                    UiaNative.function((UiaNative.F2) (self, out) -> guard(self, p -> {
                        JXAccessibleNode n = p.node();
                        if (n == null) {
                            return gone();
                        }
                        out.setInt(0, expandState(n));
                        return UiaNative.S_OK;
                    }))};
                break;
        }
        Pointer[] all = new Pointer[base.length + own.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(own, 0, all, base.length, own.length);
        return table(all);
    }

    /** 0 value, 1 minimum, 2 maximum, 3 large change, 4 small change of a range node. */
    private int real(Pointer out, int which) {
        JXAccessibleNode n = node();
        if (n == null) {
            return gone();
        }
        double span = n.getMax() - n.getMin();
        double[] values = {n.getNow(), n.getMin(), n.getMax(), span / 10, span / 100};
        out.setDouble(0, Double.isNaN(values[which]) ? 0 : values[which]);
        return UiaNative.S_OK;
    }

    private static byte[] guid(String text) {
        String[] p = text.split("-");
        byte[] out = new byte[16];
        long d1 = Long.parseLong(p[0], 16);
        int d2 = Integer.parseInt(p[1], 16);
        int d3 = Integer.parseInt(p[2], 16);
        for (int i = 0; i < 4; i++) {
            out[i] = (byte) (d1 >>> (8 * i));
        }
        out[4] = (byte) d2;
        out[5] = (byte) (d2 >>> 8);
        out[6] = (byte) d3;
        out[7] = (byte) (d3 >>> 8);
        String rest = p[3] + p[4];
        for (int i = 0; i < 8; i++) {
            out[8 + i] = (byte) Integer.parseInt(rest.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
