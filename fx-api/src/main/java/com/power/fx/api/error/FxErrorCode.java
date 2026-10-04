package com.power.fx.api.error;

/**
 * Error codes returned in the {@code error} slot of an {@link FxResult}.
 * Keeps both FS prefixes in one enum -- {@code FX_E_*} resolution
 * failures and {@code FX_V_*} validation failures -- because both are
 * returned through the same slot; {@link #category()} discriminates them
 * for metrics (Pattern #3).
 *
 * @see "Tech spec S4.2"
 */
public enum FxErrorCode {
    FX_E_NO_TENANT_CONTEXT,
    FX_E_TENANT_MISMATCH,
    FX_E_TENANT_NOT_READY,
    FX_E_DATA_NOT_LOADED,
    FX_E_NO_FX_PATH,
    FX_E_RATE_NOT_FOUND,
    FX_E_NON_PUBLICATION_DATE,
    FX_E_EXTRAPOLATION_LIMIT,
    FX_E_SOURCE_NOT_ENTITLED,
    FX_E_INACTIVE_CURRENCY,
    FX_E_FUNCTIONAL_CCY_NOT_FOUND,
    FX_E_MISSING_EVENT_DATE,
    FX_V_INVALID_POLICY,
    FX_V_AMOUNT_TYPE_MISMATCH,
    FX_V_PRICE_SERIES_MISMATCH,
    FX_V_SOURCE_NOT_ALLOWED,
    FX_V_UNSIGNED_SNAPSHOT;

    /**
     * Discriminates resolution failures ({@code FX_E_*}) from validation
     * failures ({@code FX_V_*}) for metrics purposes, without requiring
     * callers to pattern-match on the constant name themselves.
     */
    public Category category() {
        return name().startsWith("FX_E_") ? Category.RESOLUTION : Category.VALIDATION;
    }

    /** The two code families this enum discriminates between. */
    public enum Category {
        RESOLUTION,
        VALIDATION
    }
}
