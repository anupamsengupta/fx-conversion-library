package com.power.fx.core.rate;

import com.power.fx.core.pair.MarketLeg;

import java.time.LocalDate;

/**
 * A fixing lookup miss, eligible for the fallback chain only when the
 * resolved date is an open publication day. The compact constructor
 * structurally asserts this (D-02's second half, S6.8): the fallback chain
 * is mechanically unreachable from a holiday.
 */
public record RateLookupMiss(MarketLeg leg, String firstSourceCode, LocalDate fxDate, boolean calendarOpenAtResolvedDate) {

    public RateLookupMiss {
        if (!calendarOpenAtResolvedDate) {
            throw new IllegalStateException(
                    "RateLookupMiss requires an open publication day (D-02): the fallback chain is structurally "
                            + "unreachable from a holiday");
        }
    }
}
