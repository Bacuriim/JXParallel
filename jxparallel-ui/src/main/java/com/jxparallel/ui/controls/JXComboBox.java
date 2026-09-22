package com.jxparallel.ui.controls;

import com.jxparallel.properties.JXIntegerProperty;
import com.jxparallel.properties.JXObservableList;
import com.jxparallel.properties.JXStringProperty;
import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXComboBox<T> implements JXComponent {
    private final JXObservableList<T> items = new JXObservableList<T>();
    private final JXIntegerProperty selectedIndex = new JXIntegerProperty(-1);
    private final JXStringProperty value = new JXStringProperty("");

    public JXObservableList<T> getItems() {
        return items;
    }

    public int getSelectedIndex() {
        return selectedIndex.getInt();
    }

    public void select(int index) {
        if (index < 0 || index >= items.size()) {
            selectedIndex.set(-1);
            value.set("");
            return;
        }
        selectedIndex.set(index);
        value.set(String.valueOf(items.get(index)));
    }

    public String getValue() {
        return value.get();
    }

    @Override
    public JXElement render() {
        return JXElement.of("select", JXProps.builder()
                .set("value", getValue())
                .set("selectedIndex", getSelectedIndex())
                .set("options", items.snapshot().toArray())
                .build());
    }
}
