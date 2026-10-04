package com.power.fx.api.request;

/**
 * Sealed hierarchy of every public request shape the library accepts.
 * Permits match S4.7 exactly; a {@code switch} over this type without a
 * {@code default} branch fails to compile if a permit is missing.
 *
 * @see "Tech spec S4.7"
 */
public sealed interface FxRequest
        permits RateRequest, ConversionRequest, SeriesRequest, ChainRequest, MonetaryRevaluationRequest {

    FxRequestContext context();
}
