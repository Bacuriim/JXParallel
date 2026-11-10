package com.jxparallel.examples.orders;

import java.time.LocalDate;
import java.util.function.Supplier;

/** What the controllers of one window share: the host, the orders and the theme. One per window; nothing is static. */
public final class Session {
    private final Host host;
    private final OrderStore store = new OrderStore();
    private final Supplier<LocalDate> clock;
    private Palette palette = Palette.of(Palette.LIGHT);

    public Session(Host host, Supplier<LocalDate> clock) {
        this.host = host;
        this.clock = clock;
    }

    public Host host() {
        return host;
    }

    public OrderStore store() {
        return store;
    }

    public LocalDate today() {
        return clock.get();
    }

    public Palette palette() {
        return palette;
    }

    public void setTheme(String name) {
        palette = Palette.of(name);
    }
}
