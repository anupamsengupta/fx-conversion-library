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
 * The result of {@code FxConverter.convert(ConversionRequest)}.
 * {@code toAmountUnrounded} is always the full-precision value;
 * {@code toAmountBooked} is rounded exactly once (FS S15).
 *
 * @see "Tech spec S4.8"
 */
public record ConversionResult(
        CurrencyCode fromCcy,
        CurrencyCode toCcy,
        BigDecimal fromAmount,
        BigDecimal toAmountUnrounded,
        BigDecimal toAmountBooked,
        BigDecimal effectiveRate,
        RateType rateType,
        RateFinality finality,
        List<FxReason> reasons,
        List<PathStep> path,
        DistributionRestriction distributionRestriction,
        Lineage lineage,
        List<FxWarning> warnings,
        Optional<FxError> error) implements FxResult {

    public ConversionResult {
        Objects.requireNonNull(fromCcy, "fromCcy must not be null");
        Objects.requireNonNull(toCcy, "toCcy must not be null");
        Objects.requireNonNull(fromAmount, "fromAmount must not be null");
        Objects.requireNonNull(toAmountUnrounded, "toAmountUnrounded must not be null");
        Objects.requireNonNull(toAmountBooked, "toAmountBooked must not be null");
        Objects.requireNonNull(effectiveRate, "effectiveRate must not be null");
        Objects.requireNonNull(finality, "finality must not be null");
        Objects.requireNonNull(lineage, "lineage must not be null");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        path = path == null ? List.of() : List.copyOf(path);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        error = error == null ? Optional.empty() : error;
    }
}
