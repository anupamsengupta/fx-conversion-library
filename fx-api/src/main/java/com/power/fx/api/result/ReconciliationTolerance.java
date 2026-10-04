package com.power.fx.api.result;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Computes the reconciliation tolerance formula of FS S15 so every
 * consumer uses the library's own definition rather than reinventing it:
 * {@code max(0.5 * 10^-d * lines, 1e-9 * |amount|)}.
 *
 * <p>This is a stateless utility, not a value object -- {@link
 * AllocationResidual#tolerance()} is typed {@link BigDecimal} directly,
 * so there is no separate carrier type to instantiate. D-09: computed
 * entirely with exact {@link BigDecimal}/{@link BigInteger} arithmetic,
 * never {@code double}/{@code float}/{@code Math}.
 *
 * @see "Tech spec S6.12"
 */
public final class ReconciliationTolerance {

    private ReconciliationTolerance() {
    }

    /**
     * @param scale  the currency decimal scale {@code d}
     * @param lines  the number of allocated lines
     * @param amount the total amount being allocated
     * @return {@code max(0.5 * 10^-d * lines, 1e-9 * |amount|)}
     */
    public static BigDecimal of(int scale, int lines, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
        if (lines < 0) {
            throw new IllegalArgumentException("lines must not be negative");
        }

        // 0.5 * 10^-scale == 5 * 10^-(scale + 1), exact.
        BigDecimal halfUlp = new BigDecimal(BigInteger.valueOf(5), scale + 1);
        BigDecimal perLineTerm = halfUlp.multiply(BigDecimal.valueOf(lines));

        // 1e-9 == 1 * 10^-9, exact.
        BigDecimal relativeTerm = new BigDecimal(BigInteger.ONE, 9).multiply(amount.abs());

        return perLineTerm.max(relativeTerm);
    }
}
