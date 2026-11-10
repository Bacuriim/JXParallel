package com.jxparallel.ui.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One node of an accessibility tree: an immutable snapshot, so an assistive-technology thread can read it while the UI
 * keeps changing. Build one from a live window with {@code JXNativeAccessibility}, or from an element with
 * {@link JXAccessibilityTree#from}.
 *
 * <p>Numbers that do not apply are -1 ({@link #getItemIndex}, {@link #getRowIndex}...), 0 for {@link #getLevel}, and NaN
 * for the range ({@link #hasRange()}).
 */
public final class JXAccessibleNode {
    private final long id;
    private final JXAccessibilityRole role;
    private final String name;
    private final String description;
    private final String value;
    private final double min;
    private final double max;
    private final double now;
    private final Set<JXAccessibleState> states;
    private final Set<JXAccessibleAction> actions;
    private final int[] bounds;
    private final int[] screenBounds;
    private final List<JXAccessibleNode> children;
    private final int itemIndex;
    private final int itemCount;
    private final int rowIndex;
    private final int rowCount;
    private final int columnIndex;
    private final int columnCount;
    private final int level;
    private final boolean content;
    private final long labelledBy;
    private final String type;
    private JXAccessibleNode parent;
    private int indexInParent = -1;

    public JXAccessibleNode(JXAccessibilityRole role, String name, List<JXAccessibleNode> children) {
        this(new Builder(role).name(name).children(children));
    }

    private JXAccessibleNode(Builder b) {
        this.id = b.id;
        this.role = b.role == null ? JXAccessibilityRole.UNKNOWN : b.role;
        this.name = b.name == null ? "" : b.name;
        this.description = b.description == null ? "" : b.description;
        this.value = b.value == null ? "" : b.value;
        this.min = b.min;
        this.max = b.max;
        this.now = b.now;
        this.states = Collections.unmodifiableSet(b.states.isEmpty() ? EnumSet.noneOf(JXAccessibleState.class) : EnumSet.copyOf(b.states));
        this.actions = Collections.unmodifiableSet(b.actions.isEmpty() ? EnumSet.noneOf(JXAccessibleAction.class) : EnumSet.copyOf(b.actions));
        this.bounds = b.bounds.clone();
        this.screenBounds = b.screenBounds.clone();
        this.children = Collections.unmodifiableList(new ArrayList<JXAccessibleNode>(b.children));
        this.itemIndex = b.itemIndex;
        this.itemCount = b.itemCount;
        this.rowIndex = b.rowIndex;
        this.rowCount = b.rowCount;
        this.columnIndex = b.columnIndex;
        this.columnCount = b.columnCount;
        this.level = b.level;
        this.content = b.content;
        this.labelledBy = b.labelledBy;
        this.type = b.type == null ? "" : b.type;
        for (int i = 0; i < this.children.size(); i++) {
            this.children.get(i).parent = this;
            this.children.get(i).indexInParent = i;
        }
    }

    /** Stable while the node lives in the window (it survives re-renders that reconcile it); 0 for nodes of an element tree. */
    public long getId() { return id; }
    public JXAccessibilityRole getRole() { return role; }
    /** The accessible name: what a screen reader reads for the node ("Save"). Never null. */
    public String getName() { return name; }
    /** The longer help text (tooltip or {@code accessibleDescription}). Never null. */
    public String getDescription() { return description; }
    /** The text value (the text of a field, the option of a combo box, "50%"). Empty for a password field. Never null. */
    public String getValue() { return value; }
    public boolean hasRange() { return !Double.isNaN(now); }
    public double getMin() { return min; }
    public double getMax() { return max; }
    /** The numeric value of a slider, spinner, scroll bar or progress bar; NaN when it has none (or is indeterminate). */
    public double getNow() { return now; }
    public Set<JXAccessibleState> getStates() { return states; }
    public boolean is(JXAccessibleState state) { return states.contains(state); }
    public Set<JXAccessibleAction> getActions() { return actions; }
    public boolean supports(JXAccessibleAction action) { return actions.contains(action); }
    /** Bounds in logical pixels of the window's content, as {x, y, width, height}. */
    public int[] getBounds() { return bounds.clone(); }
    /** Bounds in screen pixels (the units GLFW positions the window in), as {x, y, width, height}. */
    public int[] getScreenBounds() { return screenBounds.clone(); }
    public List<JXAccessibleNode> getChildren() { return children; }
    public JXAccessibleNode getParent() { return parent; }
    public int getIndexInParent() { return indexInParent; }
    /** Position of a list row, tab, page or option among all of them, even those not built (virtual lists); -1 elsewhere. */
    public int getItemIndex() { return itemIndex; }
    /** How many rows a list has, built or not; -1 for nodes that are not lists or their items. */
    public int getItemCount() { return itemCount; }
    public int getRowIndex() { return rowIndex; }
    public int getRowCount() { return rowCount; }
    public int getColumnIndex() { return columnIndex; }
    public int getColumnCount() { return columnCount; }
    /** Depth of a tree item, 1 for the top level; 0 for other nodes. */
    public int getLevel() { return level; }
    /** False for layout-only groups an adapter may leave out of its control view. */
    public boolean isContent() { return content; }
    /** The id of the node that names this one (a {@code labelFor} label), or 0. */
    public long getLabelledBy() { return labelledBy; }
    /** The type of the element in the renderer ("button", "list"...), for diagnostics; empty for element trees. */
    public String getType() { return type; }

    public List<JXAccessibleNode> flatten() {
        List<JXAccessibleNode> result = new ArrayList<JXAccessibleNode>();
        result.add(this);
        for (JXAccessibleNode child : children) result.addAll(child.flatten());
        return result;
    }

    /** The first node (this one included, depth first) with this id, or null. */
    public JXAccessibleNode findById(long wanted) {
        if (id == wanted && id != 0) return this;
        for (JXAccessibleNode child : children) {
            JXAccessibleNode found = child.findById(wanted);
            if (found != null) return found;
        }
        return null;
    }

    /** The first node (depth first) with this role and name, or null. */
    public JXAccessibleNode find(JXAccessibilityRole wantedRole, String wantedName) {
        if (role == wantedRole && name.equals(wantedName)) return this;
        for (JXAccessibleNode child : children) {
            JXAccessibleNode found = child.find(wantedRole, wantedName);
            if (found != null) return found;
        }
        return null;
    }

    /** The tree as indented text, one node per line; see {@link JXAccessibilityTree#dump}. */
    public String dump() {
        return JXAccessibilityTree.dump(this);
    }

    @Override
    public String toString() {
        return role + " \"" + name + "\"";
    }

    /** Builds a node; the setters that do not apply keep the "none" defaults. */
    public static final class Builder {
        private long id;
        private final JXAccessibilityRole role;
        private String name = "";
        private String description = "";
        private String value = "";
        private double min = Double.NaN;
        private double max = Double.NaN;
        private double now = Double.NaN;
        private final Set<JXAccessibleState> states = EnumSet.noneOf(JXAccessibleState.class);
        private final Set<JXAccessibleAction> actions = EnumSet.noneOf(JXAccessibleAction.class);
        private int[] bounds = new int[4];
        private int[] screenBounds = new int[4];
        private List<JXAccessibleNode> children = new ArrayList<JXAccessibleNode>();
        private int itemIndex = -1;
        private int itemCount = -1;
        private int rowIndex = -1;
        private int rowCount = -1;
        private int columnIndex = -1;
        private int columnCount = -1;
        private int level;
        private boolean content = true;
        private long labelledBy;
        private String type = "";

        public Builder(JXAccessibilityRole role) {
            this.role = role;
        }

        public Builder id(long v) { id = v; return this; }
        public Builder name(String v) { name = v; return this; }
        public Builder description(String v) { description = v; return this; }
        public Builder value(String v) { value = v; return this; }
        public Builder range(double minimum, double maximum, double current) { min = minimum; max = maximum; now = current; return this; }
        public Builder state(JXAccessibleState s, boolean on) {
            if (on) states.add(s); else states.remove(s);
            return this;
        }
        public Builder action(JXAccessibleAction a) { actions.add(a); return this; }
        public Builder bounds(int x, int y, int w, int h) { bounds = new int[] {x, y, w, h}; return this; }
        public Builder screenBounds(int x, int y, int w, int h) { screenBounds = new int[] {x, y, w, h}; return this; }
        public Builder children(List<JXAccessibleNode> v) {
            children = new ArrayList<JXAccessibleNode>(v == null ? Collections.<JXAccessibleNode>emptyList() : v);
            return this;
        }
        public Builder item(int index, int count) { itemIndex = index; itemCount = count; return this; }
        public Builder row(int index, int count) { rowIndex = index; rowCount = count; return this; }
        public Builder column(int index, int count) { columnIndex = index; columnCount = count; return this; }
        public Builder level(int v) { level = v; return this; }
        public Builder content(boolean v) { content = v; return this; }
        public Builder labelledBy(long v) { labelledBy = v; return this; }
        public Builder type(String v) { type = v; return this; }
        public boolean has(JXAccessibleState s) { return states.contains(s); }

        public JXAccessibleNode build() {
            return new JXAccessibleNode(this);
        }
    }
}
