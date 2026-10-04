package com.power.fx.api.model;

/**
 * A right a tenant may hold over a market data source, checked by
 * {@code EntitlementResolver} (S6.6, D-10).
 *
 * @see "Tech spec S4.1"
 */
public enum SourceRight {
    VALUATION,
    DISPLAY,
    REDISTRIBUTION
}
