package com.power.fx.api.model;

import java.util.Objects;
import java.util.Set;

/**
 * The rights a tenant holds over a market data source (D-10).
 *
 * @see "Tech spec S4.4"
 */
public record SourceEntitlement(
        VersionEnvelope envelope,
        String tenantId,
        String sourceCode,
        Set<SourceRight> rights) {

    public SourceEntitlement {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        rights = rights == null ? Set.of() : Set.copyOf(rights);
    }
}
