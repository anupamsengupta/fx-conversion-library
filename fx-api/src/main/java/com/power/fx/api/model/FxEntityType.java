package com.power.fx.api.model;

/**
 * Discriminant for the entity carried by an {@code FxIngestRecord} /
 * {@code CdmFxEvent}. The thirteen values cover all reference, fixing and
 * snapshot entities the library ingests.
 *
 * @see "Tech spec S4.1"
 */
public enum FxEntityType {
    CURRENCY,
    PAIR_CONVENTION,
    FIXED_FACTOR,
    FIXING_SOURCE,
    PUBLICATION_CALENDAR,
    SETTLEMENT_CALENDAR,
    ACCOUNTING_UNIT,
    FX_POLICY,
    ACCOUNTING_FX_POLICY,
    SOURCE_ENTITLEMENT,
    MANUAL_RATE_OVERRIDE,
    FIXING,
    MARKET_SNAPSHOT
}
