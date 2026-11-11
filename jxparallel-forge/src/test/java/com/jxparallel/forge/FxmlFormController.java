package com.jxparallel.forge;

/** Controller of customer-form.fxml, after moving the code that touched the nodes to getters. */
public class FxmlFormController {
    public int saved;

    public String getName() {
        return "Ada";
    }

    public String getCity() {
        return "Recife";
    }

    public boolean getActive() {
        return true;
    }

    public boolean getCanReset() {
        return false;
    }

    public boolean getReadOnly() {
        return false;
    }

    public String getStatus() {
        return "Ready";
    }

    public String getKind() {
        return "company";
    }

    public void save() {
        saved++;
    }

    public void close() {
    }

    public void reset() {
    }
}
