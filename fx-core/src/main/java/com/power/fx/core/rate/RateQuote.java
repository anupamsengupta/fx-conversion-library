package com.power.fx.core.rate;

import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.result.PathStep;
import com.power.fx.core.entitlement.RightsSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A resolved rate for one route leg at one point in the pipeline (S6.8).
 * {@code needsForward}/{@code forwardValueDate} signal that stage 12 must
 * still query {@code ForwardCurveCache} before this quote's {@link
 * #rate()} is final (R6/R5 rows of the S6.8 decision table).
 *
 * @see "Tech spec S6.8"
 */
public record RateQuote(
        BigDecimal rate,
        RateType rateType,
        RateFinality finality,
        List<FxReason> reasons,
        List<PathStep> path,
        RightsSet rights,
        boolean needsForward,
        LocalDate forwardValueDate,
        String source,
        String fixingVersionId,
        FixingStatus fixingStatus) {

    public RateQuote {
        Objects.requireNonNull(rateType, "rateType must not be null");
        Objects.requireNonNull(finality, "finality must not be null");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        path = path == null ? List.of() : List.copyOf(path);
        Objects.requireNonNull(rights, "rights must not be null");
    }

    public RateQuote withRate(BigDecimal newRate) {
        return new RateQuote(newRate, rateType, finality, reasons, path, rights, false, null, source,
                fixingVersionId, fixingStatus);
    }

    public RateQuote withFinality(RateFinality newFinality, FxReason extraReason) {
        List<FxReason> newReasons = new java.util.ArrayList<>(reasons);
        if (extraReason != null) {
            newReasons.add(extraReason);
        }
        return new RateQuote(rate, rateType, newFinality, newReasons, path, rights, needsForward, forwardValueDate,
                source, fixingVersionId, fixingStatus);
    }
}
