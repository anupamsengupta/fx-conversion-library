package com.power.fx.core.cache;

import com.power.fx.api.model.CurrencyPair;

import java.util.Objects;

/**
 * Identity of one bitemporal fixing series: source, pair and cutoff class
 * (S7.1.3).
 */
public record SeriesKey(String sourceCode, CurrencyPair pair, String cutoff) {

    public SeriesKey {
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(cutoff, "cutoff must not be null");
    }
}
