package com.power.fx.guice;

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
 * re-run through the Guice-wired stack (implementation plan Task 4.4) --
 * ported from {@code fx-testkit}'s shipped {@code CalendarVectorTest}, with
 * {@link GuiceVectorHarness} standing in for {@code VectorRunner}.
 */
class GuiceCalendarVectorWiringTest {

    private static final VectorExpectations EXPECTED = VectorExpectations.load("/vectors/calendar-C01-C05.csv");
    private static final LocalDate GOOD_FRIDAY_2026 = GoldenReferenceData.GOOD_FRIDAY_2026;
    private static final LocalDate EASTER_MONDAY_2026 = GoldenReferenceData.EASTER_MONDAY_2026;

    private GuiceVectorHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GuiceVectorHarness();
        harness.setTenant(GoldenReferenceData.TENANT);
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
        return harness.converter().rate(new RateRequest(context, new CurrencyCode("EUR"), new CurrencyCode("USD")));
    }

    private void setUpCalendarFixingsAndSnapshot(Set<LocalDate> holidays) {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.weekdayCalendar("ECB-CAL",
                        GoldenReferenceData.CALENDAR_START, GoldenReferenceData.CALENDAR_END, holidays))
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"))
                .addCurrency(GoldenReferenceData.currency("EUR")).addCurrency(GoldenReferenceData.currency("USD"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "USD"));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
        harness.addFixing(fixing(LocalDate.of(2026, 4, 2), "1.0800"));
        harness.addFixing(fixing(LocalDate.of(2026, 4, 7), "1.0801"));
        harness.publishFixings(GoldenReferenceData.TENANT);
        harness.publishSnapshot(GoldenSnapshots.eod("SNAP-C", GoldenReferenceData.TENANT, LocalDate.of(2026, 4, 1),
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
        CatalogueBuilder wmr = new CatalogueBuilder()
                .addFixingSource(GoldenReferenceData.fixingSource("WMR", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "WMR"));
        harness.publishCatalogue(GoldenReferenceData.TENANT, wmr);
        LocalDate fxDate = LocalDate.of(2026, 4, 2);
        FxPolicy p = policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("WMR", "ECB"),
                List.of(new FallbackStep(FallbackStepKind.ALT_SOURCE, 1)));
        RateResult result = rate(p, fxDate, null, LocalDate.of(2026, 4, 10));
        assertTrue(result.isSuccess(), () -> "C04 error: " + result.error());
        FxAssertions.assertDecimalEquals("1.0800", result.rate());
        assertEquals(RateFinality.ESTIMATED, result.finality());
        assertTrue(result.reasons().contains(com.power.fx.api.error.FxReason.FALLBACK_ALT_SOURCE),
                () -> "reasons: " + result.reasons());
    }

    @Test
    void c05_saturdayGasDeliveryUsesPrecedingFriday() {
        setUpCalendarFixingsAndSnapshot(Set.of());
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        harness.addFixing(fixing(LocalDate.of(2026, 11, 6), "1.0820"));
        harness.publishFixings(GoldenReferenceData.TENANT);
        FxPolicy p = policy(DateRule.DELIVERY_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("ECB"), List.of());
        RateResult result = rate(p, null, saturday, LocalDate.of(2026, 11, 1));
        assertTrue(result.isSuccess(), () -> "C05 error: " + result.error());
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }
}
