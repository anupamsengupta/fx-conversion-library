package com.power.fx.core.cache;

import com.power.fx.api.model.LocalDateRange;

/**
 * Internal port (S5.4, Pattern #21 Repository, in-memory, bitemporal,
 * columnar) over the fixing-store generations for one tenant.
 */
public interface FixingStore {

    FixingView view(String tenantId);

    long generation(String tenantId);

    boolean swap(String tenantId, FixingView next);

    LocalDateRange loadedWindow(String tenantId);
}
