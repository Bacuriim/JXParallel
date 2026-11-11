package com.jxparallel.examples.orders;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * State of the order editor dialog ({@code editor.jxml}). The form holds an {@link OrderDraft}; a field shows its
 * message once the user has touched it or pressed Save, so a new blank order does not open covered in red.
 */
public final class EditorController {
    private final Session session;
    private final Consumer<Order> onSaved;

    private boolean open;
    private OrderDraft draft = OrderDraft.blank(java.time.LocalDate.now());
    private boolean submitted;
    private final Set<String> touched = new HashSet<String>();

    /** @param onSaved called, on the UI thread, with the order that was just saved to the store */
    public EditorController(Session session, Consumer<Order> onSaved) {
        this.session = session;
        this.onSaved = onSaved;
    }

    public void openNew() {
        begin(OrderDraft.blank(session.today()));
    }

    public void openFor(Order order) {
        begin(OrderDraft.of(order));
    }

    private void begin(OrderDraft d) {
        draft = d;
        submitted = false;
        touched.clear();
        open = true;
        session.host().requestRender();
    }

    // ---- state ---------------------------------------------------------------------------------------------

    public boolean getOpen() {
        return open;
    }

    /** The dialog's {@code onShowing}: Escape, or a press outside, asks for {@code false}. */
    public void setOpen(boolean v) {
        if (!v) {
            cancel();
        }
    }

    public OrderDraft getDraft() {
        return draft;
    }

    public String getTitle() {
        return draft.isNew() ? "New order" : "Edit order #" + draft.getId();
    }

    public Palette getPalette() {
        return session.palette();
    }

    // ---- fields --------------------------------------------------------------------------------------------

    public void setCustomer(String v) {
        draft.setCustomer(v);
        touch("customer");
    }

    public void setEmail(String v) {
        draft.setEmail(v);
        touch("email");
    }

    public void setDate(String v) {
        draft.setDateText(v);
        touch("date");
    }

    public void setStatus(String v) {
        draft.setStatusLabel(v);
        touch("status");
    }

    public List<String> getStatusChoices() {
        return Status.labels();
    }

    private void touch(String field) {
        touched.add(field);
        session.host().requestRender();
    }

    // ---- line items ----------------------------------------------------------------------------------------

    public void addLine() {
        draft.addLine();
        touched.remove("lines");
        session.host().requestRender();
    }

    public void removeLine(OrderDraft.DraftLine line) {
        draft.removeLine(line);
        touched.add("lines");
        session.host().requestRender();
    }

    public void setDescription(OrderDraft.DraftLine line, String v) {
        line.setDescription(v);
        line.touch();
        touched.add("lines");
        session.host().requestRender();
    }

    /** The spinner ({@code integer="true"}) reports an int. */
    public void setQuantity(OrderDraft.DraftLine line, int v) {
        line.setQuantity(v);
        line.touch();
        touched.add("lines");
        session.host().requestRender();
    }

    public void setPrice(OrderDraft.DraftLine line, String v) {
        line.setUnitPrice(v);
        line.touch();
        touched.add("lines");
        session.host().requestRender();
    }

    // ---- messages ------------------------------------------------------------------------------------------

    private String message(String field) {
        Map<String, String> errors = draft.validate(session.today());
        String m = errors.get(field);
        return m != null && (submitted || touched.contains(field)) ? m : "";
    }

    public String getCustomerError() {
        return message("customer");
    }

    public String getEmailError() {
        return message("email");
    }

    public String getDateError() {
        return message("date");
    }

    public String getLinesError() {
        return message("lines");
    }

    public String getTotalText() {
        return draft.getTotalText();
    }

    public boolean getHasErrors() {
        return !draft.validate(session.today()).isEmpty();
    }

    // ---- the buttons ---------------------------------------------------------------------------------------

    /** Save: validates, and either shows the messages or stores the order and closes. */
    public void save() {
        submitted = true;
        for (OrderDraft.DraftLine l : draft.getLines()) {
            l.markSubmitted();
        }
        if (!draft.validate(session.today()).isEmpty()) {
            session.host().requestRender();
            return;
        }
        OrderStore store = session.store();
        Order order = draft.toOrder(draft.isNew() ? store.nextId() : draft.getId(), session.today());
        store.save(order);
        open = false;
        onSaved.accept(order);
    }

    public void cancel() {
        open = false;
        session.host().requestRender();
    }
}
