package com.jxparallel.events;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXEventTypeTest {
    @Test
    void equalNamedTypesShouldShareHandlers() {
        JXEventDispatcher dispatcher = new JXEventDispatcher();
        AtomicInteger calls = new AtomicInteger();
        dispatcher.addListener(new JXEventType<TestEvent>("TestEvent"), event -> calls.incrementAndGet());

        dispatcher.dispatch(new TestEvent("source"));

        assertEquals(1, calls.get());
    }

    private static final class TestEvent extends JXEvent {
        private TestEvent(String source) {
            super(source);
        }
    }
}
