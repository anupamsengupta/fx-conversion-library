package com.power.fx.core.curve;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DiscountCurvePayload;
import com.power.fx.api.model.DiscountPillar;
import com.power.fx.api.model.Rational;
import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A compiled discount curve: ascending pillar dates, discount factors and
 * their logs (log-DF, for log-linear interpolation), used by {@link
 * CipForwardCalculator} (S7.1.4). Interpreted strictly as discount
 * factors, per OQ-04's unresolved choice between discount-factor and
 * zero-rate representations -- {@link DiscountPillar#discountFactor()} is
 * the only field this spec's v1.0 admits.
 *
 * @see "Tech spec S7.1.4, S6.9, OQ-04"
 */
public final class DiscountCurve {

    private final CurrencyCode currency;
    private final String curveRef;
    private final List<LocalDate> dates;
    private final List<BigDecimal> discountFactors;
    private final List<BigDecimal> lnDf;

    private DiscountCurve(CurrencyCode currency, String curveRef, List<LocalDate> dates,
            List<BigDecimal> discountFactors, List<BigDecimal> lnDf) {
        this.currency = currency;
        this.curveRef = curveRef;
        this.dates = dates;
        this.discountFactors = discountFactors;
        this.lnDf = lnDf;
    }

    public static DiscountCurve of(DiscountCurvePayload payload, FxMath fxMath) {
        List<DiscountPillar> sorted = new ArrayList<>(payload.pillars());
        sorted.sort(Comparator.comparing(DiscountPillar::date));
        List<LocalDate> dates = new ArrayList<>(sorted.size());
        List<BigDecimal> dfs = new ArrayList<>(sorted.size());
        List<BigDecimal> lns = new ArrayList<>(sorted.size());
        for (DiscountPillar p : sorted) {
            dates.add(p.date());
            dfs.add(p.discountFactor());
            lns.add(fxMath.ln(p.discountFactor()));
        }
        return new DiscountCurve(payload.currency(), payload.curveRef(), List.copyOf(dates), List.copyOf(dfs),
                List.copyOf(lns));
    }

    public CurrencyCode currency() {
        return currency;
    }

    public String curveRef() {
        return curveRef;
    }

    public LocalDate firstDate() {
        return dates.get(0);
    }

    public LocalDate lastDate() {
        return dates.get(dates.size() - 1);
    }

    /**
     * Log-linear interpolated discount factor at {@code date}. Flat
     * (constant) extrapolation beyond either end -- a discount curve's
     * extrapolation policy is not separately specified by the tech spec,
     * so this implementation holds the nearest observed discount factor
     * constant, which is the standard convention and the conservative
     * choice for a derived forward.
     */
    public BigDecimal discountFactor(LocalDate date, FxMath fxMath) {
        if (!date.isAfter(firstDate())) {
            return discountFactors.get(0);
        }
        if (!date.isBefore(lastDate())) {
            return discountFactors.get(discountFactors.size() - 1);
        }
        int hi = 0;
        while (dates.get(hi).isBefore(date)) {
            hi++;
        }
        int lo = hi - 1;
        if (dates.get(hi).isEqual(date)) {
            return discountFactors.get(hi);
        }
        Rational tLo = DayCount.act365f(dates.get(lo), date);
        Rational tSpan = DayCount.act365f(dates.get(lo), dates.get(hi));
        BigDecimal fraction = tLo.toDecimal(fxMath.working()).divide(tSpan.toDecimal(fxMath.working()), fxMath.working());
        BigDecimal lnLo = lnDf.get(lo);
        BigDecimal lnHi = lnDf.get(hi);
        BigDecimal interpolatedLn = lnLo.add(lnHi.subtract(lnLo, fxMath.working()).multiply(fraction, fxMath.working()),
                fxMath.working());
        return fxMath.exp(interpolatedLn);
    }
}
