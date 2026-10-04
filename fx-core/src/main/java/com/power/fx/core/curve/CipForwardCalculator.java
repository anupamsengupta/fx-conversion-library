package com.power.fx.core.curve;

import com.power.fx.core.decimal.FxMath;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Covered interest parity forward: {@code F = S * DF_base / DF_quote}
 * (multiply before dividing, one division), and the HYBRID anchor variant
 * {@code F(t) = F_last * (DF_base(t)/DF_base(t_last)) / (DF_quote(t)/DF_quote(t_last))}
 * rearranged to one division (FS S11.4).
 */
public final class CipForwardCalculator {

    private CipForwardCalculator() {
    }

    public static BigDecimal cip(BigDecimal spot, DiscountCurve base, DiscountCurve quote, LocalDate valueDate, FxMath fxMath) {
        BigDecimal dfBase = base.discountFactor(valueDate, fxMath);
        BigDecimal dfQuote = quote.discountFactor(valueDate, fxMath);
        return spot.multiply(dfBase, fxMath.working()).divide(dfQuote, fxMath.working());
    }

    public static BigDecimal hybridAnchored(BigDecimal lastOutright, LocalDate lastDate, DiscountCurve base,
            DiscountCurve quote, LocalDate valueDate, FxMath fxMath) {
        BigDecimal dfBaseT = base.discountFactor(valueDate, fxMath);
        BigDecimal dfBaseLast = base.discountFactor(lastDate, fxMath);
        BigDecimal dfQuoteT = quote.discountFactor(valueDate, fxMath);
        BigDecimal dfQuoteLast = quote.discountFactor(lastDate, fxMath);
        // Rearranged to one division: F = F_last * (dfBaseT * dfQuoteLast) / (dfBaseLast * dfQuoteT)
        BigDecimal numerator = lastOutright.multiply(dfBaseT, fxMath.working()).multiply(dfQuoteLast, fxMath.working());
        BigDecimal denominator = dfBaseLast.multiply(dfQuoteT, fxMath.working());
        return numerator.divide(denominator, fxMath.working());
    }
}
