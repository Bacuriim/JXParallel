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
    /** {@code #method}: a public no-argument controller method, as a {@link Runnable}. */
    HANDLER,
    /** A bound {@code JXElement} or {@code JXComponent} built in Java: how JXML hosts hand-written widgets. */
    ELEMENT
}
