package com.power.fx.api.spi;

import java.util.Optional;

/**
 * Host-implemented strategy for resolving the current tenant (A-05).
 * Returns {@code Optional<String>} rather than relying on {@code
 * ScopedValue}, so hosts choose their own propagation mechanism; a host
 * on Java 25 may back this with {@code ScopedValue} without a library
 * change.
 *
 * @see "Tech spec S5.2"
 */
public interface TenantContextProvider {
    Optional<String> currentTenant();
}
