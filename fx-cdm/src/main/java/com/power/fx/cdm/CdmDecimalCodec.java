package com.power.fx.cdm;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parses decimal values out of a CDM payload's string representation.
 *
 * <p>Unlike the rest of {@code fx-cdm} (Task 3a.1-3a.4 mapping logic),
 * this class is <strong>not</strong> blocked by TI-01: whatever the real
 * CDM schema eventually mandates for numeric wire encoding, it will be a
 * decimal string (not a binary double), and this is a pure parsing
 * utility with no dependency on field names or schema shape. It is fully
 * implemented and tested now.
 *
 * <p>D-09: decimals are parsed with {@link BigDecimal#BigDecimal(String)}
 * only. {@link BigDecimal#valueOf(double)} is never used anywhere in this
 * class, because it would round-trip the value through an IEEE-754
 * {@code double} first and silently lose or distort precision before the
 * value ever reaches {@code fx-core}.
 *
 * @see "Tech spec S6.17 (decimal-parsing rule)"
 */
public final class CdmDecimalCodec {

    private CdmDecimalCodec() {
    }

    /**
     * Parses {@code raw} as an exact decimal.
     *
     * @throws NullPointerException     if {@code raw} is {@code null}.
     * @throws IllegalArgumentException if {@code raw} is blank or is not
     *                                   a syntactically valid decimal
     *                                   literal.
     */
    public static BigDecimal parse(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("decimal value must not be blank");
        }
        try {
            // D-09: new BigDecimal(String) only, never BigDecimal.valueOf(double).
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed decimal literal: " + raw, e);
        }
    }
}
