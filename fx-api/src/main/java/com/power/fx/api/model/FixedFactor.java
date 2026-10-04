package com.power.fx.api.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A fixed conversion factor between two currencies: a minor-unit
 * relationship or a legal peg. Consumed by {@code FixedFactorNormaliser}
 * at the first and last step of pair resolution only (FS S11.2).
 *
 * @see "Tech spec S4.4"
 */
public record FixedFactor(
        VersionEnvelope envelope,
        CurrencyCode from,
        CurrencyCode to,
        BigDecimal factor,
        FixedFactorKind kind,
        boolean preferOverMarket) {

    public FixedFactor {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(factor, "factor must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
