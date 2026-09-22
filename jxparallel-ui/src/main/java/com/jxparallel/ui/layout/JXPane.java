package com.jxparallel.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

public final class JXPane implements JXComponent {
    private final List<JXComponent> children = new ArrayList<JXComponent>();
    private double gap;

    public JXPane() {
        this(0.0);
    }

    public JXPane(double gap) {
        this.gap = Math.max(0.0, gap);
    }

    public void add(JXComponent child) {
        if (child == null) {
            throw new IllegalArgumentException("Child cannot be null");
        }
        children.add(child);
    }

    public boolean remove(JXComponent child) {
        return children.remove(child);
    }

    public List<JXComponent> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public double getGap() {
        return gap;
    }

    public void setGap(double value) {
        gap = Math.max(0.0, value);
    }

    @Override
    public JXElement render() {
        JXElement[] elements = new JXElement[children.size()];
        for (int index = 0; index < children.size(); index++) {
            elements[index] = children.get(index).render();
        }
        return JXElement.of("column", JXProps.builder().set("gap", gap).build(), elements);
    }
}
