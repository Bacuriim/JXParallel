package com.jxparallel.mockito;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JXParallelMockitoSupportTest {
    @Test
    void shouldExposeIsolationUtilities() {
        assertNotNull(JXParallelMockitoSupport.class);
        JXParallelMockitoSupport.isolate();
    }
}
