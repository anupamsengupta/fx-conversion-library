package com.power.fx.testkit.fixtures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

/**
 * Decimal-equality assertions via {@link BigDecimal#compareTo}, never
 * {@code equals} (A-07/AR-08: {@code 1.50} and {@code 1.5} must compare
 * equal, which {@code BigDecimal.equals} would reject because it is also
 * scale-sensitive). Deliberately JDK-only, no dependency on a particular
 * test framework's assertion mechanism beyond throwing {@link
 * AssertionError}, so it can be used from JUnit 5 and jqwik property
 * bodies alike (Task 3b.3).
 *
 * @see "Tech spec S10.6, A-07"
 */
public final class FxAssertions {

    private FxAssertions() {
    }

    public static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertDecimalEquals(expected, actual, () -> "");
    }

    public static void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String message) {
        assertDecimalEquals(expected, actual, () -> message);
    }

    public static void assertDecimalEquals(BigDecimal expected, BigDecimal actual, Supplier<String> message) {
        if (expected == null || actual == null) {
            if (expected != actual) {
                throw new AssertionError("expected " + expected + " but was " + actual + ": " + message.get());
            }
            return;
        }
        if (expected.compareTo(actual) != 0) {
            throw new AssertionError("expected " + expected + " but was " + actual
                    + " (compareTo, not equals; scale-insensitive): " + message.get());
        }
    }

    public static void assertDecimalEquals(String expected, BigDecimal actual) {
        assertDecimalEquals(new BigDecimal(expected), actual);
    }

    public static void assertDecimalEquals(String expected, BigDecimal actual, String message) {
        assertDecimalEquals(new BigDecimal(expected), actual, message);
    }

    /** Rounds both {@code expected} and {@code actual} to {@code scale} (HALF_EVEN) before comparing. */
    public static void assertDecimalEqualsAtScale(String expected, BigDecimal actual, int scale) {
        BigDecimal roundedExpected = new BigDecimal(expected).setScale(scale, RoundingMode.HALF_EVEN);
        BigDecimal roundedActual = actual.setScale(scale, RoundingMode.HALF_EVEN);
        assertDecimalEquals(roundedExpected, roundedActual);
    }

    /** Relative-error assertion for transcendental conformance (Appendix D.5). */
    public static void assertRelativeErrorWithin(BigDecimal expected, BigDecimal actual, BigDecimal tolerance,
            java.math.MathContext mc, String label) {
        if (expected.signum() == 0) {
            if (actual.abs(mc).compareTo(tolerance) >= 0) {
                throw new AssertionError(label + ": expected 0, got " + actual);
            }
            return;
        }
        BigDecimal relError = actual.subtract(expected, mc).abs(mc).divide(expected.abs(mc), mc);
        if (relError.compareTo(tolerance) >= 0) {
            throw new AssertionError(label + ": relative error " + relError + " exceeds " + tolerance
                    + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
