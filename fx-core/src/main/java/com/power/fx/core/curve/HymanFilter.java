package com.power.fx.core.curve;

import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;
import java.util.List;

/**
 * Filters Fritsch-Carlson secant-based slopes to preserve monotonicity
 * (Hyman, 1983), for {@code MONOTONE_CUBIC_POINTS} interpolation (FS
 * S11.4).
 *
 * <p><strong>Documented simplification:</strong> this implements the
 * widely-used simplified Hyman correction (clamp each slope to at most
 * {@code 3 * min(adjacent secants)} in magnitude, zero where a secant
 * changes sign) rather than the full original Hyman (1983) algorithm in
 * all its boundary-condition detail. No golden vector in this phase
 * exercises {@code MONOTONE_CUBIC_POINTS} with an exact expected value
 * (G04/G05 use {@code LINEAR_POINTS}/{@code LOG_LINEAR_CARRY}), so this
 * simplification has not been verified against an authoritative reference
 * and should be treated as provisional.
 */
public final class HymanFilter {

    private HymanFilter() {
    }

    public static List<BigDecimal> filter(List<BigDecimal> secants, List<BigDecimal> rawSlopes, FxMath fxMath) {
        int n = rawSlopes.size();
        BigDecimal[] filtered = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            BigDecimal slope = rawSlopes.get(i);
            BigDecimal secantBefore = i > 0 ? secants.get(i - 1) : null;
            BigDecimal secantAfter = i < secants.size() ? secants.get(i) : null;
            if (secantBefore != null && secantAfter != null
                    && secantBefore.signum() != secantAfter.signum()) {
                filtered[i] = BigDecimal.ZERO;
                continue;
            }
            BigDecimal limit = null;
            if (secantBefore != null) {
                limit = secantBefore.abs(fxMath.working());
            }
            if (secantAfter != null) {
                BigDecimal a = secantAfter.abs(fxMath.working());
                limit = (limit == null) ? a : limit.min(a);
            }
            if (limit == null) {
                filtered[i] = slope;
                continue;
            }
            BigDecimal cap = limit.multiply(BigDecimal.valueOf(3), fxMath.working());
            if (slope.abs(fxMath.working()).compareTo(cap) > 0) {
                filtered[i] = slope.signum() >= 0 ? cap : cap.negate();
            } else {
                filtered[i] = slope;
            }
        }
        return List.of(filtered);
    }
}
