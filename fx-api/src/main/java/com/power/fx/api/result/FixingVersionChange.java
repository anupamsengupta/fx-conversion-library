package com.power.fx.api.result;

import com.power.fx.api.model.CurrencyPair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One fixing input that changed between two lineage snapshots, as
 * identified by {@code assessCorrectionImpact} (D-06).
 *
 * @see "Tech spec S4.8"
 */
public record FixingVersionChange(
        String sourceCode,
        CurrencyPair pair,
        LocalDate fixingDate,
        String oldVersionId,
        String newVersionId,
        BigDecimal oldValue,
        BigDecimal newValue) {

    public FixingVersionChange {
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(fixingDate, "fixingDate must not be null");
    }
}
