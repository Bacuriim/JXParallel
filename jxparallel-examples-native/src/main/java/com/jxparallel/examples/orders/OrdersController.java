package com.jxparallel.examples.orders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * State of the orders screen ({@code orders.jxml}): the search, the filters, the table's window and selection, and
 * the details panel. The table shows {@link #getVisible()}, the whole list after the filters, and the screen scrolls
 * over all of it ({@code first="@{top}"}); only the rows in view are built.
 *
 * <p>Selection is kept as order ids, so it survives a new search or sort: the rows that are still listed stay
 * selected, the others drop out. The table speaks in row indices (its {@code selected} and {@code selection}).
 */
public final class OrdersController {
    private final Session session;
    private final Runnable onNewOrder;
    private final Consumer<Order> onEditOrder;
    private final Runnable onChanged;

    private String query = "";
    private String statusFilter = OrderQuery.ALL_STATUSES;
    private SortKey sortKey = SortKey.ID_ASC;
    private int sortColumn = SortKey.ID_ASC.column();
    private boolean sortAscending = true;
    private boolean multiple = true;
    private boolean confirmDelete;
    private int top;
    private List<Order> visible = Collections.emptyList();
    private Map<Integer, Integer> indexById = new HashMap<Integer, Integer>();
    private final Set<Integer> selectedIds = new LinkedHashSet<Integer>();
    private int lastId = -1;

    /**
     * @param onNewOrder opens the editor on a blank order
     * @param onEditOrder opens the editor on that order
     * @param onChanged called after this screen changed the orders (a delete): the shell refreshes the others
     */
    public OrdersController(Session session, Runnable onNewOrder, Consumer<Order> onEditOrder, Runnable onChanged) {
        this.session = session;
        this.onNewOrder = onNewOrder;
        this.onEditOrder = onEditOrder;
        this.onChanged = onChanged;
    }

    // ---- the list ----------------------------------------------------------------------------------------

    /** Applies the search, the status and the sort to the store's orders again; call it when any of them changed. */
    public void refresh() {
        visible = Collections.unmodifiableList(OrderQuery.apply(session.store().getOrders(), query, statusFilter,
                sortKey));
        Map<Integer, Integer> index = new HashMap<Integer, Integer>(visible.size() * 2);
        for (int i = 0; i < visible.size(); i++) {
            index.put(visible.get(i).getId(), i);
        }
        indexById = index;
        selectedIds.retainAll(index.keySet());
        if (!index.containsKey(lastId)) {
            lastId = selectedIds.isEmpty() ? -1 : selectedIds.iterator().next();
        }
        top = clampTop(top);
    }

    private int clampTop(int value) {
        return Math.max(0, Math.min(value, Math.max(0, visible.size() - 1)));
    }

    public List<Order> getVisible() {
        return visible;
    }

    public String getCountText() {
        int total = session.store().size();
        return visible.size() == total ? count(total) + " orders"
                : count(visible.size()) + " of " + count(total) + " orders";
    }

    static String count(int n) {
        return String.format(java.util.Locale.US, "%,d", n);
    }

    // ---- search, filter, sort ----------------------------------------------------------------------------

    public String getQuery() {
        return query;
    }

    /** Called on every keystroke of the search field. */
    public void setQuery(String v) {
        query = v == null ? "" : v;
        top = 0;
        refresh();
        session.host().requestRender();
    }

    public List<String> getStatusChoices() {
        return OrderQuery.statusChoices();
    }

    public String getStatusFilter() {
        return statusFilter;
    }

    public void setStatusFilter(String v) {
        statusFilter = v;
        top = 0;
        refresh();
        session.host().requestRender();
    }

    /** The column the table is sorted by (0 Id ... 4 Total), -1 for none: its header draws the arrow. */
    public int getSortColumn() {
        return sortColumn;
    }

    public boolean getSortAscending() {
        return sortAscending;
    }

    /** The table's {@code onSort}: a press on a header gives {@code {column, 1 or 0}}, -1 for no column. */
    public void sortBy(double[] state) {
        int column = (int) state[0];
        boolean ascending = state[1] != 0;
        sort(SortKey.of(column, ascending));
        // unsorted (-1) is shown as no arrow, and lists the orders by id
        sortColumn = column;
        sortAscending = ascending;
    }

    /** Sorts by {@code key}, as a press on its column header does. */
    public void sort(SortKey key) {
        sortKey = key;
        sortColumn = key.column();
        sortAscending = key.ascending();
        top = 0;
        refresh();
        session.host().requestRender();
    }

    public SortKey getSortKey() {
        return sortKey;
    }

    // ---- the table's window and selection -----------------------------------------------------------------

    public int getTop() {
        return top;
    }

    public void setTop(int v) {
        top = clampTop(v);
        session.host().requestRender();
    }

    /** {@code single} or {@code multiple}: the table's {@code selectionMode}. */
    public String getSelectionMode() {
        return multiple ? "multiple" : "single";
    }

    public boolean getMultiple() {
        return multiple;
    }

    public void setMultiple(boolean v) {
        multiple = v;
        if (!multiple && lastId >= 0 && selectedIds.contains(lastId)) {
            selectedIds.clear();
            selectedIds.add(lastId);
        }
        session.host().requestRender();
    }

    /** The row the user selected last, in the table's indices (-1: none). */
    public int getCurrent() {
        Integer i = lastId < 0 ? null : indexById.get(lastId);
        return i == null || !selectedIds.contains(lastId) ? -1 : i;
    }

    public void setCurrent(int index) {
        if (index < 0 || index >= visible.size()) {
            lastId = -1;
            if (!multiple) {
                selectedIds.clear();
            }
        } else {
            lastId = visible.get(index).getId();
            if (!multiple) {
                selectedIds.clear();
                selectedIds.add(lastId);
            }
        }
        session.host().requestRender();
    }

    /** Every selected row, in the table's indices. */
    public double[] getPicked() {
        double[] out = new double[selectedIds.size()];
        int n = 0;
        for (Integer id : selectedIds) {
            out[n++] = indexById.get(id);
        }
        Arrays.sort(out);
        return out;
    }

    public void setPicked(double[] indices) {
        selectedIds.clear();
        for (double d : indices) {
            int i = (int) d;
            if (i >= 0 && i < visible.size()) {
                selectedIds.add(visible.get(i).getId());
            }
        }
        if (!selectedIds.contains(lastId)) {
            lastId = -1;
            for (Integer id : selectedIds) {
                lastId = id;
            }
        }
        session.host().requestRender();
    }

    public List<Order> getSelection() {
        List<Order> out = new ArrayList<Order>();
        for (Integer id : selectedIds) {
            Integer i = indexById.get(id);
            if (i != null) {
                out.add(visible.get(i));
            }
        }
        return out;
    }

    public void clearSelection() {
        selectedIds.clear();
        lastId = -1;
        session.host().requestRender();
    }

    /** Selects the order with that id if it is listed, and scrolls to it. */
    public void select(int id) {
        Integer i = indexById.get(id);
        selectedIds.clear();
        lastId = -1;
        if (i != null) {
            selectedIds.add(id);
            lastId = id;
            top = clampTop(i - 3);
        }
    }

    // ---- the details panel -------------------------------------------------------------------------------

    public boolean getNoSelection() {
        return selectedIds.isEmpty();
    }

    public boolean getHasSelection() {
        return !selectedIds.isEmpty();
    }

    public boolean getOneSelected() {
        return selectedIds.size() == 1;
    }

    public boolean getSeveralSelected() {
        return selectedIds.size() > 1;
    }

    /** The only selected order, or {@code null}; read it only under {@code getOneSelected()}. */
    public Order getOne() {
        List<Order> s = getSelection();
        return s.size() == 1 ? s.get(0) : null;
    }

    public String getDetailTitle() {
        int n = selectedIds.size();
        return n == 0 ? "No order selected" : n == 1 ? "Order #" + getOne().getId() : n + " orders selected";
    }

    /** Sum of the selected orders (several selected). */
    public String getSelectedTotalText() {
        long sum = 0;
        for (Order o : getSelection()) {
            sum += o.getTotalCents();
        }
        return Money.format(sum);
    }

    public String getSelectedHint() {
        return "Ctrl+click adds a row, Shift+click a range.";
    }

    // ---- actions ------------------------------------------------------------------------------------------

    public void newOrder() {
        onNewOrder.run();
    }

    /** The Edit button, and Enter or a double click on a row ({@code edit(o)}). */
    public void editSelected() {
        Order o = getOne();
        if (o != null) {
            onEditOrder.accept(o);
        }
    }

    public void edit(Order order) {
        onEditOrder.accept(order);
    }

    public boolean getConfirmDelete() {
        return confirmDelete;
    }

    public void setConfirmDelete(boolean v) {
        confirmDelete = v && !selectedIds.isEmpty();
        session.host().requestRender();
    }

    public void askDelete() {
        setConfirmDelete(true);
    }

    public void keepOrders() {
        setConfirmDelete(false);
    }

    public String getDeleteMessage() {
        int n = selectedIds.size();
        return n == 1 ? "Order #" + lastId + " will be removed from this file." : n + " orders will be removed from this file.";
    }

    public void delete() {
        confirmDelete = false;
        session.store().delete(new LinkedHashSet<Integer>(selectedIds));
        selectedIds.clear();
        lastId = -1;
        onChanged.run();
    }

    public Palette getPalette() {
        return session.palette();
    }
}
