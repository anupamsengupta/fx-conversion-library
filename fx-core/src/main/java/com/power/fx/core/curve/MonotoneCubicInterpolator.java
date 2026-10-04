package com.power.fx.core.curve;

import com.power.fx.api.model.Rational;
import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code MONOTONE_CUBIC_POINTS}: Fritsch-Carlson slopes, Hyman-filtered
 * (FS S11.4). Pure DECIMAL128/rational arithmetic, no transcendentals.
 *
 * @see HymanFilter
 */
public final class MonotoneCubicInterpolator {

    private MonotoneCubicInterpolator() {
    }

    /** Fritsch-Carlson secant slopes between consecutive (t_i, points_i) pillars. */
    public static List<BigDecimal> secants(List<Rational> t, List<BigDecimal> points, FxMath fxMath) {
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i + 1 < t.size(); i++) {
            BigDecimal dt = t.get(i + 1).toDecimal(fxMath.working()).subtract(t.get(i).toDecimal(fxMath.working()), fxMath.working());
            BigDecimal dp = points.get(i + 1).subtract(points.get(i), fxMath.working());
            result.add(dp.divide(dt, fxMath.working()));
        }
        return result;
    }

    /** Initial (pre-Hyman-filter) slope at each pillar: average of adjacent secants, zero at the ends' sign changes. */
    public static List<BigDecimal> initialSlopes(List<BigDecimal> secants, FxMath fxMath) {
        int n = secants.size() + 1;
        BigDecimal[] slopes = new BigDecimal[n];
        slopes[0] = secants.isEmpty() ? BigDecimal.ZERO : secants.get(0);
        slopes[n - 1] = secants.isEmpty() ? BigDecimal.ZERO : secants.get(secants.size() - 1);
        for (int i = 1; i < n - 1; i++) {
            BigDecimal a = secants.get(i - 1);
            BigDecimal b = secants.get(i);
            slopes[i] = a.add(b, fxMath.working()).divide(BigDecimal.valueOf(2), fxMath.working());
        }
        return List.of(slopes);
    }

    /** Cubic Hermite evaluation between bracket [i, i+1] at fraction {@code u in [0,1]} of the interval. */
    public static BigDecimal hermite(BigDecimal p0, BigDecimal p1, BigDecimal m0, BigDecimal m1, BigDecimal dt,
            BigDecimal u, FxMath fxMath) {
        java.math.MathContext mc = fxMath.working();
        BigDecimal u2 = u.multiply(u, mc);
        BigDecimal u3 = u2.multiply(u, mc);
        BigDecimal h00 = BigDecimal.valueOf(2).multiply(u3, mc).subtract(BigDecimal.valueOf(3).multiply(u2, mc), mc).add(BigDecimal.ONE, mc);
        BigDecimal h10 = u3.subtract(BigDecimal.valueOf(2).multiply(u2, mc), mc).add(u, mc);
        BigDecimal h01 = BigDecimal.valueOf(-2).multiply(u3, mc).add(BigDecimal.valueOf(3).multiply(u2, mc), mc);
        BigDecimal h11 = u3.subtract(u2, mc);
        return h00.multiply(p0, mc)
                .add(h10.multiply(dt, mc).multiply(m0, mc), mc)
                .add(h01.multiply(p1, mc), mc)
                .add(h11.multiply(dt, mc).multiply(m1, mc), mc);
    }
}
