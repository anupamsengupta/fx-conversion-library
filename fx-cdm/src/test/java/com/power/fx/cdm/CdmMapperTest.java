package com.power.fx.cdm;

import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.model.AccountingFxPolicy;
import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.MarketSnapshotPayload;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.SettlementCalendar;
import com.power.fx.api.model.SourceEntitlement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@code CdmFxEventMapper}'s dispatch-by-{@code entityType} routing
 * for all thirteen {@link FxEntityType} values, decimal-parsing
 * correctness, and malformed-payload rejection (Task 3a.4's acceptance
 * criterion).
 *
 * <p><strong>What this test class proves, and what it does not.</strong>
 * It proves the mapping <em>shape</em> and dispatch logic are correct
 * against the illustrative placeholder field names chosen in
 * {@link CdmReferenceMapper}/{@link CdmFixingMapper}/{@link
 * CdmSnapshotMapper} (themselves documented as placeholders pending
 * TI-01). It cannot prove, and does not claim to prove, that those field
 * names match a real CDM schema -- no such schema exists yet to check
 * against. Re-running this suite (or a rewritten equivalent) against the
 * real field names is required once TI-01 is answered; see the
 * implementation plan's Phase 3a acceptance gate.
 *
 * @see "Implementation plan Phase 3a Task 3a.4; tech spec S6.17; TI-01"
 */
class CdmMapperTest {

    private static final String RECORDED_AT = "2020-01-01T00:00:00Z";

    private static Map<String, String> envelopeFields() {
        Map<String, String> f = new HashMap<>();
        f.put("versionId", "v1");
        f.put("validFrom", "2000-01-01");
        f.put("recordedAt", RECORDED_AT);
        f.put("status", "APPROVED");
        f.put("authoredBy", "loader");
        f.put("approvedBy", "approver");
        f.put("approvedAt", RECORDED_AT);
        f.put("sourceSystem", "TEST");
        f.put("catalogueRelease", "rel-1");
        return f;
    }

    private static CdmFxEvent event(FxEntityType type, String naturalKey, Map<String, String> extra) {
        Map<String, String> fields = envelopeFields();
        fields.putAll(extra);
        return new CdmFxEvent("evt-" + naturalKey, type, "GLOBAL", null, naturalKey, 1L, RECORDED_AT, fields);
    }

    private static FxIngestRecord mapOrFail(CdmFxEvent event) {
        CdmMappingResult result = CdmFxEventMapper.map(event);
        assertInstanceOf(CdmMappingResult.Mapped.class, result,
                () -> "expected successful mapping but got: " + result);
        return ((CdmMappingResult.Mapped) result).record();
    }

    // --- dispatch-by-entityType routing: all thirteen FxEntityType values ---

    @Test
    void currency() {
        Map<String, String> extra = Map.of("currencyCode", "USD", "decimals", "2", "deliverable", "true");
        FxIngestRecord record = mapOrFail(event(FxEntityType.CURRENCY, "USD", extra));
        assertEquals(FxEntityType.CURRENCY, record.entityType());
        Currency currency = assertInstanceOf(Currency.class, record.payload());
        assertEquals(new CurrencyCode("USD"), currency.code());
        assertEquals(2, currency.decimals());
    }

    @Test
    void pairConvention() {
        Map<String, String> extra = Map.ofEntries(
                Map.entry("base", "EUR"), Map.entry("quote", "USD"),
                Map.entry("pipPrecision", "4"), Map.entry("pointsScale", "10000"),
                Map.entry("spotLag", "2"), Map.entry("forwardMethod", "POINTS"),
                Map.entry("interpolation", "LOG_LINEAR_CARRY"), Map.entry("maxExtrapolationYears", "2"));
        FxIngestRecord record = mapOrFail(event(FxEntityType.PAIR_CONVENTION, "EUR/USD", extra));
        PairConvention pc = assertInstanceOf(PairConvention.class, record.payload());
        assertEquals(4, pc.pipPrecision());
        assertEquals(new BigDecimal("10000"), pc.pointsScale());
    }

    @Test
    void fixedFactor() {
        Map<String, String> extra = Map.of(
                "from", "GBp", "to", "GBP", "factor", "0.01", "kind", "MINOR_UNIT");
        FxIngestRecord record = mapOrFail(event(FxEntityType.FIXED_FACTOR, "GBp/GBP", extra));
        FixedFactor factor = assertInstanceOf(FixedFactor.class, record.payload());
        assertEquals(new BigDecimal("0.01"), factor.factor());
    }

    @Test
    void fixingSource() {
        Map<String, String> extra = Map.of(
                "sourceCode", "ECB", "cutoffTime", "14:15:00", "cutoffZone", "Europe/Berlin",
                "publicationCalendarRef", "ECB-CAL", "pairsPublished", "EUR/USD,GBP/USD",
                "usageClass", "INVOICING_ELIGIBLE");
        FxIngestRecord record = mapOrFail(event(FxEntityType.FIXING_SOURCE, "ECB", extra));
        FixingSource source = assertInstanceOf(FixingSource.class, record.payload());
        assertEquals("ECB", source.sourceCode());
        assertEquals(2, source.pairsPublished().size());
    }

    @Test
    void publicationCalendar() {
        Map<String, String> extra = Map.of(
                "calendarRef", "ECB-CAL", "zone", "Europe/Berlin",
                "coverageStart", "2020-01-01", "coverageEnd", "2030-12-31",
                "publicationDates", "2020-01-02,2020-01-03");
        FxIngestRecord record = mapOrFail(event(FxEntityType.PUBLICATION_CALENDAR, "ECB-CAL", extra));
        PublicationCalendar cal = assertInstanceOf(PublicationCalendar.class, record.payload());
        assertEquals(2, cal.publicationDates().size());
    }

    @Test
    void settlementCalendar() {
        Map<String, String> extra = Map.of(
                "calendarRef", "TARGET", "zone", "Europe/Berlin",
                "coverageStart", "2020-01-01", "coverageEnd", "2030-12-31",
                "weekend", "SATURDAY,SUNDAY");
        FxIngestRecord record = mapOrFail(event(FxEntityType.SETTLEMENT_CALENDAR, "TARGET", extra));
        SettlementCalendar cal = assertInstanceOf(SettlementCalendar.class, record.payload());
        assertEquals(2, cal.weekend().size());
    }

    @Test
    void accountingUnit() {
        Map<String, String> extra = Map.of(
                "unitId", "UNIT-1", "legalEntityId", "LE-1", "functionalCurrency", "EUR",
                "presentationCurrencies", "EUR,USD");
        FxIngestRecord record = mapOrFail(event(FxEntityType.ACCOUNTING_UNIT, "UNIT-1", extra));
        AccountingUnit unit = assertInstanceOf(AccountingUnit.class, record.payload());
        assertEquals(2, unit.presentationCurrencies().size());
    }

    @Test
    void fxPolicy() {
        Map<String, String> extra = Map.ofEntries(
                Map.entry("policyId", "POL-1"), Map.entry("version", "1"),
                Map.entry("leg", "CONTRACT"), Map.entry("dateRule", "TRADE_DATE"),
                Map.entry("nonPublicationDayHandling", "USE_PREVIOUS"),
                Map.entry("rollConvention", "FOLLOWING"),
                Map.entry("fixingVersionSelection", "FIRST_OFFICIAL"),
                Map.entry("futureDateTreatment", "SPOT"),
                Map.entry("spotAdjustment", "NONE"),
                Map.entry("estimatedEventHandling", "FAIL"));
        FxIngestRecord record = mapOrFail(event(FxEntityType.FX_POLICY, "POL-1", extra));
        FxPolicy policy = assertInstanceOf(FxPolicy.class, record.payload());
        assertEquals("POL-1", policy.policyId());
        assertTrue(policy.fixingVersionSelection() instanceof com.power.fx.api.model.FixingVersionSelection.FirstOfficial);
    }

    @Test
    void fxPolicyWithAsOfKnowledgeSelection() {
        Map<String, String> extra = Map.ofEntries(
                Map.entry("policyId", "POL-2"), Map.entry("version", "1"),
                Map.entry("leg", "CONTRACT"), Map.entry("dateRule", "TRADE_DATE"),
                Map.entry("nonPublicationDayHandling", "USE_PREVIOUS"),
                Map.entry("rollConvention", "FOLLOWING"),
                Map.entry("fixingVersionSelection", "AS_OF_KNOWLEDGE"),
                Map.entry("fixingVersionSelectionAsOf", RECORDED_AT),
                Map.entry("futureDateTreatment", "SPOT"),
                Map.entry("spotAdjustment", "NONE"),
                Map.entry("estimatedEventHandling", "FAIL"));
        FxIngestRecord record = mapOrFail(event(FxEntityType.FX_POLICY, "POL-2", extra));
        FxPolicy policy = assertInstanceOf(FxPolicy.class, record.payload());
        assertInstanceOf(com.power.fx.api.model.FixingVersionSelection.AsOfKnowledge.class,
                policy.fixingVersionSelection());
    }

    @Test
    void accountingFxPolicy() {
        Map<String, String> extra = Map.of("unitId", "UNIT-1", "purpose", "ACCOUNTING_RECOGNITION");
        FxIngestRecord record = mapOrFail(event(FxEntityType.ACCOUNTING_FX_POLICY, "UNIT-1/ACCOUNTING_RECOGNITION", extra));
        AccountingFxPolicy policy = assertInstanceOf(AccountingFxPolicy.class, record.payload());
        assertEquals("UNIT-1", policy.unitId());
    }

    @Test
    void sourceEntitlement() {
        CdmFxEvent evt = new CdmFxEvent("evt-ent", FxEntityType.SOURCE_ENTITLEMENT, "TENANT", "TN_TEST",
                "TN_TEST/ECB", 1L, RECORDED_AT,
                mergeExtra(envelopeFields(), Map.of("sourceCode", "ECB", "rights", "VALUATION,DISPLAY")));
        FxIngestRecord record = mapOrFail(evt);
        SourceEntitlement ent = assertInstanceOf(SourceEntitlement.class, record.payload());
        assertEquals(2, ent.rights().size());
    }

    @Test
    void manualRateOverride() {
        Map<String, String> extra = Map.of(
                "scope", "GLOBAL", "base", "EUR", "quote", "USD", "fxDate", "2024-06-03",
                "rate", "1.0850", "reasonCode", "MANUAL_CORRECTION");
        FxIngestRecord record = mapOrFail(event(FxEntityType.MANUAL_RATE_OVERRIDE, "EUR/USD/2024-06-03", extra));
        ManualRateOverride override = assertInstanceOf(ManualRateOverride.class, record.payload());
        assertEquals(new BigDecimal("1.0850"), override.rate());
    }

    @Test
    void fixing() {
        Map<String, String> extra = Map.of(
                "sourceCode", "ECB", "base", "EUR", "quote", "USD", "fixingDate", "2024-06-03",
                "value", "1.0850", "fixingStatus", "OFFICIAL");
        FxIngestRecord record = mapOrFail(event(FxEntityType.FIXING, "ECB/EUR/USD/2024-06-03", extra));
        assertEquals(FxEntityType.FIXING, record.entityType());
        FixingVersion fixing = assertInstanceOf(FixingVersion.class, record.payload());
        assertEquals(new BigDecimal("1.0850"), fixing.value());
    }

    @Test
    void marketSnapshot() {
        Map<String, String> extra = Map.of(
                "marketSnapshotId", "SNAP-1", "kind", "EOD", "asOfDate", "2024-06-03",
                "fixingKnowledgeCut", RECORDED_AT, "signOffStatus", "SIGNED_OFF",
                "chunkIndex", "0", "chunkCount", "1", "completionMarker", "true");
        FxIngestRecord record = mapOrFail(event(FxEntityType.MARKET_SNAPSHOT, "SNAP-1", extra));
        MarketSnapshotPayload snapshot = assertInstanceOf(MarketSnapshotPayload.class, record.payload());
        assertEquals(1, snapshot.chunkCount());
        assertTrue(snapshot.completionMarker());
    }

    // --- decimal-parsing correctness, exercised through a real mapper field ---

    @Test
    void decimalFieldsParseExactlyNotViaDouble() {
        Map<String, String> extra = Map.of(
                "scope", "GLOBAL", "base", "EUR", "quote", "USD", "fxDate", "2024-06-03",
                "rate", "0.1000000000000000000000000000001", "reasonCode", "MANUAL_CORRECTION");
        FxIngestRecord record = mapOrFail(event(FxEntityType.MANUAL_RATE_OVERRIDE, "EUR/USD/2024-06-03", extra));
        ManualRateOverride override = assertInstanceOf(ManualRateOverride.class, record.payload());
        assertEquals(new BigDecimal("0.1000000000000000000000000000001"), override.rate());
    }

    // --- malformed/missing-field input: rejection, never an exception ---

    @Test
    void missingRequiredFieldYieldsRejectionNotException() {
        Map<String, String> extra = Map.of("decimals", "2"); // currencyCode missing
        CdmFxEvent evt = event(FxEntityType.CURRENCY, "USD", extra);
        CdmMappingResult result = CdmFxEventMapper.map(evt);
        CdmMappingResult.Rejected rejected = assertInstanceOf(CdmMappingResult.Rejected.class, result);
        assertEquals(FxIngestCode.FX_I_INVALID_VALUE, rejected.rejection().code());
        assertEquals("evt-USD", rejected.rejection().eventId());
    }

    @Test
    void malformedDecimalYieldsRejectionNotException() {
        Map<String, String> extra = Map.of("currencyCode", "USD", "decimals", "not-a-number");
        CdmMappingResult result = CdmFxEventMapper.map(event(FxEntityType.CURRENCY, "USD", extra));
        assertInstanceOf(CdmMappingResult.Rejected.class, result);
    }

    @Test
    void malformedEnumValueYieldsRejectionNotException() {
        Map<String, String> extra = Map.of(
                "from", "GBp", "to", "GBP", "factor", "0.01", "kind", "NOT_A_REAL_KIND");
        CdmMappingResult result = CdmFxEventMapper.map(event(FxEntityType.FIXED_FACTOR, "GBp/GBP", extra));
        assertInstanceOf(CdmMappingResult.Rejected.class, result);
    }

    @Test
    void malformedTopLevelScopeYieldsRejectionNotException() {
        CdmFxEvent evt = new CdmFxEvent("evt-bad-scope", FxEntityType.CURRENCY, "NOT_A_SCOPE", null, "USD", 1L,
                RECORDED_AT, mergeExtra(envelopeFields(), Map.of("currencyCode", "USD", "decimals", "2")));
        CdmMappingResult result = CdmFxEventMapper.map(evt);
        assertInstanceOf(CdmMappingResult.Rejected.class, result);
    }

    @Test
    void malformedPublishedAtYieldsRejectionNotException() {
        CdmFxEvent evt = new CdmFxEvent("evt-bad-ts", FxEntityType.CURRENCY, "GLOBAL", null, "USD", 1L,
                "not-an-instant", mergeExtra(envelopeFields(), Map.of("currencyCode", "USD", "decimals", "2")));
        CdmMappingResult result = CdmFxEventMapper.map(evt);
        assertInstanceOf(CdmMappingResult.Rejected.class, result);
    }

    private static Map<String, String> mergeExtra(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new HashMap<>(base);
        merged.putAll(extra);
        return merged;
    }
}
