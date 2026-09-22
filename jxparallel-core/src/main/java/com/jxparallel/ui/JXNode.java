package com.jxparallel.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JXNode {
    private String type;
    private final Map<String, Object> props = new LinkedHashMap<String, Object>();
    private final List<JXNode> children = new ArrayList<JXNode>();

    public static JXNode mount(JXElement element) {
        JXNode node = new JXNode();
        node.patch(null, element);
        return node;
    }

    public void patch(JXElement previous, JXElement next) {
        if (next == null) {
            throw new IllegalArgumentException("Next element cannot be null");
        }
        if (type == null || !type.equals(next.getType())) {
            type = next.getType();
            props.clear();
            children.clear();
        }
        props.clear();
        props.putAll(next.getProps().asMap());
        List<JXElement> nextChildren = next.getChildren();
        for (int index = 0; index < nextChildren.size(); index++) {
            if (index < children.size()) {
                children.get(index).patch(
                        previous == null || index >= previous.getChildren().size()
                                ? null : previous.getChildren().get(index),
                        nextChildren.get(index));
            } else {
                children.add(JXNode.mount(nextChildren.get(index)));
            }
        }
        while (children.size() > nextChildren.size()) {
            children.remove(children.size() - 1);
        }
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getProps() {
        return Collections.unmodifiableMap(props);
    }

    public List<JXNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
