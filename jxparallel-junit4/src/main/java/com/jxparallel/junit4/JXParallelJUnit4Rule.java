package com.jxparallel.junit4;

import com.jxparallel.core.JXParallel;
import org.junit.rules.ExternalResource;

public class JXParallelJUnit4Rule extends ExternalResource {
    @Override
    protected void before() {
        JXParallel.start();
    }

    @Override
    protected void after() {
        JXParallel.shutdown();
    }
}
