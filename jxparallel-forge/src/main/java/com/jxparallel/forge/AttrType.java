package com.jxparallel.forge;

/** What an attribute holds once converted, and so which Java type its prop gets. */
public enum AttrType {
    /** {@link String}. */
    STRING,
    /** {@link Double}: sizes, gaps, values. */
    DOUBLE,
    /** {@link Integer}: grid cells, indexes. */
    INT,
    /** {@link Boolean}. */
    BOOLEAN,
    /** {@link Double} for all sides, or {@code double[4]} top, right, bottom, left. */
    INSETS,
    /** {@code String[]} from a comma-separated list. */
    STRINGS,
    /** {@code double[]} from a comma-separated list of numbers: table column widths. */
    DOUBLES,
    /** {@code #method}: a public no-argument controller method, as a {@link Runnable}. */
    HANDLER,
    /** A bound {@code JXElement} or {@code JXComponent} built in Java: how JXML hosts hand-written widgets. */
    ELEMENT,
    /** The object a binding returns, as it is: the items of a {@code <For>} and the keys of its elements. */
    OBJECT,
    /** {@code #method}: a controller method that takes the new value, as a {@link java.util.function.Consumer}. */
    CHANGE,
    /** An ARGB {@link Integer}, written {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}. */
    COLOR,
    /** An {@link Integer}: how many items a bound {@link Iterable} or array holds. Bindings only. */
    COUNT,
    /** An {@code int[]} of ARGB pixels, handed on as it is (the renderer compares it by identity). Bindings only. */
    PIXELS
}
