package com.power.fx.core.averaging;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.AveragingMethod;
import com.power.fx.api.model.AveragingSpec;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.Rational;
import com.power.fx.core.FxErrors;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.decimal.RationalMath;
import com.power.fx.core.entitlement.RestrictionPropagator;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.rate.RateQuote;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code RATE_AVERAGE} and {@code PRICE_MATCHED} (S6.11, FS S10.1-10.3).
 * Vectors G06 (partial period), G07 (PRICE_MATCHED), G08 (RATE_AVERAGE).
 *
 * <p>Weighted-average arithmetic follows the binding order exactly
 * (S6.11): {@link RationalMath#commonDenominator} brings weights to one
 * common denominator {@code q} (exact {@link BigInteger} lcm); {@code
 * sum(p_i * X_i)} is computed integer-times-decimal; {@code / q} is the
 * single division.
 */
public final class DefaultAveragingEngine implements AveragingEngine {

    private final PartialPeriodAggregator partialPeriodAggregator = new PartialPeriodAggregator();

    @Override
    public SeriesOutcome average(ObservationSet set, List<RateQuote> quotes, List<BigDecimal> pricesOrNull,
            BigDecimal periodAmountOrNull, boolean invertAtApplication, AveragingSpec spec, FxMath fxMath) {
        List<Observation> observations = set.observations();
        if (observations.size() != quotes.size()) {
            throw new IllegalArgumentException("observations and quotes must be the same size");
        }
        List<Rational> weights = observations.stream().map(Observation::weight).toList();
        List<BigDecimal> rates = quotes.stream().map(RateQuote::rate).toList();
        List<RateFinality> finalities = quotes.stream().map(RateQuote::finality).toList();

        RationalMath.CommonDenominator cd = RationalMath.commonDenominator(weights);
        BigInteger q = cd.denominator();
        List<BigInteger> numerators = cd.numerators();

        BigDecimal weightedRateSum = BigDecimal.ZERO;
        for (int i = 0; i < rates.size(); i++) {
            weightedRateSum = weightedRateSum.add(new BigDecimal(numerators.get(i)).multiply(rates.get(i), fxMath.working()),
                    fxMath.working());
        }
        BigDecimal averageRate = weightedRateSum.divide(new BigDecimal(q), fxMath.working()).round(FxMath.DECIMAL128);

        AveragingMethod method = spec == null ? AveragingMethod.NONE : spec.method();
        List<SeriesOutcome.ObservationOutcome> outcomes = new ArrayList<>();
        BigDecimal totalUnrounded;

        if (method == AveragingMethod.PRICE_MATCHED) {
            if (pricesOrNull == null || pricesOrNull.size() != observations.size()) {
                throw FxErrors.of(FxErrorCode.FX_V_PRICE_SERIES_MISMATCH, "PRICE_MATCHED requires one price per observation");
            }
            BigDecimal weightedAmountSum = BigDecimal.ZERO;
            for (int i = 0; i < observations.size(); i++) {
                BigDecimal price = pricesOrNull.get(i);
                BigDecimal rate = rates.get(i);
                BigDecimal amount = invertAtApplication
                        ? price.divide(rate, fxMath.working())
                        : price.multiply(rate, fxMath.working());
                weightedAmountSum = weightedAmountSum.add(new BigDecimal(numerators.get(i)).multiply(amount, fxMath.working()),
                        fxMath.working());
                outcomes.add(new SeriesOutcome.ObservationOutcome(observations.get(i), rate, amount.round(FxMath.DECIMAL128),
                        quotes.get(i).finality(), quotes.get(i).reasons(), quotes.get(i).source(), quotes.get(i).fixingVersionId()));
            }
            totalUnrounded = weightedAmountSum.divide(new BigDecimal(q), fxMath.working()).round(FxMath.DECIMAL128);
        } else {
            // RATE_AVERAGE (and NONE, trivially: one observation, weight 1).
            BigDecimal baseAmount;
            if (pricesOrNull != null && !pricesOrNull.isEmpty()) {
                BigDecimal weightedPriceSum = BigDecimal.ZERO;
                for (int i = 0; i < pricesOrNull.size(); i++) {
                    weightedPriceSum = weightedPriceSum.add(
                            new BigDecimal(numerators.get(i)).multiply(pricesOrNull.get(i), fxMath.working()), fxMath.working());
                }
                baseAmount = weightedPriceSum.divide(new BigDecimal(q), fxMath.working());
            } else {
                if (periodAmountOrNull == null) {
                    throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                            "RATE_AVERAGE requires either prices[] or a period amount to apply the average rate to");
                }
                baseAmount = periodAmountOrNull;
            }
            totalUnrounded = (invertAtApplication
                    ? baseAmount.divide(averageRate, fxMath.working())
                    : baseAmount.multiply(averageRate, fxMath.working())).round(FxMath.DECIMAL128);

            for (int i = 0; i < observations.size(); i++) {
                outcomes.add(new SeriesOutcome.ObservationOutcome(observations.get(i), rates.get(i), null,
                        quotes.get(i).finality(), quotes.get(i).reasons(), quotes.get(i).source(), quotes.get(i).fixingVersionId()));
            }
        }

        PartialPeriodAggregator.Result partial = partialPeriodAggregator.aggregate(weights, rates, finalities, fxMath);
        RateFinality overallFinality = RateFinality.weakest(finalities.toArray(new RateFinality[0]));
        List<FxReason> reasons = new ArrayList<>();
        if (partial.confirmedPortion().compareTo(Rational.ONE) < 0) {
            reasons.add(FxReason.PARTIAL_PERIOD);
        }
        if (method == AveragingMethod.RATE_AVERAGE && spec != null && spec.averageInverted()) {
            reasons.add(FxReason.AVERAGE_INVERTED);
        }

        RightsSet rights = RestrictionPropagator.average(quotes.stream().map(RateQuote::rights).toList());

        return new SeriesOutcome(totalUnrounded, averageRate, partial.confirmedAverage(), partial.estimatedAverage(),
                partial.confirmedPortion(), overallFinality, reasons, outcomes, rights);
    }
}
