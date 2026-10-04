package com.power.fx.core.decimal;

import com.power.fx.api.model.Rational;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Exact {@link BigInteger} gcd/lcm helpers for common-denominator weighted
 * arithmetic (FS S10.2 "used exactly"; Task 2.12's weighted-average
 * binding order).
 *
 * @see "Tech spec S6.11"
 */
public final class RationalMath {

    private RationalMath() {
    }

    public static BigInteger gcd(BigInteger a, BigInteger b) {
        return a.gcd(b);
    }

    public static BigInteger lcm(BigInteger a, BigInteger b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");
        if (a.signum() == 0 || b.signum() == 0) {
            return BigInteger.ZERO;
        }
        BigInteger gcd = a.gcd(b);
        return a.divide(gcd).multiply(b).abs();
    }

    /**
     * {@code weights} brought to one common denominator {@code q} (exact
     * {@link BigInteger} lcm), with the corresponding integer numerators
     * {@code p_i} such that {@code weights.get(i) == p_i / q} exactly.
     */
    public record CommonDenominator(BigInteger denominator, List<BigInteger> numerators) {
    }

    public static CommonDenominator commonDenominator(List<Rational> weights) {
        Objects.requireNonNull(weights, "weights must not be null");
        if (weights.isEmpty()) {
            return new CommonDenominator(BigInteger.ONE, List.of());
        }
        BigInteger q = BigInteger.ONE;
        for (Rational w : weights) {
            q = lcm(q, w.den());
        }
        List<BigInteger> numerators = new ArrayList<>(weights.size());
        for (Rational w : weights) {
            numerators.add(w.num().multiply(q.divide(w.den())));
        }
        return new CommonDenominator(q, numerators);
    }
}
