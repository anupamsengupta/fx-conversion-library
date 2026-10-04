package com.power.fx.core.decimal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Deterministic exponential, {@code BigDecimal}/{@code BigInteger} only --
 * no {@code double}, no {@code Math}, no {@code StrictMath} (D-09).
 *
 * <p>Algorithm (Appendix D.3): reduce {@code x} modulo {@code LN10} to an
 * integer power-of-ten shift plus a small remainder; halve the remainder
 * {@code 11} times; evaluate the Taylor series on the halved remainder;
 * square the result {@code 11} times to undo the halving; apply the
 * power-of-ten shift exactly via {@link BigDecimal#scaleByPowerOfTen(int)}.
 *
 * @see "Tech spec Appendix D.3"
 */
public final class DecimalExp {

    private DecimalExp() {
    }

    /**
     * Computes {@code exp(x)} at the precision and rounding of {@code mc}.
     * See {@link DecimalLn#ln} for the dual working/publish-precision
     * calling convention.
     *
     * @throws IllegalArgumentException if {@code |x| > 700} (Appendix D.3
     *         "Range": an engine invariant violation, not a business error)
     */
    public static BigDecimal exp(BigDecimal x, MathContext mc) {
        if (x.abs().compareTo(DecimalConstants.EXP_MAX_ARG) > 0) {
            throw new IllegalArgumentException("exp argument out of range (|x| > 700): " + x);
        }
        if (x.signum() == 0) {
            return BigDecimal.ONE;
        }

        // Step 2: reduce by LN10. n = round(x / LN10); rem = x - n*LN10.
        BigDecimal nDecimal = x.divide(DecimalConstants.LN10, mc).setScale(0, RoundingMode.HALF_EVEN);
        int n = nDecimal.intValueExact();
        BigDecimal rem = x.subtract(nDecimal.multiply(DecimalConstants.LN10, mc), mc);

        // Step 3: halve.
        BigDecimal r = rem.divide(DecimalConstants.EXP_HALVING_DIVISOR, mc);

        // Step 4: Taylor series for exp(r).
        BigDecimal t = BigDecimal.ONE;
        BigDecimal s = BigDecimal.ONE;
        long k = 1;
        BigDecimal truncationThreshold = BigDecimal.ONE.movePointLeft(mc.getPrecision() + 4);
        int guard = 0;
        while (guard < 10_000) {
            t = t.multiply(r, mc).divide(BigDecimal.valueOf(k), mc);
            s = s.add(t, mc);
            if (t.abs(mc).compareTo(truncationThreshold) < 0) {
                break;
            }
            k++;
            guard++;
        }

        // Step 5: undo the halving by repeated squaring.
        for (int i = 0; i < DecimalConstants.EXP_HALVINGS; i++) {
            s = s.multiply(s, mc);
        }

        // Step 6: apply the power-of-ten shift, exact.
        BigDecimal result = s.scaleByPowerOfTen(n);

        // Step 7: round to the requested context.
        return result.round(mc);
    }
}
