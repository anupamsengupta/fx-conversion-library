package com.power.fx.core.rate;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixedFactorKind;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FixingVersionPolicy;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.api.model.SourceRight;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.api.model.VersionEnvelope;
import com.power.fx.api.model.VersionStatus;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.cache.FixingSeries;
import com.power.fx.core.cache.FixingView;
import com.power.fx.core.cache.InMemoryMarketSnapshotStore;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.cache.SeriesKey;
import com.power.fx.core.curve.ForwardCurveCache;
import com.power.fx.core.curve.SnapshotForwardCurveCache;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.DefaultEntitlementResolver;
import com.power.fx.core.entitlement.FilteredSources;
import com.power.fx.core.memo.ResolutionMemo;
import com.power.fx.core.pair.DefaultPairResolver;
import com.power.fx.core.pair.PairRoute;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.core.snapshot.PinnedState;
import com.power.fx.core.testsupport.TestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden vectors G01, G02, G03, G09, C04, driven directly against {@link
 * DefaultPairResolver} + {@link DefaultRateSelector} (the full pipeline,
 * {@code DefaultFxConverter}, does not exist until Task 2.18).
 */
class RateSelectionVectorTest {

    private static final String TENANT = "TENANT-TEST";
    private static final Instant CUT = Instant.parse("2026-06-10T00:00:00Z");
    private static final LocalDate FX_DATE = LocalDate.of(2026, 6, 5);
    private final FxMath fxMath = new FxMath(60);

    private Currency currency(String code, String major) {
        return new Currency(TestFixtures.globalEnvelope(code, code + "-v1"), new CurrencyCode(code), major == null ? 2 : 2,
                major == null ? null : new CurrencyCode(major), "CAL", true);
    }

    private PairConvention convention(String base, String quote) {
        CurrencyPair p = TestFixtures.pair(base, quote);
        return new PairConvention(TestFixtures.globalEnvelope(p.canonical(), p.canonical() + "-v1"), p, 4,
                new BigDecimal("10000"), 2, List.of(), null, ForwardMethod.POINTS, InterpolationMethod.LOG_LINEAR_CARRY,
                new BigDecimal("2"), Map.of());
    }

    private SourceEntitlement entitlement(String sourceCode) {
        return new SourceEntitlement(TestFixtures.tenantEnvelope(TENANT, sourceCode, sourceCode + "-v1"), TENANT,
                sourceCode, java.util.Set.of(SourceRight.VALUATION, SourceRight.DISPLAY));
    }

    private FixingVersion fixing(CurrencyPair pair, String source, LocalDate date, BigDecimal value) {
        return new FixingVersion(Scope.TENANT, TENANT, source, pair, date, "16:00", value, FixingStatus.OFFICIAL,
                date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), source + "-" + pair.canonical() + "-" + date, null, date);
    }

    @Test
    void g01_eurJpyViaMajorCross() {
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        CurrencyPair usdJpy = TestFixtures.pair("USD", "JPY");
        ReferenceCatalogue tenant = new CatalogueBuilder()
                .addCurrency(currency("EUR", null)).addCurrency(currency("USD", null)).addCurrency(currency("JPY", null))
                .addPairConvention(convention("EUR", "USD")).addPairConvention(convention("USD", "JPY"))
                .addEntitlement(entitlement("ECB"))
                .build(null, 1);
        FixingView fixings = new FixingView(Map.of(
                new SeriesKey("ECB", eurUsd, "16:00"), FixingSeries.of(List.of(fixing(eurUsd, "ECB", FX_DATE, new BigDecimal("1.0850")))),
                new SeriesKey("ECB", usdJpy, "16:00"), FixingSeries.of(List.of(fixing(usdJpy, "ECB", FX_DATE, new BigDecimal("149.20"))))
        ), null, 1);

        BigDecimal result = resolveRate(tenant, fixings, "EUR", "JPY", List.of("ECB"));
        assertEquals(0, new BigDecimal("161.8820").compareTo(result.setScale(4, java.math.RoundingMode.HALF_EVEN)));
    }

    @Test
    void g02_gbpAudViaInverse() {
        CurrencyPair gbpUsd = TestFixtures.pair("GBP", "USD");
        CurrencyPair audUsd = TestFixtures.pair("AUD", "USD");
        ReferenceCatalogue tenant = new CatalogueBuilder()
                .addCurrency(currency("GBP", null)).addCurrency(currency("USD", null)).addCurrency(currency("AUD", null))
                .addPairConvention(convention("GBP", "USD")).addPairConvention(convention("AUD", "USD"))
                .addEntitlement(entitlement("ECB"))
                .build(null, 1);
        FixingView fixings = new FixingView(Map.of(
                new SeriesKey("ECB", gbpUsd, "16:00"), FixingSeries.of(List.of(fixing(gbpUsd, "ECB", FX_DATE, new BigDecimal("1.2700")))),
                new SeriesKey("ECB", audUsd, "16:00"), FixingSeries.of(List.of(fixing(audUsd, "ECB", FX_DATE, new BigDecimal("0.6600"))))
        ), null, 1);

        BigDecimal result = resolveRate(tenant, fixings, "GBP", "AUD", List.of("ECB"));
        BigDecimal expected = new BigDecimal("1.2700").divide(new BigDecimal("0.6600"), FxMath.DECIMAL128);
        assertEquals(0, expected.setScale(9, java.math.RoundingMode.HALF_EVEN).compareTo(result.setScale(9, java.math.RoundingMode.HALF_EVEN)));
    }

    @Test
    void g03_gbpToInrViaFixedFactorAndCross() {
        CurrencyPair gbpUsd = TestFixtures.pair("GBP", "USD");
        CurrencyPair usdInr = TestFixtures.pair("USD", "INR");
        FixedFactor gbpToGbP = new FixedFactor(TestFixtures.globalEnvelope("GBp>GBP", "GBp>GBP-v1"), new CurrencyCode("GBp"),
                new CurrencyCode("GBP"), new BigDecimal("0.01"), FixedFactorKind.MINOR_UNIT, false);
        ReferenceCatalogue tenant = new CatalogueBuilder()
                .addCurrency(currency("GBP", null)).addCurrency(new Currency(TestFixtures.globalEnvelope("GBp", "GBp-v1"),
                        new CurrencyCode("GBp"), 2, new CurrencyCode("GBP"), "CAL", true))
                .addCurrency(currency("USD", null)).addCurrency(currency("INR", null))
                .addFixedFactor(gbpToGbP)
                .addPairConvention(convention("GBP", "USD")).addPairConvention(convention("USD", "INR"))
                .addEntitlement(entitlement("ECB"))
                .build(null, 1);
        FixingView fixings = new FixingView(Map.of(
                new SeriesKey("ECB", gbpUsd, "16:00"), FixingSeries.of(List.of(fixing(gbpUsd, "ECB", FX_DATE, new BigDecimal("1.2700")))),
                new SeriesKey("ECB", usdInr, "16:00"), FixingSeries.of(List.of(fixing(usdInr, "ECB", FX_DATE, new BigDecimal("83.40"))))
        ), null, 1);

        BigDecimal result = resolveRate(tenant, fixings, "GBp", "INR", List.of("ECB"));
        assertEquals(0, new BigDecimal("1.05918").compareTo(result.setScale(5, java.math.RoundingMode.HALF_EVEN)));
    }

    @Test
    void g09_bgnEurLegalPeg() {
        FixedFactor peg = new FixedFactor(TestFixtures.globalEnvelope("BGN>EUR", "BGN>EUR-v1"), new CurrencyCode("BGN"),
                new CurrencyCode("EUR"), new BigDecimal("1.95583"), FixedFactorKind.LEGAL_PEG, true);
        ReferenceCatalogue tenant = new CatalogueBuilder()
                .addCurrency(currency("BGN", null)).addCurrency(currency("EUR", null))
                .addFixedFactor(peg)
                .addEntitlement(entitlement("ECB"))
                .build(null, 1);
        FixingView fixings = FixingView.empty(1);

        BigDecimal result = resolveRate(tenant, fixings, "BGN", "EUR", List.of("ECB"));
        assertEquals(0, new BigDecimal("1.95583").compareTo(result));
    }

    @Test
    void c04_wmrOutageFallsBackToEcb() {
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        ReferenceCatalogue tenant = new CatalogueBuilder()
                .addCurrency(currency("EUR", null)).addCurrency(currency("USD", null))
                .addPairConvention(convention("EUR", "USD"))
                .addEntitlement(entitlement("WMR")).addEntitlement(entitlement("ECB"))
                .build(null, 1);
        // WMR has no fixing on FX_DATE (outage); ECB does.
        FixingView fixings = new FixingView(Map.of(
                new SeriesKey("ECB", eurUsd, "16:00"), FixingSeries.of(List.of(fixing(eurUsd, "ECB", FX_DATE, new BigDecimal("1.0850"))))
        ), null, 1);

        com.power.fx.api.model.FxPolicy policy = policyWithFallback(List.of("WMR", "ECB"));
        PinnedState state = pinnedState(tenant, fixings);
        ResolvedPolicy rp = new ResolvedPolicy(policy, false, "POL", 1);
        DefaultPairResolver pairResolver = new DefaultPairResolver(fxMath);
        PairRoute route = pairResolver.route(new CurrencyCode("EUR"), new CurrencyCode("USD"), rp, state, FX_DATE);
        ForwardCurveCache curveCache = new SnapshotForwardCurveCache(fxMath, 512);
        DefaultFallbackChainRunner fallback = new DefaultFallbackChainRunner();
        DefaultRateSelector selector = new DefaultRateSelector(fxMath, curveCache, fallback);
        RateQuote quote = selector.select(route, FX_DATE, LocalDate.of(2026, 6, 20), Purpose.CONTRACT_SETTLEMENT, rp, state);
        assertEquals(0, new BigDecimal("1.0850").compareTo(quote.rate()));
        assertEquals(com.power.fx.api.model.RateFinality.ESTIMATED, quote.finality());
    }

    // --- helpers ---

    private BigDecimal resolveRate(ReferenceCatalogue tenant, FixingView fixings, String from, String to, List<String> sources) {
        PinnedState state = pinnedState(tenant, fixings);
        com.power.fx.api.model.FxPolicy policy = policyWithSources(sources);
        ResolvedPolicy rp = new ResolvedPolicy(policy, false, "POL", 1);
        DefaultPairResolver pairResolver = new DefaultPairResolver(fxMath);
        PairRoute route = pairResolver.route(new CurrencyCode(from), new CurrencyCode(to), rp, state, FX_DATE);
        ForwardCurveCache curveCache = new SnapshotForwardCurveCache(fxMath, 512);
        DefaultFallbackChainRunner fallback = new DefaultFallbackChainRunner();
        DefaultRateSelector selector = new DefaultRateSelector(fxMath, curveCache, fallback);
        RateQuote quote = selector.select(route, FX_DATE, LocalDate.of(2026, 6, 20), Purpose.CONTRACT_SETTLEMENT, rp, state);
        return quote.rate();
    }

    private PinnedState pinnedState(ReferenceCatalogue tenant, FixingView fixings) {
        MarketSnapshot snapshot = new MarketSnapshot("SNAP-1", Scope.TENANT, TENANT, SnapshotKind.EOD,
                FX_DATE, CUT, SignOffStatus.SIGNED_OFF, Map.of(), Map.of(), Map.of(), Map.of());
        return new PinnedState(TENANT, null, tenant, fixings, snapshot, CUT, 1, 1, new ResolutionMemo(100));
    }

    private com.power.fx.api.model.FxPolicy policyWithSources(List<String> sources) {
        return new com.power.fx.api.model.FxPolicy(TestFixtures.globalEnvelope("POL-1", "POL-1-v1"), "POL-1", 1,
                com.power.fx.api.model.Leg.CONTRACT, com.power.fx.api.model.DateRule.TRADE_DATE,
                new com.power.fx.api.model.OffsetSpec(0, com.power.fx.api.model.OffsetCalendarKind.CALENDAR_DAYS, null),
                com.power.fx.api.model.NonPublicationDayHandling.USE_PREVIOUS, com.power.fx.api.model.RollConvention.MODIFIED_FOLLOWING,
                sources, new FixingVersionSelection.FirstOfficial(), com.power.fx.api.model.FutureDateTreatment.FORWARD,
                com.power.fx.api.model.SpotAdjustment.NONE, null, List.of(), false, null,
                new com.power.fx.api.model.RoundingSpec(java.math.RoundingMode.HALF_UP, null, null, null), null,
                com.power.fx.api.model.EstimatedEventHandling.USE_ESTIMATE, true);
    }

    private com.power.fx.api.model.FxPolicy policyWithFallback(List<String> sources) {
        return new com.power.fx.api.model.FxPolicy(TestFixtures.globalEnvelope("POL-2", "POL-2-v1"), "POL-2", 1,
                com.power.fx.api.model.Leg.CONTRACT, com.power.fx.api.model.DateRule.TRADE_DATE,
                new com.power.fx.api.model.OffsetSpec(0, com.power.fx.api.model.OffsetCalendarKind.CALENDAR_DAYS, null),
                com.power.fx.api.model.NonPublicationDayHandling.USE_PREVIOUS, com.power.fx.api.model.RollConvention.MODIFIED_FOLLOWING,
                sources, new FixingVersionSelection.FirstOfficial(), com.power.fx.api.model.FutureDateTreatment.FORWARD,
                com.power.fx.api.model.SpotAdjustment.NONE, null,
                List.of(new com.power.fx.api.model.FallbackStep(com.power.fx.api.model.FallbackStepKind.ALT_SOURCE, 1)),
                false, null, new com.power.fx.api.model.RoundingSpec(java.math.RoundingMode.HALF_UP, null, null, null), null,
                com.power.fx.api.model.EstimatedEventHandling.USE_ESTIMATE, true);
    }
}
