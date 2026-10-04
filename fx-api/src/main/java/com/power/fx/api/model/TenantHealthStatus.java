package com.power.fx.api.model;

/**
 * Per-tenant bootstrap/health status, exposed via {@code FxHealth}.
 *
 * @see "Tech spec S4.1"
 */
public enum TenantHealthStatus {
    NOT_LOADED,
    LOADING,
    READY,
    STALE
}
