package com.power.fx.api;

import com.power.fx.api.model.TenantHealthStatus;

import java.util.Optional;
import java.util.Set;

/**
 * Per-tenant health and bootstrap status.
 *
 * @see "Tech spec S5.3"
 */
public interface FxHealth {

    TenantHealthStatus status(String tenantId);

    Optional<String> latestSnapshotId(String tenantId);

    Set<String> staleKeys(String tenantId);
}
