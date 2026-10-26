package com.jxparallel.forge;

import com.jxparallel.ui.JXElement;

/** A screen: generated from a JXML file by the compiler, or a {@link JxmlTemplate} read at runtime. */
public interface JxmlView<C> {
    /** The screen's element tree, with bindings read from and handlers bound to {@code controller}. */
    JXElement build(C controller);

    /**
     * The screen kept live: the same tree {@link #build} gives, plus a recording that lets {@link JxmlLive#refresh} write
     * only what changed into the mounted tree. A view that has no plan (the interpreter) records nothing, and its refresh
     * builds the screen and reconciles it, as before; generated classes override this with their plan.
     */
    default JxmlLive<C> live(C controller) {
        return JxmlLive.of(this, null, controller);
    }
}
