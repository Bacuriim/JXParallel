package com.jxparallel.properties;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JXObservableListTest {
    @Test
    void clearShouldNotRaceWithConcurrentRemovals() throws Exception {
        JXObservableList<Integer> list = new JXObservableList<Integer>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 200; round++) {
                for (int i = 0; i < 100; i++) {
                    list.add(i);
                }
                // Before the fix, clear() read size() and then removed that index, so a concurrent
                // remove in between made it throw IndexOutOfBoundsException.
                Future<?> clearing = pool.submit(list::clear);
                Future<?> removing = pool.submit(() -> {
                    try {
                        list.remove(0);
                    } catch (IndexOutOfBoundsException alreadyCleared) {
                        // expected when clear() ran first
                    }
                });
                clearing.get(2, TimeUnit.SECONDS);
                removing.get(2, TimeUnit.SECONDS);
                assertEquals(0, list.size());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void addAllShouldReportOneAddPerElementInOrder() {
        JXObservableList<String> list = new JXObservableList<String>();
        list.add("a");
        StringBuilder events = new StringBuilder();
        list.addListener(event -> events.append(event.getIndex()).append(event.getNewValue()).append(' '));

        assertTrue(list.addAll(Arrays.asList("b", "c")));

        assertEquals(Arrays.asList("a", "b", "c"), list.snapshot());
        assertEquals("1b 2c ", events.toString());
    }
}
