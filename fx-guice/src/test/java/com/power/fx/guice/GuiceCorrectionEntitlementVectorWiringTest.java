package com.power.fx.guice;

import com.power.fx.api.FxSnapshot;
import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxWarningCode;
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
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.model.UsageClass;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden correction/entitlement/determinism vectors X01-X11 (functional
 * spec S19.4), re-run through the Guice-wired stack (implementation plan
 * Task 4.4) -- ported from {@code fx-testkit}'s shipped {@code
 * CorrectionEntitlementVectorTest}.
 *
 * <p><strong>X07, X10, X11 are intentionally not reproduced here</strong>,
 * matching the shipped suite's own scope exactly:
 * <ul>
 *   <li>X07 is documented as structurally unreachable (a {@code
 *       ManualRateOverride} with {@code authoredBy == approvedBy} cannot be
 *       constructed at all -- {@code VersionEnvelope}'s own compact
 *       constructor rejects it) -- a pure {@code fx-api} value-type check
 *       with no converter or Guice binding involved; re-asserting it here
 *       would not exercise anything this task's wiring touches.
 *   <li>X10 (cross-JVM determinism) is {@code JvmMatrixDeterminismTest}'s
 *       responsibility (Task 3b.8), out of this task's scope and blocked on
 *       TI-07 regardless of which converter instance drives it.
 *   <li>X11 exercises {@code RequestValidator} directly, with no converter
 *       or store involved.
 * </ul>
 * None of these three touch {@link com.power.fx.api.FxConverter} or any
 * Guice-bound port, so re-running them through {@link GuiceVectorHarness}
 * would not add coverage beyond what {@code fx-testkit} already proves;
 * documented here rather than silently dropped.
 */
class GuiceCorrectionEntitlementVectorWiringTest {

    private static final VectorExpectations EXPECTED = VectorExpectations.load("/vectors/corrections-X01-X11.csv");
    private static final LocalDate FIXING_DATE = LocalDate.of(2026, 4, 2);
    private static final Instant OFFICIAL_RECORDED_AT = Instant.parse("2026-04-02T14:20:00Z");
    private static final Instant CORRECTED_RECORDED_AT = Instant.parse("2026-04-03T10:00:00Z");
    private static final Instant CUT_BEFORE = Instant.parse("2026-04-02T18:00:00Z");
    private static final Instant CUT_AFTER = Instant.parse("2026-04-03T18:00:00Z");

    private GuiceVectorHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GuiceVectorHarness();
        harness.setTenant(GoldenReferenceData.TENANT);
    }

    private void ingestReferenceData() {
        var cal = GoldenReferenceData.ecbCalendarNoHolidays();
        List<FxIngestRecord> records = List.of(
                GuiceVectorHarness.globalRecord(FxEntityType.PUBLICATION_CALENDAR, "ECB-CAL", cal),
                GuiceVectorHarness.globalRecord(FxEntityType.FIXING_SOURCE, "ECB", GoldenReferenceData.fixingSource("ECB", "ECB-CAL")),
                GuiceVectorHarness.tenantRecord(GoldenReferenceData.TENANT, FxEntityType.SOURCE_ENTITLEMENT, "ECB",
                        GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB")),
                GuiceVectorHarness.globalRecord(FxEntityType.CURRENCY, "EUR", GoldenReferenceData.currency("EUR")),
                GuiceVectorHarness.globalRecord(FxEntityType.CURRENCY, "USD", GoldenReferenceData.currency("USD")),
                GuiceVectorHarness.globalRecord(FxEntityType.PAIR_CONVENTION, "EUR/USD", GoldenReferenceData.convention("EUR", "USD")));
        IngestOutcome outcome = harness.ingest(records);
        assertEquals(0, outcome.rejected(), () -> "unexpected rejections: " + outcome.rejections());
    }

    private void ingestOfficialThenCorrected() {
        FixingVersion official = new FixingVersion(Scope.TENANT, GoldenReferenceData.TENANT, "ECB",
                GoldenReferenceData.pair("EUR", "USD"), FIXING_DATE, "16:00", new BigDecimal("1.0800"),
                FixingStatus.OFFICIAL, OFFICIAL_RECORDED_AT, "ECB-EURUSD-" + FIXING_DATE + "-v1", null, FIXING_DATE);
        IngestOutcome o1 = harness.ingest(List.of(GuiceVectorHarness.fixingRecord(GoldenReferenceData.TENANT, official, 1)));
        assertEquals(1, o1.applied());

        FixingVersion corrected = new FixingVersion(Scope.TENANT, GoldenReferenceData.TENANT, "ECB",
                GoldenReferenceData.pair("EUR", "USD"), FIXING_DATE, "16:00", new BigDecimal("1.0810"),
                FixingStatus.CORRECTED, CORRECTED_RECORDED_AT, "ECB-EURUSD-" + FIXING_DATE + "-v2", official.versionId(),
                FIXING_DATE);
        IngestOutcome o2 = harness.ingest(List.of(GuiceVectorHarness.fixingRecord(GoldenReferenceData.TENANT, corrected, 2)));
        assertEquals(1, o2.applied(), () -> "unexpected rejections: " + o2.rejections());
    }

    private FxPolicy policy(Leg leg, FixingVersionSelection selection) {
        return policy(leg, selection, List.of("ECB"), false);
    }

    private FxPolicy policy(Leg leg, FixingVersionSelection selection, List<String> sources) {
        return policy(leg, selection, sources, false);
    }

    private FxPolicy policy(Leg leg, FixingVersionSelection selection, List<String> sources, boolean allowOverrides) {
        return new FxPolicy(GoldenReferenceData.globalEnvelope("POL-X", "POL-X-v1"), "POL-X", 1, leg,
                leg == Leg.CONTRACT ? DateRule.SPECIFIC_DATE : DateRule.VALUATION_DATE,
                new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null), NonPublicationDayHandling.USE_PREVIOUS,
                RollConvention.MODIFIED_FOLLOWING, sources, selection, FutureDateTreatment.FORWARD,
                SpotAdjustment.NONE, null, List.of(), false, null,
                new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null, EstimatedEventHandling.USE_ESTIMATE,
                allowOverrides);
    }

    private RateRequest rateRequest(FxPolicy policy, Purpose purpose, AmountType amountType) {
        FxRequestContext context = new FxRequestContext(purpose, FIXING_DATE, null, RunMode.AD_HOC, "UNIT-1",
                amountType, SettlementAmountState.UNINVOICED, purpose == Purpose.CONTRACT_SETTLEMENT ? null
                : com.power.fx.api.model.ItemType.MONETARY,
                new TradeDates(null, FIXING_DATE, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(policy), "req-x");
        return new RateRequest(context, new CurrencyCode("EUR"), new CurrencyCode("USD"));
    }

    @Test
    void x01_firstOfficial_immuneToLaterCorrection() {
        ingestReferenceData();
        ingestOfficialThenCorrected();
        harness.publishSnapshot(GoldenSnapshots.eod("SNAP-X01", GoldenReferenceData.TENANT, FIXING_DATE, CUT_AFTER));
        FxSnapshot pinned = harness.pin("SNAP-X01", CUT_AFTER);

        RateResult result = pinned.rate(rateRequest(policy(Leg.CONTRACT, new FixingVersionSelection.FirstOfficial()),
                Purpose.CONTRACT_SETTLEMENT, AmountType.NOMINAL));
        assertTrue(result.isSuccess(), () -> "X01 error: " + result.error());
        FxAssertions.assertDecimalEquals("1.0800", result.rate());
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }

    @Test
    void x02_x03_latestCorrected_cutDependentValue_x04_replayBitIdentical() {
        ingestReferenceData();
        ingestOfficialThenCorrected();
        harness.publishSnapshot(GoldenSnapshots.eod("SNAP-X0203", GoldenReferenceData.TENANT, FIXING_DATE, CUT_AFTER));

        RateRequest req = rateRequest(policy(Leg.ACCOUNTING_TRANSACTION, new FixingVersionSelection.LatestCorrected()),
                Purpose.UNREALISED_MTM, AmountType.PRESENT_VALUE);

        FxSnapshot pinnedBefore = harness.pin("SNAP-X0203", CUT_BEFORE);
        RateResult resultBefore = pinnedBefore.rate(req);
        assertTrue(resultBefore.isSuccess(), () -> "X02 error: " + resultBefore.error());
        FxAssertions.assertDecimalEquals("1.0800", resultBefore.rate());

        FxSnapshot pinnedAfter = harness.pin("SNAP-X0203", CUT_AFTER);
        RateResult resultAfter = pinnedAfter.rate(req);
        assertTrue(resultAfter.isSuccess(), () -> "X03 error: " + resultAfter.error());
        FxAssertions.assertDecimalEquals("1.0810", resultAfter.rate());

        FxSnapshot pinnedReplay = harness.pin("SNAP-X0203", CUT_BEFORE);
        RateResult replay = pinnedReplay.rate(req);
        assertEquals(0, resultBefore.rate().compareTo(replay.rate()));
        assertEquals(resultBefore.finality(), replay.finality());
        assertEquals(resultBefore.rateType(), replay.rateType());
    }

    @Test
    void x05_sourceSkippedNotEntitled_x06_noEntitledSource() {
        var cal = GoldenReferenceData.ecbCalendarNoHolidays();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(cal)
                .addFixingSource(GoldenReferenceData.fixingSource("WMR", "ECB-CAL"))
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT_NO_WMR, "ECB"))
                .addCurrency(GoldenReferenceData.currency("EUR")).addCurrency(GoldenReferenceData.currency("USD"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "USD"));
        harness.setTenant(GoldenReferenceData.TENANT_NO_WMR);
        harness.publishCatalogue(GoldenReferenceData.TENANT_NO_WMR, b);
        harness.addFixing(fixing("ECB", FIXING_DATE, "1.0850"));
        harness.publishFixings(GoldenReferenceData.TENANT_NO_WMR);
        harness.publishSnapshot(GoldenSnapshots.eod("SNAP-X05", GoldenReferenceData.TENANT_NO_WMR, FIXING_DATE, CUT_AFTER));

        RateResult resX05 = harness.converter().rate(rateRequest(
                policy(Leg.CONTRACT, new FixingVersionSelection.FirstOfficial(), List.of("WMR", "ECB")),
                Purpose.CONTRACT_SETTLEMENT, AmountType.NOMINAL));
        assertTrue(resX05.isSuccess(), () -> "X05 error: " + resX05.error());
        assertTrue(resX05.warnings().stream().anyMatch(w -> w.code() == FxWarningCode.FX_W_SOURCE_SKIPPED_NOT_ENTITLED));

        RateResult resX06 = harness.converter().rate(rateRequest(
                policy(Leg.CONTRACT, new FixingVersionSelection.FirstOfficial(), List.of("WMR")),
                Purpose.CONTRACT_SETTLEMENT, AmountType.NOMINAL));
        assertFalse(resX06.isSuccess());
        assertEquals(FxErrorCode.FX_E_SOURCE_NOT_ENTITLED, resX06.error().get().code());
    }

    @Test
    void x08_approvedOverride_usedWhenFixingMissing() {
        LocalDate overrideDate = LocalDate.of(2026, 6, 15);
        var rbiCal = GoldenReferenceData.weekdayCalendar("RBI-CAL", GoldenReferenceData.CALENDAR_START,
                GoldenReferenceData.CALENDAR_END, java.util.Set.of());
        List<FxIngestRecord> refRecords = List.of(
                GuiceVectorHarness.globalRecord(FxEntityType.CURRENCY, "USD", GoldenReferenceData.currency("USD")),
                GuiceVectorHarness.globalRecord(FxEntityType.CURRENCY, "INR", GoldenReferenceData.currency("INR")),
                GuiceVectorHarness.globalRecord(FxEntityType.PAIR_CONVENTION, "USD/INR", GoldenReferenceData.convention("USD", "INR")),
                GuiceVectorHarness.globalRecord(FxEntityType.PUBLICATION_CALENDAR, "RBI-CAL", rbiCal),
                GuiceVectorHarness.globalRecord(FxEntityType.FIXING_SOURCE, "RBI", GoldenReferenceData.fixingSource("RBI", "RBI-CAL")),
                GuiceVectorHarness.tenantRecord(GoldenReferenceData.TENANT, FxEntityType.SOURCE_ENTITLEMENT, "RBI",
                        GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "RBI")));
        IngestOutcome refOutcome = harness.ingest(refRecords);
        assertEquals(0, refOutcome.rejected(), () -> "unexpected rejections: " + refOutcome.rejections());

        ManualRateOverride override = new ManualRateOverride(
                GoldenReferenceData.tenantEnvelope(GoldenReferenceData.TENANT, "USD/INR", "ov-x08-v1"), Scope.TENANT,
                GoldenReferenceData.pair("USD", "INR"), overrideDate, null, new BigDecimal("83.50"), "RBI_MISSING", "TCK-X08");
        IngestOutcome overrideOutcome = harness.ingest(List.of(GuiceVectorHarness.tenantRecord(GoldenReferenceData.TENANT,
                FxEntityType.MANUAL_RATE_OVERRIDE, "USD/INR", override)));
        assertEquals(1, overrideOutcome.applied(), () -> "unexpected rejections: " + overrideOutcome.rejections());

        harness.publishFixings(GoldenReferenceData.TENANT); // no fixings -- the point of X08.
        harness.publishSnapshot(GoldenSnapshots.eod("SNAP-X08", GoldenReferenceData.TENANT, overrideDate, CUT_AFTER));

        FxPolicy p = policy(Leg.CONTRACT, new FixingVersionSelection.FirstOfficial(), List.of("RBI"), true);
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, overrideDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, null,
                new TradeDates(null, overrideDate, null, null, null, null, List.of(), null), Map.of(), null, null,
                List.of(), new PolicyRef.Inline(p), "req-x08");
        RateResult result = harness.converter().rate(new RateRequest(context, new CurrencyCode("USD"), new CurrencyCode("INR")));
        assertTrue(result.isSuccess(), () -> "X08 error: " + result.error());
        assertTrue(EXPECTED.expected("X08").contains("MANUAL_OVERRIDE"), "CSV expectation sanity check");
        FxAssertions.assertDecimalEquals("83.50", result.rate());
        assertEquals(RateType.MANUAL_OVERRIDE, result.rateType());
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }

    @Test
    void x09_internalEodRejectedForContractSettlement() {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("INTERNAL_EOD", "ECB-CAL", UsageClass.MTM_ONLY))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "INTERNAL_EOD"))
                .addCurrency(GoldenReferenceData.currency("EUR")).addCurrency(GoldenReferenceData.currency("USD"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "USD"));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
        harness.addFixing(fixing("INTERNAL_EOD", FIXING_DATE, "1.0850"));
        harness.publishFixings(GoldenReferenceData.TENANT);
        harness.publishSnapshot(GoldenSnapshots.eod("SNAP-X09", GoldenReferenceData.TENANT, FIXING_DATE, CUT_AFTER));

        RateResult result = harness.converter().rate(rateRequest(
                policy(Leg.CONTRACT, new FixingVersionSelection.FirstOfficial(), List.of("INTERNAL_EOD")),
                Purpose.CONTRACT_SETTLEMENT, AmountType.NOMINAL));
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_V_SOURCE_NOT_ALLOWED, result.error().get().code());
    }

    // --- helpers ---

    private FixingVersion fixing(String source, LocalDate date, String value) {
        var pair = GoldenReferenceData.pair("EUR", "USD");
        return new FixingVersion(Scope.TENANT, harness.tenantContextProvider().currentTenant().orElseThrow(), source,
                pair, date, "16:00", new BigDecimal(value), FixingStatus.OFFICIAL,
                date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), source + "-" + pair.canonical() + "-" + date,
                null, date);
    }
}
