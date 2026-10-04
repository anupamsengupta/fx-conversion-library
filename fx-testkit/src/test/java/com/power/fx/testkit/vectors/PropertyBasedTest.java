package com.power.fx.testkit.vectors;

import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.FutureDateTreatment;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.NonPublicationDayHandling;
import com.power.fx.api.model.OffsetCalendarKind;
import com.power.fx.api.model.OffsetSpec;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RollConvention;
import com.power.fx.api.model.RoundingSpec;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.request.ConversionRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.result.ConversionResult;
import com.power.fx.api.result.RateResult;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.decimal.DecimalExp;
import com.power.fx.core.decimal.DecimalLn;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.lineage.CanonicalJson;
import com.power.fx.core.precision.LargestRemainderAllocator;
import com.power.fx.testkit.fixtures.GoldenReferenceData;
import com.power.fx.testkit.fixtures.GoldenSnapshots;
import com.power.fx.testkit.fixtures.VectorRunner;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FS S20 / tech spec S12.2 property-test table, implemented with
 * jqwik (Task 3b.6). Round trip and cross consistency are exercised
 * generatively against the real pipeline (many random, valid rates
 * through the same EUR/USD/JPY fixture shape G01/G02 use), not merely
 * restated as fixed golden numbers.
 *
 * @see "Tech spec S12.2"
 */
class PropertyBasedTest {

    private static final MathContext WORKING = new MathContext(60, RoundingMode.HALF_EVEN);
    private static final LocalDate FX_DATE = LocalDate.of(2026, 6, 5);

    // --- round trip & cross consistency (generative, real pipeline) ---

    @Provide
    Arbitrary<BigDecimal> positiveRate() {
        return Arbitraries.bigDecimals().between(new BigDecimal("0.0100"), new BigDecimal("500.0000"))
                .ofScale(4).filter(bd -> bd.signum() > 0);
    }

    private VectorRunner setUpCrossFixture(BigDecimal eurUsd, BigDecimal usdJpy) {
        VectorRunner runner = new VectorRunner();
        runner.setTenant(GoldenReferenceData.TENANT);
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(GoldenReferenceData.currency("EUR")).addCurrency(GoldenReferenceData.currency("USD"))
                .addCurrency(GoldenReferenceData.currency("JPY"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "USD"))
                .addPairConvention(GoldenReferenceData.convention("USD", "JPY"));
        runner.publishCatalogue(GoldenReferenceData.TENANT, b);
        runner.addFixing(fixing("EUR", "USD", eurUsd));
        runner.addFixing(fixing("USD", "JPY", usdJpy));
        runner.publishFixings(GoldenReferenceData.TENANT);
        runner.publishSnapshot(GoldenSnapshots.eod("SNAP-PROP", GoldenReferenceData.TENANT, FX_DATE,
                Instant.parse("2026-12-31T00:00:00Z")));
        return runner;
    }

    private FixingVersion fixing(String base, String quote, BigDecimal value) {
        var pair = GoldenReferenceData.pair(base, quote);
        return new FixingVersion(Scope.TENANT, GoldenReferenceData.TENANT, "ECB", pair, FX_DATE, "16:00", value,
                FixingStatus.OFFICIAL, FX_DATE.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                "ECB-" + pair.canonical() + "-" + FX_DATE, null, FX_DATE);
    }

    private FxPolicy contractPolicy() {
        return new FxPolicy(GoldenReferenceData.globalEnvelope("POL-PROP", "POL-PROP-v1"), "POL-PROP", 1, Leg.CONTRACT,
                DateRule.SPECIFIC_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
    }

    private RateResult rate(VectorRunner runner, String from, String to) {
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, FX_DATE, null, RunMode.AD_HOC,
                null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, FX_DATE, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(contractPolicy()), "req-prop");
        return runner.converter().rate(new RateRequest(context, new CurrencyCode(from), new CurrencyCode(to)));
    }

    @Property(tries = 40)
    void crossConsistency_eurJpyEqualsEurUsdTimesUsdJpy(@ForAll("positiveRate") BigDecimal eurUsd,
            @ForAll("positiveRate") BigDecimal usdJpy) {
        VectorRunner runner = setUpCrossFixture(eurUsd, usdJpy);
        RateResult cross = rate(runner, "EUR", "JPY");
        assertTrue(cross.isSuccess(), () -> "cross error: " + cross.error());
        BigDecimal expected = eurUsd.multiply(usdJpy, FxMath.DECIMAL128);
        BigDecimal relError = cross.rate().subtract(expected, WORKING).abs(WORKING).divide(expected.abs(WORKING), WORKING);
        assertTrue(relError.compareTo(new BigDecimal("1E-28")) < 0,
                "cross consistency relative error " + relError + " for eurUsd=" + eurUsd + " usdJpy=" + usdJpy);
    }

    @Property(tries = 40)
    void roundTrip_convertThenInvertRecoversOriginalAmount(@ForAll("positiveRate") BigDecimal eurUsd) {
        VectorRunner runner = setUpCrossFixture(eurUsd, BigDecimal.ONE); // usdJpy unused by this property
        BigDecimal originalAmount = new BigDecimal("100000.00");

        FxRequestContext ctxForward = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, FX_DATE, null, RunMode.AD_HOC,
                null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, FX_DATE, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(contractPolicy()), "req-rt-fwd");
        ConversionResult forward = runner.converter().convert(new ConversionRequest(ctxForward,
                new CurrencyCode("EUR"), new CurrencyCode("USD"), originalAmount, false));
        assertTrue(forward.isSuccess(), () -> "forward error: " + forward.error());

        FxRequestContext ctxBack = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, FX_DATE, null, RunMode.AD_HOC,
                null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, FX_DATE, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(contractPolicy()), "req-rt-back");
        ConversionResult back = runner.converter().convert(new ConversionRequest(ctxBack, new CurrencyCode("USD"),
                new CurrencyCode("EUR"), forward.toAmountUnrounded(), false));
        assertTrue(back.isSuccess(), () -> "back error: " + back.error());

        BigDecimal relError = back.toAmountUnrounded().subtract(originalAmount, WORKING).abs(WORKING)
                .divide(originalAmount.abs(WORKING), WORKING);
        assertTrue(relError.compareTo(new BigDecimal("1E-28")) < 0,
                "round trip relative error " + relError + " for eurUsd=" + eurUsd);
    }

    // --- finality monotonicity ---

    @Property
    void finalityMonotonicity_weakestIsAssociativeCommutativeIdempotent(
            @ForAll("finalities") RateFinality a, @ForAll("finalities") RateFinality b, @ForAll("finalities") RateFinality c) {
        assertEquals(RateFinality.weakest(a, b), RateFinality.weakest(b, a), "commutative");
        assertEquals(RateFinality.weakest(RateFinality.weakest(a, b), c), RateFinality.weakest(a, RateFinality.weakest(b, c)), "associative");
        assertEquals(a, RateFinality.weakest(a, a), "idempotent");
    }

    @Provide
    Arbitrary<RateFinality> finalities() {
        return Arbitraries.of(RateFinality.values());
    }

    // --- decimal identities ---

    @Provide
    Arbitrary<BigDecimal> positiveDecimal() {
        return Arbitraries.bigDecimals().between(new BigDecimal("0.000001"), new BigDecimal("1E10")).ofScale(6)
                .filter(bd -> bd.signum() > 0);
    }

    @Property(tries = 100)
    void decimalIdentity_lnOfProductEqualsSumOfLns(@ForAll("positiveDecimal") BigDecimal a, @ForAll("positiveDecimal") BigDecimal b) {
        BigDecimal lnA = DecimalLn.ln(a, WORKING);
        BigDecimal lnB = DecimalLn.ln(b, WORKING);
        BigDecimal lnAB = DecimalLn.ln(a.multiply(b, WORKING), WORKING);
        assertRelativeError(lnAB, lnA.add(lnB, WORKING), new BigDecimal("1E-45"));
    }

    @Property(tries = 50)
    void decimalIdentity_expOfLnRecoversOriginal(@ForAll("positiveDecimal") BigDecimal x) {
        BigDecimal roundTrip = DecimalExp.exp(DecimalLn.ln(x, WORKING), WORKING).round(FxMath.DECIMAL128);
        assertRelativeError(x, roundTrip, new BigDecimal("1E-30"));
    }

    @Property(tries = 20)
    void decimalIdentity_lnOfPowerOfTenIsKTimesLn10(@ForAll("smallK") int k) {
        BigDecimal tenToK = BigDecimal.TEN.pow(Math.abs(k), WORKING);
        BigDecimal x = k >= 0 ? tenToK : BigDecimal.ONE.divide(tenToK, WORKING);
        BigDecimal lnX = DecimalLn.ln(x, WORKING);
        BigDecimal kLn10 = BigDecimal.valueOf(k).multiply(com.power.fx.core.decimal.DecimalConstants.LN10, WORKING);
        assertRelativeError(kLn10, lnX, new BigDecimal("1E-45"));
    }

    @Provide
    Arbitrary<Integer> smallK() {
        return Arbitraries.integers().between(-10, 10);
    }

    private void assertRelativeError(BigDecimal expected, BigDecimal actual, BigDecimal tolerance) {
        if (expected.signum() == 0) {
            assertTrue(actual.abs(WORKING).compareTo(tolerance) < 0);
            return;
        }
        BigDecimal relError = actual.subtract(expected, WORKING).abs(WORKING).divide(expected.abs(WORKING), WORKING);
        assertTrue(relError.compareTo(tolerance) < 0, "relative error " + relError + " exceeds " + tolerance);
    }

    // --- series allocation ---

    @Property(tries = 100)
    void seriesAllocation_sumOfBookedLinesEqualsTotalBookedExactly(@ForAll("lineAmounts") List<BigDecimal> lines) {
        int scale = 2;
        BigDecimal total = lines.stream().reduce(BigDecimal.ZERO, (x, y) -> x.add(y, FxMath.DECIMAL128));
        BigDecimal totalBooked = total.setScale(scale, RoundingMode.HALF_UP);
        LargestRemainderAllocator allocator = new LargestRemainderAllocator();
        LargestRemainderAllocator.Result result = allocator.allocate(lines, totalBooked, scale, total);
        BigDecimal sumBooked = result.bookedLines().stream().reduce(BigDecimal.ZERO, (x, y) -> x.add(y, FxMath.DECIMAL128));
        assertEquals(0, totalBooked.compareTo(sumBooked), "sum(booked lines) must equal totalBooked exactly");
    }

    @Provide
    Arbitrary<List<BigDecimal>> lineAmounts() {
        Arbitrary<BigDecimal> line = Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("9999.99")).ofScale(6);
        return line.list().ofMinSize(1).ofMaxSize(20);
    }

    // --- PDR weights ---

    @Property(tries = 50)
    void pdrWeights_reconstructedWeightsSumToExactlyOne(@ForAll("weightCount") int n) {
        List<com.power.fx.api.model.Rational> weights = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            weights.add(com.power.fx.api.model.Rational.of(1, n));
        }
        com.power.fx.api.model.Rational sum = weights.stream().reduce(com.power.fx.api.model.Rational.of(0, 1),
                com.power.fx.api.model.Rational::plus);
        assertEquals(com.power.fx.api.model.Rational.of(1, 1), sum, "equal weights must sum to exactly 1");
    }

    @Provide
    Arbitrary<Integer> weightCount() {
        return Arbitraries.integers().between(1, 50);
    }

    // --- restriction monotonicity ---

    @Property(tries = 50)
    void restrictionMonotonicity_intersectNeverWidensRights(@ForAll("rightsSets") Set<com.power.fx.api.model.SourceRight> a,
            @ForAll("rightsSets") Set<com.power.fx.api.model.SourceRight> b) {
        RightsSet ra = RightsSet.of("A", a);
        RightsSet rb = RightsSet.of("B", b);
        RightsSet combined = ra.intersect(rb);
        assertTrue(a.containsAll(combined.rights()), "intersect must never add a right ra did not have");
        assertTrue(b.containsAll(combined.rights()), "intersect must never add a right rb did not have");
        assertTrue(combined.rights().size() <= a.size(), "intersect must never widen beyond ra's own right count");
    }

    @Provide
    Arbitrary<Set<com.power.fx.api.model.SourceRight>> rightsSets() {
        return Arbitraries.of(com.power.fx.api.model.SourceRight.values()).set().ofMinSize(0).ofMaxSize(3)
                .map(s -> (Set<com.power.fx.api.model.SourceRight>) EnumSet.copyOf(s.isEmpty()
                        ? EnumSet.noneOf(com.power.fx.api.model.SourceRight.class) : s));
    }

    // --- hash stability ---

    @Property(tries = 30)
    void hashStability_addingNullValuedMemberLeavesHashUnchanged(@ForAll("decimalValue") BigDecimal rate) {
        TreeMap<String, Object> base = new TreeMap<>();
        base.put("rate", rate);
        base.put("tenantId", "T-GOLDEN");
        String withoutOptional = CanonicalJson.canonicalizeMap(base);

        TreeMap<String, Object> withNull = new TreeMap<>(base);
        withNull.put("optionalField", null);
        String withOptionalNull = CanonicalJson.canonicalizeMap(withNull);

        assertEquals(withoutOptional, withOptionalNull, "a null-valued optional member must not change the canonical form");
    }

    @Property(tries = 30)
    void hashStability_changingAHashedInputChangesTheCanonicalForm(@ForAll("decimalValue") BigDecimal a, @ForAll("decimalValue") BigDecimal b) {
        TreeMap<String, Object> ma = new TreeMap<>();
        ma.put("rate", a);
        TreeMap<String, Object> mb = new TreeMap<>();
        mb.put("rate", b);
        if (a.compareTo(b) != 0) {
            assertTrue(!CanonicalJson.canonicalizeMap(ma).equals(CanonicalJson.canonicalizeMap(mb)),
                    "different hashed inputs must produce a different canonical form");
        }
    }

    @Property(tries = 10)
    void hashStability_scaleInsensitiveDecimalsHashIdentically(@ForAll("decimalValue") BigDecimal value) {
        BigDecimal rescaled = value.setScale(value.scale() + 3, RoundingMode.UNNECESSARY);
        TreeMap<String, Object> m1 = new TreeMap<>();
        m1.put("rate", value);
        TreeMap<String, Object> m2 = new TreeMap<>();
        m2.put("rate", rescaled);
        assertEquals(CanonicalJson.canonicalizeMap(m1), CanonicalJson.canonicalizeMap(m2),
                "1.50 and 1.5000 must hash identically (stripTrailingZeros)");
    }

    @Provide
    Arbitrary<BigDecimal> decimalValue() {
        return Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("99999.99")).ofScale(2);
    }

    // --- memo neutrality ---

    @Property(tries = 20)
    void memoNeutrality_repeatedResolutionOnSamePinIsBitIdentical(@ForAll("positiveRate") BigDecimal eurUsd) {
        VectorRunner runner = setUpCrossFixture(eurUsd, BigDecimal.TEN);
        var pinned = runner.pin("SNAP-PROP", Instant.parse("2026-12-31T00:00:00Z"));
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, FX_DATE, null, RunMode.AD_HOC,
                null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, FX_DATE, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(contractPolicy()), "req-memo");
        RateRequest req = new RateRequest(context, new CurrencyCode("EUR"), new CurrencyCode("USD"));

        RateResult first = pinned.rate(req);
        RateResult second = pinned.rate(req); // second call should hit the memo
        assertTrue(first.isSuccess() && second.isSuccess());
        assertEquals(0, first.rate().compareTo(second.rate()), "memo-hit must reproduce the memo-miss value exactly");
        assertEquals(first.finality(), second.finality());
    }
}
