package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The ingest-facing and loader-facing shape of a single fixing version.
 * The in-memory store uses a columnar layout (S7.1.3) and does not retain
 * one object per fixing.
 *
 * @see "Tech spec S4.5"
 */
public record FixingVersion(
        Scope scope,
        String tenantId,
        String sourceCode,
        CurrencyPair pair,
        LocalDate fixingDate,
        String cutoff,
        BigDecimal value,
        FixingStatus fixingStatus,
        Instant recordedAt,
        String versionId,
        String correctionOf,
        LocalDate valueDate) {

    public FixingVersion {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(fixingDate, "fixingDate must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(fixingStatus, "fixingStatus must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
    }
}
