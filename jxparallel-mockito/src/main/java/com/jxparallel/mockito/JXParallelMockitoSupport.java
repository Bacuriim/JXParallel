package com.jxparallel.mockito;

import org.mockito.Mockito;

public final class JXParallelMockitoSupport {
    private JXParallelMockitoSupport() {
    }

    public static void isolate() {
    }

    public static void reset(Object... mocks) {
        if (mocks != null && mocks.length > 0) {
            Mockito.reset(mocks);
        }
    }
}
