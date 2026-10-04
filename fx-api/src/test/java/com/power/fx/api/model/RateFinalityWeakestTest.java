package com.power.fx.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 1.2 acceptance: {@link RateFinality#weakest(RateFinality...)} is
 * associative, commutative and idempotent over all 3-element
 * combinations of {@code {CONFIRMED, ESTIMATED, UNRESOLVED}}, and the
 * finality ordering is {@code UNRESOLVED > ESTIMATED > CONFIRMED}.
 *
 * <p>This is the "finality monotonicity" property of S12.2 pulled
 * forward into Phase 1 because every downstream combinator depends on it
 * being correct from day one; the full {@code jqwik}-based property test
 * is re-asserted in Phase 3b Task 3b.6.
 */
class RateFinalityWeakestTest {

    private static final RateFinality[] ALL = RateFinality.values();

    @Test
    void ordering_unresolvedIsWeakestThanEstimatedIsWeakestThanConfirmed() {
        assertTrue(RateFinality.UNRESOLVED.ordinal() > RateFinality.ESTIMATED.ordinal());
        assertTrue(RateFinality.ESTIMATED.ordinal() > RateFinality.CONFIRMED.ordinal());
        assertEquals(RateFinality.UNRESOLVED, RateFinality.weakest(RateFinality.CONFIRMED, RateFinality.UNRESOLVED));
        assertEquals(RateFinality.ESTIMATED, RateFinality.weakest(RateFinality.CONFIRMED, RateFinality.ESTIMATED));
        assertEquals(RateFinality.CONFIRMED, RateFinality.weakest(RateFinality.CONFIRMED, RateFinality.CONFIRMED));
    }

    @Test
    void weakest_isCommutativeOverAllThreeElementCombinations() {
        for (RateFinality a : ALL) {
            for (RateFinality b : ALL) {
                for (RateFinality c : ALL) {
                    RateFinality forward = RateFinality.weakest(a, b, c);
                    RateFinality permuted = RateFinality.weakest(c, a, b);
                    assertEquals(forward, permuted,
                            () -> "commutativity failed for " + a + "," + b + "," + c);
                }
            }
        }
    }

    @Test
    void weakest_isAssociativeOverAllThreeElementCombinations() {
        for (RateFinality a : ALL) {
            for (RateFinality b : ALL) {
                for (RateFinality c : ALL) {
                    RateFinality leftAssoc = RateFinality.weakest(a, b).weaker(c);
                    RateFinality rightAssoc = a.weaker(RateFinality.weakest(b, c));
                    RateFinality direct = RateFinality.weakest(a, b, c);
                    assertEquals(direct, leftAssoc,
                            () -> "left-associativity failed for " + a + "," + b + "," + c);
                    assertEquals(direct, rightAssoc,
                            () -> "right-associativity failed for " + a + "," + b + "," + c);
                }
            }
        }
    }

    @Test
    void weakest_isIdempotentOverAllThreeElementCombinations() {
        for (RateFinality a : ALL) {
            for (RateFinality b : ALL) {
                for (RateFinality c : ALL) {
                    RateFinality once = RateFinality.weakest(a, b, c);
                    RateFinality twice = RateFinality.weakest(once, once, once);
                    assertEquals(once, twice);
                }
            }
        }
    }

    @Test
    void weakest_rejectsEmptyVarargs() {
        assertThrows(IllegalArgumentException.class, RateFinality::weakest);
    }
}
