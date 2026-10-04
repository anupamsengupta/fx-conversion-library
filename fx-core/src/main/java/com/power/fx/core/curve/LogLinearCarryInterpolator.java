package com.power.fx.core.curve;

import com.power.fx.api.model.Rational;
import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;

/**
 * {@code LOG_LINEAR_CARRY} (default, D-15): linear in {@code ln(F/S)}
 * against time; {@code F = S * exp(y)}. Vector G05:
 * {@code 1.0895143}, bit-identical on repeated evaluation.
 *
 * @see "Tech spec S6.9, D.6"
 */
public final class LogLinearCarryInterpolator {

    private LogLinearCarryInterpolator() {
    }

    public static BigDecimal interpolate(BigDecimal spot, BigDecimal lnCarryLo, BigDecimal lnCarryHi,
            Rational tLo, Rational tHi, Rational t, FxMath fxMath) {
        BigDecimal tDec = t.toDecimal(fxMath.working());
        BigDecimal tLoDec = tLo.toDecimal(fxMath.working());
        BigDecimal tHiDec = tHi.toDecimal(fxMath.working());
        BigDecimal fraction = tDec.subtract(tLoDec, fxMath.working())
                .divide(tHiDec.subtract(tLoDec, fxMath.working()), fxMath.working());
        BigDecimal y = lnCarryLo.add(lnCarryHi.subtract(lnCarryLo, fxMath.working()).multiply(fraction, fxMath.working()),
                fxMath.working());
        return spot.multiply(fxMath.exp(y), fxMath.working());
    }
}
