package com.power.fx.core.cache;

import com.power.fx.api.model.AccountingFxPolicy;
import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementCalendar;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.core.date.CalendarIndex;
import com.power.fx.core.date.JointCalendarIndex;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * One immutable generation of reference data (S7.1.2). GLOBAL is a
 * separate instance ({@code global == null}, held once, shared read-only
 * across tenants). A TENANT catalogue holds a reference to the current
 * GLOBAL instance and resolves {@code TENANT union GLOBAL} with TENANT
 * winning at the same natural key (D-13); it always reads whichever
 * {@code ReferenceCatalogue} instance is passed to it at construction time
 * (TI-03's "hold a reference, not a copy" assumption is realised by the
 * caller -- typically {@code InMemoryReferenceStore} -- always
 * constructing the tenant catalogue against the store's current GLOBAL
 * reference rather than embedding a snapshot copy).
 *
 * @see "Tech spec S7.1.2"
 */
public final class ReferenceCatalogue {

    private final ReferenceCatalogue global; // null for the GLOBAL catalogue itself
    private final long generation;
    private final Instant recordedAtHighWatermark;
    private final Set<String> staleKeys;

    private final Map<String, Timeline<Currency>> currencies;
    private final Map<String, Timeline<PairConvention>> pairConventions;
    private final Map<String, Timeline<FixedFactor>> fixedFactors;
    private final Map<String, Timeline<FixingSource>> fixingSources;
    private final Map<String, Timeline<PublicationCalendar>> publicationCalendars;
    private final Map<String, Timeline<SettlementCalendar>> settlementCalendars;
    private final Map<String, Timeline<AccountingUnit>> accountingUnits;
    private final Map<String, Timeline<FxPolicy>> fxPolicies;
    private final Map<String, Timeline<AccountingFxPolicy>> accountingPolicies;
    private final Map<String, Timeline<SourceEntitlement>> entitlements;
    private final Map<String, Timeline<ManualRateOverride>> overrides;

    private final Map<String, CalendarIndex> calendarIndexes; // keyed by versionId
    private final ConcurrentHashMap<String, JointCalendarIndex> jointIndexes = new ConcurrentHashMap<>();

    public ReferenceCatalogue(
            ReferenceCatalogue global,
            long generation,
            Instant recordedAtHighWatermark,
            Set<String> staleKeys,
            Map<String, Timeline<Currency>> currencies,
            Map<String, Timeline<PairConvention>> pairConventions,
            Map<String, Timeline<FixedFactor>> fixedFactors,
            Map<String, Timeline<FixingSource>> fixingSources,
            Map<String, Timeline<PublicationCalendar>> publicationCalendars,
            Map<String, Timeline<SettlementCalendar>> settlementCalendars,
            Map<String, Timeline<AccountingUnit>> accountingUnits,
            Map<String, Timeline<FxPolicy>> fxPolicies,
            Map<String, Timeline<AccountingFxPolicy>> accountingPolicies,
            Map<String, Timeline<SourceEntitlement>> entitlements,
            Map<String, Timeline<ManualRateOverride>> overrides,
            Map<String, CalendarIndex> calendarIndexes) {
        this.global = global;
        this.generation = generation;
        this.recordedAtHighWatermark = Objects.requireNonNull(recordedAtHighWatermark);
        this.staleKeys = Set.copyOf(staleKeys);
        this.currencies = Map.copyOf(currencies);
        this.pairConventions = Map.copyOf(pairConventions);
        this.fixedFactors = Map.copyOf(fixedFactors);
        this.fixingSources = Map.copyOf(fixingSources);
        this.publicationCalendars = Map.copyOf(publicationCalendars);
        this.settlementCalendars = Map.copyOf(settlementCalendars);
        this.accountingUnits = Map.copyOf(accountingUnits);
        this.fxPolicies = Map.copyOf(fxPolicies);
        this.accountingPolicies = Map.copyOf(accountingPolicies);
        this.entitlements = Map.copyOf(entitlements);
        this.overrides = Map.copyOf(overrides);
        this.calendarIndexes = Map.copyOf(calendarIndexes);
    }

    public long generation() {
        return generation;
    }

    public Instant recordedAtHighWatermark() {
        return recordedAtHighWatermark;
    }

    public Set<String> staleKeys() {
        return staleKeys;
    }

    public boolean isStale(String key) {
        return staleKeys.contains(key) || (global != null && global.isStale(key));
    }

    // --- raw map accessors, for CatalogueBuilder re-seeding and for ingestion diffing ---

    public Map<String, Timeline<Currency>> currencyTimelines() {
        return currencies;
    }

    public Map<String, Timeline<PairConvention>> pairConventionTimelines() {
        return pairConventions;
    }

    public Map<String, Timeline<FixedFactor>> fixedFactorTimelines() {
        return fixedFactors;
    }

    public Map<String, Timeline<FixingSource>> fixingSourceTimelines() {
        return fixingSources;
    }

    public Map<String, Timeline<PublicationCalendar>> publicationCalendarTimelines() {
        return publicationCalendars;
    }

    public Map<String, Timeline<SettlementCalendar>> settlementCalendarTimelines() {
        return settlementCalendars;
    }

    public Map<String, Timeline<AccountingUnit>> accountingUnitTimelines() {
        return accountingUnits;
    }

    public Map<String, Timeline<FxPolicy>> fxPolicyTimelines() {
        return fxPolicies;
    }

    public Map<String, Timeline<AccountingFxPolicy>> accountingPolicyTimelines() {
        return accountingPolicies;
    }

    public Map<String, Timeline<SourceEntitlement>> entitlementTimelines() {
        return entitlements;
    }

    public Map<String, Timeline<ManualRateOverride>> overrideTimelines() {
        return overrides;
    }

    // --- GLOBAL+TENANT overlay resolution accessors ---

    public Optional<Currency> currency(CurrencyCode code, LocalDate date, Instant cut) {
        return resolve(currencies, code.value(), date, cut, ReferenceCatalogue::currencyTimelines);
    }

    public Optional<PairConvention> pairConvention(CurrencyPair pair, LocalDate date, Instant cut) {
        return resolve(pairConventions, pair.canonical(), date, cut, ReferenceCatalogue::pairConventionTimelines);
    }

    public Optional<FixedFactor> fixedFactor(CurrencyCode from, CurrencyCode to, LocalDate date, Instant cut) {
        return resolve(fixedFactors, from.value() + ">" + to.value(), date, cut, ReferenceCatalogue::fixedFactorTimelines);
    }

    public Optional<FixingSource> fixingSource(String sourceCode, LocalDate date, Instant cut) {
        return resolve(fixingSources, sourceCode, date, cut, ReferenceCatalogue::fixingSourceTimelines);
    }

    public Optional<PublicationCalendar> publicationCalendar(String calendarRef, LocalDate date, Instant cut) {
        return resolve(publicationCalendars, calendarRef, date, cut, ReferenceCatalogue::publicationCalendarTimelines);
    }

    public Optional<SettlementCalendar> settlementCalendar(String calendarRef, LocalDate date, Instant cut) {
        return resolve(settlementCalendars, calendarRef, date, cut, ReferenceCatalogue::settlementCalendarTimelines);
    }

    public Optional<AccountingUnit> accountingUnit(String unitId, LocalDate date, Instant cut) {
        return resolve(accountingUnits, unitId, date, cut, ReferenceCatalogue::accountingUnitTimelines);
    }

    public Optional<FxPolicy> fxPolicy(String policyId, LocalDate date, Instant cut) {
        return resolve(fxPolicies, policyId, date, cut, ReferenceCatalogue::fxPolicyTimelines);
    }

    public Optional<AccountingFxPolicy> accountingPolicy(String unitId, String purpose, LocalDate date, Instant cut) {
        return resolve(accountingPolicies, unitId + "|" + purpose, date, cut, ReferenceCatalogue::accountingPolicyTimelines);
    }

    public Optional<SourceEntitlement> entitlement(String sourceCode, LocalDate date, Instant cut) {
        return resolve(entitlements, sourceCode, date, cut, ReferenceCatalogue::entitlementTimelines);
    }

    public Optional<ManualRateOverride> override(Scope scope, CurrencyPair pair, LocalDate fxDate, String sourceCode,
            LocalDate date, Instant cut) {
        String key = scope + "|" + pair.canonical() + "|" + fxDate + "|" + (sourceCode == null ? "" : sourceCode);
        return resolve(overrides, key, date, cut, ReferenceCatalogue::overrideTimelines);
    }

    public Optional<CalendarIndex> calendarIndex(String versionId) {
        CalendarIndex own = calendarIndexes.get(versionId);
        if (own != null) {
            return Optional.of(own);
        }
        return global == null ? Optional.empty() : global.calendarIndex(versionId);
    }

    public JointCalendarIndex jointCalendar(List<CalendarIndex> constituents) {
        List<String> sortedKeys = constituents.stream()
                .map(c -> c.calendarRef() + "@" + c.versionId())
                .sorted()
                .collect(Collectors.toList());
        String key = String.join(",", sortedKeys);
        return jointIndexes.computeIfAbsent(key, k -> JointCalendarIndex.intersect(constituents));
    }

    private <V> Optional<V> resolve(Map<String, Timeline<V>> ownMap, String key, LocalDate date, Instant cut,
            java.util.function.Function<ReferenceCatalogue, Map<String, Timeline<V>>> accessor) {
        Timeline<V> own = ownMap.get(key);
        if (own != null) {
            Optional<V> result = own.resolve(date, cut);
            if (result.isPresent()) {
                return result;
            }
        }
        if (global != null) {
            Map<String, Timeline<V>> globalMap = accessor.apply(global);
            Timeline<V> theirs = globalMap.get(key);
            if (theirs != null) {
                return theirs.resolve(date, cut);
            }
        }
        return Optional.empty();
    }
}
