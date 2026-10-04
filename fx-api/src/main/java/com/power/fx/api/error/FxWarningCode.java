package com.power.fx.api.error;

/**
 * Non-fatal warning codes attached to a successful {@code FxResult}.
 *
 * @see "Tech spec S4.2"
 */
public enum FxWarningCode {
    FX_W_SOURCE_SKIPPED_NOT_ENTITLED,
    FX_W_MIXED_SOURCE,
    FX_W_DATE_RULE_ADJUSTED,
    FX_W_FALLBACK_USED,
    FX_W_EXTRAPOLATED,
    FX_W_STALE_KEY
}
