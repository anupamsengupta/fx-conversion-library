package com.power.fx.api.result;

import com.power.fx.api.error.FxError;
import com.power.fx.api.error.FxException;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.model.RateFinality;

import java.util.List;
import java.util.Optional;

/**
 * Sealed hierarchy of every public result shape. Business failures are
 * always returned through {@link #error()}, never thrown -- {@link
 * #orThrow()} is the opt-in (FS S14.1). Permits match S4.8 exactly.
 *
 * @see "Tech spec S4.8"
 */
public sealed interface FxResult
        permits RateResult, ConversionResult, SeriesResult, ChainResult, RevaluationResult {

    RateFinality finality();

    List<FxWarning> warnings();

    Optional<FxError> error();

    Lineage lineage();

    default boolean isSuccess() {
        return error().isEmpty();
    }

    /**
     * Returns {@code this}, cast to the caller's expected result type,
     * when no error is present; throws {@link FxException} otherwise.
     * Compiles against every permitted type via an unchecked cast of
     * {@code this}, since every implementation of this sealed interface
     * is itself a valid {@code T}.
     */
    @SuppressWarnings("unchecked")
    default <T extends FxResult> T orThrow() {
        Optional<FxError> error = error();
        if (error.isPresent()) {
            throw new FxException(error.get());
        }
        return (T) this;
    }
}
