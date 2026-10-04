package com.power.fx.api.model;

import java.util.Objects;

/**
 * Links an {@link AccountingUnit} and a {@link Purpose} to the two
 * policies that together convert settlement currency to functional and
 * functional to presentation currency.
 *
 * @see "Tech spec S4.4"
 */
public record AccountingFxPolicy(
        VersionEnvelope envelope,
        String unitId,
        Purpose purpose,
        String settlementToFunctionalPolicyId,
        String functionalToPresentationPolicyId) {

    public AccountingFxPolicy {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(unitId, "unitId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
    }
}
