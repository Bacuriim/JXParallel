package com.jxparallel.forge;

import com.jxparallel.ui.JXElement;

/** A screen: generated from a JXML file by the compiler, or a {@link JxmlTemplate} read at runtime. */
public interface JxmlView<C> {
    /** The screen's element tree, with bindings read from and handlers bound to {@code controller}. */
    JXElement build(C controller);
}
