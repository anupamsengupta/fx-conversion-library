package com.power.fx.api.model;

/**
 * Confidence classification of a resolved rate. Declaration order is
 * significant: it is the finality roll-up order from strongest to
 * weakest, {@code CONFIRMED < ESTIMATED < UNRESOLVED}.
 *
 * <p>Carries behaviour (Pattern #3): every combinator in the engine
 * (inverse, cross, forward, average, leg chain) rolls up the finality of
 * its inputs via {@link #weakest(RateFinality...)} so this rule is
 * defined in exactly one place (S4.1).
 *
 * @see "Tech spec S4.1"
 */
public enum RateFinality {
    CONFIRMED,
    ESTIMATED,
    UNRESOLVED;

    /**
     * Returns whichever of {@code this} and {@code other} is weaker
     * (i.e. has the higher ordinal). Associative, commutative and
     * idempotent.
     */
    public RateFinality weaker(RateFinality other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return this.ordinal() >= other.ordinal() ? this : other;
    }

    /**
     * Rolls up the weakest (lowest-confidence) finality across one or
     * more inputs. Used by every combinator that merges finality from
     * multiple rate resolutions into one.
     *
     * @throws IllegalArgumentException if {@code values} is null, empty,
     *                                  or contains a null element
     */
    public static RateFinality weakest(RateFinality... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("at least one RateFinality value is required");
        }
        RateFinality result = values[0];
        if (result == null) {
            throw new IllegalArgumentException("values must not contain null");
        }
        for (int i = 1; i < values.length; i++) {
            result = result.weaker(values[i]);
        }
        return result;
    }
}
