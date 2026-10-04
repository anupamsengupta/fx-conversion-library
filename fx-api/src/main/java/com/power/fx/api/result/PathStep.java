package com.power.fx.api.result;

import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One step of the resolved pair/rate path, carried for lineage and audit
 * display.
 *
 * @see "Tech spec S4.8"
 */
public record PathStep(
        CurrencyPair pair,
        RateType rateType,
        BigDecimal value,
        boolean inverted,
        String source,
        String cutoff,
        LocalDate rawDate,
        LocalDate resolvedDate,
        LocalDate valueDate,
        String fixingVersionId,
        FixingStatus fixingStatus,
        InterpolationMethod interpolation,
        List<PillarRef> pillars,
        List<FxReason> reasons) {

    public PathStep {
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(rateType, "rateType must not be null");
        Objects.requireNonNull(value, "value must not be null");
        pillars = pillars == null ? List.of() : List.copyOf(pillars);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
