package com.power.fx.core.decimal;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Deterministic natural logarithm, {@code BigDecimal}/{@code BigInteger}
 * only -- no {@code double}, no {@code Math}, no {@code StrictMath}
 * (D-09).
 *
 * <p>Algorithm (Appendix D.2): decompose {@code x = m * 10^k} with
 * {@code m} in {@code [1,10)}; reduce {@code m} toward {@code 1} by
 * repeated exact square roots ({@link DecimalSqrt}); apply the
 * {@code atanh}-series expansion {@code ln(y) = 2 * atanh((y-1)/(y+1))} to
 * the reduced value; recombine {@code ln(x) = k*LN10 + 2^r * ln(y)}.
 *
 * @see "Tech spec Appendix D.2"
 */
public final class DecimalLn {

    private DecimalLn() {
    }

    /**
     * Computes {@code ln(x)} at the precision and rounding of {@code mc}.
     * {@code mc} is used both as the internal working precision for every
     * intermediate operation and as the rounding context of the returned
     * value -- callers that need a higher-precision intermediate (e.g.
     * {@code ForwardCurveBuilder}'s {@code lnCarry} array) pass
     * {@code FxMath.WORKING}; callers that need a published value pass
     * {@code FxMath.DECIMAL128}.
     *
     * @throws IllegalArgumentException if {@code x <= 0}
     */
    public static BigDecimal ln(BigDecimal x, MathContext mc) {
        if (x.signum() <= 0) {
            throw new IllegalArgumentException("ln undefined for x <= 0: " + x);
        }

        // Step 1 (special case, exact): ln(1) == 0.
        if (x.compareTo(BigDecimal.ONE) == 0) {
            return BigDecimal.ZERO;
        }

        // Step 2: decompose x = m * 10^k, m in [1,10), exact (no rounding).
        int k = x.precision() - x.scale() - 1;
        BigDecimal m = x.movePointLeft(k);

        // Step 3: reduce y toward 1 by repeated exact square roots.
        BigDecimal y = m;
        int r = 0;
        while (y.subtract(BigDecimal.ONE, mc).abs(mc).compareTo(DecimalConstants.LN_REDUCTION_THRESHOLD) > 0
                && r < DecimalConstants.LN_MAX_REDUCTION_ROUNDS) {
            y = DecimalSqrt.sqrt(y, mc);
            r++;
        }

        // Step 4: atanh series on z = (y-1)/(y+1), |z| small after reduction.
        BigDecimal z = y.subtract(BigDecimal.ONE, mc).divide(y.add(BigDecimal.ONE, mc), mc);
        BigDecimal zz = z.multiply(z, mc);
        BigDecimal s = z;
        BigDecimal term = z;
        long n = 3;
        BigDecimal truncationThreshold = BigDecimal.ONE.movePointLeft(mc.getPrecision() + 4);
        int guard = 0;
        while (guard < 10_000) {
            term = term.multiply(zz, mc);
            BigDecimal contribution = term.divide(BigDecimal.valueOf(n), mc);
            s = s.add(contribution, mc);
            if (contribution.abs(mc).compareTo(truncationThreshold) < 0) {
                break;
            }
            n += 2;
            guard++;
        }
        BigDecimal lnY = DecimalConstants.TWO.multiply(s, mc);

        // Step 5: recombine. 2^r is exact as a BigDecimal (small integer power of two).
        BigDecimal pow2r = new BigDecimal(1L << r);
        BigDecimal kLn10 = BigDecimal.valueOf(k).multiply(DecimalConstants.LN10, mc);
        BigDecimal result = kLn10.add(pow2r.multiply(lnY, mc), mc);

        // Step 6: round to the requested context (DECIMAL128 for published values).
        return result.round(mc);
    }
}
