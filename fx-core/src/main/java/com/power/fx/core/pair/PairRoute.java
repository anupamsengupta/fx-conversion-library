package com.power.fx.core.pair;

import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The result of the nine-step pair resolution chain (FS S11.2, tech spec
 * S6.7): an optional pre-normalisation fixed factor, the core resolution
 * (identity / directly-resolved / market chain), and an optional
 * post-denormalisation fixed factor. Fixed factors occupy only the first
 * and last position of the overall derivation -- never mid-cross -- which
 * this shape enforces structurally (there is nowhere else to put one).
 *
 * @see "Tech spec S6.7"
 */
public record PairRoute(
        CurrencyCode requestedFrom,
        CurrencyCode requestedTo,
        BigDecimal preFactor,
        FxReason preFactorReason,
        CoreResolution core,
        BigDecimal postFactor,
        FxReason postFactorReason) {

    public PairRoute {
        Objects.requireNonNull(requestedFrom, "requestedFrom must not be null");
        Objects.requireNonNull(requestedTo, "requestedTo must not be null");
        Objects.requireNonNull(core, "core must not be null");
    }
}
