package com.power.fx.api.error;

/**
 * Ingest-time rejection codes (FS S16, S6.18).
 *
 * @see "Tech spec S4.2"
 */
public enum FxIngestCode {
    FX_I_APPROVAL_INVALID,
    FX_I_SCOPE_VIOLATION,
    FX_I_SNAPSHOT_IMMUTABLE,
    FX_I_SNAPSHOT_INCOMPLETE,
    FX_I_FIXING_SEQUENCE,
    FX_I_OVERLAP,
    FX_I_INVALID_VALUE,
    FX_I_SEQUENCE_GAP
}
