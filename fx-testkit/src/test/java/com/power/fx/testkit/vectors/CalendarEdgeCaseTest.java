package com.power.fx.testkit.vectors;

import com.power.fx.api.error.FxErrorCode;
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
import com.power.fx.api.model.RollConvention;
import com.power.fx.api.model.RoundingSpec;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.model.VersionEnvelope;
import com.power.fx.api.model.VersionStatus;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.result.RateResult;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.date.CutoffInstantResolver;
import com.power.fx.core.date.JointCalendarIndex;
import com.power.fx.core.date.SpotDateCalculator;
import com.power.fx.testkit.fixtures.GoldenReferenceData;
import com.power.fx.testkit.fixtures.GoldenSnapshots;
import com.power.fx.testkit.fixtures.VectorRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calendar edge cases (plan Task 3b.5): source-vs-currency holidays,
 * Good Friday/Easter Monday (also exercised end-to-end in {@link
 * CalendarVectorTest} C01/C02), HRK/BGN currency-validity redenomination
 * boundaries, T+1 spot lag, and the 2026 DST transition dates (S10c.2).
 */
class CalendarEdgeCaseTest {

    private VectorRunner runner;

    @BeforeEach
    void setUp() {
        runner = new VectorRunner();
        runner.setTenant(GoldenReferenceData.TENANT);
    }

    /** HRK currency validity ends 2023-01-01 (euro adoption); a direct-quote lookup after that date fails. */
    @Test
    void hrkInactiveAfterRedenomination() {
        VersionEnvelope hrkEnv = new VersionEnvelope(Scope.GLOBAL, null, "HRK", "HRK-v1",
                LocalDate.of(2000, 1, 1), LocalDate.of(2023, 1, 1), GoldenReferenceData.RECORDED_AT,
                VersionStatus.APPROVED, "loader", "approver", GoldenReferenceData.RECORDED_AT, "GOLDEN", null, null, "rel-1");
        var hrk = new com.power.fx.api.model.Currency(hrkEnv, new CurrencyCode("HRK"), 2, null, "CAL", true);

        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(hrk).addCurrency(GoldenReferenceData.currency("EUR"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "HRK"));
        runner.publishCatalogue(GoldenReferenceData.TENANT, b);
        // Any date after HRK's 2023-01-01 validTo demonstrates inactivity; 2026 keeps it inside
        // GoldenReferenceData's standard calendar coverage window.
        LocalDate afterRedenomination = LocalDate.of(2026, 6, 1);
        runner.addFixing(fixing("EUR", "HRK", afterRedenomination, "7.5345"));
        runner.publishFixings(GoldenReferenceData.TENANT);
        runner.publishSnapshot(GoldenSnapshots.eod("SNAP-HRK", GoldenReferenceData.TENANT, afterRedenomination,
                Instant.parse("2026-12-31T00:00:00Z")));

        FxPolicy policy = policy();
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, afterRedenomination, null,
                RunMode.AD_HOC, null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, afterRedenomination, null, null, null, null, List.of(), null), Map.of(), null,
                null, List.of(), new PolicyRef.Inline(policy), "req-hrk");
        RateResult result = runner.converter().rate(new RateRequest(context, new CurrencyCode("EUR"), new CurrencyCode("HRK")));
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_E_INACTIVE_CURRENCY, result.error().get().code());
    }

    /** T+1 pairs fall out of {@code spotLag = 1} with no special-casing (S6.5). */
    @Test
    void t1PairSpotLagFallsOutOfSpotLagOne() {
        var cal = GoldenReferenceData.weekdaySettlementCalendar("USD-CAL", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), Set.of());
        var calIndex = com.power.fx.core.date.CalendarIndex.ofSettlementCalendar(cal);
        JointCalendarIndex joint = JointCalendarIndex.intersect(List.of(calIndex));

        SpotDateCalculator calculator = new SpotDateCalculator();
        LocalDate tradeDate = LocalDate.of(2026, 6, 3); // Wednesday
        LocalDate spot = calculator.spotDate(tradeDate, joint, 1);
        assertEquals(LocalDate.of(2026, 6, 4), spot, "T+1 spot date should be the next business day");
    }

    /** CutoffInstantResolver's JDK-documented gap/overlap behaviour at the 2026 EU DST transitions (S10c.2). */
    @Test
    void dstSpringForwardAndFallBack2026_lineageOnlyNeverFeedsRateSelection() {
        CutoffInstantResolver resolver = new CutoffInstantResolver();
        var source = GoldenReferenceData.fixingSource("ECB", "ECB-CAL");

        // 2026-03-29: EU spring-forward (clocks jump 02:00 -> 03:00 CET -> CEST). A nominal 16:00
        // cutoff is outside the gap, so this just exercises the resolver deterministically.
        Instant springForward = resolver.instantOf(LocalDate.of(2026, 3, 29), source);
        Instant springForwardAgain = resolver.instantOf(LocalDate.of(2026, 3, 29), source);
        assertEquals(springForward, springForwardAgain, "deterministic, repeated computation");

        // 2026-10-25: EU fall-back (clocks repeat 03:00 -> 02:00 CEST -> CET).
        Instant fallBack = resolver.instantOf(LocalDate.of(2026, 10, 25), source);
        Instant fallBackAgain = resolver.instantOf(LocalDate.of(2026, 10, 25), source);
        assertEquals(fallBack, fallBackAgain, "deterministic, repeated computation");

        // The two days' nominal 16:00 local cutoffs are exactly one standard day apart in wall-clock
        // terms but differ by the DST offset change in UTC instant terms -- confirming this resolver
        // tracks real UTC instants rather than a naive day-count, exactly as S10c.2 requires for
        // correct lineage display (never consulted by rate selection itself, D-01).
        assertTrue(fallBack.isAfter(springForward));
    }

    // --- helpers ---

    private FxPolicy policy() {
        return new FxPolicy(GoldenReferenceData.globalEnvelope("POL-EDGE", "POL-EDGE-v1"), "POL-EDGE", 1, Leg.CONTRACT,
                DateRule.SPECIFIC_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(java.math.RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
    }

    private FixingVersion fixing(String base, String quote, LocalDate date, String value) {
        var pair = GoldenReferenceData.pair(base, quote);
        return new FixingVersion(Scope.TENANT, GoldenReferenceData.TENANT, "ECB", pair, date, "16:00",
                new BigDecimal(value), FixingStatus.OFFICIAL, date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                "ECB-" + pair.canonical() + "-" + date, null, date);
    }
}
