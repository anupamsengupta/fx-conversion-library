package com.power.fx.core.cache;

import com.power.fx.api.model.AccountingFxPolicy;
import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.SettlementCalendar;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.core.date.CalendarIndex;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates raw reference-data versions and compiles them into one
 * immutable {@link ReferenceCatalogue} generation (S7.1.2, S8.2 step 5).
 *
 * <p><strong>Documented simplification:</strong> the tech spec's S8.2 step
 * 5 describes rebuilding "only affected {@code Timeline}s ... and compiled
 * indexes" copy-on-write, sharing everything else structurally. This
 * builder instead re-seeds the full version list from the previous
 * generation ({@link #seedFrom(ReferenceCatalogue)}) and rebuilds every
 * {@link Timeline}/{@link CalendarIndex} on every {@link #build} call --
 * correct (the externally observable atomic-swap-per-generation contract
 * and GLOBAL/TENANT overlay semantics are unaffected) but not the
 * fine-grained incremental rebuild the spec sketches as an optimisation.
 * For the reference-data volumes A-03 assumes ("thousands of records"),
 * this trades a constant-factor rebuild cost for materially simpler,
 * more obviously correct ingestion code within this task's time budget.
 */
public final class CatalogueBuilder {

    private final Map<String, List<Currency>> currencies = new HashMap<>();
    private final Map<String, List<PairConvention>> pairConventions = new HashMap<>();
    private final Map<String, List<FixedFactor>> fixedFactors = new HashMap<>();
    private final Map<String, List<FixingSource>> fixingSources = new HashMap<>();
    private final Map<String, List<PublicationCalendar>> publicationCalendars = new HashMap<>();
    private final Map<String, List<SettlementCalendar>> settlementCalendars = new HashMap<>();
    private final Map<String, List<AccountingUnit>> accountingUnits = new HashMap<>();
    private final Map<String, List<FxPolicy>> fxPolicies = new HashMap<>();
    private final Map<String, List<AccountingFxPolicy>> accountingPolicies = new HashMap<>();
    private final Map<String, List<SourceEntitlement>> entitlements = new HashMap<>();
    private final Map<String, List<ManualRateOverride>> overrides = new HashMap<>();

    private final Set<String> staleKeys = new HashSet<>();
    private Instant watermark = Instant.EPOCH;

    public CatalogueBuilder seedFrom(ReferenceCatalogue previous) {
        if (previous == null) {
            return this;
        }
        previous.currencyTimelines().forEach((k, t) -> currencies.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.pairConventionTimelines().forEach((k, t) -> pairConventions.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.fixedFactorTimelines().forEach((k, t) -> fixedFactors.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.fixingSourceTimelines().forEach((k, t) -> fixingSources.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.publicationCalendarTimelines().forEach((k, t) -> publicationCalendars.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.settlementCalendarTimelines().forEach((k, t) -> settlementCalendars.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.accountingUnitTimelines().forEach((k, t) -> accountingUnits.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.fxPolicyTimelines().forEach((k, t) -> fxPolicies.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.accountingPolicyTimelines().forEach((k, t) -> accountingPolicies.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.entitlementTimelines().forEach((k, t) -> entitlements.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        previous.overrideTimelines().forEach((k, t) -> overrides.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t.versions()));
        staleKeys.addAll(previous.staleKeys());
        watermark = previous.recordedAtHighWatermark();
        return this;
    }

    public CatalogueBuilder markStale(String key) {
        staleKeys.add(key);
        return this;
    }

    public CatalogueBuilder clearStale(String key) {
        staleKeys.remove(key);
        return this;
    }

    public CatalogueBuilder advanceWatermark(Instant t) {
        if (t.isAfter(watermark)) {
            watermark = t;
        }
        return this;
    }

    public CatalogueBuilder addCurrency(Currency c) {
        currencies.computeIfAbsent(c.code().value(), k -> new ArrayList<>()).add(c);
        return this;
    }

    public CatalogueBuilder addPairConvention(PairConvention p) {
        pairConventions.computeIfAbsent(p.marketConvention().canonical(), k -> new ArrayList<>()).add(p);
        return this;
    }

    public CatalogueBuilder addFixedFactor(FixedFactor f) {
        fixedFactors.computeIfAbsent(f.from().value() + ">" + f.to().value(), k -> new ArrayList<>()).add(f);
        return this;
    }

    public CatalogueBuilder addFixingSource(FixingSource s) {
        fixingSources.computeIfAbsent(s.sourceCode(), k -> new ArrayList<>()).add(s);
        return this;
    }

    public CatalogueBuilder addPublicationCalendar(PublicationCalendar c) {
        publicationCalendars.computeIfAbsent(c.calendarRef(), k -> new ArrayList<>()).add(c);
        return this;
    }

    public CatalogueBuilder addSettlementCalendar(SettlementCalendar c) {
        settlementCalendars.computeIfAbsent(c.calendarRef(), k -> new ArrayList<>()).add(c);
        return this;
    }

    public CatalogueBuilder addAccountingUnit(AccountingUnit u) {
        accountingUnits.computeIfAbsent(u.unitId(), k -> new ArrayList<>()).add(u);
        return this;
    }

    public CatalogueBuilder addFxPolicy(FxPolicy p) {
        fxPolicies.computeIfAbsent(p.policyId(), k -> new ArrayList<>()).add(p);
        return this;
    }

    public CatalogueBuilder addAccountingPolicy(AccountingFxPolicy p) {
        accountingPolicies.computeIfAbsent(p.unitId() + "|" + p.purpose(), k -> new ArrayList<>()).add(p);
        return this;
    }

    public CatalogueBuilder addEntitlement(SourceEntitlement e) {
        entitlements.computeIfAbsent(e.sourceCode(), k -> new ArrayList<>()).add(e);
        return this;
    }

    public CatalogueBuilder addOverride(ManualRateOverride o) {
        String key = o.scope() + "|" + o.pair().canonical() + "|" + o.fxDate() + "|"
                + (o.sourceCode() == null ? "" : o.sourceCode());
        overrides.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        return this;
    }

    /** Dispatches by {@link FxEntityType}, for ingestion (S8.2). */
    @SuppressWarnings("unchecked")
    public CatalogueBuilder add(FxEntityType type, Object payload) {
        switch (type) {
            case CURRENCY -> addCurrency((Currency) payload);
            case PAIR_CONVENTION -> addPairConvention((PairConvention) payload);
            case FIXED_FACTOR -> addFixedFactor((FixedFactor) payload);
            case FIXING_SOURCE -> addFixingSource((FixingSource) payload);
            case PUBLICATION_CALENDAR -> addPublicationCalendar((PublicationCalendar) payload);
            case SETTLEMENT_CALENDAR -> addSettlementCalendar((SettlementCalendar) payload);
            case ACCOUNTING_UNIT -> addAccountingUnit((AccountingUnit) payload);
            case FX_POLICY -> addFxPolicy((FxPolicy) payload);
            case ACCOUNTING_FX_POLICY -> addAccountingPolicy((AccountingFxPolicy) payload);
            case SOURCE_ENTITLEMENT -> addEntitlement((SourceEntitlement) payload);
            case MANUAL_RATE_OVERRIDE -> addOverride((ManualRateOverride) payload);
            default -> throw new IllegalArgumentException("not a reference-data entity type: " + type);
        }
        return this;
    }

    public ReferenceCatalogue build(ReferenceCatalogue global, long generation) {
        Map<String, Timeline<Currency>> currencyTimelines = timelines(currencies, c -> c.envelope());
        Map<String, Timeline<PairConvention>> pairConventionTimelines = timelines(pairConventions, p -> p.envelope());
        Map<String, Timeline<FixedFactor>> fixedFactorTimelines = timelines(fixedFactors, f -> f.envelope());
        Map<String, Timeline<FixingSource>> fixingSourceTimelines = timelines(fixingSources, s -> s.envelope());
        Map<String, Timeline<PublicationCalendar>> publicationCalendarTimelines = timelines(publicationCalendars, c -> c.envelope());
        Map<String, Timeline<SettlementCalendar>> settlementCalendarTimelines = timelines(settlementCalendars, c -> c.envelope());
        Map<String, Timeline<AccountingUnit>> accountingUnitTimelines = timelines(accountingUnits, u -> u.envelope());
        Map<String, Timeline<FxPolicy>> fxPolicyTimelines = timelines(fxPolicies, p -> p.envelope());
        Map<String, Timeline<AccountingFxPolicy>> accountingPolicyTimelines = timelines(accountingPolicies, p -> p.envelope());
        Map<String, Timeline<SourceEntitlement>> entitlementTimelines = timelines(entitlements, e -> e.envelope());
        Map<String, Timeline<ManualRateOverride>> overrideTimelines = timelines(overrides, o -> o.envelope());

        Map<String, CalendarIndex> calendarIndexes = new HashMap<>();
        for (List<PublicationCalendar> versions : publicationCalendars.values()) {
            for (PublicationCalendar v : versions) {
                calendarIndexes.put(v.envelope().versionId(), CalendarIndex.ofPublicationCalendar(v));
            }
        }
        for (List<SettlementCalendar> versions : settlementCalendars.values()) {
            for (SettlementCalendar v : versions) {
                calendarIndexes.put(v.envelope().versionId(), CalendarIndex.ofSettlementCalendar(v));
            }
        }

        return new ReferenceCatalogue(global, generation, watermark, staleKeys,
                currencyTimelines, pairConventionTimelines, fixedFactorTimelines, fixingSourceTimelines,
                publicationCalendarTimelines, settlementCalendarTimelines, accountingUnitTimelines,
                fxPolicyTimelines, accountingPolicyTimelines, entitlementTimelines, overrideTimelines,
                calendarIndexes);
    }

    private static <V> Map<String, Timeline<V>> timelines(Map<String, List<V>> raw,
            java.util.function.Function<V, com.power.fx.api.model.VersionEnvelope> envelopeOf) {
        Map<String, Timeline<V>> result = new HashMap<>();
        raw.forEach((key, versions) -> result.put(key, Timeline.of(versions, envelopeOf)));
        return result;
    }
}
