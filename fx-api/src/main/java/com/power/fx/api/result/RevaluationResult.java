package com.power.fx.api.result;

import com.power.fx.api.error.FxError;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.FxDifferenceClass;
import com.power.fx.api.model.RateFinality;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@code FxConverter.revalue(MonetaryRevaluationRequest)}:
 * a signed difference against the caller-supplied carrying amount,
 * classified realised/unrealised (D-04, FS S9.3).
 *
 * @see "Tech spec S4.8"
 */
public record RevaluationResult(
        CurrencyCode functionalCurrency,
        BigDecimal newFunctionalUnrounded,
        BigDecimal newFunctionalBooked,
        BigDecimal carryingFunctionalAmount,
        BigDecimal difference,
        FxDifferenceClass classification,
        BigDecimal rateUsed,
        RateFinality finality,
        List<PathStep> path,
        Lineage lineage,
        List<FxWarning> warnings,
        Optional<FxError> error) implements FxResult {

    public RevaluationResult {
        Objects.requireNonNull(functionalCurrency, "functionalCurrency must not be null");
        Objects.requireNonNull(finality, "finality must not be null");
        Objects.requireNonNull(lineage, "lineage must not be null");
        path = path == null ? List.of() : List.copyOf(path);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        error = error == null ? Optional.empty() : error;
    }
}
