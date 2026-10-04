package com.power.fx.api.result;

import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.Rational;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One per-observation row of a {@link SeriesResult}.
 *
 * @see "Tech spec S4.8"
 */
public record ObservationResult(
        int sequence,
        LocalDate observationDate,
        LocalDate rawFxDate,
        LocalDate resolvedFxDate,
        Rational weight,
        BigDecimal price,
        BigDecimal rate,
        BigDecimal amountUnrounded,
        BigDecimal amountBooked,
        RateType rateType,
        RateFinality finality,
        List<FxReason> reasons,
        List<PathStep> path,
        boolean skipped) {

    public ObservationResult {
        Objects.requireNonNull(observationDate, "observationDate must not be null");
        Objects.requireNonNull(weight, "weight must not be null");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        path = path == null ? List.of() : List.copyOf(path);
    }
}
