package com.power.fx.core.averaging;

import com.power.fx.api.model.Rational;
import com.power.fx.api.model.RateFinality;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.decimal.RationalMath;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * FS S10.3 partial-period split: {@code confirmedAverage}/{@code
 * estimatedAverage}/{@code confirmedPortion}, using the same common
 * denominator as the overall weighted average (S6.11's binding
 * multiply-before-divide order). Vector G06.
 */
public final class PartialPeriodAggregator {

    public record Result(BigDecimal confirmedAverage, BigDecimal estimatedAverage, Rational confirmedPortion) {
    }

    public Result aggregate(List<Rational> weights, List<BigDecimal> rates, List<RateFinality> finalities, FxMath fxMath) {
        RationalMath.CommonDenominator cd = RationalMath.commonDenominator(weights);
        BigInteger q = cd.denominator();
        List<BigInteger> numerators = cd.numerators();

        BigInteger confirmedP = BigInteger.ZERO;
        BigDecimal confirmedWeightedSum = BigDecimal.ZERO;
        BigInteger estimatedP = BigInteger.ZERO;
        BigDecimal estimatedWeightedSum = BigDecimal.ZERO;

        for (int i = 0; i < weights.size(); i++) {
            BigInteger p = numerators.get(i);
            BigDecimal contribution = new BigDecimal(p).multiply(rates.get(i), fxMath.working());
            if (finalities.get(i) == RateFinality.CONFIRMED) {
                confirmedP = confirmedP.add(p);
                confirmedWeightedSum = confirmedWeightedSum.add(contribution, fxMath.working());
            } else {
                estimatedP = estimatedP.add(p);
                estimatedWeightedSum = estimatedWeightedSum.add(contribution, fxMath.working());
            }
        }

        BigDecimal confirmedAverage = confirmedP.signum() == 0 ? null
                : confirmedWeightedSum.divide(new BigDecimal(confirmedP), fxMath.working()).round(FxMath.DECIMAL128);
        BigDecimal estimatedAverage = estimatedP.signum() == 0 ? null
                : estimatedWeightedSum.divide(new BigDecimal(estimatedP), fxMath.working()).round(FxMath.DECIMAL128);
        Rational confirmedPortion = new Rational(confirmedP, q);

        return new Result(confirmedAverage, estimatedAverage, confirmedPortion);
    }
}
