package com.power.fx.core.e2e;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.AccountingDates;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.FixedFactorKind;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.FutureDateTreatment;
import com.power.fx.api.model.FxDifferenceClass;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ItemType;
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
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.model.UsageClass;
import com.power.fx.api.request.ChainRequest;
import com.power.fx.api.request.ConversionRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.MonetaryRevaluationRequest;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.result.ChainResult;
import com.power.fx.api.result.ConversionResult;
import com.power.fx.api.result.RateResult;
import com.power.fx.api.result.RevaluationResult;
import com.power.fx.core.testsupport.TestFixtures;
import com.power.fx.core.cache.CatalogueBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end golden vectors driven directly against the real {@link
 * com.power.fx.core.DefaultFxConverter} (Phase 2 acceptance gate's binding
 * requirement), not a Guice-wired stack (that is Phase 4).
 */
class EndToEndGoldenVectorTest {

    private static final LocalDate GOOD_FRIDAY_2026 = LocalDate.of(2026, 4, 3);
    private static final LocalDate EASTER_MONDAY_2026 = LocalDate.of(2026, 4, 6);
    private static final LocalDate FX_DATE = LocalDate.of(2026, 6, 5);

    private E2eEnvironment env;

    @BeforeEach
    void setUp() {
        env = new E2eEnvironment();
    }

    private FxPolicy simplePolicy(DateRule rule, NonPublicationDayHandling handling, List<String> sources,
            Leg leg, boolean allowOverrides) {
        return new FxPolicy(TestFixtures.globalEnvelope("POL", "POL-v1"), "POL", 1, leg, rule,
                new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null), handling, RollConvention.MODIFIED_FOLLOWING,
                sources, new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE,
                null, List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, allowOverrides);
    }

    private FxRequestContext ctx(Purpose purpose, LocalDate valuationDate, FxPolicy policy, LocalDate specificDate) {
        return new FxRequestContext(purpose, valuationDate, null, RunMode.AD_HOC, null, AmountType.NOMINAL,
                SettlementAmountState.UNINVOICED, null, new TradeDates(null, specificDate, null, null, null, null, List.of(), null),
                Map.of(), null, null, List.of(), new PolicyRef.Inline(policy), "req");
    }

    private void setUpCalendarAndEcb() {
        var cal = TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                java.util.Set.of(GOOD_FRIDAY_2026, EASTER_MONDAY_2026));
        CatalogueBuilder builder = new CatalogueBuilder()
                .addPublicationCalendar(cal)
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"));
        env.publishCatalogue(E2eEnvironment.TENANT, builder);
        env.publishSnapshot("SNAP-1", SignOffStatus.SIGNED_OFF);
    }

    @Test
    void g01_eurJpy() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("EUR", null)).addCurrency(env.currency("USD", null)).addCurrency(env.currency("JPY", null))
                .addPairConvention(env.convention("EUR", "USD")).addPairConvention(env.convention("USD", "JPY"));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        env.addFixing("ECB", "EUR", "USD", FX_DATE, "1.0850");
        env.addFixing("ECB", "USD", "JPY", FX_DATE, "149.20");
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy policy = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("ECB"), Leg.CONTRACT, false);
        RateRequest req = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, FX_DATE, policy, FX_DATE),
                new CurrencyCode("EUR"), new CurrencyCode("JPY"));
        RateResult result = env.converter.rate(req);
        assertTrue(result.isSuccess(), () -> "error: " + result.error());
        assertEquals(0, new BigDecimal("161.8820").compareTo(result.rate().setScale(4, RoundingMode.HALF_EVEN)));
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }

    @Test
    void g09_bgnEurLegalPeg() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("BGN", null)).addCurrency(env.currency("EUR", null))
                .addFixedFactor(env.fixedFactor("BGN", "EUR", "1.95583", FixedFactorKind.LEGAL_PEG, true));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy policy = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("ECB"), Leg.CONTRACT, false);
        LocalDate dealDate = LocalDate.of(2026, 3, 1);
        RateRequest req = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, dealDate, policy, dealDate),
                new CurrencyCode("BGN"), new CurrencyCode("EUR"));
        RateResult result = env.converter.rate(req);
        assertTrue(result.isSuccess(), () -> "error: " + result.error());
        assertEquals(0, new BigDecimal("1.95583").compareTo(result.rate()));
        assertEquals(RateFinality.CONFIRMED, result.finality());
    }

    @Test
    void c01_c02_c03_calendarResolution() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        java.util.Set.of(GOOD_FRIDAY_2026, EASTER_MONDAY_2026)))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("EUR", null)).addCurrency(env.currency("USD", null))
                .addPairConvention(env.convention("EUR", "USD"));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        env.addFixing("ECB", "EUR", "USD", LocalDate.of(2026, 4, 2), "1.0800");
        env.addFixing("ECB", "EUR", "USD", LocalDate.of(2026, 4, 7), "1.0801");
        env.publishFixings(E2eEnvironment.TENANT);

        // C01: USE_PREVIOUS.
        FxPolicy p1 = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("ECB"), Leg.CONTRACT, false);
        RateRequest r1 = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, GOOD_FRIDAY_2026, p1, GOOD_FRIDAY_2026),
                new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult res1 = env.converter.rate(r1);
        assertTrue(res1.isSuccess(), () -> "C01 error: " + res1.error());
        assertEquals(0, new BigDecimal("1.0800").compareTo(res1.rate()));
        assertEquals(RateFinality.CONFIRMED, res1.finality());
        assertFalse(res1.warnings().isEmpty());

        // C02: USE_NEXT.
        FxPolicy p2 = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_NEXT, List.of("ECB"), Leg.CONTRACT, false);
        RateRequest r2 = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, GOOD_FRIDAY_2026, p2, GOOD_FRIDAY_2026),
                new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult res2 = env.converter.rate(r2);
        assertTrue(res2.isSuccess(), () -> "C02 error: " + res2.error());
        assertEquals(0, new BigDecimal("1.0801").compareTo(res2.rate()));

        // C03: FAIL.
        FxPolicy p3 = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.FAIL, List.of("ECB"), Leg.CONTRACT, false);
        RateRequest r3 = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, GOOD_FRIDAY_2026, p3, GOOD_FRIDAY_2026),
                new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult res3 = env.converter.rate(r3);
        assertFalse(res3.isSuccess());
        assertEquals(FxErrorCode.FX_E_NON_PUBLICATION_DATE, res3.error().get().code());
    }

    @Test
    void f06_unrealisedMtm() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("USD", null)).addCurrency(env.currency("GBP", null))
                .addPairConvention(env.convention("GBP", "USD"));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        LocalDate valDate = LocalDate.of(2026, 6, 10);
        env.addFixing("ECB", "GBP", "USD", valDate, "1.2500");
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy policy = new FxPolicy(TestFixtures.globalEnvelope("MTM-POL", "MTM-POL-v1"), "MTM-POL", 1,
                Leg.ACCOUNTING_TRANSACTION, DateRule.VALUATION_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.LatestCorrected(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
        FxRequestContext context = new FxRequestContext(Purpose.UNREALISED_MTM, valDate, null, RunMode.AD_HOC, "UNIT-1",
                AmountType.PRESENT_VALUE, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), null, null,
                List.of(), new PolicyRef.Inline(policy), "req-f06");
        ConversionRequest req = new ConversionRequest(context, new CurrencyCode("USD"), new CurrencyCode("GBP"),
                new BigDecimal("379000.00"), false);
        ConversionResult result = env.converter.convert(req);
        assertTrue(result.isSuccess(), () -> "F06 error: " + result.error());
        assertEquals(0, new BigDecimal("303200.00").compareTo(result.toAmountBooked()));
    }

    @Test
    void f07_amountTypeMismatch() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("USD", null)).addCurrency(env.currency("GBP", null))
                .addPairConvention(env.convention("GBP", "USD"));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        LocalDate valDate = LocalDate.of(2026, 6, 10);
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy policy = new FxPolicy(TestFixtures.globalEnvelope("MTM-POL2", "MTM-POL2-v1"), "MTM-POL2", 1,
                Leg.ACCOUNTING_TRANSACTION, DateRule.VALUATION_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.LatestCorrected(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
        FxRequestContext context = new FxRequestContext(Purpose.UNREALISED_MTM, valDate, null, RunMode.AD_HOC, "UNIT-1",
                AmountType.NOMINAL_FUTURE, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), null, null,
                List.of(), new PolicyRef.Inline(policy), "req-f07");
        ConversionRequest req = new ConversionRequest(context, new CurrencyCode("USD"), new CurrencyCode("GBP"),
                new BigDecimal("379000.00"), false);
        ConversionResult result = env.converter.convert(req);
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_V_AMOUNT_TYPE_MISMATCH, result.error().get().code());
    }

    @Test
    void f09_nonMonetaryHistoricalRejectsClosingRate() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("USD", null)).addCurrency(env.currency("GBP", null))
                .addPairConvention(env.convention("GBP", "USD"));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy policy = new FxPolicy(TestFixtures.globalEnvelope("REVAL-POL", "REVAL-POL-v1"), "REVAL-POL", 1,
                Leg.ACCOUNTING_TRANSACTION, DateRule.CLOSING_RATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.LatestCorrected(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
        AccountingDates ad = new AccountingDates(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null);
        FxRequestContext context = new FxRequestContext(Purpose.ACCOUNTING_REVALUATION, LocalDate.of(2026, 12, 31), null,
                RunMode.AD_HOC, "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED,
                ItemType.NON_MONETARY_HISTORICAL, null, Map.of(), ad, null, List.of(), new PolicyRef.Inline(policy), "req-f09");
        ConversionRequest req = new ConversionRequest(context, new CurrencyCode("USD"), new CurrencyCode("GBP"),
                new BigDecimal("100.00"), false);
        ConversionResult result = env.converter.convert(req);
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_V_INVALID_POLICY, result.error().get().code());
    }

    @Test
    void f03_f04_revaluation() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("USD", null)).addCurrency(env.currency("GBP", null))
                .addPairConvention(env.convention("GBP", "USD"))
                .addAccountingUnit(env.accountingUnit("UNIT-1", "GBP", LocalDate.of(2000, 1, 1), null));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        LocalDate closingDate = LocalDate.of(2026, 6, 30);
        LocalDate settleDate = LocalDate.of(2026, 7, 15);
        env.addFixing("ECB", "GBP", "USD", closingDate, "1.2500");
        env.addFixing("ECB", "GBP", "USD", settleDate, "1.2600");
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy policy = simplePolicy(DateRule.CLOSING_RATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("ECB"),
                Leg.ACCOUNTING_TRANSACTION, false);

        // F03: CLOSING_RATE revaluation.
        FxRequestContext ctxF03 = new FxRequestContext(Purpose.ACCOUNTING_REVALUATION, closingDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), null,
                null, List.of(), new PolicyRef.Inline(policy), "req-f03");
        MonetaryRevaluationRequest reqF03 = new MonetaryRevaluationRequest(ctxF03, new CurrencyCode("USD"),
                new BigDecimal("379750.00"), new BigDecimal("299015.75"), null, DateRule.CLOSING_RATE);
        RevaluationResult resF03 = env.converter.revalue(reqF03);
        assertTrue(resF03.isSuccess(), () -> "F03 error: " + resF03.error());
        assertEquals(0, new BigDecimal("303800.00").compareTo(resF03.newFunctionalBooked()));
        assertEquals(0, new BigDecimal("4784.25").compareTo(resF03.difference().setScale(2, RoundingMode.HALF_EVEN)));
        assertEquals(FxDifferenceClass.UNREALISED_FX_PNL, resF03.classification());

        // F04: SETTLEMENT_DATE revaluation.
        FxPolicy policySettle = simplePolicy(DateRule.SETTLEMENT_DATE, NonPublicationDayHandling.USE_PREVIOUS, List.of("ECB"),
                Leg.ACCOUNTING_TRANSACTION, false);
        AccountingDates adF04 = new AccountingDates(null, null, null, settleDate, null, null);
        FxRequestContext ctxF04 = new FxRequestContext(Purpose.ACCOUNTING_SETTLEMENT, settleDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), adF04,
                null, List.of(), new PolicyRef.Inline(policySettle), "req-f04");
        MonetaryRevaluationRequest reqF04 = new MonetaryRevaluationRequest(ctxF04, new CurrencyCode("USD"),
                new BigDecimal("379750.00"), new BigDecimal("303800.00"), null, DateRule.SETTLEMENT_DATE);
        RevaluationResult resF04 = env.converter.revalue(reqF04);
        assertTrue(resF04.isSuccess(), () -> "F04 error: " + resF04.error());
        assertEquals(0, new BigDecimal("301388.89").compareTo(resF04.newFunctionalBooked()));
        assertEquals(0, new BigDecimal("-2411.11").compareTo(resF04.difference().setScale(2, RoundingMode.HALF_EVEN)));
        assertEquals(FxDifferenceClass.REALISED_FX_PNL, resF04.classification());
    }

    @Test
    void x05_sourceSkippedNotEntitled_x06_noEntitledSource() {
        var cal = TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of());
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(cal)
                .addFixingSource(env.fixingSource("WMR", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT_NO_WMR, "ECB")) // no WMR entitlement
                .addCurrency(env.currency("EUR", null)).addCurrency(env.currency("USD", null))
                .addPairConvention(env.convention("EUR", "USD"));
        env.currentTenant = E2eEnvironment.TENANT_NO_WMR;
        env.publishCatalogue(E2eEnvironment.TENANT_NO_WMR, b);
        env.addFixing("ECB", "EUR", "USD", FX_DATE, "1.0850");
        env.publishFixings(E2eEnvironment.TENANT_NO_WMR);
        env.publishSnapshot("SNAP-X05", SignOffStatus.SIGNED_OFF);

        // X05: [WMR, ECB] -> ECB used, warning.
        FxPolicy policyX05 = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS,
                List.of("WMR", "ECB"), Leg.CONTRACT, false);
        RateRequest reqX05 = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, FX_DATE, policyX05, FX_DATE),
                new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult resX05 = env.converter.rate(reqX05);
        assertTrue(resX05.isSuccess(), () -> "X05 error: " + resX05.error());
        assertTrue(resX05.warnings().stream().anyMatch(w -> w.code() == com.power.fx.api.error.FxWarningCode.FX_W_SOURCE_SKIPPED_NOT_ENTITLED));

        // X06: [WMR] only -> FX_E_SOURCE_NOT_ENTITLED.
        FxPolicy policyX06 = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS,
                List.of("WMR"), Leg.CONTRACT, false);
        RateRequest reqX06 = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, FX_DATE, policyX06, FX_DATE),
                new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult resX06 = env.converter.rate(reqX06);
        assertFalse(resX06.isSuccess());
        assertEquals(FxErrorCode.FX_E_SOURCE_NOT_ENTITLED, resX06.error().get().code());
    }

    @Test
    void f01_f02_f05_chain() {
        setUpCalendarAndEcb();
        LocalDate paymentDate = LocalDate.of(2026, 3, 16);
        LocalDate recognitionDate = LocalDate.of(2026, 3, 17);
        LocalDate translationDate = LocalDate.of(2026, 3, 31);
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("ECB", "ECB-CAL", UsageClass.INVOICING_ELIGIBLE))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "ECB"))
                .addCurrency(env.currency("EUR", null)).addCurrency(env.currency("USD", null)).addCurrency(env.currency("GBP", null))
                .addPairConvention(env.convention("EUR", "USD")).addPairConvention(env.convention("GBP", "USD")).addPairConvention(env.convention("EUR", "GBP"))
                .addAccountingUnit(env.accountingUnit("UNIT-1", "GBP", LocalDate.of(2000, 1, 1), null));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        env.addFixing("ECB", "EUR", "USD", paymentDate, "1.0850");
        env.addFixing("ECB", "GBP", "USD", recognitionDate, "1.2700");
        env.addFixing("ECB", "EUR", "GBP", translationDate, "0.854700855"); // EUR per GBP 1.1700 inverse: GBP/EUR convention
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy contractPolicy = new FxPolicy(TestFixtures.globalEnvelope("CP", "CP-v1"), "CP", 1, Leg.CONTRACT,
                DateRule.PAYMENT_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.FirstOfficial(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
        FxPolicy acctPolicy = new FxPolicy(TestFixtures.globalEnvelope("AP", "AP-v1"), "AP", 1, Leg.ACCOUNTING_TRANSACTION,
                DateRule.RECOGNITION_DATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.LatestCorrected(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);
        FxPolicy translationPolicy = new FxPolicy(TestFixtures.globalEnvelope("TP", "TP-v1"), "TP", 1, Leg.TRANSLATION,
                DateRule.CLOSING_RATE, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null),
                NonPublicationDayHandling.USE_PREVIOUS, RollConvention.MODIFIED_FOLLOWING, List.of("ECB"),
                new FixingVersionSelection.LatestCorrected(), FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null,
                List.of(), false, null, new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null,
                EstimatedEventHandling.USE_ESTIMATE, false);

        TradeDates td = new TradeDates(null, null, paymentDate, null, null, null, List.of(), null);
        AccountingDates ad = new AccountingDates(recognitionDate, LocalDate.of(2026, 3, 31), translationDate, null, null, null);
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, paymentDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, ItemType.MONETARY, td, Map.of(), ad,
                null, List.of(), new PolicyRef.Inline(contractPolicy), "req-chain");

        ChainRequest req = new ChainRequest(context, new CurrencyCode("EUR"), new BigDecimal("350000.00"),
                new CurrencyCode("USD"), new PolicyRef.Inline(contractPolicy), new PolicyRef.Inline(acctPolicy),
                new PolicyRef.Inline(translationPolicy), List.of(new CurrencyCode("EUR")), List.of());

        ChainResult result = env.converter.convertChain(req);
        assertTrue(result.error().isEmpty(), () -> "chain error: " + result.error());

        var contractLeg = result.legs().get(Leg.CONTRACT);
        assertEquals(0, new BigDecimal("379750.00").compareTo(contractLeg.conversion().toAmountBooked()));

        var acctLeg = result.legs().get(Leg.ACCOUNTING_TRANSACTION);
        assertEquals(0, new BigDecimal("299015.75").compareTo(acctLeg.conversion().toAmountBooked()));

        var translationLeg = result.legs().get(Leg.TRANSLATION);
        assertEquals(0, new BigDecimal("349848.43").compareTo(translationLeg.conversion().toAmountBooked()));
    }

    @Test
    void x09_internalEodRejectedForContractSettlement() {
        setUpCalendarAndEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(TestFixtures.weekdayCalendar("ECB-CAL", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), java.util.Set.of()))
                .addFixingSource(env.fixingSource("INTERNAL_EOD", "ECB-CAL", UsageClass.MTM_ONLY))
                .addEntitlement(env.entitlement(E2eEnvironment.TENANT, "INTERNAL_EOD"))
                .addCurrency(env.currency("EUR", null)).addCurrency(env.currency("USD", null))
                .addPairConvention(env.convention("EUR", "USD"));
        env.publishCatalogue(E2eEnvironment.TENANT, b);
        env.addFixing("INTERNAL_EOD", "EUR", "USD", FX_DATE, "1.0850");
        env.publishFixings(E2eEnvironment.TENANT);

        FxPolicy policy = simplePolicy(DateRule.SPECIFIC_DATE, NonPublicationDayHandling.USE_PREVIOUS,
                List.of("INTERNAL_EOD"), Leg.CONTRACT, false);
        RateRequest req = new RateRequest(ctx(Purpose.CONTRACT_SETTLEMENT, FX_DATE, policy, FX_DATE),
                new CurrencyCode("EUR"), new CurrencyCode("USD"));
        RateResult result = env.converter.rate(req);
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_V_SOURCE_NOT_ALLOWED, result.error().get().code());
    }
}
