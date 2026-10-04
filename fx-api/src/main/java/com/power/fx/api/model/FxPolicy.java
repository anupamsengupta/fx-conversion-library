package com.power.fx.api.model;

import java.util.List;
import java.util.Objects;

/**
 * The full resolution policy for one leg: date rule, source priority,
 * fixing version selection, averaging, fallback chain, rounding and
 * contract-rate override.
 *
 * @see "Tech spec S4.4"
 */
public record FxPolicy(
        VersionEnvelope envelope,
        String policyId,
        int version,
        Leg leg,
        DateRule dateRule,
        OffsetSpec offset,
        NonPublicationDayHandling nonPublicationDayHandling,
        RollConvention rollConvention,
        List<String> rateSourcePriority,
        FixingVersionSelection fixingVersionSelection,
        FutureDateTreatment futureDateTreatment,
        SpotAdjustment spotAdjustment,
        AveragingSpec averaging,
        List<FallbackStep> fallbackChain,
        boolean allowMixedSources,
        CurrencyCode forceCrossVia,
        RoundingSpec rounding,
        ContractRate contractRate,
        EstimatedEventHandling estimatedEventHandling,
        boolean allowOverrides) {

    public FxPolicy {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(leg, "leg must not be null");
        Objects.requireNonNull(dateRule, "dateRule must not be null");
        Objects.requireNonNull(nonPublicationDayHandling, "nonPublicationDayHandling must not be null");
        Objects.requireNonNull(rollConvention, "rollConvention must not be null");
        Objects.requireNonNull(fixingVersionSelection, "fixingVersionSelection must not be null");
        Objects.requireNonNull(futureDateTreatment, "futureDateTreatment must not be null");
        Objects.requireNonNull(spotAdjustment, "spotAdjustment must not be null");
        Objects.requireNonNull(estimatedEventHandling, "estimatedEventHandling must not be null");
        rateSourcePriority = rateSourcePriority == null ? List.of() : List.copyOf(rateSourcePriority);
        fallbackChain = fallbackChain == null ? List.of() : List.copyOf(fallbackChain);
    }
}
