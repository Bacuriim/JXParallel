package com.jxparallel.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JXElement {
    private final String type;
    private final JXProps props;
    private final List<JXElement> children;

    private JXElement(String type, JXProps props, List<JXElement> children) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Element type cannot be empty");
        }
        this.type = type;
        this.props = props == null ? JXProps.empty() : props;
        this.children = Collections.unmodifiableList(new ArrayList<JXElement>(children));
    }

    public static JXElement of(String type) {
        return new JXElement(type, JXProps.empty(), Collections.<JXElement>emptyList());
    }

    public static JXElement of(String type, JXProps props, JXElement... children) {
        List<JXElement> values = new ArrayList<JXElement>();
        if (children != null) {
            for (JXElement child : children) {
                if (child != null) {
                    values.add(child);
                }
            }
        }
        return new JXElement(type, props, values);
    }

    public static JXElement text(String value) {
        return of("#text", JXProps.builder().set("value", value == null ? "" : value).build());
    }

    public String getType() {
        return type;
    }

    public JXProps getProps() {
        return props;
    }

    public List<JXElement> getChildren() {
        return children;
    }
}
