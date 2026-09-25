package com.jxparallel.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.controls.JXRenderMemo;

public final class JXPane implements JXComponent {
    private final List<JXComponent> children = new ArrayList<JXComponent>();
    private final JXRenderMemo memo = new JXRenderMemo();
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

    /** Returns the previous element when the gap and every child's element are unchanged. */
    @Override
    public JXElement render() {
        final JXElement[] elements = new JXElement[children.size()];
        Object[] state = new Object[elements.length + 1];
        state[0] = gap;
        for (int index = 0; index < elements.length; index++) {
            elements[index] = children.get(index).render();
            state[index + 1] = elements[index];
        }
        return memo.render(() -> JXElement.of("column", JXProps.builder().set("gap", gap).build(), elements), state);
    }
}
