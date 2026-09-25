package com.jxparallel.ui.controls;

import java.util.Arrays;
import java.util.function.Supplier;

import com.jxparallel.ui.JXElement;

/**
 * Remembers the last element a component rendered and returns the same instance while the
 * component's state is unchanged. The state is passed in on every call (not tracked through
 * listeners), so a stale element cannot be returned. Identical instances let
 * {@code JXNativeNode.reconcile} skip whole subtrees.
 */
public final class JXRenderMemo {
    private Object[] keys;
    private JXElement element;

    public JXElement render(Supplier<JXElement> build, Object... state) {
        if (element == null || !Arrays.equals(keys, state)) {
            keys = state;
            element = build.get();
        }
        return element;
    }
}
