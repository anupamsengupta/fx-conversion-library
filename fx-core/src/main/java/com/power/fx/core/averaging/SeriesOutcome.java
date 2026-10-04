package com.power.fx.core.averaging;

import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.Rational;
import com.power.fx.core.entitlement.RightsSet;

import java.math.BigDecimal;
import java.util.List;

/** The result of {@link AveragingEngine#average} (S6.11, FS S10). */
public record SeriesOutcome(
        BigDecimal totalUnrounded,
        BigDecimal averageRate,
        BigDecimal confirmedAverage,
        BigDecimal estimatedAverage,
        Rational confirmedPortion,
        RateFinality finality,
        List<FxReason> reasons,
        List<ObservationOutcome> observationOutcomes,
        RightsSet rights) {

    public record ObservationOutcome(
            Observation observation,
            BigDecimal rate,
            BigDecimal amountUnrounded,
            RateFinality finality,
            List<FxReason> reasons,
            String source,
            String fixingVersionId) {
    }
}
