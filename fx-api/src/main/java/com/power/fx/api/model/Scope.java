package com.power.fx.api.model;

/**
 * Whether a reference-data or market-data record applies to all tenants
 * ({@code GLOBAL}) or to one specific tenant ({@code TENANT}), the
 * overlay model of S6/S7.1.
 *
 * @see "Tech spec S4.1"
 */
public enum Scope {
    GLOBAL,
    TENANT
}
