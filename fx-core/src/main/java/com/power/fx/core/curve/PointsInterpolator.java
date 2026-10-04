package com.power.fx.core.curve;

import com.power.fx.api.model.Rational;
import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;

/**
 * {@code LINEAR_POINTS} interpolation: linear in forward points against
 * time (FS S11.4). Vector G04: {@code 35 + 33*28/91 = 45.153846...}
 * points.
 */
public final class PointsInterpolator {

    private PointsInterpolator() {
    }

    public static BigDecimal interpolate(BigDecimal spot, BigDecimal pointsScale, BigDecimal pointsLo, BigDecimal pointsHi,
            Rational tLo, Rational tHi, Rational t, FxMath fxMath) {
        BigDecimal fraction = t.plus(tLo.times(Rational.of(-1, 1))).toDecimal(fxMath.working())
                .divide(tHi.plus(tLo.times(Rational.of(-1, 1))).toDecimal(fxMath.working()), fxMath.working());
        BigDecimal points = pointsLo.add(pointsHi.subtract(pointsLo, fxMath.working()).multiply(fraction, fxMath.working()),
                fxMath.working());
        return spot.add(points.divide(pointsScale, fxMath.working()), fxMath.working());
    }
}
