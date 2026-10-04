package com.power.fx.core.pair;

import com.power.fx.api.model.CurrencyPair;

/**
 * One market-quoted leg of a {@link PairRoute}: the pair as conventionally
 * quoted, and whether the requested direction is the inverse of that
 * convention (S6.7 steps 6-9).
 */
public record MarketLeg(CurrencyPair quotedPair, boolean inverted) {
}
