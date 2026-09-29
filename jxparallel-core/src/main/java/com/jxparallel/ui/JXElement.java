package com.jxparallel.ui;

import java.util.Arrays;
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
        this.children = children;
    }

    public static JXElement of(String type) {
        return new JXElement(type, JXProps.empty(), Collections.<JXElement>emptyList());
    }

    /** Null children are left out; the array is copied, so the caller may reuse it. */
    public static JXElement of(String type, JXProps props, JXElement... children) {
        int n = 0;
        if (children != null) {
            for (JXElement child : children) {
                if (child != null) {
                    n++;
                }
            }
        }
        if (n == 0) {
            return new JXElement(type, props, Collections.<JXElement>emptyList());
        }
        JXElement[] values = new JXElement[n];
        int i = 0;
        for (JXElement child : children) {
            if (child != null) {
                values[i++] = child;
            }
        }
        return new JXElement(type, props, Collections.unmodifiableList(Arrays.asList(values)));
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
