package com.power.fx.core.e2e;

import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestOutcome;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.FutureDateTreatment;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ItemType;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.NonPublicationDayHandling;
import com.power.fx.api.model.OffsetCalendarKind;
import com.power.fx.api.model.OffsetSpec;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.RollConvention;
import com.power.fx.api.model.RoundingSpec;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.model.UsageClass;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.result.RateResult;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.testsupport.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden vectors X01 and X03 (plus X08, where reachable), driven through
 * the real {@link com.power.fx.core.DefaultFxIngestor} (Job 1 backfill of
 * Task 2.17) rather than direct store swap/put calls, per the Job 1 task
 * instructions.
 */
class IngestionGoldenVectorTest {

    private static final LocalDate FIXING_DATE = LocalDate.of(2026, 4, 2);
    private static final Instant OFFICIAL_RECORDED_AT = Instant.parse("2026-04-02T14:20:00Z");
    private static final Instant CORRECTED_RECORDED_AT = Instant.parse("2026-04-03T10:00:00Z");
    private static final Instant CUT_BEFORE_CORRECTION = Instant.parse("2026-04-02T18:00:00Z");
    private static final Instant CUT_AFTER_CORRECTION = Instant.parse("2026-04-03T18:00:00Z");

    private E2eEnvironment env;

    @BeforeEach
    void setUp() {
        env = new E2eEnvironment();
    }

    private void ingestReferenceData() {
        var cal = TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                java.util.Set.of());
        List<FxIngestRecord> records = List.of(
                refRecord(FxEntityType.PUBLICATION_CALENDAR, "ECB-CAL", cal),
                refRecord(FxEntityType.FIXING_SOURCE, "ECB", env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE)),
                tenantRefRecord(FxEntityType.SOURCE_ENTITLEMENT, "ECB", env.entitlement(E2eEnvironment.TENANT, "ECB")),
                refRecord(FxEntityType.CURRENCY, "EUR", env.currency("EUR", null)),
                refRecord(FxEntityType.CURRENCY, "USD", env.currency("USD", null)),
                refRecord(FxEntityType.PAIR_CONVENTION, "EUR/USD", env.convention("EUR", "USD")));
        IngestOutcome outcome = env.ingest(records);
        assertEquals(0, outcome.rejected(), () -> "unexpected rejections: " + outcome.rejections());
    }

    private void ingestOfficialThenCorrectedFixing() {
        FixingVersion official = new FixingVersion(Scope.TENANT, E2eEnvironment.TENANT, "ECB",
                TestFixtures.pair("EUR", "USD"), FIXING_DATE, "16:00", new BigDecimal("1.0800"), FixingStatus.OFFICIAL,
                OFFICIAL_RECORDED_AT, "ECB-EURUSD-" + FIXING_DATE + "-v1", null, FIXING_DATE);
        IngestOutcome officialOutcome = env.ingest(List.of(fixingRecord(official, 1)));
        assertEquals(1, officialOutcome.applied());
        assertEquals(0, officialOutcome.rejected());

        FixingVersion corrected = new FixingVersion(Scope.TENANT, E2eEnvironment.TENANT, "ECB",
                TestFixtures.pair("EUR", "USD"), FIXING_DATE, "16:00", new BigDecimal("1.0810"), FixingStatus.CORRECTED,
                CORRECTED_RECORDED_AT, "ECB-EURUSD-" + FIXING_DATE + "-v2", official.versionId(), FIXING_DATE);
        IngestOutcome correctedOutcome = env.ingest(List.of(fixingRecord(corrected, 2)));
        assertEquals(1, correctedOutcome.applied(), () -> "unexpected rejections: " + correctedOutcome.rejections());
        assertEquals(0, correctedOutcome.rejected());

        // The correction-notification ordering requirement (vector X03): onFixingCorrected fired,
        // carrying the old/new values, and -- because DefaultFxIngestor fires it only after the
        // atomic store swap -- a resolution issued right now already sees the corrected generation.
        assertEquals(1, env.eventListener.corrections.size());
        var correction = env.eventListener.corrections.get(0);
        assertEquals(0, new BigDecimal("1.0800").compareTo(correction.oldValue()));
        assertEquals(0, new BigDecimal("1.0810").compareTo(correction.newValue()));
    }

    @Test
    void x01_firstOfficial_immuneToLaterCorrection() {
        ingestReferenceData();
        ingestOfficialThenCorrectedFixing();
        env.publishSnapshot("SNAP-X01", SignOffStatus.SIGNED_OFF);

        // Pin at a cut strictly after the correction -- X01's "snapshot after the correction".
        var pinned = env.converter.pin("SNAP-X01", CUT_AFTER_CORRECTION);

        FxPolicy contractPolicy = simplePolicy(DateRule.SPECIFIC_DATE, List.of("ECB"),
                new FixingVersionSelection.FirstOfficial());
        RateRequest req = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, Leg.CONTRACT, AmountType.NOMINAL,
                null, contractPolicy), new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult result = pinned.rate(req);

        assertTrue(result.isSuccess(), () -> "X01 error: " + result.error());
        assertEquals(0, new BigDecimal("1.0800").compareTo(result.rate()));
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }

    @Test
    void x02_x03_latestCorrected_cutDependentValue() {
        ingestReferenceData();
        ingestOfficialThenCorrectedFixing();
        env.publishSnapshot("SNAP-X0203", SignOffStatus.SIGNED_OFF);

        FxPolicy mtmPolicy = simplePolicy(DateRule.VALUATION_DATE, List.of("ECB"),
                new FixingVersionSelection.LatestCorrected());

        // X02: knowledge cut before the correction was recorded -> 1.0800.
        var pinnedBefore = env.converter.pin("SNAP-X0203", CUT_BEFORE_CORRECTION);
        RateRequest reqBefore = new RateRequest(ctx(Purpose.UNREALISED_MTM, Leg.ACCOUNTING_TRANSACTION,
                AmountType.PRESENT_VALUE, ItemType.MONETARY, mtmPolicy), new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult resultBefore = pinnedBefore.rate(reqBefore);
        assertTrue(resultBefore.isSuccess(), () -> "X02 error: " + resultBefore.error());
        assertEquals(0, new BigDecimal("1.0800").compareTo(resultBefore.rate()));

        // X03: knowledge cut after the correction was recorded -> 1.0810.
        var pinnedAfter = env.converter.pin("SNAP-X0203", CUT_AFTER_CORRECTION);
        RateRequest reqAfter = new RateRequest(ctx(Purpose.UNREALISED_MTM, Leg.ACCOUNTING_TRANSACTION,
                AmountType.PRESENT_VALUE, ItemType.MONETARY, mtmPolicy), new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult resultAfter = pinnedAfter.rate(reqAfter);
        assertTrue(resultAfter.isSuccess(), () -> "X03 error: " + resultAfter.error());
        assertEquals(0, new BigDecimal("1.0810").compareTo(resultAfter.rate()));
    }

    /**
     * X08: an approved manual override for USD/INR, ingested as ordinary
     * reference data, is used when the regular fixing is missing on that
     * date. Reachable end-to-end through ingestion (unlike X07): the
     * override here is validly four-eyes-approved, so {@link
     * com.power.fx.core.pair.DefaultPairResolver}'s {@code
     * ManualOverrideStep} picks it up with no need to construct an
     * invalid {@code VersionEnvelope}.
     */
    @Test
    void x08_approvedOverride_usedWhenFixingMissing() {
        LocalDate overrideDate = LocalDate.of(2026, 6, 15);
        var rbiCal = TestFixtures.weekdayCalendar("RBI-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                java.util.Set.of());
        List<FxIngestRecord> refRecords = List.of(
                refRecord(FxEntityType.CURRENCY, "USD", env.currency("USD", null)),
                refRecord(FxEntityType.CURRENCY, "INR", env.currency("INR", null)),
                refRecord(FxEntityType.PAIR_CONVENTION, "USD/INR", env.convention("USD", "INR")),
                refRecord(FxEntityType.PUBLICATION_CALENDAR, "RBI-CAL", rbiCal),
                refRecord(FxEntityType.FIXING_SOURCE, "RBI", env.fixingSource("RBI", "RBI-CAL", UsageClass.INVOICING_ELIGIBLE)),
                tenantRefRecord(FxEntityType.SOURCE_ENTITLEMENT, "RBI", env.entitlement(E2eEnvironment.TENANT, "RBI")));
        IngestOutcome refOutcome = env.ingest(refRecords);
        assertEquals(0, refOutcome.rejected(), () -> "unexpected rejections: " + refOutcome.rejections());

        // No FIXING-entity record is ever ingested for USD/INR on overrideDate -- the regular
        // fixing is genuinely missing; DirectQuoteStep (step 6) will find nothing, but
        // ManualOverrideStep (step 5, earlier in the chain) resolves first regardless.
        ManualRateOverride override = new ManualRateOverride(
                TestFixtures.tenantEnvelope(E2eEnvironment.TENANT, "USD/INR", "ov-x08-v1"), Scope.TENANT,
                TestFixtures.pair("USD", "INR"), overrideDate, null, new BigDecimal("83.50"), "RBI_MISSING", "TCK-X08");
        IngestOutcome overrideOutcome = env.ingest(List.of(tenantRefRecord(FxEntityType.MANUAL_RATE_OVERRIDE,
                "USD/INR", override)));
        assertEquals(1, overrideOutcome.applied(), () -> "unexpected rejections: " + overrideOutcome.rejections());

        // No FixingStore generation exists for this tenant yet (no FIXING-entity records were
        // ever ingested, by design -- X08 is precisely the "fixing is missing" scenario); a pin
        // still requires *some* FixingView to exist, so publish an explicitly empty one. This is
        // infrastructure setup for a store this vector does not exercise, not a bypass of the
        // ingestion path under test (the override and reference data above are both ingested).
        env.publishFixings(E2eEnvironment.TENANT);
        env.publishSnapshot("SNAP-X08", SignOffStatus.SIGNED_OFF);

        FxPolicy policy = new FxPolicy(TestFixtures.globalEnvelope("X08-POL", "X08-POL-v1"), "X08-POL", 1,
                Leg.CONTRACT, DateRule.SPECIFIC_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("RBI"),
                new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, true);
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, overrideDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, overrideDate, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(policy), "req-x08");
        RateRequest req = new RateRequest(context, new CurrencyCode("USD"), new CurrencyCode("INR"));
        RateResult result = env.converter.rate(req);

        assertTrue(result.isSuccess(), () -> "X08 error: " + result.error());
        assertEquals(0, new BigDecimal("83.50").compareTo(result.rate()));
        assertEquals(RateType.MANUAL_OVERRIDE, result.rateType());
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }

    // --- helpers ---

    private FxPolicy simplePolicy(DateRule rule, List<String> sources, FixingVersionSelection selection) {
        return new FxPolicy(TestFixtures.globalEnvelope("POL-ING", "POL-ING-v1"), "POL-ING", 1,
                rule == DateRule.VALUATION_DATE ? Leg.ACCOUNTING_TRANSACTION : Leg.CONTRACT, rule,
                new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null), NonPublicationDayHandling.USE_PREVIOUS,
                RollConvention.MODIFIED_FOLLOWING, sources, selection, FutureDateTreatment.FORWARD,
                SpotAdjustment.NONE, null, List.of(), false, null,
                new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null, EstimatedEventHandling.USE_ESTIMATE, false);
    }

    private FxRequestContext ctx(Purpose purpose, Leg leg, AmountType amountType, ItemType itemType, FxPolicy policy) {
        return new FxRequestContext(purpose, FIXING_DATE, null, RunMode.AD_HOC, "UNIT-1", amountType,
                SettlementAmountState.UNINVOICED, itemType, new TradeDates(null, FIXING_DATE, null, null, null, null,
                List.of(), null), Map.of(), null, null, List.of(), new PolicyRef.Inline(policy), "req-ing");
    }

    private FxIngestRecord refRecord(FxEntityType type, String naturalKey, Object payload) {
        return new FxIngestRecord("evt-" + type + "-" + naturalKey, type, Scope.GLOBAL, null, naturalKey, 1, payload,
                TestFixtures.RECORDED_AT);
    }

    private FxIngestRecord tenantRefRecord(FxEntityType type, String naturalKey, Object payload) {
        return new FxIngestRecord("evt-" + type + "-" + naturalKey, type, Scope.TENANT, E2eEnvironment.TENANT,
                naturalKey, 1, payload, TestFixtures.RECORDED_AT);
    }

    private FxIngestRecord fixingRecord(FixingVersion fv, long sequence) {
        return new FxIngestRecord("evt-fix-" + fv.versionId(), FxEntityType.FIXING, Scope.TENANT, E2eEnvironment.TENANT,
                fv.sourceCode() + "|" + fv.pair().canonical() + "|" + fv.cutoff(), sequence, fv, fv.recordedAt());
    }
}
