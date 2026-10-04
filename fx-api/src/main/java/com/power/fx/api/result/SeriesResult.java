package com.power.fx.api.result;

import com.power.fx.api.error.FxError;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.Rational;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@code FxConverter.convertSeries(SeriesRequest)}. Carries
 * both per-observation results and the total in one result (A-17).
 *
 * @see "Tech spec S4.8"
 */
public record SeriesResult(
        CurrencyCode fromCcy,
        CurrencyCode toCcy,
        BigDecimal totalUnrounded,
        BigDecimal totalBooked,
        BigDecimal averageRate,
        BigDecimal confirmedAverage,
        BigDecimal estimatedAverage,
        Rational confirmedPortion,
        List<ObservationResult> observations,
        AllocationResidual residual,
        RateFinality finality,
        List<FxReason> reasons,
        DistributionRestriction distributionRestriction,
        Lineage lineage,
        List<FxWarning> warnings,
        Optional<FxError> error) implements FxResult {

    public SeriesResult {
        Objects.requireNonNull(fromCcy, "fromCcy must not be null");
        Objects.requireNonNull(toCcy, "toCcy must not be null");
        Objects.requireNonNull(finality, "finality must not be null");
        Objects.requireNonNull(lineage, "lineage must not be null");
        observations = observations == null ? List.of() : List.copyOf(observations);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        error = error == null ? Optional.empty() : error;
    }
}
