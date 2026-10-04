package com.power.fx.core.decimal;

import java.math.BigDecimal;

/**
 * Constants for the deterministic decimal transcendentals (D-09, Appendix D).
 *
 * <p>{@link #LN10} is a 100-significant-digit literal (comfortably over the
 * Appendix D.5 "70-significant-digit" minimum), computed offline via
 * Python's {@code decimal.Decimal.ln()} (a correctly-rounded, arbitrary
 * precision, non-floating-point implementation) at 100 digits of working
 * precision. This is the provisional TI-06 substitute for an
 * MPFR/mpmath-cross-checked value: it is a single, well-known mathematical
 * constant (not a 2,000-point reference table), so the risk of a
 * transcription error is low, but the authoritative cross-check TI-06
 * describes has not been performed by a second independent arbitrary
 * precision library in this environment. Flagged explicitly, not silently
 * assumed authoritative.
 *
 * @see "Tech spec Appendix D.1, D.5; D-09"
 */
public final class DecimalConstants {

    private DecimalConstants() {
    }

    /**
     * ln(10) to 100 significant digits.
     *
     * <pre>
     * 2.302585092994045684017991454684364207601101488628772976033327900967572609677352480235997205089598298
     * </pre>
     */
    public static final BigDecimal LN10 = new BigDecimal(
            "2.302585092994045684017991454684364207601101488628772976033327900967572609677352480235997205089598298");

    /** 2 as a {@link BigDecimal}, used in {@code DecimalLn}'s {@code lnY = 2 * s} recombination. */
    public static final BigDecimal TWO = BigDecimal.valueOf(2);

    /** Reduction-loop threshold: {@code |y - 1| <= 1e-3} (Appendix D.2 step 3). */
    public static final BigDecimal LN_REDUCTION_THRESHOLD = new BigDecimal("0.001");

    /** Maximum square-root reduction rounds (Appendix D.2 step 3: {@code r < 16}). */
    public static final int LN_MAX_REDUCTION_ROUNDS = 16;

    /** {@code exp} argument-halving count (Appendix D.3 step 3: {@code h = 11}). */
    public static final int EXP_HALVINGS = 11;

    /** {@code 2^11}, the halving divisor for {@code exp} (Appendix D.3 step 3). */
    public static final BigDecimal EXP_HALVING_DIVISOR = BigDecimal.valueOf(1L << EXP_HALVINGS);

    /**
     * Hard domain guard for {@code exp}: {@code |x| > 700} is an engine
     * invariant violation, not a business error (Appendix D.3 "Range").
     */
    public static final BigDecimal EXP_MAX_ARG = BigDecimal.valueOf(700);
}
