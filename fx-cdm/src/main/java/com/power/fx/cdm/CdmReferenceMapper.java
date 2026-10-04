package com.power.fx.cdm;

import com.power.fx.api.model.AccountingFxPolicy;
import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixedFactorKind;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.FutureDateTreatment;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.NonPublicationDayHandling;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RollConvention;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementCalendar;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.api.model.SourceRight;
import com.power.fx.api.model.SpotAdjustment;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.UsageClass;
import com.power.fx.api.model.VersionEnvelope;
import com.power.fx.api.model.VersionStatus;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.FxEntityType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Maps a {@link CdmFxEvent} carrying one of the eleven reference
 * {@link FxEntityType} values into the corresponding {@code fx-api}
 * reference-data value object.
 *
 * <p><strong>Scaffolded and blocked on TI-01</strong> (implementation
 * plan Phase 3a Task 3a.2): the field names read out of
 * {@link CdmFxEvent#fields()} below (e.g. {@code "currencyCode"},
 * {@code "decimals"}) are illustrative only, invented so the mapping
 * shape and per-entity dispatch can be built and tested now. They are
 * expected to be replaced, not refined, once TI-01 names a real schema.
 *
 * <p><strong>Known placeholder simplification:</strong> {@link FxPolicy}
 * carries several deeply nested sub-objects ({@code offset},
 * {@code averaging}, {@code fallbackChain}, {@code rounding},
 * {@code contractRate}) that do not fit naturally into a flat
 * {@code Map<String, String>} payload. This mapper leaves them
 * {@code null}/empty (all are nullable or default-to-empty in
 * {@link FxPolicy}'s compact constructor) rather than inventing a flat
 * encoding for nested structure that a real, structured CDM schema would
 * almost certainly represent differently. This is flagged here, not
 * silently done.
 *
 * <p>Pure functions over {@code Map<String, String>}; no transport, no
 * I/O, no dependency on {@code fx-core} (Appendix A, MC-5).
 *
 * @see "Implementation plan Phase 3a Task 3a.2; tech spec S6.17; TI-01"
 */
final class CdmReferenceMapper {

    private CdmReferenceMapper() {
    }

    /** Dispatches on {@code event.entityType()}; {@code entityType} must be one of the eleven reference values. */
    static Object map(CdmFxEvent event) {
        Map<String, String> f = event.fields();
        return switch (event.entityType()) {
            case CURRENCY -> mapCurrency(event, f);
            case PAIR_CONVENTION -> mapPairConvention(event, f);
            case FIXED_FACTOR -> mapFixedFactor(event, f);
            case FIXING_SOURCE -> mapFixingSource(event, f);
            case PUBLICATION_CALENDAR -> mapPublicationCalendar(event, f);
            case SETTLEMENT_CALENDAR -> mapSettlementCalendar(event, f);
            case ACCOUNTING_UNIT -> mapAccountingUnit(event, f);
            case FX_POLICY -> mapFxPolicy(event, f);
            case ACCOUNTING_FX_POLICY -> mapAccountingFxPolicy(event, f);
            case SOURCE_ENTITLEMENT -> mapSourceEntitlement(event, f);
            case MANUAL_RATE_OVERRIDE -> mapManualRateOverride(event, f);
            case FIXING, MARKET_SNAPSHOT -> throw new IllegalArgumentException(
                    "not a reference entity type: " + event.entityType());
        };
    }

    /**
     * Builds the common {@link VersionEnvelope} shared by every reference
     * entity, from {@code event.scope()}/{@code tenantId()}/{@code naturalKey()}
     * plus the envelope-metadata keys of the flat field bag.
     */
    private static VersionEnvelope buildEnvelope(CdmFxEvent event, Map<String, String> f) {
        Scope scope = CdmFields.parseEnum(event.scope(), Scope.class, "scope");
        return new VersionEnvelope(
                scope,
                event.tenantId(),
                event.naturalKey(),
                CdmFields.require(f, "versionId"),
                CdmFields.requireDate(f, "validFrom"),
                CdmFields.optionalDate(f, "validTo"),
                CdmFields.requireInstant(f, "recordedAt"),
                CdmFields.requireEnum(f, "status", VersionStatus.class),
                CdmFields.require(f, "authoredBy"),
                CdmFields.require(f, "approvedBy"),
                CdmFields.requireInstant(f, "approvedAt"),
                CdmFields.optional(f, "sourceSystem"),
                CdmFields.optional(f, "correctionOf"),
                CdmFields.optional(f, "reasonCode"),
                CdmFields.optional(f, "catalogueRelease"));
    }

    private static Currency mapCurrency(CdmFxEvent event, Map<String, String> f) {
        return new Currency(
                buildEnvelope(event, f),
                CdmFields.requireCurrency(f, "currencyCode"),
                CdmFields.requireInt(f, "decimals"),
                CdmFields.optionalCurrency(f, "majorCurrency"),
                CdmFields.optional(f, "settlementCalendarRef"),
                CdmFields.optionalBoolean(f, "deliverable", true));
    }

    private static PairConvention mapPairConvention(CdmFxEvent event, Map<String, String> f) {
        CurrencyPair marketConvention = new CurrencyPair(
                CdmFields.requireCurrency(f, "base"), CdmFields.requireCurrency(f, "quote"));
        List<String> spotCalendars = CdmFields.splitList(f, "spotCalendars");
        return new PairConvention(
                buildEnvelope(event, f),
                marketConvention,
                CdmFields.requireInt(f, "pipPrecision"),
                CdmFields.requireDecimal(f, "pointsScale"),
                CdmFields.requireInt(f, "spotLag"),
                spotCalendars,
                CdmFields.optionalCurrency(f, "triangulationVia"),
                CdmFields.requireEnum(f, "forwardMethod", ForwardMethod.class),
                CdmFields.requireEnum(f, "interpolation", InterpolationMethod.class),
                CdmFields.requireDecimal(f, "maxExtrapolationYears"),
                Map.of());
    }

    private static FixedFactor mapFixedFactor(CdmFxEvent event, Map<String, String> f) {
        return new FixedFactor(
                buildEnvelope(event, f),
                CdmFields.requireCurrency(f, "from"),
                CdmFields.requireCurrency(f, "to"),
                CdmFields.requireDecimal(f, "factor"),
                CdmFields.requireEnum(f, "kind", FixedFactorKind.class),
                CdmFields.optionalBoolean(f, "preferOverMarket", false));
    }

    private static FixingSource mapFixingSource(CdmFxEvent event, Map<String, String> f) {
        LocalTime cutoffTime = CdmFields.requireTime(f, "cutoffTime");
        ZoneId cutoffZone = CdmFields.requireZone(f, "cutoffZone");
        Set<CurrencyPair> pairsPublished = new LinkedHashSet<>();
        for (String raw : CdmFields.splitList(f, "pairsPublished")) {
            String[] parts = raw.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("malformed pair in pairsPublished: " + raw);
            }
            pairsPublished.add(new CurrencyPair(new CurrencyCode(parts[0]), new CurrencyCode(parts[1])));
        }
        return new FixingSource(
                buildEnvelope(event, f),
                CdmFields.require(f, "sourceCode"),
                cutoffTime,
                cutoffZone,
                CdmFields.require(f, "publicationCalendarRef"),
                pairsPublished,
                CdmFields.optional(f, "ndfTemplate"),
                CdmFields.requireEnum(f, "usageClass", UsageClass.class));
    }

    private static PublicationCalendar mapPublicationCalendar(CdmFxEvent event, Map<String, String> f) {
        LocalDateRange coverage = new LocalDateRange(
                CdmFields.requireDate(f, "coverageStart"), CdmFields.requireDate(f, "coverageEnd"));
        SortedSet<LocalDate> publicationDates = new TreeSet<>();
        for (String raw : CdmFields.splitList(f, "publicationDates")) {
            publicationDates.add(LocalDate.parse(raw));
        }
        return new PublicationCalendar(
                buildEnvelope(event, f),
                CdmFields.require(f, "calendarRef"),
                CdmFields.requireZone(f, "zone"),
                coverage,
                publicationDates);
    }

    private static SettlementCalendar mapSettlementCalendar(CdmFxEvent event, Map<String, String> f) {
        LocalDateRange coverage = new LocalDateRange(
                CdmFields.requireDate(f, "coverageStart"), CdmFields.requireDate(f, "coverageEnd"));
        SortedSet<LocalDate> businessDates = new TreeSet<>();
        for (String raw : CdmFields.splitList(f, "businessDates")) {
            businessDates.add(LocalDate.parse(raw));
        }
        Set<java.time.DayOfWeek> weekend = new LinkedHashSet<>();
        for (String raw : CdmFields.splitList(f, "weekend")) {
            weekend.add(java.time.DayOfWeek.valueOf(raw));
        }
        return new SettlementCalendar(
                buildEnvelope(event, f),
                CdmFields.require(f, "calendarRef"),
                CdmFields.requireZone(f, "zone"),
                coverage,
                businessDates,
                weekend);
    }

    private static AccountingUnit mapAccountingUnit(CdmFxEvent event, Map<String, String> f) {
        List<CurrencyCode> presentationCurrencies = CdmFields.splitList(f, "presentationCurrencies").stream()
                .map(CurrencyCode::new)
                .toList();
        return new AccountingUnit(
                buildEnvelope(event, f),
                CdmFields.require(f, "unitId"),
                CdmFields.require(f, "legalEntityId"),
                CdmFields.requireCurrency(f, "functionalCurrency"),
                presentationCurrencies,
                CdmFields.optional(f, "parentUnitId"),
                CdmFields.optional(f, "accountingPolicyRef"));
    }

    private static FxPolicy mapFxPolicy(CdmFxEvent event, Map<String, String> f) {
        FixingVersionSelection selection = switch (CdmFields.require(f, "fixingVersionSelection")) {
            case "FIRST_OFFICIAL" -> new FixingVersionSelection.FirstOfficial();
            case "LATEST_CORRECTED" -> new FixingVersionSelection.LatestCorrected();
            case "AS_OF_KNOWLEDGE" -> new FixingVersionSelection.AsOfKnowledge(
                    CdmFields.requireInstant(f, "fixingVersionSelectionAsOf"));
            case String other -> throw new IllegalArgumentException(
                    "unrecognised fixingVersionSelection: " + other);
        };
        List<String> rateSourcePriority = CdmFields.splitList(f, "rateSourcePriority");
        return new FxPolicy(
                buildEnvelope(event, f),
                CdmFields.require(f, "policyId"),
                CdmFields.requireInt(f, "version"),
                CdmFields.requireEnum(f, "leg", Leg.class),
                CdmFields.requireEnum(f, "dateRule", DateRule.class),
                null, // offset: nested object, not represented in the flat placeholder payload (see class Javadoc)
                CdmFields.requireEnum(f, "nonPublicationDayHandling", NonPublicationDayHandling.class),
                CdmFields.requireEnum(f, "rollConvention", RollConvention.class),
                rateSourcePriority,
                selection,
                CdmFields.requireEnum(f, "futureDateTreatment", FutureDateTreatment.class),
                CdmFields.requireEnum(f, "spotAdjustment", SpotAdjustment.class),
                null, // averaging: nested object, see class Javadoc
                List.of(), // fallbackChain: nested list, see class Javadoc
                CdmFields.optionalBoolean(f, "allowMixedSources", false),
                CdmFields.optionalCurrency(f, "forceCrossVia"),
                null, // rounding: nested object, see class Javadoc
                null, // contractRate: nested object, see class Javadoc
                CdmFields.requireEnum(f, "estimatedEventHandling", EstimatedEventHandling.class),
                CdmFields.optionalBoolean(f, "allowOverrides", false));
    }

    private static AccountingFxPolicy mapAccountingFxPolicy(CdmFxEvent event, Map<String, String> f) {
        return new AccountingFxPolicy(
                buildEnvelope(event, f),
                CdmFields.require(f, "unitId"),
                CdmFields.requireEnum(f, "purpose", Purpose.class),
                CdmFields.optional(f, "settlementToFunctionalPolicyId"),
                CdmFields.optional(f, "functionalToPresentationPolicyId"));
    }

    private static SourceEntitlement mapSourceEntitlement(CdmFxEvent event, Map<String, String> f) {
        Set<SourceRight> rights = new LinkedHashSet<>();
        for (String raw : CdmFields.splitList(f, "rights")) {
            rights.add(CdmFields.parseEnum(raw, SourceRight.class, "rights"));
        }
        return new SourceEntitlement(
                buildEnvelope(event, f),
                event.tenantId() != null ? event.tenantId() : CdmFields.require(f, "tenantId"),
                CdmFields.require(f, "sourceCode"),
                rights);
    }

    private static ManualRateOverride mapManualRateOverride(CdmFxEvent event, Map<String, String> f) {
        Scope scope = CdmFields.requireEnum(f, "scope", Scope.class);
        CurrencyPair pair = new CurrencyPair(CdmFields.requireCurrency(f, "base"), CdmFields.requireCurrency(f, "quote"));
        return new ManualRateOverride(
                buildEnvelope(event, f),
                scope,
                pair,
                CdmFields.requireDate(f, "fxDate"),
                CdmFields.optional(f, "sourceCode"),
                CdmFields.requireDecimal(f, "rate"),
                CdmFields.require(f, "reasonCode"),
                CdmFields.optional(f, "ticketRef"));
    }
}
