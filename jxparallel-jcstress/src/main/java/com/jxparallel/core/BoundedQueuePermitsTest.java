package com.jxparallel.core;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.IIII_Result;

/**
 * Capacity 1: whatever order offer, offer and poll run in, queued items plus free permits must
 * still be 1. A leaked permit (the 2026-09-24 poll(timeout) bug) shows up as 0 or 2.
 */
@JCStressTest
@Outcome(id = ".*, 1", expect = Expect.ACCEPTABLE, desc = "queued + free permits == capacity")
@Outcome(expect = Expect.FORBIDDEN, desc = "permit leaked or lost")
@State
public class BoundedQueuePermitsTest {
    private final AdaptiveWorkerPool.BoundedPriorityBlockingQueue queue =
            new AdaptiveWorkerPool.BoundedPriorityBlockingQueue(1, (a, b) -> 0);

    @Actor
    public void offerA(IIII_Result r) {
        r.r1 = queue.offer(() -> { }) ? 1 : 0;
    }

    @Actor
    public void offerB(IIII_Result r) {
        r.r2 = queue.offer(() -> { }) ? 1 : 0;
    }

    @Actor
    public void poll(IIII_Result r) {
        r.r3 = queue.poll() != null ? 1 : 0;
    }

    @Arbiter
    public void check(IIII_Result r) {
        r.r4 = queue.size() + (queue.offer(() -> { }) ? 1 : 0);
    }
}
