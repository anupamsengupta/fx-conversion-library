package com.power.fx.core.curve;

import com.power.fx.api.model.Rational;

import java.time.LocalDate;
import java.math.BigInteger;

/**
 * ACT/365F day count, exact {@link Rational} throughout (FS S11.4: "exact
 * rational", never a decimal division) -- {@code t = days(spotDate,
 * valueDate) / 365}.
 *
 * @see "Tech spec S6.9"
 */
public final class DayCount {

    private DayCount() {
    }

    public static Rational act365f(LocalDate from, LocalDate to) {
        long days = to.toEpochDay() - from.toEpochDay();
        return new Rational(BigInteger.valueOf(days), BigInteger.valueOf(365));
    }
}
