package com.power.fx.api.result;

import com.power.fx.api.error.FxError;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.RateFinality;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@code FxConverter.convertChain(ChainRequest)}. Leg
 * ordering halts on the first {@code UPSTREAM_UNRESOLVED} leg -- no
 * market data is touched for subsequent legs (S6.14).
 *
 * @see "Tech spec S4.8"
 */
public record ChainResult(
        Map<Leg, LegResult> legs,
        List<ManagementViewResult> managementViews,
        RateFinality finality,
        Lineage lineage,
        List<FxWarning> warnings,
        Optional<FxError> error) implements FxResult {

    public ChainResult {
        Objects.requireNonNull(finality, "finality must not be null");
        Objects.requireNonNull(lineage, "lineage must not be null");
        legs = legs == null ? Map.of() : Map.copyOf(legs);
        managementViews = managementViews == null ? List.of() : List.copyOf(managementViews);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        error = error == null ? Optional.empty() : error;
    }
}
