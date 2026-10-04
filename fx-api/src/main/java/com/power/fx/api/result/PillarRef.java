package com.power.fx.api.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A single forward-curve pillar referenced by a {@link PathStep} for
 * lineage display.
 *
 * @see "Tech spec S4.8"
 */
public record PillarRef(String tenor, LocalDate valueDate, BigDecimal value) {

    public PillarRef {
        Objects.requireNonNull(tenor, "tenor must not be null");
        Objects.requireNonNull(valueDate, "valueDate must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
