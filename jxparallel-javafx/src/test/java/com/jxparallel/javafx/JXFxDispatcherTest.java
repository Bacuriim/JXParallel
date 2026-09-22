package com.jxparallel.javafx;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JXFxDispatcherTest {
    @Test
    void shouldFailExplicitlyWhenToolkitIsUnavailable() {
        assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                JXFxDispatcher.runAndWait(new Runnable() {
                    @Override
                    public void run() {
                    }
                });
            }
        });
    }
}
