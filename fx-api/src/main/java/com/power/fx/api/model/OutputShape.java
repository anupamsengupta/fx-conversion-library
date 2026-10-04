package com.power.fx.api.model;

/**
 * Whether an averaging result is a single total or a per-observation
 * series plus total (A-17: {@code SERIES} returns both in one
 * {@code SeriesResult}, no separate call).
 *
 * @see "Tech spec S4.1"
 */
public enum OutputShape {
    TOTAL,
    SERIES
}
