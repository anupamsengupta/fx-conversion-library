package com.power.fx.api.model;

/**
 * The kind of rate that resolved a conversion, carried in every
 * {@code PathStep} and result for lineage purposes.
 *
 * @see "Tech spec S4.1"
 */
public enum RateType {
    FIXING,
    SPOT,
    FORWARD,
    FIXED_FACTOR,
    CONTRACT_RATE,
    MANUAL_OVERRIDE,
    AVERAGE
}
