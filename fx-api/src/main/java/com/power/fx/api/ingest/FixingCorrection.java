package com.power.fx.api.ingest;

import com.power.fx.api.model.CurrencyPair;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A fixing correction event, delivered to {@code
 * FxEventListener.onFixingCorrected} at ingest (D-06).
 *
 * @see "Tech spec S4.10"
 */
public record FixingCorrection(
        String tenantId,
        String sourceCode,
        CurrencyPair pair,
        LocalDate fixingDate,
        String cutoff,
        String oldVersionId,
        String newVersionId,
        BigDecimal oldValue,
        BigDecimal newValue,
        Instant recordedAt) {

    public FixingCorrection {
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(fixingDate, "fixingDate must not be null");
        Objects.requireNonNull(newVersionId, "newVersionId must not be null");
        Objects.requireNonNull(newValue, "newValue must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }
}
