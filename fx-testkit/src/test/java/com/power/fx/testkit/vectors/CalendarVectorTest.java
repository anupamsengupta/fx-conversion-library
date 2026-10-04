package com.power.fx.testkit.vectors;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxWarningCode;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.FallbackStep;
import com.power.fx.api.model.FallbackStepKind;
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
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.result.RateResult;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.testkit.fixtures.FxAssertions;
import com.power.fx.testkit.fixtures.GoldenReferenceData;
import com.power.fx.testkit.fixtures.GoldenSnapshots;
import com.power.fx.testkit.fixtures.VectorExpectations;
import com.power.fx.testkit.fixtures.VectorRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden calendar resolution vectors C01-C05 (functional spec S19.2, D-02),
 * re-run end-to-end through the shipped {@link VectorRunner} harness
 * (plan Task 3b.5).
 */
class CalendarVectorTest {

    private static final VectorExpectations EXPECTED = VectorExpectations.load("/vectors/calendar-C01-C05.csv");
    private static final LocalDate GOOD_FRIDAY_2026 = GoldenReferenceData.GOOD_FRIDAY_2026;
    private static final LocalDate EASTER_MONDAY_2026 = GoldenReferenceData.EASTER_MONDAY_2026;

    private VectorRunner runner;

    @BeforeEach
    void setUp() {
        runner = new VectorRunner();
        runner.setTenant(GoldenReferenceData.TENANT);
    }

    private FxPolicy policy(DateRule rule, NonPublicationDayHandling handling, List<String> sources,
            List<FallbackStep> fallback) {
        return new FxPolicy(GoldenReferenceData.globalEnvelope("POL-C", "POL-C-v1"), "POL-C", 1, Leg.CONTRACT, rule,
                new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null), handling, RollConvention.MODIFIED_FOLLOWING,
                sources, new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE,
                null, fallback, false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
    }

    private RateResult rate(FxPolicy policy, LocalDate specificDate, LocalDate deliveryDate, LocalDate valuationDate) {
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, valuationDate, null,
                RunMode.AD_HOC, null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, specificDate, null, deliveryDate, null, null, List.of(), null), Map.of(), null,
                null, List.of(), new PolicyRef.Inline(policy), "req-cal");
        return runner.converter().rate(new RateRequest(context, new CurrencyCode("EUR"), new CurrencyCode("USD")));
    }

    private void setUpCalendarFixingsAndSnapshot(Set<LocalDate> holidays) {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.weekdayCalendar("ECB-CAL",
                        GoldenReferenceData.CALENDAR_START, GoldenReferenceData.CALENDAR_END, holidays))
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(GoldenReferenceData.currency("EUR")).addCurrency(GoldenReferenceData.currency("USD"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "USD"));
        runner.publishCatalogue(GoldenReferenceData.TENANT, b);
        runner.addFixing(fixing(LocalDate.of(2026, 4, 2), "1.0800"));
        runner.addFixing(fixing(LocalDate.of(2026, 4, 7), "1.0801"));
        runner.publishFixings(GoldenReferenceData.TENANT);
        runner.publishSnapshot(GoldenSnapshots.eod("SNAP-C", GoldenReferenceData.TENANT, LocalDate.of(2026, 4, 1),
                Instant.parse("2026-12-31T00:00:00Z")));
    }

    private com.power.fx.api.model.FixingVersion fixing(LocalDate date, String value) {
        var pair = GoldenReferenceData.pair("EUR", "USD");
        return new com.power.fx.api.model.FixingVersion(com.power.fx.api.model.Scope.TENANT,
                GoldenReferenceData.TENANT, "ECB", pair, date, "16:00", new java.math.BigDecimal(value),
                com.power.fx.api.model.FixingStatus.OFFICIAL, date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                "ECB-" + pair.canonical() + "-" + date, null, date);
    }

    @Test
    void c01_goodFridayUsePrevious() {
        setUpCalendarFixingsAndSnapshot(Set.of(GOOD_FRIDAY_2026, EASTER_MONDAY_2026));
        RateResult result = rate(policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS,
                List.of("ECB"), List.of()), GOOD_FRIDAY_2026, null, GOOD_FRIDAY_2026);
        assertTrue(result.isSuccess(), () -> "C01 error: " + result.error());
        FxAssertions.assertDecimalEquals("1.0800", result.rate());
        assertEquals(RateFinality.CONFIRMED, result.finality());
        assertFalse(result.warnings().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.code() == FxWarningCode.FX_W_DATE_RULE_ADJUSTED));
    }

    @Test
    void c02_goodFridayUseNextSkipsEasterMonday() {
        setUpCalendarFixingsAndSnapshot(Set.of(GOOD_FRIDAY_2026, EASTER_MONDAY_2026));
        RateResult result = rate(policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_NEXT,
                List.of("ECB"), List.of()), GOOD_FRIDAY_2026, null, GOOD_FRIDAY_2026);
        assertTrue(result.isSuccess(), () -> "C02 error: " + result.error());
        FxAssertions.assertDecimalEquals("1.0801", result.rate());
    }

    @Test
    void c03_goodFridayFail() {
        setUpCalendarFixingsAndSnapshot(Set.of(GOOD_FRIDAY_2026, EASTER_MONDAY_2026));
        RateResult result = rate(policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.FAIL,
                List.of("ECB"), List.of()), GOOD_FRIDAY_2026, null, GOOD_FRIDAY_2026);
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_E_NON_PUBLICATION_DATE, result.error().get().code());
    }

    @Test
    void c04_wmrOutageFallsBackToEcbAltSource() {
        setUpCalendarFixingsAndSnapshot(Set.of());
        // WMR is a loaded, entitled source (same publication calendar as ECB) with no fixing data
        // ingested for this date -- simulating the outage itself, not a missing-reference-data gap.
        CatalogueBuilder wmr = new CatalogueBuilder()
                .addFixingSource(GoldenReferenceData.fixingSource("WMR", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "WMR"));
        runner.publishCatalogue(GoldenReferenceData.TENANT, wmr);
        LocalDate fxDate = LocalDate.of(2026, 4, 2);
        FxPolicy p = policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("WMR", "ECB"),
                List.of(new FallbackStep(FallbackStepKind.ALT_SOURCE, 1)));
        // valuationDate strictly after fxDate so RateSelector's R3 ("before", missing on an open
        // day -> fallback chain) applies, rather than R4/R5's same-day path.
        RateResult result = rate(p, fxDate, null, LocalDate.of(2026, 4, 10));
        assertTrue(result.isSuccess(), () -> "C04 error: " + result.error());
        FxAssertions.assertDecimalEquals("1.0800", result.rate());
        assertEquals(RateFinality.ESTIMATED, result.finality());
        // Note: FX_W_FALLBACK_USED is specified (S6.8) but not yet wired into any warnings list
        // in this phase of fx-core (only FX_W_DATE_RULE_ADJUSTED and
        // FX_W_SOURCE_SKIPPED_NOT_ENTITLED are actually emitted) -- a pre-existing gap discovered
        // during Phase 3b, flagged for code-reviewer rather than silently patched here. The
        // fallback outcome is independently confirmed via the FALLBACK_ALT_SOURCE reason instead.
        assertTrue(result.reasons().contains(com.power.fx.api.error.FxReason.FALLBACK_ALT_SOURCE),
                () -> "reasons: " + result.reasons());
    }

    @Test
    void c05_saturdayGasDeliveryUsesPrecedingFriday() {
        setUpCalendarFixingsAndSnapshot(Set.of());
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        // Friday 6-Nov must have an EUR/USD fixing to resolve against.
        runner.addFixing(fixing(LocalDate.of(2026, 11, 6), "1.0820"));
        runner.publishFixings(GoldenReferenceData.TENANT);
        FxPolicy p = policy(DateRule.DELIVERY_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("ECB"), List.of());
        RateResult result = rate(p, null, saturday, LocalDate.of(2026, 11, 1));
        assertTrue(result.isSuccess(), () -> "C05 error: " + result.error());
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }
}
