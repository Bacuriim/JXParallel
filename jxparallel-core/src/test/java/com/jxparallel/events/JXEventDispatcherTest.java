package com.jxparallel.events;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JXEventDispatcherTest {
    @Test
    void shouldDispatchTypedEvents() {
        JXEventDispatcher dispatcher = new JXEventDispatcher();
        final AtomicInteger count = new AtomicInteger();
        JXEventType<TestEvent> type = new JXEventType<TestEvent>("test");

        dispatcher.addListener(type, new JXEventHandler<TestEvent>() {
            @Override
            public void handle(TestEvent event) {
                count.incrementAndGet();
            }
        });

        dispatcher.dispatch(type, new TestEvent("source"));

        assertEquals(1, count.get());
    }

    private static final class TestEvent extends JXEvent {
        private TestEvent(String source) {
            super(source);
        }
    }
}
