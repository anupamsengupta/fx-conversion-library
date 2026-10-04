package com.power.fx.core.validation;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.AveragingMethod;
import com.power.fx.api.model.PricingDaySet;
import com.power.fx.api.model.PricingObservation;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.request.ObservationPrice;
import com.power.fx.core.FxErrors;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage 2/4 precheck validations that are not part of {@link
 * PolicyMatrix}'s rule tables (S6.3's {@code runMode}/{@code
 * UNREALISED_MTM}/{@code UNSIGNED} combination, and the {@code
 * PRICE_MATCHED} sequence-matching precheck ahead of the averaging
 * engine).
 */
public final class RequestValidator {

    /** A-09/S6.3: an OFFICIAL MTM run against an unsigned snapshot is rejected. */
    public void validateUnsignedSnapshot(Purpose purpose, RunMode runMode, SignOffStatus signOffStatus) {
        if (runMode == RunMode.OFFICIAL && purpose == Purpose.UNREALISED_MTM && signOffStatus == SignOffStatus.UNSIGNED) {
            throw FxErrors.of(FxErrorCode.FX_V_UNSIGNED_SNAPSHOT,
                    "OFFICIAL UNREALISED_MTM run against an UNSIGNED snapshot");
        }
    }

    /** Vector X11: PRICE_MATCHED prices[] must key exactly onto the PDR sequence set. */
    public void validatePriceSeriesMatch(AveragingMethod method, PricingDaySet pricingSet, List<ObservationPrice> prices) {
        if (method != AveragingMethod.PRICE_MATCHED) {
            return;
        }
        Set<Integer> pdrSequences = pricingSet == null
                ? Set.of()
                : pricingSet.observations().stream().map(PricingObservation::sequence).collect(Collectors.toSet());
        Set<Integer> priceSequences = prices == null
                ? Set.of()
                : prices.stream().map(ObservationPrice::sequence).collect(Collectors.toSet());
        if (!pdrSequences.equals(priceSequences)) {
            throw FxErrors.of(FxErrorCode.FX_V_PRICE_SERIES_MISMATCH,
                    "PRICE_MATCHED prices[] sequences " + priceSequences + " do not match PDR sequences " + pdrSequences,
                    Map.of("pdrSequences", String.valueOf(pdrSequences), "priceSequences", String.valueOf(priceSequences)));
        }
    }
}
