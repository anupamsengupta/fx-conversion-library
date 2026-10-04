package com.power.fx.testkit.vectors;

import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.AveragingMethod;
import com.power.fx.api.model.AveragingSpec;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.FixedFactorKind;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.ForwardPillar;
import com.power.fx.api.model.FutureDateTreatment;
import com.power.fx.api.model.FxDateFromObservation;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.NonPublicationDayHandling;
import com.power.fx.api.model.ObservationSetKind;
import com.power.fx.api.model.OffsetCalendarKind;
import com.power.fx.api.model.OffsetSpec;
import com.power.fx.api.model.OutputShape;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.Rational;
import com.power.fx.api.model.RollConvention;
import com.power.fx.api.model.RoundingSpec;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.model.Weighting;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.result.RateResult;
import com.power.fx.core.averaging.DefaultAveragingEngine;
import com.power.fx.core.averaging.Observation;
import com.power.fx.core.averaging.ObservationSet;
import com.power.fx.core.averaging.SeriesOutcome;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.curve.ForwardCurve;
import com.power.fx.core.curve.ForwardCurveBuilder;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.rate.RateQuote;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.testkit.fixtures.FxAssertions;
import com.power.fx.testkit.fixtures.GoldenReferenceData;
import com.power.fx.testkit.fixtures.GoldenSnapshots;
import com.power.fx.testkit.fixtures.VectorExpectations;
import com.power.fx.testkit.fixtures.VectorRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden vectors G01-G09 (functional spec S19.1), re-run for real against
 * the shipped {@code fx-testkit} harness ({@link VectorRunner}, {@link
 * GoldenReferenceData}) -- not a rubber-stamp of the Phase 2 shift-left
 * results (plan Task 3b.5).
 */
class GoldenArithmeticVectorTest {

    private static final VectorExpectations EXPECTED = VectorExpectations.load("/vectors/arithmetic-G01-G09.csv");
    private static final LocalDate FX_DATE = LocalDate.of(2026, 6, 5);

    private VectorRunner runner;

    @BeforeEach
    void setUp() {
        runner = new VectorRunner();
        runner.setTenant(GoldenReferenceData.TENANT);
    }

    private FxPolicy contractPolicy(List<String> sources) {
        return new FxPolicy(GoldenReferenceData.globalEnvelope("POL-G", "POL-G-v1"), "POL-G", 1, Leg.CONTRACT,
                DateRule.SPECIFIC_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, sources,
                new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
    }

    private RateResult rate(FxPolicy policy, String from, String to, LocalDate date) {
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, date, null, RunMode.AD_HOC, null,
                AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, date, null, null, null, null, List.of(), null), Map.of(), null, null, List.of(),
                new PolicyRef.Inline(policy), "req-golden");
        return runner.converter().rate(new RateRequest(context, new CurrencyCode(from), new CurrencyCode(to)));
    }

    @Test
    void g01_eurJpyMajorCross() {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(GoldenReferenceData.currency("EUR")).addCurrency(GoldenReferenceData.currency("USD"))
                .addCurrency(GoldenReferenceData.currency("JPY"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "USD"))
                .addPairConvention(GoldenReferenceData.convention("USD", "JPY"));
        runner.publishCatalogue(GoldenReferenceData.TENANT, b);
        runner.addFixing(fixing("ECB", "EUR", "USD", FX_DATE, "1.0850"));
        runner.addFixing(fixing("ECB", "USD", "JPY", FX_DATE, "149.20"));
        runner.publishFixings(GoldenReferenceData.TENANT);
        publishGoldenSnapshot("SNAP-G01");

        RateResult result = rate(contractPolicy(List.of("ECB")), "EUR", "JPY", FX_DATE);
        assertTrue(result.isSuccess(), () -> "G01 error: " + result.error());
        FxAssertions.assertDecimalEqualsAtScale(EXPECTED.expected("G01"), result.rate(), 4);
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }

    @Test
    void g02_gbpAudInverseLeg() {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(GoldenReferenceData.currency("GBP")).addCurrency(GoldenReferenceData.currency("USD"))
                .addCurrency(GoldenReferenceData.currency("AUD"))
                .addPairConvention(GoldenReferenceData.convention("GBP", "USD"))
                .addPairConvention(GoldenReferenceData.convention("AUD", "USD"));
        runner.publishCatalogue(GoldenReferenceData.TENANT, b);
        runner.addFixing(fixing("ECB", "GBP", "USD", FX_DATE, "1.2700"));
        runner.addFixing(fixing("ECB", "AUD", "USD", FX_DATE, "0.6600"));
        runner.publishFixings(GoldenReferenceData.TENANT);
        publishGoldenSnapshot("SNAP-G02");

        RateResult result = rate(contractPolicy(List.of("ECB")), "GBP", "AUD", FX_DATE);
        assertTrue(result.isSuccess(), () -> "G02 error: " + result.error());
        BigDecimal expected = new BigDecimal("1.2700").divide(new BigDecimal("0.6600"), FxMath.DECIMAL128);
        assertEquals(0, expected.setScale(9, RoundingMode.HALF_EVEN).compareTo(result.rate().setScale(9, RoundingMode.HALF_EVEN)));
    }

    @Test
    void g03_gbpToInrViaFixedFactorAndCross() {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(GoldenReferenceData.currency("GBP"))
                .addCurrency(GoldenReferenceData.currency("GBp", "GBP", 2))
                .addCurrency(GoldenReferenceData.currency("USD")).addCurrency(GoldenReferenceData.currency("INR"))
                .addFixedFactor(GoldenReferenceData.fixedFactor("GBp", "GBP", "0.01", FixedFactorKind.MINOR_UNIT, false))
                .addPairConvention(GoldenReferenceData.convention("GBP", "USD"))
                .addPairConvention(GoldenReferenceData.convention("USD", "INR"));
        runner.publishCatalogue(GoldenReferenceData.TENANT, b);
        runner.addFixing(fixing("ECB", "GBP", "USD", FX_DATE, "1.2700"));
        runner.addFixing(fixing("ECB", "USD", "INR", FX_DATE, "83.40"));
        runner.publishFixings(GoldenReferenceData.TENANT);
        publishGoldenSnapshot("SNAP-G03");

        RateResult result = rate(contractPolicy(List.of("ECB")), "GBp", "INR", FX_DATE);
        assertTrue(result.isSuccess(), () -> "G03 error: " + result.error());
        FxAssertions.assertDecimalEqualsAtScale(EXPECTED.expected("G03"), result.rate(), 5);
    }

    @Test
    void g04_forwardLinearPoints() {
        FxMath fxMath = runner.fxMath();
        CurrencyPair eurUsd = GoldenReferenceData.pair("EUR", "USD");
        MarketSnapshot snap = forwardSnapshot(eurUsd);
        PairConvention conv = forwardConvention(eurUsd, InterpolationMethod.LINEAR_POINTS);
        ForwardCurve curve = new ForwardCurveBuilder(fxMath).build(eurUsd, conv, snap, 512);

        LocalDate target = LocalDate.of(2026, 1, 5).plusDays(120);
        BigDecimal result = curve.outright(target, fxMath);
        FxAssertions.assertDecimalEqualsAtScale(EXPECTED.expected("G04"), result, 7);
    }

    @Test
    void g05_forwardLogLinearCarry_bitIdentical() {
        FxMath fxMath = runner.fxMath();
        CurrencyPair eurUsd = GoldenReferenceData.pair("EUR", "USD");
        MarketSnapshot snap = forwardSnapshot(eurUsd);
        PairConvention conv = forwardConvention(eurUsd, InterpolationMethod.LOG_LINEAR_CARRY);
        ForwardCurve curve = new ForwardCurveBuilder(fxMath).build(eurUsd, conv, snap, 512);

        LocalDate target = LocalDate.of(2026, 1, 5).plusDays(120);
        BigDecimal first = curve.outright(target, fxMath);
        BigDecimal second = curve.outright(target, fxMath);
        assertEquals(0, first.compareTo(second), "G05: repeated evaluation must be bit-identical");
        FxAssertions.assertDecimalEqualsAtScale(EXPECTED.expected("G05"), first, 7);
    }

    @Test
    void g06_partialPeriod() {
        DefaultAveragingEngine engine = new DefaultAveragingEngine();
        List<Observation> observations = new ArrayList<>();
        List<RateQuote> quotes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            observations.add(observation(i, Rational.of(1, 22)));
            quotes.add(rateQuote("1.0840", RateFinality.CONFIRMED));
        }
        for (int i = 10; i < 22; i++) {
            observations.add(observation(i, Rational.of(1, 22)));
            quotes.add(rateQuote("1.0872", RateFinality.ESTIMATED));
        }
        AveragingSpec spec = new AveragingSpec(AveragingMethod.RATE_AVERAGE, ObservationSetKind.EXPLICIT, null,
                Weighting.EQUAL, OutputShape.TOTAL, FxDateFromObservation.SAME_DATE, 0, false, List.of());

        SeriesOutcome outcome = engine.average(new ObservationSet(observations), quotes, null, BigDecimal.ONE, false,
                spec, runner.fxMath());
        FxAssertions.assertDecimalEqualsAtScale(EXPECTED.expected("G06"), outcome.averageRate(), 10);
        assertEquals(Rational.of(5, 11), outcome.confirmedPortion());
        assertEquals(RateFinality.ESTIMATED, outcome.finality());
    }

    @Test
    void g07_priceMatched() {
        DefaultAveragingEngine engine = new DefaultAveragingEngine();
        List<Observation> observations = fiveObservations();
        List<RateQuote> quotes = fiveQuotes();
        List<BigDecimal> prices = List.of(new BigDecimal("80"), new BigDecimal("81"), new BigDecimal("82"),
                new BigDecimal("83"), new BigDecimal("84"));
        AveragingSpec spec = new AveragingSpec(AveragingMethod.PRICE_MATCHED, ObservationSetKind.FROM_PRICING_SET, null,
                Weighting.FROM_PRICING_SET, OutputShape.TOTAL, FxDateFromObservation.SAME_DATE, 0, false, List.of());

        SeriesOutcome outcome = engine.average(new ObservationSet(observations), quotes, prices, null, true, spec,
                runner.fxMath());
        FxAssertions.assertDecimalEqualsAtScale(EXPECTED.expected("G07"), outcome.totalUnrounded(), 9);
    }

    @Test
    void g08_rateAverage_vs_g07_spread() {
        DefaultAveragingEngine engine = new DefaultAveragingEngine();
        List<Observation> observations = fiveObservations();
        List<RateQuote> quotes = fiveQuotes();
        List<BigDecimal> prices = List.of(new BigDecimal("80"), new BigDecimal("81"), new BigDecimal("82"),
                new BigDecimal("83"), new BigDecimal("84"));
        AveragingSpec spec = new AveragingSpec(AveragingMethod.RATE_AVERAGE, ObservationSetKind.FROM_PRICING_SET, null,
                Weighting.FROM_PRICING_SET, OutputShape.TOTAL, FxDateFromObservation.SAME_DATE, 0, false, List.of());

        SeriesOutcome outcome = engine.average(new ObservationSet(observations), quotes, prices, null, true, spec,
                runner.fxMath());
        FxAssertions.assertDecimalEqualsAtScale("1.09", outcome.averageRate(), 2);
        FxAssertions.assertDecimalEqualsAtScale(EXPECTED.expected("G08"), outcome.totalUnrounded(), 9);

        BigDecimal g07 = new BigDecimal(EXPECTED.expected("G07"));
        BigDecimal spread = g07.subtract(outcome.totalUnrounded().setScale(9, RoundingMode.HALF_EVEN));
        assertEquals(0, new BigDecimal("0.049862981").compareTo(spread.setScale(9, RoundingMode.HALF_EVEN)));
    }

    @Test
    void g09_bgnEurLegalPeg() {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(GoldenReferenceData.currency("BGN")).addCurrency(GoldenReferenceData.currency("EUR"))
                .addFixedFactor(GoldenReferenceData.fixedFactor("BGN", "EUR", "1.95583", FixedFactorKind.LEGAL_PEG, true));
        runner.publishCatalogue(GoldenReferenceData.TENANT, b);
        runner.publishFixings(GoldenReferenceData.TENANT);
        publishGoldenSnapshot("SNAP-G09");

        LocalDate dealDate = LocalDate.of(2026, 3, 1);
        RateResult result = rate(contractPolicy(List.of("ECB")), "BGN", "EUR", dealDate);
        assertTrue(result.isSuccess(), () -> "G09 error: " + result.error());
        FxAssertions.assertDecimalEquals(EXPECTED.expected("G09"), result.rate());
        assertEquals(RateFinality.CONFIRMED, result.finality());
        assertEquals(RateType.FIXED_FACTOR, result.rateType());
    }

    // --- helpers ---

    private void publishGoldenSnapshot(String id) {
        runner.publishSnapshot(GoldenSnapshots.eod(id, GoldenReferenceData.TENANT, FX_DATE,
                Instant.parse("2026-12-31T00:00:00Z")));
    }

    private FixingVersion fixing(String source, String base, String quote, LocalDate date, String value) {
        CurrencyPair pair = GoldenReferenceData.pair(base, quote);
        return new FixingVersion(Scope.TENANT, GoldenReferenceData.TENANT, source, pair, date, "16:00",
                new BigDecimal(value), FixingStatus.OFFICIAL, date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                source + "-" + pair.canonical() + "-" + date, null, date);
    }

    private MarketSnapshot forwardSnapshot(CurrencyPair pair) {
        SpotQuote spot = new SpotQuote(pair, new BigDecimal("1.0850"), LocalDate.of(2026, 1, 5));
        ForwardPillar p3m = new ForwardPillar("3M", LocalDate.of(2026, 4, 7), new BigDecimal("35.0"), null);
        ForwardPillar p6m = new ForwardPillar("6M", LocalDate.of(2026, 7, 7), new BigDecimal("68.0"), null);
        return new MarketSnapshot("SNAP-G04G05", Scope.TENANT, GoldenReferenceData.TENANT,
                com.power.fx.api.model.SnapshotKind.EOD, LocalDate.of(2026, 1, 2),
                Instant.parse("2026-01-02T18:00:00Z"), SignOffStatus.SIGNED_OFF, Map.of(pair, spot),
                Map.of(pair, List.of(p3m, p6m)), Map.of(), Map.of(pair, RightsSet.unrestricted()));
    }

    private PairConvention forwardConvention(CurrencyPair pair, InterpolationMethod interpolation) {
        return new PairConvention(GoldenReferenceData.globalEnvelope(pair.canonical(), pair.canonical() + "-fwd-v1"),
                pair, 4, new BigDecimal("10000"), 2, List.of("USNY", "TARGET"), null, ForwardMethod.POINTS,
                interpolation, new BigDecimal("2"), Map.of());
    }

    private Observation observation(int seq, Rational weight) {
        return new Observation(seq, LocalDate.of(2026, 1, seq + 1), LocalDate.of(2026, 1, seq + 1),
                LocalDate.of(2026, 1, seq + 1), weight, null, null, false);
    }

    private RateQuote rateQuote(String rate, RateFinality finality) {
        return new RateQuote(new BigDecimal(rate), RateType.FIXING, finality, List.of(), List.of(),
                RightsSet.unrestricted(), false, null, "SRC", null, null);
    }

    private List<Observation> fiveObservations() {
        return List.of(observation(1, Rational.of(1, 5)), observation(2, Rational.of(1, 5)),
                observation(3, Rational.of(1, 5)), observation(4, Rational.of(1, 5)), observation(5, Rational.of(1, 5)));
    }

    private List<RateQuote> fiveQuotes() {
        return List.of(rateQuote("1.10", RateFinality.CONFIRMED), rateQuote("1.08", RateFinality.CONFIRMED),
                rateQuote("1.12", RateFinality.CONFIRMED), rateQuote("1.10", RateFinality.CONFIRMED),
                rateQuote("1.05", RateFinality.CONFIRMED));
    }
}
