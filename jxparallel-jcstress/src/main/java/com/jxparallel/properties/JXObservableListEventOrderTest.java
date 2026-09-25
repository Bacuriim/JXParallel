package com.jxparallel.properties;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.L_Result;

/**
 * Two threads add to the list. A listener that mirrors the list (what a list view does) needs
 * the ADD events in index order.
 */
@JCStressTest
@Outcome(id = "0 1 ", expect = Expect.ACCEPTABLE, desc = "events in index order")
@Outcome(id = "1 0 ", expect = Expect.FORBIDDEN, desc = "index 1 reported before index 0")
@State
public class JXObservableListEventOrderTest {
    private final JXObservableList<String> list = new JXObservableList<String>();
    private final StringBuffer indexes = new StringBuffer();

    public JXObservableListEventOrderTest() {
        list.addListener(event -> indexes.append(event.getIndex() + " "));
    }

    @Actor
    public void addA() {
        list.add("a");
    }

    @Actor
    public void addB() {
        list.add("b");
    }

    @Arbiter
    public void check(L_Result r) {
        r.r1 = indexes.toString();
    }
}
