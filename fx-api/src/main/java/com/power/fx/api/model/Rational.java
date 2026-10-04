package com.power.fx.api.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/**
 * An exact rational number, used wherever the functional spec requires
 * weights or fractions to be "used exactly" rather than approximated by
 * a decimal division (FS S10.2 PDR weights, FS S7.1 renormalisation on
 * {@code SKIP_OBSERVATION}, FS S10.3 {@code confirmedPortion}).
 *
 * <p>The compact constructor normalises the sign onto the numerator (the
 * denominator is always positive) and reduces the fraction by its
 * greatest common divisor.
 *
 * @see "Tech spec S4.3"
 */
public record Rational(BigInteger num, BigInteger den) implements Comparable<Rational> {

    public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
    public static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);

    public Rational {
        Objects.requireNonNull(num, "num must not be null");
        Objects.requireNonNull(den, "den must not be null");
        if (den.signum() == 0) {
            throw new ArithmeticException("Rational denominator must not be zero");
        }
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        BigInteger gcd = num.gcd(den);
        if (gcd.signum() != 0 && !gcd.equals(BigInteger.ONE)) {
            num = num.divide(gcd);
            den = den.divide(gcd);
        }
    }

    public static Rational of(long n, long d) {
        return new Rational(BigInteger.valueOf(n), BigInteger.valueOf(d));
    }

    public Rational plus(Rational o) {
        Objects.requireNonNull(o, "o must not be null");
        return new Rational(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den));
    }

    public Rational times(Rational o) {
        Objects.requireNonNull(o, "o must not be null");
        return new Rational(num.multiply(o.num), den.multiply(o.den));
    }

    public Rational dividedBy(Rational o) {
        Objects.requireNonNull(o, "o must not be null");
        if (o.num.signum() == 0) {
            throw new ArithmeticException("division by zero Rational");
        }
        return new Rational(num.multiply(o.den), den.multiply(o.num));
    }

    /** Converts to a {@link BigDecimal} at the precision/rounding of {@code mc}. */
    public BigDecimal toDecimal(MathContext mc) {
        Objects.requireNonNull(mc, "mc must not be null");
        return new BigDecimal(num).divide(new BigDecimal(den), mc);
    }

    @Override
    public int compareTo(Rational o) {
        // Cross-multiply; both denominators are positive post-normalisation.
        return num.multiply(o.den).compareTo(o.num.multiply(den));
    }
}
