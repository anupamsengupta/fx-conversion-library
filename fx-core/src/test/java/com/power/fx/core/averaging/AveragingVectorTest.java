package com.power.fx.core.averaging;

import com.power.fx.api.model.AveragingMethod;
import com.power.fx.api.model.AveragingSpec;
import com.power.fx.api.model.FxDateFromObservation;
import com.power.fx.api.model.ObservationSetKind;
import com.power.fx.api.model.OutputShape;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.Rational;
import com.power.fx.api.model.Weighting;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.rate.RateQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Golden vectors G06, G07, G08 (functional spec S19.1), driven directly against {@link DefaultAveragingEngine}. */
class AveragingVectorTest {

    private final FxMath fxMath = new FxMath(60);
    private final DefaultAveragingEngine engine = new DefaultAveragingEngine();

    private RateQuote quote(String rate, RateFinality finality) {
        return new RateQuote(new BigDecimal(rate), RateType.FIXING, finality, List.of(), List.of(),
                RightsSet.unrestricted(), false, null, "SRC", null, null);
    }

    private Observation obs(int seq, Rational weight) {
        return new Observation(seq, LocalDate.of(2026, 1, seq + 1), LocalDate.of(2026, 1, seq + 1),
                LocalDate.of(2026, 1, seq + 1), weight, null, null, false);
    }

    @Test
    void g06_partialPeriod() {
        List<Observation> observations = new ArrayList<>();
        List<RateQuote> quotes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            observations.add(obs(i, Rational.of(1, 22)));
            quotes.add(quote("1.0840", RateFinality.CONFIRMED));
        }
        for (int i = 10; i < 22; i++) {
            observations.add(obs(i, Rational.of(1, 22)));
            quotes.add(quote("1.0872", RateFinality.ESTIMATED));
        }
        ObservationSet set = new ObservationSet(observations);
        AveragingSpec spec = new AveragingSpec(AveragingMethod.RATE_AVERAGE, ObservationSetKind.EXPLICIT, null,
                Weighting.EQUAL, OutputShape.TOTAL, FxDateFromObservation.SAME_DATE, 0, false, List.of());

        SeriesOutcome outcome = engine.average(set, quotes, null, BigDecimal.ONE, false, spec, fxMath);
        assertEquals(0, new BigDecimal("1.0857454545").compareTo(outcome.averageRate().setScale(10, java.math.RoundingMode.HALF_EVEN)));
        assertEquals(Rational.of(5, 11), outcome.confirmedPortion());
        assertEquals(RateFinality.ESTIMATED, outcome.finality());
        assertEquals(0, new BigDecimal("1.0840").compareTo(outcome.confirmedAverage()));
        assertEquals(0, new BigDecimal("1.0872").compareTo(outcome.estimatedAverage()));
    }

    @Test
    void g07_priceMatched() {
        List<Observation> observations = List.of(obs(1, Rational.of(1, 5)), obs(2, Rational.of(1, 5)),
                obs(3, Rational.of(1, 5)), obs(4, Rational.of(1, 5)), obs(5, Rational.of(1, 5)));
        List<RateQuote> quotes = List.of(quote("1.10", RateFinality.CONFIRMED), quote("1.08", RateFinality.CONFIRMED),
                quote("1.12", RateFinality.CONFIRMED), quote("1.10", RateFinality.CONFIRMED), quote("1.05", RateFinality.CONFIRMED));
        List<BigDecimal> prices = List.of(new BigDecimal("80"), new BigDecimal("81"), new BigDecimal("82"),
                new BigDecimal("83"), new BigDecimal("84"));
        AveragingSpec spec = new AveragingSpec(AveragingMethod.PRICE_MATCHED, ObservationSetKind.FROM_PRICING_SET, null,
                Weighting.FROM_PRICING_SET, OutputShape.TOTAL, FxDateFromObservation.SAME_DATE, 0, false, List.of());

        SeriesOutcome outcome = engine.average(new ObservationSet(observations), quotes, prices, null, true, spec, fxMath);
        assertEquals(0, new BigDecimal("75.279220779").compareTo(outcome.totalUnrounded().setScale(9, java.math.RoundingMode.HALF_EVEN)));
    }

    @Test
    void g08_rateAverage_vs_g07_spread() {
        List<Observation> observations = List.of(obs(1, Rational.of(1, 5)), obs(2, Rational.of(1, 5)),
                obs(3, Rational.of(1, 5)), obs(4, Rational.of(1, 5)), obs(5, Rational.of(1, 5)));
        List<RateQuote> quotes = List.of(quote("1.10", RateFinality.CONFIRMED), quote("1.08", RateFinality.CONFIRMED),
                quote("1.12", RateFinality.CONFIRMED), quote("1.10", RateFinality.CONFIRMED), quote("1.05", RateFinality.CONFIRMED));
        List<BigDecimal> prices = List.of(new BigDecimal("80"), new BigDecimal("81"), new BigDecimal("82"),
                new BigDecimal("83"), new BigDecimal("84"));
        AveragingSpec spec = new AveragingSpec(AveragingMethod.RATE_AVERAGE, ObservationSetKind.FROM_PRICING_SET, null,
                Weighting.FROM_PRICING_SET, OutputShape.TOTAL, FxDateFromObservation.SAME_DATE, 0, false, List.of());

        SeriesOutcome outcome = engine.average(new ObservationSet(observations), quotes, prices, null, true, spec, fxMath);
        assertEquals(0, new BigDecimal("1.09").compareTo(outcome.averageRate()));
        assertEquals(0, new BigDecimal("75.229357798").compareTo(outcome.totalUnrounded().setScale(9, java.math.RoundingMode.HALF_EVEN)));

        BigDecimal g07 = new BigDecimal("75.279220779");
        BigDecimal spread = g07.subtract(outcome.totalUnrounded().setScale(9, java.math.RoundingMode.HALF_EVEN));
        assertEquals(0, new BigDecimal("0.049862981").compareTo(spread.setScale(9, java.math.RoundingMode.HALF_EVEN)));
    }
}
