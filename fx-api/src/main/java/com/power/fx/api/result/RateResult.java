package com.power.fx.api.result;

import com.power.fx.api.error.FxError;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@code FxConverter.rate(RateRequest)}.
 *
 * @see "Tech spec S4.8"
 */
public record RateResult(
        CurrencyCode from,
        CurrencyCode to,
        BigDecimal rate,
        RateType rateType,
        RateFinality finality,
        List<FxReason> reasons,
        List<PathStep> path,
        DistributionRestriction distributionRestriction,
        Lineage lineage,
        List<FxWarning> warnings,
        Optional<FxError> error) implements FxResult {

    public RateResult {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(finality, "finality must not be null");
        Objects.requireNonNull(lineage, "lineage must not be null");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        path = path == null ? List.of() : List.copyOf(path);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        error = error == null ? Optional.empty() : error;
    }
}
