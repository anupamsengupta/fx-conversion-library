package com.power.fx.api.model;

/**
 * Discriminates the three in-memory stores an ingest or generation
 * advancement applies to.
 *
 * @see "Tech spec S4.1"
 */
public enum FxStoreKind {
    REFERENCE,
    FIXING,
    SNAPSHOT
}
