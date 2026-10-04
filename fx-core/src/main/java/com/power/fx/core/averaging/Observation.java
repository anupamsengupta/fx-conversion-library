package com.power.fx.core.averaging;

import com.power.fx.api.model.Rational;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One observation of an {@link ObservationSet} (S6.11): sequence, raw and
 * resolved FX dates, exact weight, optional price/volume, and whether it
 * was skipped ({@code SKIP_OBSERVATION}).
 */
public record Observation(
        int sequence,
        LocalDate observationDate,
        LocalDate rawFxDate,
        LocalDate resolvedFxDate,
        Rational weight,
        BigDecimal price,
        BigDecimal volume,
        boolean skipped) {
}
