package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.List;

/** Controller of orders-table.fxml, after moving the code that filled the TableView to a getter. */
public class FxmlTableController {
    private final List<FxmlOrder> orders = new ArrayList<FxmlOrder>();
    public int refreshed;

    public FxmlTableController() {
        orders.add(new FxmlOrder(1, "Ada", 10.5));
        orders.add(new FxmlOrder(2, "Grace", 99));
    }

    public List<FxmlOrder> getOrders() {
        return orders;
    }

    public String getFilter() {
        return "";
    }

    public String getCount() {
        return orders.size() + " orders";
    }

    public void refresh() {
        refreshed++;
    }
}
