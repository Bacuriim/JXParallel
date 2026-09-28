package com.jxparallel.ui;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/** Two threads set the state. The last value a subscriber saw must be the state's value. */
@JCStressTest
@Outcome(id = {"1, 1", "2, 2"}, expect = Expect.ACCEPTABLE, desc = "subscriber saw the final value last")
@Outcome(id = {"1, 2", "2, 1"}, expect = Expect.FORBIDDEN, desc = "notifications arrived out of order")
@State
public class JXStateNotificationOrderTest {
    private final JXState<Integer> state = new JXState<Integer>(0);
    private volatile int lastSeen;

    public JXStateNotificationOrderTest() {
        state.subscribe(value -> lastSeen = value);
    }

    @Actor
    public void setOne() {
        state.set(1);
    }

    @Actor
    public void setTwo() {
        state.set(2);
    }

    @Arbiter
    public void check(II_Result r) {
        r.r1 = state.get();
        r.r2 = lastSeen;
    }
}
