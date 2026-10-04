package com.power.fx.core.curve;

import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Short-end (before spot) adjustment via overnight/tomorrow-next points
 * (FS S11.4): {@code F(T+1) = S - TN/pointsScale}, {@code F(T+0) = S -
 * (ON+TN)/pointsScale}. Used for {@code spotAdjustment = TO_VALUATION_DATE}.
 *
 * <p><strong>OQ-T06 (accepted-for-now):</strong> when ON/TN points are
 * absent, this falls back to spot unadjusted with no additional reason
 * code, per the tech spec's own stated (if flagged-as-silent) behaviour.
 * Linked to OQ-06; must be revisited together when those close.
 */
public final class ShortEndAdjuster {

    private ShortEndAdjuster() {
    }

    public static BigDecimal adjust(BigDecimal spot, LocalDate spotDate, LocalDate valueDate, BigDecimal onPoints,
            BigDecimal tnPoints, BigDecimal pointsScale, FxMath fxMath) {
        if (onPoints == null || tnPoints == null) {
            // OQ-T06: ON/TN absent -> fall back to spot unadjusted, silently (no new reason code).
            return spot;
        }
        long daysBeforeSpot = spotDate.toEpochDay() - valueDate.toEpochDay();
        if (daysBeforeSpot <= 0) {
            return spot;
        }
        if (daysBeforeSpot == 1) {
            return spot.subtract(tnPoints.divide(pointsScale, fxMath.working()), fxMath.working());
        }
        // T+0 (or earlier): both ON and TN points subtracted.
        BigDecimal combined = onPoints.add(tnPoints, fxMath.working());
        return spot.subtract(combined.divide(pointsScale, fxMath.working()), fxMath.working());
    }
}
