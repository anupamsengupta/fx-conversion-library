package com.power.fx.guice;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.AccountingDates;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
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
import com.power.fx.api.model.RollConvention;
import com.power.fx.api.model.RoundingSpec;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.request.ChainRequest;
import com.power.fx.api.request.ConversionRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.MonetaryRevaluationRequest;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.result.ChainResult;
import com.power.fx.api.result.ConversionResult;
import com.power.fx.api.result.RevaluationResult;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.leg.FunctionalCurrencyResolver;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden chain / functional-currency vectors F01-F09 (functional spec
 * S19.3, D-04, D-05), re-run through the Guice-wired stack (implementation
 * plan Task 4.4) -- ported from {@code fx-testkit}'s shipped {@code
 * ChainVectorTest}.
 *
 * <p>F08 is, in the shipped suite, verified directly against {@link
 * FunctionalCurrencyResolver} rather than through a converter (same
 * component-level precedent as G04-G08). This class fetches that resolver
 * from the Guice injector instead of {@code new}-ing it, so even this
 * component-level check exercises the DI-assembled singleton.
 */
class GuiceChainVectorWiringTest {

    private static final VectorExpectations EXPECTED = VectorExpectations.load("/vectors/chain-F01-F09.csv");

    private GuiceVectorHarness harness;

    @BeforeEach
    void setUp() {
        harness = new GuiceVectorHarness();
        harness.setTenant(GoldenReferenceData.TENANT);
    }

    private void setUpEcb() {
        CatalogueBuilder b = new CatalogueBuilder()
                .addPublicationCalendar(GoldenReferenceData.ecbCalendarNoHolidays())
                .addFixingSource(GoldenReferenceData.fixingSource("ECB", "ECB-CAL"))
                .addEntitlement(GoldenReferenceData.entitlement(GoldenReferenceData.TENANT, "ECB"));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
    }

    private void publishSnapshot(String id) {
        harness.publishSnapshot(GoldenSnapshots.eod(id, GoldenReferenceData.TENANT, LocalDate.of(2026, 6, 10),
                Instant.parse("2026-12-31T00:00:00Z")));
    }

    private FixingVersion fixing(String base, String quote, LocalDate date, String value) {
        var pair = GoldenReferenceData.pair(base, quote);
        return new FixingVersion(Scope.TENANT, GoldenReferenceData.TENANT, "ECB", pair, date, "16:00",
                new java.math.BigDecimal(value), FixingStatus.OFFICIAL, date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                "ECB-" + pair.canonical() + "-" + date, null, date);
    }

    private FxPolicy policy(Leg leg, DateRule rule) {
        return new FxPolicy(GoldenReferenceData.globalEnvelope("POL-F-" + leg, "POL-F-" + leg + "-v1"), "POL-F", 1,
                leg, rule, new OffsetSpec(0, OffsetCalendarKind.CALENDAR_DAYS, null), NonPublicationDayHandling.USE_PREVIOUS,
                RollConvention.MODIFIED_FOLLOWING, List.of("ECB"), new FixingVersionSelection.FirstOfficial(),
                FutureDateTreatment.FORWARD, SpotAdjustment.NONE, null, List.of(), false, null,
                new RoundingSpec(RoundingMode.HALF_UP, null, null, null), null, EstimatedEventHandling.USE_ESTIMATE, false);
    }

    @Test
    void f01_f02_f05_chain() {
        setUpEcb();
        LocalDate paymentDate = LocalDate.of(2026, 3, 16);
        LocalDate recognitionDate = LocalDate.of(2026, 3, 17);
        LocalDate translationDate = LocalDate.of(2026, 3, 31);
        CatalogueBuilder b = new CatalogueBuilder()
                .addCurrency(GoldenReferenceData.currency("EUR")).addCurrency(GoldenReferenceData.currency("USD"))
                .addCurrency(GoldenReferenceData.currency("GBP"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "USD"))
                .addPairConvention(GoldenReferenceData.convention("GBP", "USD"))
                .addPairConvention(GoldenReferenceData.convention("EUR", "GBP"))
                .addAccountingUnit(GoldenReferenceData.accountingUnit("UNIT-1", "GBP", LocalDate.of(2000, 1, 1), null));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
        harness.addFixing(fixing("EUR", "USD", paymentDate, "1.0850"));
        harness.addFixing(fixing("GBP", "USD", recognitionDate, "1.2700"));
        harness.addFixing(fixing("EUR", "GBP", translationDate, "0.854700855"));
        harness.publishFixings(GoldenReferenceData.TENANT);
        publishSnapshot("SNAP-F01");

        FxPolicy contractPolicy = policy(Leg.CONTRACT, DateRule.PAYMENT_DATE);
        FxPolicy acctPolicy = policy(Leg.ACCOUNTING_TRANSACTION, DateRule.RECOGNITION_DATE);
        FxPolicy translationPolicy = policy(Leg.TRANSLATION, DateRule.CLOSING_RATE);

        TradeDates td = new TradeDates(null, null, paymentDate, null, null, null, List.of(), null);
        AccountingDates ad = new AccountingDates(recognitionDate, LocalDate.of(2026, 3, 31), translationDate, null, null, null);
        FxRequestContext context = new FxRequestContext(Purpose.CONTRACT_SETTLEMENT, paymentDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, ItemType.MONETARY, td, Map.of(), ad,
                null, List.of(), new PolicyRef.Inline(contractPolicy), "req-chain");
        ChainRequest req = new ChainRequest(context, new CurrencyCode("EUR"), new java.math.BigDecimal("350000.00"),
                new CurrencyCode("USD"), new PolicyRef.Inline(contractPolicy), new PolicyRef.Inline(acctPolicy),
                new PolicyRef.Inline(translationPolicy), List.of(new CurrencyCode("EUR")), List.of());

        ChainResult result = harness.converter().convertChain(req);
        assertTrue(result.error().isEmpty(), () -> "chain error: " + result.error());

        FxAssertions.assertDecimalEquals(EXPECTED.field("F01", 3),
                result.legs().get(Leg.CONTRACT).conversion().toAmountBooked());
        FxAssertions.assertDecimalEquals(EXPECTED.field("F02", 3),
                result.legs().get(Leg.ACCOUNTING_TRANSACTION).conversion().toAmountBooked());
        FxAssertions.assertDecimalEquals(EXPECTED.field("F05", 3),
                result.legs().get(Leg.TRANSLATION).conversion().toAmountBooked());
    }

    @Test
    void f03_f04_revaluation() {
        setUpEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addCurrency(GoldenReferenceData.currency("USD")).addCurrency(GoldenReferenceData.currency("GBP"))
                .addPairConvention(GoldenReferenceData.convention("GBP", "USD"))
                .addAccountingUnit(GoldenReferenceData.accountingUnit("UNIT-1", "GBP", LocalDate.of(2000, 1, 1), null));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
        LocalDate closingDate = LocalDate.of(2026, 6, 30);
        LocalDate settleDate = LocalDate.of(2026, 7, 15);
        harness.addFixing(fixing("GBP", "USD", closingDate, "1.2500"));
        harness.addFixing(fixing("GBP", "USD", settleDate, "1.2600"));
        harness.publishFixings(GoldenReferenceData.TENANT);
        publishSnapshot("SNAP-F0304");

        FxPolicy closingPolicy = policy(Leg.ACCOUNTING_TRANSACTION, DateRule.CLOSING_RATE);
        FxRequestContext ctxF03 = new FxRequestContext(Purpose.ACCOUNTING_REVALUATION, closingDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), null,
                null, List.of(), new PolicyRef.Inline(closingPolicy), "req-f03");
        MonetaryRevaluationRequest reqF03 = new MonetaryRevaluationRequest(ctxF03, new CurrencyCode("USD"),
                new java.math.BigDecimal("379750.00"), new java.math.BigDecimal("299015.75"), null, DateRule.CLOSING_RATE);
        RevaluationResult resF03 = harness.converter().revalue(reqF03);
        assertTrue(resF03.isSuccess(), () -> "F03 error: " + resF03.error());
        FxAssertions.assertDecimalEquals(EXPECTED.field("F03", 3).split(" ")[0], resF03.newFunctionalBooked());
        assertEquals(FxDifferenceClass.UNREALISED_FX_PNL, resF03.classification());

        FxPolicy settlePolicy = policy(Leg.ACCOUNTING_TRANSACTION, DateRule.SETTLEMENT_DATE);
        AccountingDates adF04 = new AccountingDates(null, null, null, settleDate, null, null);
        FxRequestContext ctxF04 = new FxRequestContext(Purpose.ACCOUNTING_SETTLEMENT, settleDate, null, RunMode.AD_HOC,
                "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), adF04,
                null, List.of(), new PolicyRef.Inline(settlePolicy), "req-f04");
        MonetaryRevaluationRequest reqF04 = new MonetaryRevaluationRequest(ctxF04, new CurrencyCode("USD"),
                new java.math.BigDecimal("379750.00"), new java.math.BigDecimal("303800.00"), null, DateRule.SETTLEMENT_DATE);
        RevaluationResult resF04 = harness.converter().revalue(reqF04);
        assertTrue(resF04.isSuccess(), () -> "F04 error: " + resF04.error());
        FxAssertions.assertDecimalEquals(EXPECTED.field("F04", 3).split(" ")[0], resF04.newFunctionalBooked());
        assertEquals(FxDifferenceClass.REALISED_FX_PNL, resF04.classification());
    }

    @Test
    void f06_unrealisedMtm() {
        setUpEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addCurrency(GoldenReferenceData.currency("USD")).addCurrency(GoldenReferenceData.currency("GBP"))
                .addPairConvention(GoldenReferenceData.convention("GBP", "USD"));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
        LocalDate valDate = LocalDate.of(2026, 6, 10);
        harness.addFixing(fixing("GBP", "USD", valDate, "1.2500"));
        harness.publishFixings(GoldenReferenceData.TENANT);
        publishSnapshot("SNAP-F06");

        FxPolicy mtmPolicy = policy(Leg.ACCOUNTING_TRANSACTION, DateRule.VALUATION_DATE);
        FxRequestContext context = new FxRequestContext(Purpose.UNREALISED_MTM, valDate, null, RunMode.AD_HOC, "UNIT-1",
                AmountType.PRESENT_VALUE, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), null,
                null, List.of(), new PolicyRef.Inline(mtmPolicy), "req-f06");
        ConversionRequest req = new ConversionRequest(context, new CurrencyCode("USD"), new CurrencyCode("GBP"),
                new java.math.BigDecimal("379000.00"), false);
        ConversionResult result = harness.converter().convert(req);
        assertTrue(result.isSuccess(), () -> "F06 error: " + result.error());
        FxAssertions.assertDecimalEquals(EXPECTED.field("F06", 3), result.toAmountBooked());
    }

    @Test
    void f07_amountTypeMismatch() {
        setUpEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addCurrency(GoldenReferenceData.currency("USD")).addCurrency(GoldenReferenceData.currency("GBP"))
                .addPairConvention(GoldenReferenceData.convention("GBP", "USD"));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
        harness.publishFixings(GoldenReferenceData.TENANT);
        publishSnapshot("SNAP-F07");

        LocalDate valDate = LocalDate.of(2026, 6, 10);
        FxPolicy mtmPolicy = policy(Leg.ACCOUNTING_TRANSACTION, DateRule.VALUATION_DATE);
        FxRequestContext context = new FxRequestContext(Purpose.UNREALISED_MTM, valDate, null, RunMode.AD_HOC, "UNIT-1",
                AmountType.NOMINAL_FUTURE, SettlementAmountState.UNINVOICED, ItemType.MONETARY, null, Map.of(), null,
                null, List.of(), new PolicyRef.Inline(mtmPolicy), "req-f07");
        ConversionRequest req = new ConversionRequest(context, new CurrencyCode("USD"), new CurrencyCode("GBP"),
                new java.math.BigDecimal("379000.00"), false);
        ConversionResult result = harness.converter().convert(req);
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_V_AMOUNT_TYPE_MISMATCH, result.error().get().code());
    }

    /** F08: component-level, through the Guice-injected {@link FunctionalCurrencyResolver} singleton. */
    @Test
    void f08_functionalCurrencyEffectiveDating_throughGuiceWiredResolver() {
        CatalogueBuilder b = new CatalogueBuilder()
                .addAccountingUnit(GoldenReferenceData.accountingUnit("UNIT-F08", "INR", LocalDate.of(2000, 1, 1),
                        LocalDate.of(2027, 1, 1)))
                .addAccountingUnit(GoldenReferenceData.accountingUnit("UNIT-F08", "USD", LocalDate.of(2027, 1, 1), null));
        var tenant = b.build(null, 1);
        FunctionalCurrencyResolver resolver = harness.injector().getInstance(FunctionalCurrencyResolver.class);
        CurrencyCode resolved = resolver.resolve("UNIT-F08", LocalDate.of(2027, 1, 5), tenant, GoldenReferenceData.RECORDED_AT.plusSeconds(1));
        assertEquals(new CurrencyCode(EXPECTED.field("F08", 3)), resolved);
    }

    @Test
    void f09_nonMonetaryHistoricalRejectsClosingRate() {
        setUpEcb();
        CatalogueBuilder b = new CatalogueBuilder()
                .addCurrency(GoldenReferenceData.currency("USD")).addCurrency(GoldenReferenceData.currency("GBP"))
                .addPairConvention(GoldenReferenceData.convention("GBP", "USD"));
        harness.publishCatalogue(GoldenReferenceData.TENANT, b);
        harness.publishFixings(GoldenReferenceData.TENANT);
        publishSnapshot("SNAP-F09");

        FxPolicy revalPolicy = policy(Leg.ACCOUNTING_TRANSACTION, DateRule.CLOSING_RATE);
        AccountingDates ad = new AccountingDates(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null);
        FxRequestContext context = new FxRequestContext(Purpose.ACCOUNTING_REVALUATION, LocalDate.of(2026, 12, 31), null,
                RunMode.AD_HOC, "UNIT-1", AmountType.NOMINAL, SettlementAmountState.UNINVOICED,
                ItemType.NON_MONETARY_HISTORICAL, null, Map.of(), ad, null, List.of(), new PolicyRef.Inline(revalPolicy), "req-f09");
        ConversionRequest req = new ConversionRequest(context, new CurrencyCode("USD"), new CurrencyCode("GBP"),
                new java.math.BigDecimal("100.00"), false);
        ConversionResult result = harness.converter().convert(req);
        assertFalse(result.isSuccess());
        assertEquals(FxErrorCode.FX_V_INVALID_POLICY, result.error().get().code());
    }
}
