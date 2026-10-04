package com.power.fx.testkit.doubles;

import com.power.fx.api.spi.TenantContextProvider;

import java.util.Optional;

/**
 * Mutable, thread-confined {@link TenantContextProvider} double for tests
 * and host conformance use (Task 3b.2).
 *
 * <p><strong>AR-10 exemption:</strong> this is the one place in the whole
 * reactor where a literal, tenant-id-shaped string is expected to appear
 * at call sites (test setup code calling {@link #setTenant(String)}).
 * {@code fx-testkit} is explicitly exempted from AR-10 (S12.5: "no string
 * literal matching a tenant-id pattern in fx-api/fx-core main sources;
 * fx-testkit is exempt") precisely because test fixtures need to name
 * concrete tenant ids to set up multi-tenant scenarios. {@code fx-api} and
 * {@code fx-core} main sources never hardcode a tenant id -- they always
 * take one as a parameter or read it from {@link TenantContextProvider}, a
 * live port whose only literal-bearing implementation is this class.
 *
 * @see "Tech spec S12.5 AR-10; implementation plan Task 3b.2"
 */
public final class InMemoryTenantContextProvider implements TenantContextProvider {

    private volatile String tenantId;

    public InMemoryTenantContextProvider() {
    }

    public InMemoryTenantContextProvider(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setTenant(String tenantId) {
        this.tenantId = tenantId;
    }

    public void clear() {
        this.tenantId = null;
    }

    @Override
    public Optional<String> currentTenant() {
        return Optional.ofNullable(tenantId);
    }
}
