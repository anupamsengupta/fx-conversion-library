package com.power.fx.api.model;

import java.util.Objects;

/**
 * A currency reference record: minor-unit decimals, the major currency it
 * is a minor unit of (null iff this record IS the major), and whether it
 * is deliverable.
 *
 * @see "Tech spec S4.4"
 */
public record Currency(
        VersionEnvelope envelope,
        CurrencyCode code,
        int decimals,
        CurrencyCode majorCurrency,
        String settlementCalendarRef,
        boolean deliverable) {

    public Currency {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(code, "code must not be null");
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must not be negative");
        }
    }
}
