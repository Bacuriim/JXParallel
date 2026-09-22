package com.jxparallel.ui.style;

import java.util.ArrayList;
import java.util.List;

import com.jxparallel.ui.JXElement;

public final class JXStyleSheet {
    private final List<Rule> rules = new ArrayList<Rule>();

    public JXStyleSheet add(String selector, JXStyle style) {
        if (selector == null || selector.trim().isEmpty() || style == null) {
            throw new IllegalArgumentException("Selector and style are required");
        }
        rules.add(new Rule(selector.trim(), style));
        return this;
    }

    public JXStyle resolve(JXElement element, JXStyle fallback) {
        JXStyle result = fallback == null ? JXStyle.builder().build() : fallback;
        for (Rule rule : rules) {
            if (rule.matches(element)) result = result.merge(rule.style);
        }
        return result;
    }

    private static final class Rule {
        private final String selector;
        private final JXStyle style;
        private Rule(String selector, JXStyle style) {
            this.selector = selector;
            this.style = style;
        }
        private boolean matches(JXElement element) {
            if (selector.startsWith("#")) {
                return selector.substring(1).equals(element.getProps().getString("id"));
            }
            if (selector.startsWith(".")) {
                String classes = element.getProps().getString("class");
                return classes != null && (" " + classes + " ").contains(" " + selector.substring(1) + " ");
            }
            return selector.equals(element.getType());
        }
    }
}
