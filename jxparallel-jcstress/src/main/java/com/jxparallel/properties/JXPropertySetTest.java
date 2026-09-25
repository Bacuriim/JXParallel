package com.jxparallel.properties;

import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.LLI_Result;

/** Two threads set the property. Results: final value, last value a listener saw, change events. */
public class JXPropertySetTest {

    @JCStressTest
    @Outcome(id = {"B, B, 2", "C, C, 2"}, expect = Expect.ACCEPTABLE, desc = "two changes, listener saw the final value last")
    @Outcome(expect = Expect.FORBIDDEN, desc = "listener saw another value last, or a change was not reported")
    @State
    public static class DifferentValues {
        private final JXProperty<String> property = new JXProperty<String>("A");
        private final AtomicInteger events = new AtomicInteger();
        private volatile Object lastSeen;

        public DifferentValues() {
            property.addListener(event -> {
                lastSeen = event.getNewValue();
                events.incrementAndGet();
            });
        }

        @Actor
        public void setB() {
            property.set("B");
        }

        @Actor
        public void setC() {
            property.set("C");
        }

        @Arbiter
        public void check(LLI_Result r) {
            r.r1 = property.get();
            r.r2 = lastSeen;
            r.r3 = events.get();
        }
    }

    /** Both threads set "B": that is one change, so one event. */
    @JCStressTest
    @Outcome(id = "B, B, 1", expect = Expect.ACCEPTABLE, desc = "one change, one event")
    @Outcome(expect = Expect.FORBIDDEN, desc = "the same change reported twice")
    @State
    public static class SameValue {
        private final JXProperty<String> property = new JXProperty<String>("A");
        private final AtomicInteger events = new AtomicInteger();
        private volatile Object lastSeen;

        public SameValue() {
            property.addListener(event -> {
                lastSeen = event.getNewValue();
                events.incrementAndGet();
            });
        }

        @Actor
        public void first() {
            property.set("B");
        }

        @Actor
        public void second() {
            property.set("B");
        }

        @Arbiter
        public void check(LLI_Result r) {
            r.r1 = property.get();
            r.r2 = lastSeen;
            r.r3 = events.get();
        }
    }
}
