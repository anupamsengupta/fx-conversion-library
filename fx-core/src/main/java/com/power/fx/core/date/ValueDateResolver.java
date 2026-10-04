package com.power.fx.core.date;

import com.power.fx.api.model.RollConvention;

import java.time.LocalDate;

/**
 * Resolves a settlement/forward value date against a joint settlement
 * calendar and roll convention (FS S7.1 step 3, tech spec S6.5).
 */
public final class ValueDateResolver {

    public LocalDate resolve(LocalDate date, JointCalendarIndex jointSettlementCalendar, RollConvention rollConvention) {
        return RollConventions.apply(date, jointSettlementCalendar, rollConvention);
    }
}
