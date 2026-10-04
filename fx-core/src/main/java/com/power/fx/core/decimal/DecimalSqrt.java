package com.power.fx.core.decimal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * Deterministic, JDK-only square root via {@link BigInteger#sqrt()} --
 * never {@link Math#sqrt(double)}, never {@code BigDecimal.sqrt}, which
 * (per the JDK implementation) seeds its Newton iteration from
 * {@code Math.sqrt(double)} and would inject a floating-point dependency
 * into the {@code ln}/{@code exp} accuracy argument (D-09, Appendix D.4).
 *
 * @see "Tech spec Appendix D.4"
 */
public final class DecimalSqrt {

    private DecimalSqrt() {
    }

    /**
     * Computes {@code sqrt(a)} rounded to {@code mc}.
     *
     * <p>Algorithm (Appendix D.4): shift {@code a}'s unscaled value left so
     * the target has at least {@code 2 * mc.getPrecision()} digits and the
     * resulting scale is even; take the exact integer square root of the
     * scaled unscaled value via {@link BigInteger#sqrt()}; rebuild the
     * {@link BigDecimal} at half the shifted scale and round to {@code mc}.
     *
     * @throws ArithmeticException if {@code a} is negative
     */
    public static BigDecimal sqrt(BigDecimal a, MathContext mc) {
        if (a.signum() < 0) {
            throw new ArithmeticException("sqrt of a negative value: " + a);
        }
        if (a.signum() == 0) {
            return BigDecimal.ZERO;
        }

        // Guard digits beyond the target precision so the final round() is safe.
        int targetDigits = mc.getPrecision() + 10;

        BigInteger unscaled = a.unscaledValue().abs();
        int scale = a.scale();
        int currentDigits = unscaled.toString().length();

        int shift = 2 * targetDigits - currentDigits;
        if (shift < 0) {
            shift = 0;
        }
        // (scale + shift) must be even so that (scale + shift) / 2 is an exact integer scale.
        // Bitwise AND (not Math.floorMod -- AR-05 forbids java.lang.Math entirely) correctly
        // tests the low bit of a two's-complement int regardless of sign.
        if (((scale + shift) & 1) != 0) {
            shift++;
        }

        BigInteger shiftedUnscaled = unscaled.multiply(BigInteger.TEN.pow(shift));
        int shiftedScale = scale + shift;

        BigInteger root = shiftedUnscaled.sqrt();
        BigDecimal result = new BigDecimal(root, shiftedScale / 2);
        return result.round(mc);
    }
}
