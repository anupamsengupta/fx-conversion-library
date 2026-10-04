package com.power.fx.core.date;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxException;
import com.power.fx.api.model.AccountingDates;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.ContractRate;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.FallbackStep;
import com.power.fx.api.model.FixingVersionPolicy;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.FutureDateTreatment;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ItemType;
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
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.cache.FixingView;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.memo.ResolutionMemo;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.core.snapshot.PinnedState;
import com.power.fx.core.testsupport.TestFixtures;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden vectors C01-C03, C05 (functional spec S19.2), driven directly
 * against {@link DefaultDateRuleResolver}, which is all stage 6 needs
 * (D-02: dates resolved strictly before any pair/rate component exists).
 */
class CalendarVectorTest {

    private static final LocalDate GOOD_FRIDAY_2026 = LocalDate.of(2026, 4, 3);
    private static final LocalDate EASTER_MONDAY_2026 = LocalDate.of(2026, 4, 6);

    private final DefaultDateRuleResolver resolver = new DefaultDateRuleResolver();

    private ReferenceCatalogue catalogue() {
        var cal = TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Set.of(GOOD_FRIDAY_2026, EASTER_MONDAY_2026));
        var source = TestFixtures.fixingSource("ECB", "ECB-CAL", TestFixtures.pair("EUR", "USD"));
        return new CatalogueBuilder()
                .addPublicationCalendar(cal)
                .addFixingSource(source)
                .build(null, 1);
    }

    private PinnedState pinnedState(ReferenceCatalogue tenant) {
        MarketSnapshot snapshot = new MarketSnapshot("SNAP-1", Scope.TENANT, "TENANT-TEST",
                com.power.fx.api.model.SnapshotKind.EOD, LocalDate.of(2026, 4, 1), Instant.parse("2026-04-10T00:00:00Z"),
                com.power.fx.api.model.SignOffStatus.SIGNED_OFF, Map.of(), Map.of(), Map.of(), Map.of());
        return new PinnedState("TENANT-TEST", null, tenant, FixingView.empty(1), snapshot,
                Instant.parse("2026-04-10T00:00:00Z"), 1, 1, new ResolutionMemo(100));
    }

    private FxPolicy policy(DateRule dateRule, NonPublicationDayHandling handling) {
        return new FxPolicy(TestFixtures.globalEnvelope("POL-1", "POL-1-v1"), "POL-1", 1, Leg.CONTRACT, dateRule,
                new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null), handling, RollConvention.MODIFIED_FOLLOWING,
                List.of("ECB"), new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD,
                SpotAdjustment.NONE, null, List.of(), false, null,
                new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null, EstimatedEventHandling.USE_ESTIMATE,
                false);
    }

    private FxRequestContext context(LocalDate specificDate, DateRule dateRule, NonPublicationDayHandling handling) {
        return new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, LocalDate.of(2026, 4, 1), null, RunMode.AD_HOC,
                null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, specificDate, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(policy(dateRule, handling)), "req-1");
    }

    @Test
    void c01_goodFridayUsePrevious() {
        ReferenceCatalogue tenant = catalogue();
        ResolvedPolicy rp = ResolvedPolicy.resolve(new PolicyRef.Inline(policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS)),
                tenant, LocalDate.of(2026, 4, 1), Instant.parse("2026-04-10T00:00:00Z"));
        ResolvedDates dates = resolver.resolve(rp, pinnedState(tenant),
                context(GOOD_FRIDAY_2026, DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS));
        assertEquals(LocalDate.of(2026, 4, 2), dates.primary().resolvedDate());
        assertTrue(dates.primary().dateRuleAdjusted());
        assertEquals(1, dates.warnings().size());
    }

    @Test
    void c02_goodFridayUseNextSkipsEasterMonday() {
        ReferenceCatalogue tenant = catalogue();
        ResolvedPolicy rp = ResolvedPolicy.resolve(new PolicyRef.Inline(policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_NEXT)),
                tenant, LocalDate.of(2026, 4, 1), Instant.parse("2026-04-10T00:00:00Z"));
        ResolvedDates dates = resolver.resolve(rp, pinnedState(tenant),
                context(GOOD_FRIDAY_2026, DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_NEXT));
        assertEquals(LocalDate.of(2026, 4, 7), dates.primary().resolvedDate());
    }

    @Test
    void c03_goodFridayFail() {
        ReferenceCatalogue tenant = catalogue();
        ResolvedPolicy rp = ResolvedPolicy.resolve(new PolicyRef.Inline(policy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.FAIL)),
                tenant, LocalDate.of(2026, 4, 1), Instant.parse("2026-04-10T00:00:00Z"));
        FxException ex = assertThrows(FxException.class, () -> resolver.resolve(rp, pinnedState(tenant),
                context(GOOD_FRIDAY_2026, DateRule.SPECIFIC_DATE, NonPublicationDayHandling.FAIL)));
        assertEquals(FxErrorCode.FX_E_NON_PUBLICATION_DATE, ex.error().code());
    }

    @Test
    void c05_saturdayGasDeliveryUsesPrecedingFriday() {
        // Sat 7-Nov-26 delivery day, USE_PREVIOUS -> Fri 6-Nov-26.
        ReferenceCatalogue tenant = catalogue();
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        FxPolicy p = policy(DateRule.DELIVERY_DATE, NonPublicationDayHandling.USE_PREVIOUS);
        ResolvedPolicy rp = ResolvedPolicy.resolve(new PolicyRef.Inline(p), tenant, LocalDate.of(2026, 11, 1),
                Instant.parse("2026-04-10T00:00:00Z"));
        FxRequestContext ctx = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, LocalDate.of(2026, 11, 1), null,
                RunMode.AD_HOC, null, AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, null, null, saturday, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(p), "req-c05");
        ResolvedDates dates = resolver.resolve(rp, pinnedState(tenant), ctx);
        assertEquals(LocalDate.of(2026, 11, 6), dates.primary().resolvedDate());
    }
}
