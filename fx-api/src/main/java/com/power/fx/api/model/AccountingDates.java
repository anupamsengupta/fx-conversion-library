package com.power.fx.api.model;

import java.time.LocalDate;

/**
 * Accounting-side dates a {@link DateRule} may resolve against (FS S9).
 *
 * @see "Tech spec S4.7"
 */
public record AccountingDates(
        LocalDate recognitionDate,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate settlementDate,
        LocalDate fairValueDate,
        LocalDate historicalDate) {
}
