package com.power.fx.core.pair;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;

/**
 * Parses a {@code ContractRate.quotedIn} string ("GBp per USD", "EUR/USD")
 * and determines whether its direction matches a given pair's
 * {@code base -> quote} convention, or is the inverse (S6.7 step 4).
 */
public final class QuotedInParser {

    private QuotedInParser() {
    }

    public record Direction(CurrencyCode numerator, CurrencyCode denominator) {
    }

    public static Direction parse(String quotedIn) {
        String s = quotedIn.trim();
        String[] parts;
        if (s.contains(" per ")) {
            parts = s.split(" per ", 2);
        } else if (s.contains("/")) {
            parts = s.split("/", 2);
        } else {
            throw new IllegalArgumentException("unrecognised quotedIn format: " + quotedIn);
        }
        return new Direction(new CurrencyCode(parts[0].trim()), new CurrencyCode(parts[1].trim()));
    }

    /** True if {@code quotedIn} reads "quote per base" for {@code pair} (rate usable as-is). */
    public static boolean matchesPairDirection(String quotedIn, CurrencyPair pair) {
        Direction d = parse(quotedIn);
        return d.numerator().equals(pair.quote()) && d.denominator().equals(pair.base());
    }
}
