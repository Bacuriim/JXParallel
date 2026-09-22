package com.jxparallel.junit5;

import com.jxparallel.core.JXParallel;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class JXParallelJUnit5Extension implements BeforeEachCallback, AfterEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        JXParallel.start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        JXParallel.shutdown();
    }
}
