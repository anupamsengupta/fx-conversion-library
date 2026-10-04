package com.power.fx.api.result;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FS S15 tolerance formula: {@code max(0.5 * 10^-d * lines, 1e-9 * |amount|)}
 * (S6.12).
 */
class ReconciliationToleranceTest {

    @Test
    void perLineTermWins_whenLineCountDominatesOverSmallAmount() {
        // 0.5 * 10^-2 * 10 = 0.05 ; 1e-9 * 1 = 0.000000001 -> 0.05 wins
        BigDecimal tolerance = ReconciliationTolerance.of(2, 10, new BigDecimal("1"));
        assertEquals(0, tolerance.compareTo(new BigDecimal("0.05")), () -> "got " + tolerance);
    }

    @Test
    void relativeTermWins_whenAmountIsLarge() {
        // 0.5 * 10^-0 * 1 = 0.5 ; 1e-9 * 1_000_000_000_000 = 1000 -> 1000 wins
        BigDecimal tolerance = ReconciliationTolerance.of(0, 1, new BigDecimal("1000000000000"));
        assertEquals(0, tolerance.compareTo(new BigDecimal("1000")), () -> "got " + tolerance);
    }

    @Test
    void usesAbsoluteValueOfAmount() {
        BigDecimal positive = ReconciliationTolerance.of(0, 1, new BigDecimal("1000000000000"));
        BigDecimal negative = ReconciliationTolerance.of(0, 1, new BigDecimal("-1000000000000"));
        assertEquals(0, positive.compareTo(negative));
    }

    @Test
    void zeroLinesAndZeroAmount_yieldsZeroTolerance() {
        BigDecimal tolerance = ReconciliationTolerance.of(2, 0, BigDecimal.ZERO);
        assertEquals(0, tolerance.compareTo(BigDecimal.ZERO), () -> "got " + tolerance);
    }

    @Test
    void rejectsNegativeScale() {
        assertThrows(IllegalArgumentException.class,
                () -> ReconciliationTolerance.of(-1, 1, BigDecimal.ONE));
    }

    @Test
    void rejectsNegativeLineCount() {
        assertThrows(IllegalArgumentException.class,
                () -> ReconciliationTolerance.of(2, -1, BigDecimal.ONE));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class,
                () -> ReconciliationTolerance.of(2, 1, null));
    }
}
