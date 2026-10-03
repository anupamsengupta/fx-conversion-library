# FX Conversion Library -- Technical Specification v1.0

## S1 -- Metadata & Status

| Field | Value |
|-------|-------|
| Author | solutions-architect |
| Status | DRAFT |
| Version | 1.0 |
| Date | 2026-10-03 |
| Depends On | FX Conversion Library Functional Spec v2.0 (2026-10); UOM Conversion Library Technical Spec v1.0 (structural reference); UOM Conversion Library Functional Spec v2.0 (version-envelope and ingest conventions, cited as "UOM FS"); PDR Library Functional Spec v2.0 (`PricingDaySet` shape, consumed by value); ADR-0001-2 (Pattern Catalog); ADR-0001-1 / ADR-0001-3 (module topology); CLAUDE.md (platform conventions) |
| Layer | Separate Maven reactor (`fx-conversion`). Library-scope -- no framework, no I/O and no clock on the resolution path. No `valuation-app` content. No production hosting layer required by this library; hosts embed it in-process. |
| Subsystems Touched | None directly (standalone library). Prospective consumers in the platform: S2 PriceExpression (price-currency conversion), S5a Settlement Cells, S5b Forward Marks, S5c EOD Struck Marks, S7 Rollups (presentation-currency rollups). Integration adapters for those are **out of scope for v1.0** and are named in S2.2. |
| Functional Spec Decisions | D-01 through D-16 (all normative, all implemented, none reopened) |
| Open Questions | FS OQ-01 through OQ-11 (all surfaced, none resolved) plus OQ-T01 through OQ-T08 raised by this spec |
| Numbering note | `D-01..D-16` in this document always means the **FX functional spec** decisions. Platform constraints from CLAUDE.md are always written as `D-n (platform)` and live in a separate table (S13.2). The two numbering spaces are disjoint and are never mixed. |

---

## S2 -- Scope & Non-Scope

### 2.1 In Scope

1. **Separate Maven reactor** `fx-conversion`, groupId `com.power.fx` (A-01), with five modules mirroring the UOM reactor's postfix set and boundaries exactly: `fx-api`, `fx-core`, `fx-cdm`, `fx-testkit`, `fx-guice`. Consistent with FS S4.1.
2. **Public API surface** -- `FxConverter`, `FxSnapshot`, `FxIngestor`, `FxHealth`, sealed request and result hierarchies, SPIs (`TenantContextProvider`, `ReferenceDataLoader`, `MarketDataLoader`, `FxEventListener`, `FxMetrics`).
3. **Date resolution engine** -- raw FX date derivation per date rule, offsets, publication-calendar resolution with `nonPublicationDayHandling`, settlement-calendar value dates with roll conventions, delivery-day and gas-day mapping (FS S7, D-02). Strictly **before** rate lookup.
4. **Pair resolution engine** -- the nine-step ordered chain of FS S11.2, including minor-unit and legal-peg normalisation first-and-last, contract rate, manual override, direct, inverse, configured cross, major cross.
5. **Rate selection** -- the FS S11.1 decision matrix as an explicit, row-addressable table; fixing version selection per `fixingVersionPolicy` against a pinned knowledge cut; fallback chain of FS S11.3.
6. **Forward construction** -- POINTS, CIP and HYBRID methods; LOG_LINEAR_CARRY (default, D-15), LINEAR_POINTS and MONOTONE_CUBIC_POINTS (Hyman-filtered) interpolation; short-end ON/TN adjustment; extrapolation limits (FS S11.4).
7. **Deterministic decimal transcendentals** -- library-owned `ln` and `exp` with argument reduction, >= 1e-30 relative accuracy, DECIMAL128 output, bit-identical across JVM vendors (D-09, FS G05/X10). Appendix D.
8. **Unified averaging** -- `averagingMethod` x `observationSet` x `weighting` x `outputShape` (D-07), exact rational weights, PDR `PricingDaySet` consumption by value (D-08), partial-period confirmed/estimated split (FS S10.3).
9. **Leg chain and management view** -- CONTRACT, ACCOUNTING_TRANSACTION, TRANSLATION legs plus the on-read MANAGEMENT_VIEW (D-16), identity collapse, no cross-leg collapse, leg ordering with `UPSTREAM_UNRESOLVED`, leg-2 input selection per `settlementAmountState` (FS S9).
10. **Revaluation helper** -- signed difference, realised/unrealised classification against a caller-supplied carrying amount (D-04, FS S9.3).
11. **Caches** -- bitemporal reference catalogue (GLOBAL + TENANT overlay), bitemporal versioned fixing store with knowledge-cut resolution, immutable market snapshot store with completion tracking, hot window and explicit `prewarm` (FS S5, S6).
12. **Snapshot pinning** -- `FxSnapshot` binding tenant, reference generation, fixing-store generation, market snapshot and fixing knowledge cut; per-snapshot memoisation (FS S6.4, D-03).
13. **Tenancy and entitlements** -- implicit tenancy via `TenantContextProvider`, GLOBAL+TENANT overlay, entitlement filtering of `rateSourcePriority`, most-restrictive propagation of rights through inverses, crosses, forwards and averages, `distributionRestriction` on every result (D-10, D-13, FS S12).
14. **Ingestion** -- idempotent on `versionId` / `marketSnapshotId`, four-eyes validation, fixing sequence validation, snapshot chunk completion, sequence-gap detection with loader repair, single-writer-per-tenant, atomic generation swap (FS S6.3, S16).
15. **Precision and rounding** -- DECIMAL128 with a fixed operation order, single final rounding, largest-remainder series allocation with earliest-sequence tie-break, explicit allocation residual, reconciliation tolerance helper (FS S15).
16. **Lineage and replay** -- per-leg lineage, `inputsHash` over an RFC 8785 canonicalised request projection, replay key enabling `assessCorrectionImpact` (FS S13, S14.3).
17. **Correction handling** -- `FxEventListener.onFixingCorrected` at ingest, `assessCorrectionImpact(Lineage, FxSnapshot)` recompute-and-diff (D-06).
18. **Codes** -- `FxIngestCode` (FS S16), `FxErrorCode` / `FxWarningCode` (FS S17) and `FxReason` (FS S11.1 reason labels) as enums, each mapped to the component that raises it (S6.18).
19. **Testkit** -- golden vectors G01-G09, C01-C05, F01-F09, X01-X11, property tests, policy matrix exhaustion, decimal conformance table, ArchUnit and bytecode architecture tests.
20. **ArchUnit and bytecode enforcement** of D-01 (no I/O, no clock) and D-09 (no `double`/`float`/`Math`/`StrictMath`) in `fx-api` and `fx-core` (S12.5).

### 2.2 Defers To

| Item | Owner |
|------|-------|
| Market-data acquisition, cleansing, EOD sign-off workflow | Market data system (FS S1.2) |
| Capture and four-eyes approval of reference data and manual rate overrides | Source reference data system (FS S1.2, D-11) |
| Unit-of-measure conversion | UOM conversion library |
| Pricing-day determination | PDR library; consumed by value (D-08) |
| Discounting cash flows to produce PV | Valuation service (D-05) |
| Persistence of converted values, locking of invoiced rows | Calling service (FS S13.4, S14.4) |
| GL posting, consolidation, CTA/OCI computation | ERP / consolidation system |
| Hedge accounting, FX P&L attribution, FX risk aggregation | FX Exposure / accounting systems |
| FX quanto convexity adjustment | Option pricer |
| CDM event transport (Kafka consumer, SQS, HTTP) | Host service. `fx-cdm` is a pure mapper |
| Network API, standalone deployment, SSE/WebSocket push | Not provided (S10b) |
| CDM schema artifact | Separate deliverable (A-02, TI-01) |
| Discount curve construction and ownership for CIP forwards | Market data system (FS OQ-04) |
| Valuation-engine integration adapter (`S2`/`S5b`/`S7` currency conversion) | Deferred to a follow-on tech spec. When built, the adapter lives in `valuation-guice` as an anti-corruption layer (Pattern #15), never in `valuation-domain`, per D-13 (platform) |
| Production hosting layer for any FX-aware service | Separate future deliverable. `valuation-app` is a simulator (D-14 platform) and MUST NOT host production FX ingestion |

---

## S3 -- Assumptions & Gaps

| # | Assumption / Gap | Impact |
|---|------------------|--------|
| A-01 | groupId is `com.power.fx`, mirroring UOM's `com.power.uom`. Reactor artifactId `fx-conversion`. | If the platform assigns a different groupId, Appendix B package names change mechanically. Flagged because the FS does not state coordinates. |
| A-02 | The CDM schema artifact exists or will be created separately. `fx-cdm` depends on it at compile scope. | `fx-cdm` cannot compile without it (TI-01). |
| A-03 | Reference data volume per tenant is small (thousands of records): currencies, pair conventions, calendars, entities, policies, entitlements, overrides. Full version history fits in memory. | Drives copy-on-write catalogue design identical to UOM S7.1. |
| A-04 | `jakarta.inject` (`@Inject`, `@Named`) is permitted in `fx-core` at provided scope, consistent with the UOM precedent (UOM A-04) and platform convention for `valuation-domain`. | If rejected, constructor wiring becomes annotation-free and `fx-guice` must use `@Provides` methods throughout. |
| A-05 | Java 21 is the minimum JDK. The library does not use `ScopedValue`; `TenantContextProvider` returns `Optional<String>` and the host chooses the propagation mechanism. | Hosts on Java 25 may back the SPI with `ScopedValue` without library change. |
| A-06 | The library version string is available at compile time via a generated constant (`FxVersion.VALUE`, Maven resource filtering or template class). | Stamped into `Lineage.libraryVersion` and into `inputsHash` (TI-05). |
| A-07 | Public API decimal types are `java.math.BigDecimal`, not decimal strings. This deviates from the UOM tech spec, which used `String`. Rationale: FS S15 requires source-published scale to be preserved ("market quotes as published"), `effectiveRate` must carry 34 significant digits, and the p99 20 us budget cannot absorb parse-and-format on every field. | All records that contain `BigDecimal` MUST NOT rely on record-generated `equals`/`hashCode` for value comparison, because `BigDecimal.equals` is scale-sensitive. Mandated mitigations in S10.6. |
| A-08 | `fx-api` declares its **own** minimal projection of the PDR types (`PricingDaySet`, `PricingObservation`, `PdrRef`) rather than depending on a `pdr-api` artifact, to keep `fx-api` JDK-only. Callers map PDR objects into the projection. | Duplicated shape must be kept in step with PDR v2.0 (OQ-T01). |
| A-09 | `runMode` (OFFICIAL, AD_HOC) is a request-context field. FS S6.3 references `runMode = OFFICIAL` but FS S14.2 omits it from the context table. The name and placement are assumed. | Needed to raise `FX_V_UNSIGNED_SNAPSHOT`. If the host instead signals official runs via config, `RequestValidator` reads it from `FxConfig` instead. |
| A-10 | `batch` is typed with sealed hierarchies (`List<FxResult> batch(List<? extends FxRequest>)`) rather than the FS's indicative `List<Result<?>> batch(List<Request<?>>)`. | Preserves FS intent with exhaustive `switch` for callers; no wildcard-capture gymnastics. Purely a signature refinement. |
| A-11 | Latency instrumentation (`System.nanoTime`) lives in an optional decorator `MeteredFxConverter` in `fx-guice`, not in `fx-core`, so that the "no clock in `fx-core`" ArchUnit rule (AR-04) can be absolute. | `FxMetrics.resolutionLatency(tenantId, nanos)` is fed by the decorator. Unwired hosts pay zero cost. |
| A-12 | `MessageDigest.getInstance("SHA-256")` is permitted in `fx-core` for `inputsHash`. SHA-256 is a mandated JDK algorithm and is bit-deterministic; it is not I/O. | AR-02's allowlist includes `java.security.MessageDigest`. |
| A-13 | `inputsHash` is computed **lazily** on first access and memoised inside `Lineage`. | Required to keep the p99 20 us convert budget (S10a.1, Risk R2). Hosts that persist lineage pay the cost once per row; hot read paths that discard lineage pay nothing. Makes `Lineage` non-record-pure: it is a final class with a memoising field, not a `record`. |
| A-14 | Calendar coverage windows are finite. A date query outside a loaded calendar's coverage window is reported as `FX_E_DATA_NOT_LOADED`, reusing the existing code rather than inventing one. | The FS has no dedicated "calendar coverage exceeded" code. Flagged as OQ-T02 rather than minting a code. |
| A-15 | A pinned `FxSnapshot` pins the **fixing store generation** in addition to the knowledge cut, so that a backfilled fixing version with `recordedAt <= cut` arriving after the pin cannot change in-flight results. | Gives physical replay determinism within a process. Cross-process determinism relies on the `FX_I_FIXING_SEQUENCE` `recordedAt`-regression rule; first-ever-seen keys backfilled below the cut are a residual gap (OQ-T03). |
| A-16 | `fixingVersionPolicy = AS_OF_KNOWLEDGE` carries its instant `t` on the policy (`asOfKnowledge`), since FS S6.1 writes it as `AS_OF_KNOWLEDGE(t)` but FS S8.1 lists `fixingVersionPolicy` as a bare enum. | Modelled as a sealed `FixingVersionSelection` in `fx-api`: `FirstOfficial`, `LatestCorrected`, `AsOfKnowledge(Instant t)`. The bare enum `FixingVersionPolicy` is retained as the discriminant for CDM payloads. |
| A-17 | Averaging with `outputShape = SERIES` returns per-observation results **and** a total in one `SeriesResult`; there is no separate call. | Matches FS S10.1 wording "per-observation results + total". |
| A-18 | The `MANAGEMENT_VIEW` leg may be requested for multiple report currencies in one `ChainRequest` (D-16 "multiple presentation and reporting currencies"). | `ChainResult.managementViews` is a `List`, each entry flagged `persistable = false`. |

---

## S4 -- Domain Model Additions

All value types are Java 21 `record`s with validating compact constructors (Pattern #1 Value Object). Sealed hierarchies use Pattern #2. Enums carrying behaviour use Pattern #3. Pattern numbers reference ADR-0001-2 and reuse the same numbers the UOM tech spec cited; where no catalogue pattern fits, this spec says so explicitly rather than inventing a number.

### 4.1 Core Enumerations (`fx-api`, package `model`)

```java
enum Purpose { CONTRACT_SETTLEMENT, UNREALISED_MTM, CASH_PROJECTION,
               ACCOUNTING_RECOGNITION, ACCOUNTING_REVALUATION, ACCOUNTING_SETTLEMENT,
               TRANSLATION, MANAGEMENT_VIEW }

enum Leg { CONTRACT, ACCOUNTING_TRANSACTION, TRANSLATION, MANAGEMENT_VIEW }

enum DateRule { TRADE_DATE, SPECIFIC_DATE, PRICING_SET, PAYMENT_DATE, DELIVERY_DATE,
                DELIVERY_DAYS, EVENT, RECOGNITION_DATE, AVERAGE_RATE, CLOSING_RATE,
                SETTLEMENT_DATE, VALUATION_DATE, FAIR_VALUE_DATE, HISTORICAL_RATE }

enum AmountType { NOMINAL, NOMINAL_FUTURE, PRESENT_VALUE }
enum SettlementAmountState { UNINVOICED, INVOICED, SETTLED }
enum ItemType { MONETARY, NON_MONETARY_HISTORICAL, NON_MONETARY_FAIR_VALUE }
enum RunMode { OFFICIAL, AD_HOC }                         // A-09

enum RateType { FIXING, SPOT, FORWARD, FIXED_FACTOR, CONTRACT_RATE, MANUAL_OVERRIDE, AVERAGE }
enum RateFinality { CONFIRMED, ESTIMATED, UNRESOLVED }
enum FixingStatus { PRELIMINARY, OFFICIAL, CORRECTED }
enum FixingVersionPolicy { FIRST_OFFICIAL, LATEST_CORRECTED, AS_OF_KNOWLEDGE }   // discriminant, A-16

enum NonPublicationDayHandling { USE_PREVIOUS, USE_NEXT, SKIP_OBSERVATION, FAIL }
enum RollConvention { FOLLOWING, MODIFIED_FOLLOWING, PRECEDING, MODIFIED_PRECEDING }
enum OffsetCalendarKind { FIXING_SOURCE, PAIR_SETTLEMENT_JOINT, CURRENCY, CUSTOM, CALENDAR_DAYS }

enum ForwardMethod { POINTS, CIP, HYBRID }
enum InterpolationMethod { LOG_LINEAR_CARRY, LINEAR_POINTS, MONOTONE_CUBIC_POINTS }
enum FutureDateTreatment { FORWARD, SPOT }
enum SpotAdjustment { TO_VALUATION_DATE, NONE }

enum AveragingMethod { NONE, RATE_AVERAGE, PRICE_MATCHED }
enum ObservationSetKind { FROM_PRICING_SET, FX_FIXING_DAYS_IN_WINDOW, DELIVERY_DAYS, EXPLICIT }
enum WindowKind { DELIVERY_PERIOD, PRICING_PERIOD, CALENDAR_MONTH, ACCOUNTING_PERIOD, CUSTOM }
enum Weighting { FROM_PRICING_SET, EQUAL, VOLUME, CUSTOM }
enum OutputShape { TOTAL, SERIES }
enum FxDateFromObservation { SAME_DATE, OFFSET }

enum FallbackStepKind { ALT_SOURCE, PREVIOUS_PUBLICATION_DAY, TRIANGULATE,
                        INTERPOLATE_FIXINGS, FAIL }

enum FixedFactorKind { MINOR_UNIT, LEGAL_PEG }
enum UsageClass { INVOICING_ELIGIBLE, MTM_ONLY }
enum SourceRight { VALUATION, DISPLAY, REDISTRIBUTION }

enum SnapshotKind { EOD, INTRADAY }
enum SignOffStatus { SIGNED_OFF, UNSIGNED }

enum EventType { BL, NOR, COD, TITLE_TRANSFER, INVOICE }
enum FxDifferenceClass { UNREALISED_FX_PNL, REALISED_FX_PNL }
enum EstimatedEventHandling { USE_ESTIMATE, FAIL }

enum Scope { GLOBAL, TENANT }
enum VersionStatus { APPROVED, RETIRED }
enum TenantHealthStatus { NOT_LOADED, LOADING, READY, STALE }
enum FxEntityType { CURRENCY, PAIR_CONVENTION, FIXED_FACTOR, FIXING_SOURCE,
                    PUBLICATION_CALENDAR, SETTLEMENT_CALENDAR, ACCOUNTING_UNIT,
                    FX_POLICY, ACCOUNTING_FX_POLICY, SOURCE_ENTITLEMENT,
                    MANUAL_RATE_OVERRIDE, FIXING, MARKET_SNAPSHOT }
enum FxStoreKind { REFERENCE, FIXING, SNAPSHOT }
```

`RateFinality` carries behaviour (Pattern #3): `RateFinality.weakest(RateFinality...)` returning `UNRESOLVED > ESTIMATED > CONFIRMED` -- used by every combinator (inverse, cross, forward, average, leg chain) so finality roll-up is defined in exactly one place.

### 4.2 Code Enumerations (`fx-api`, package `error`)

```java
enum FxErrorCode {
    FX_E_NO_TENANT_CONTEXT, FX_E_TENANT_MISMATCH, FX_E_TENANT_NOT_READY,
    FX_E_DATA_NOT_LOADED, FX_E_NO_FX_PATH, FX_E_RATE_NOT_FOUND,
    FX_E_NON_PUBLICATION_DATE, FX_E_EXTRAPOLATION_LIMIT, FX_E_SOURCE_NOT_ENTITLED,
    FX_E_INACTIVE_CURRENCY, FX_E_FUNCTIONAL_CCY_NOT_FOUND, FX_E_MISSING_EVENT_DATE,
    FX_V_INVALID_POLICY, FX_V_AMOUNT_TYPE_MISMATCH, FX_V_PRICE_SERIES_MISMATCH,
    FX_V_SOURCE_NOT_ALLOWED, FX_V_UNSIGNED_SNAPSHOT
}

enum FxWarningCode {
    FX_W_SOURCE_SKIPPED_NOT_ENTITLED, FX_W_MIXED_SOURCE, FX_W_DATE_RULE_ADJUSTED,
    FX_W_FALLBACK_USED, FX_W_EXTRAPOLATED, FX_W_STALE_KEY
}

enum FxIngestCode {
    FX_I_APPROVAL_INVALID, FX_I_SCOPE_VIOLATION, FX_I_SNAPSHOT_IMMUTABLE,
    FX_I_SNAPSHOT_INCOMPLETE, FX_I_FIXING_SEQUENCE, FX_I_OVERLAP,
    FX_I_INVALID_VALUE, FX_I_SEQUENCE_GAP
}

// Lineage reason labels from FS S7.1, S10.3, S11.1-11.4, S12. Not errors.
enum FxReason {
    IDENTITY, FIXING, PRELIM_FIXING, PRE_PUBLICATION, FORWARD_RATE, FIXED_FACTOR,
    CONTRACT_RATE, MANUAL_OVERRIDE, INVERTED, TRIANGULATED, MIXED_SOURCE,
    DATE_RULE_ADJUSTED, SKIPPED_OBSERVATION, PARTIAL_PERIOD, AVERAGE_INVERTED,
    FALLBACK_ALT_SOURCE, FALLBACK_STALE, FALLBACK_TRIANGULATED, FALLBACK_INTERPOLATED,
    RATE_NOT_FOUND, EXTRAPOLATED, UPSTREAM_UNRESOLVED, SPOT_ADJUSTED, CIP_DERIVED,
    HYBRID_ANCHORED, SOURCE_ENTITLEMENT_RESTRICTED, VIEW_ONLY
}
```

The literal `FIXED` is absent from every enum in the library (D-12). An ArchUnit rule (AR-09) asserts that no enum constant in `fx-api` equals `FIXED`.

`FxErrorCode` keeps the FS's two prefixes in one enum (`FX_E_*` resolution failures, `FX_V_*` validation failures) because both are returned in the same `FxError` slot of a result; a `category()` accessor (Pattern #3) discriminates them for metrics.

```java
record FxError(FxErrorCode code, String message, Map<String, String> details)
record FxWarning(FxWarningCode code, String message, Map<String, String> details)
final class FxException extends RuntimeException { FxError error(); }
```

### 4.3 Version Envelope and Common Value Types

The version envelope is identical to UOM FS S5.1 / UOM tech spec S4.3 (FS S5 says "the same as UOM v2.0 section 5.1"). It is **re-declared** in `fx-api`, not inherited, because `fx-api` must not depend on `uom-api` (module boundary, S13.3 MC-4).

```java
record VersionEnvelope(
    Scope scope, String tenantId, String naturalKey, String versionId,
    LocalDate validFrom, LocalDate validTo, Instant recordedAt, VersionStatus status,
    String authoredBy, String approvedBy, Instant approvedAt,
    String sourceSystem, String correctionOf, String reasonCode, String catalogueRelease)
```

Compact constructor enforces: `approvedBy != null`; `!approvedBy.equals(authoredBy)`; `approvedAt.equals(recordedAt)`; `correctionOf != null => reasonCode != null`; `scope == GLOBAL => tenantId == null`; `scope == TENANT => tenantId != null`; `validTo == null || validFrom.isBefore(validTo)`.

```java
record CurrencyCode(String value) implements Comparable<CurrencyCode>
// ISO 4217 or market minor code (GBp, USc, ZAc, ILA). Case-sensitive: minor codes
// are distinguished from majors by case. Validated against [A-Za-z]{3}.

record CurrencyPair(CurrencyCode base, CurrencyCode quote) {
    CurrencyPair inverse();
    boolean isIdentity();
    String canonical();   // "EUR/USD" -- used in lineage and memo keys
}

record Rational(BigInteger num, BigInteger den) implements Comparable<Rational> {
    // compact constructor: den != 0; sign normalised onto num; reduced by gcd
    Rational plus(Rational o); Rational times(Rational o); Rational dividedBy(Rational o);
    BigDecimal toDecimal(MathContext mc);
    static Rational of(long n, long d); static final Rational ZERO, ONE;
}

record LocalDateRange(LocalDate startInclusive, LocalDate endInclusive)
```

`Rational` is mandatory, not a convenience: FS S10.2 requires PDR weights to be "used exactly", FS S7.1 requires `SKIP_OBSERVATION` weights to be "renormalised exactly", and FS S10.3 exposes `confirmedPortion` as a rational (G06 expects `5/11`, not `0.4545...`).

### 4.4 Reference Data Value Objects (`fx-api`, package `model`)

One record per FS S5 entity. Every one embeds `VersionEnvelope`.

```java
record Currency(VersionEnvelope envelope, CurrencyCode code, int decimals,
                CurrencyCode majorCurrency,          // null iff this IS the major
                String settlementCalendarRef, boolean deliverable)

record PairConvention(VersionEnvelope envelope, CurrencyPair marketConvention,
                int pipPrecision, BigDecimal pointsScale, int spotLag,
                List<String> spotCalendars,          // joint; includes USNY for USD crosses
                CurrencyCode triangulationVia,       // nullable
                ForwardMethod forwardMethod, InterpolationMethod interpolation,
                BigDecimal maxExtrapolationYears,
                Map<CurrencyCode, String> discountCurveRefs)

record FixedFactor(VersionEnvelope envelope, CurrencyCode from, CurrencyCode to,
                BigDecimal factor, FixedFactorKind kind, boolean preferOverMarket)

record FixingSource(VersionEnvelope envelope, String sourceCode,
                LocalTime cutoffTime, ZoneId cutoffZone, String publicationCalendarRef,
                Set<CurrencyPair> pairsPublished, String ndfTemplate, UsageClass usageClass)

record PublicationCalendar(VersionEnvelope envelope, String calendarRef, ZoneId zone,
                LocalDateRange coverage, SortedSet<LocalDate> publicationDates)

record SettlementCalendar(VersionEnvelope envelope, String calendarRef, ZoneId zone,
                LocalDateRange coverage, SortedSet<LocalDate> businessDates,
                Set<DayOfWeek> weekend)

record AccountingUnit(VersionEnvelope envelope, String unitId, String legalEntityId,
                CurrencyCode functionalCurrency,     // effective-dated via envelope validity
                List<CurrencyCode> presentationCurrencies, String parentUnitId,
                String accountingPolicyRef)

record FxPolicy(VersionEnvelope envelope, String policyId, int version, Leg leg,
                DateRule dateRule, OffsetSpec offset,
                NonPublicationDayHandling nonPublicationDayHandling,
                RollConvention rollConvention, List<String> rateSourcePriority,
                FixingVersionSelection fixingVersionSelection,
                FutureDateTreatment futureDateTreatment, SpotAdjustment spotAdjustment,
                AveragingSpec averaging, List<FallbackStep> fallbackChain,
                boolean allowMixedSources, CurrencyCode forceCrossVia,
                RoundingSpec rounding, ContractRate contractRate,
                EstimatedEventHandling estimatedEventHandling, boolean allowOverrides)

record OffsetSpec(int days, OffsetCalendarKind calendarKind, String customCalendarRef)
record FallbackStep(FallbackStepKind kind, int maxSteps)   // maxSteps for PREVIOUS_PUBLICATION_DAY, default 3
record RoundingSpec(RoundingMode amountRounding,           // HALF_UP default, HALF_EVEN configurable
                Integer roundUnitPrice, Integer roundRate, Integer roundAverage)
record ContractRate(CurrencyPair pair, BigDecimal rate, String quotedIn,
                LocalDate effectiveFrom, LocalDate effectiveTo, String reference)
record AveragingSpec(AveragingMethod method, ObservationSetKind observationSet,
                WindowSpec window, Weighting weighting, OutputShape outputShape,
                FxDateFromObservation fxDateFromObservation, int fxDateOffset,
                boolean averageInverted, List<LocalDate> explicitDates)
record WindowSpec(WindowKind kind, LocalDate start, LocalDate end, int lagPublicationDays)

record AccountingFxPolicy(VersionEnvelope envelope, String unitId, Purpose purpose,
                String settlementToFunctionalPolicyId, String functionalToPresentationPolicyId)

record SourceEntitlement(VersionEnvelope envelope, String tenantId, String sourceCode,
                Set<SourceRight> rights)

record ManualRateOverride(VersionEnvelope envelope, Scope scope, CurrencyPair pair,
                LocalDate fxDate, String sourceCode, BigDecimal rate,
                String reasonCode, String ticketRef)

// Sealed, per A-16
sealed interface FixingVersionSelection {
    record FirstOfficial() implements FixingVersionSelection {}
    record LatestCorrected() implements FixingVersionSelection {}
    record AsOfKnowledge(Instant asOf) implements FixingVersionSelection {}
}
```

### 4.5 Market Data Value Objects (`fx-api`, package `model`)

```java
record FixingVersion(Scope scope, String tenantId, String sourceCode, CurrencyPair pair,
                LocalDate fixingDate, String cutoff, BigDecimal value,
                FixingStatus fixingStatus, Instant recordedAt, String versionId,
                String correctionOf, LocalDate valueDate)

record SpotQuote(CurrencyPair pair, BigDecimal rate, LocalDate spotDate)

record ForwardPillar(String tenor, LocalDate valueDate,
                BigDecimal points,        // nullable -- exactly one of points/outright
                BigDecimal outright)

record DiscountCurvePayload(CurrencyCode currency, String curveRef,
                List<DiscountPillar> pillars)
record DiscountPillar(LocalDate date, BigDecimal discountFactor)   // or zero rate, see OQ-04

record MarketSnapshotPayload(String marketSnapshotId, Scope scope, String tenantId,
                SnapshotKind kind, LocalDate asOfDate, Instant fixingKnowledgeCut,
                SignOffStatus signOffStatus,
                List<SpotQuote> spots, Map<CurrencyPair, List<ForwardPillar>> forwards,
                List<DiscountCurvePayload> discountCurves,
                int chunkIndex, int chunkCount, boolean completionMarker)
```

`FixingVersion` is the ingest-facing and loader-facing shape; the in-memory store uses a columnar layout (S7.1.3) and does not retain one `FixingVersion` object per fixing.

### 4.6 PDR Projection (`fx-api`, package `model`) -- A-08

```java
record PdrRef(String eventId, int version, String inputsHash)

record PricingObservation(int sequence, LocalDate observationDate, Rational weight,
                String componentRef,        // hybrid component, nullable
                BigDecimal volume,          // for Weighting.VOLUME, nullable
                boolean duplicate)          // ROLL_FORWARD / ROLL_BACKWARD marker

record PricingDaySet(PdrRef ref, List<PricingObservation> observations, String status)
```

`observations` preserves PDR order and never collapses duplicates (D-08, FS S10.2). The compact constructor asserts `sequence` values are distinct and ascending, and that the weight denominators are positive.

### 4.7 Request Types (`fx-api`, package `request`)

```java
sealed interface FxRequest permits RateRequest, ConversionRequest, SeriesRequest,
                                   ChainRequest, MonetaryRevaluationRequest {
    FxRequestContext context();
}

record FxRequestContext(
    Purpose purpose,                       // required, D-14
    LocalDate valuationDate,               // required, never defaulted
    FxSnapshot snapshot,                   // nullable => implicit latest; required for OFFICIAL
    RunMode runMode,                       // A-09
    String accountingUnitId,               // required for ACCT_TXN / TRANSLATION / MGMT_VIEW
    AmountType amountType,
    SettlementAmountState settlementAmountState,
    ItemType itemType,                     // required for ACCOUNTING_TRANSACTION
    TradeDates tradeDates,
    Map<EventType, EventDate> events,
    AccountingDates accountingDates,
    PricingDaySet pricingSet,
    List<ObservationPrice> prices,         // PRICE_MATCHED, keyed by PDR sequence
    PolicyRef policy,                      // required
    String requestId)                      // correlation only; excluded from inputsHash

sealed interface PolicyRef {
    record ById(String policyId) implements PolicyRef {}
    record Inline(FxPolicy policy) implements PolicyRef {}     // trade terms, FS S5/S8.1
}

record TradeDates(LocalDate tradeDate, LocalDate specificDate, LocalDate paymentDate,
                LocalDate deliveryDate, LocalDate deliveryStart, LocalDate deliveryEnd,
                List<DeliveryDay> deliveryDays, ZoneId marketZone)
record DeliveryDay(LocalDate day, BigDecimal volume)
record EventDate(LocalDate date, int sourceRank, String evidenceRef, boolean estimated)
record AccountingDates(LocalDate recognitionDate, LocalDate periodStart, LocalDate periodEnd,
                LocalDate settlementDate, LocalDate fairValueDate, LocalDate historicalDate)
record ObservationPrice(int sequence, BigDecimal price, CurrencyCode priceCurrency,
                BigDecimal volume)

record RateRequest(FxRequestContext context, CurrencyCode from, CurrencyCode to)
        implements FxRequest

record ConversionRequest(FxRequestContext context, CurrencyCode from, CurrencyCode to,
                BigDecimal amount, boolean isUnitPrice) implements FxRequest

record SeriesRequest(FxRequestContext context, CurrencyCode from, CurrencyCode to,
                BigDecimal periodAmount,          // for RATE_AVERAGE on a period total
                boolean isUnitPrice) implements FxRequest

record ChainRequest(FxRequestContext context, CurrencyCode priceCurrency,
                BigDecimal priceAmount, CurrencyCode settlementCurrency,
                PolicyRef contractPolicy, PolicyRef accountingPolicy,
                PolicyRef translationPolicy, List<CurrencyCode> presentationCurrencies,
                List<ManagementViewSpec> managementViews) implements FxRequest

record ManagementViewSpec(CurrencyCode reportCurrency, PolicyRef policy)

record MonetaryRevaluationRequest(FxRequestContext context, CurrencyCode foreignCurrency,
                BigDecimal signedForeignAmount,   // + receivable, - payable
                BigDecimal carryingFunctionalAmount,  // one of carrying amount / rate
                BigDecimal carryingRate,
                DateRule rule)                    // CLOSING_RATE or SETTLEMENT_DATE only
        implements FxRequest

record PrewarmRequest(Set<String> marketSnapshotIds, Set<String> sourceCodes,
                Set<CurrencyPair> pairs, LocalDateRange dateRange, Instant knowledgeCut)
```

### 4.8 Result Types (`fx-api`, package `result`)

```java
sealed interface FxResult permits RateResult, ConversionResult, SeriesResult,
                                  ChainResult, RevaluationResult {
    RateFinality finality();
    List<FxWarning> warnings();
    Optional<FxError> error();
    Lineage lineage();
    default boolean isSuccess() { return error().isEmpty(); }
    <T extends FxResult> T orThrow();      // throws FxException when error present
}

record RateResult(CurrencyCode from, CurrencyCode to, BigDecimal rate, RateType rateType,
                RateFinality finality, List<FxReason> reasons, List<PathStep> path,
                DistributionRestriction distributionRestriction,
                Lineage lineage, List<FxWarning> warnings, Optional<FxError> error)
        implements FxResult

record ConversionResult(CurrencyCode fromCcy, CurrencyCode toCcy, BigDecimal fromAmount,
                BigDecimal toAmountUnrounded, BigDecimal toAmountBooked,
                BigDecimal effectiveRate, RateType rateType, RateFinality finality,
                List<FxReason> reasons, List<PathStep> path,
                DistributionRestriction distributionRestriction,
                Lineage lineage, List<FxWarning> warnings, Optional<FxError> error)
        implements FxResult

record SeriesResult(CurrencyCode fromCcy, CurrencyCode toCcy,
                BigDecimal totalUnrounded, BigDecimal totalBooked,
                BigDecimal averageRate, BigDecimal confirmedAverage,
                BigDecimal estimatedAverage, Rational confirmedPortion,
                List<ObservationResult> observations, AllocationResidual residual,
                RateFinality finality, List<FxReason> reasons,
                DistributionRestriction distributionRestriction,
                Lineage lineage, List<FxWarning> warnings, Optional<FxError> error)
        implements FxResult

record ObservationResult(int sequence, LocalDate observationDate, LocalDate rawFxDate,
                LocalDate resolvedFxDate, Rational weight, BigDecimal price,
                BigDecimal rate, BigDecimal amountUnrounded, BigDecimal amountBooked,
                RateType rateType, RateFinality finality, List<FxReason> reasons,
                List<PathStep> path, boolean skipped)

record ChainResult(Map<Leg, LegResult> legs, List<ManagementViewResult> managementViews,
                RateFinality finality, Lineage lineage,
                List<FxWarning> warnings, Optional<FxError> error) implements FxResult

record LegResult(Leg leg, Purpose purpose, ConversionResult conversion,
                CurrencyCode functionalCurrency, String accountingUnitId,
                boolean persistable)

record ManagementViewResult(CurrencyCode reportCurrency, ConversionResult conversion)
        // persistable == false always (D-16)

record RevaluationResult(CurrencyCode functionalCurrency,
                BigDecimal newFunctionalUnrounded, BigDecimal newFunctionalBooked,
                BigDecimal carryingFunctionalAmount, BigDecimal difference,
                FxDifferenceClass classification, BigDecimal rateUsed,
                RateFinality finality, List<PathStep> path,
                Lineage lineage, List<FxWarning> warnings, Optional<FxError> error)
        implements FxResult

record PathStep(CurrencyPair pair, RateType rateType, BigDecimal value, boolean inverted,
                String source, String cutoff, LocalDate rawDate, LocalDate resolvedDate,
                LocalDate valueDate, String fixingVersionId, FixingStatus fixingStatus,
                InterpolationMethod interpolation, List<PillarRef> pillars,
                List<FxReason> reasons)

record PillarRef(String tenor, LocalDate valueDate, BigDecimal value)

record DistributionRestriction(boolean valuationAllowed, boolean displayAllowed,
                boolean redistributionAllowed, SortedSet<String> contributingSources)

record AllocationResidual(BigDecimal residual, int lineCount, int scale,
                BigDecimal tolerance)

record CorrectionImpact(boolean replayable, boolean materiallyChanged,
                BigDecimal previousAmount, BigDecimal newAmount, BigDecimal difference,
                BigDecimal previousRate, BigDecimal newRate,
                List<FixingVersionChange> changedInputs, Optional<FxResult> recomputed,
                Lineage previousLineage, Optional<Lineage> newLineage,
                Optional<FxError> error)

record FixingVersionChange(String sourceCode, CurrencyPair pair, LocalDate fixingDate,
                String oldVersionId, String newVersionId,
                BigDecimal oldValue, BigDecimal newValue)

record PrewarmOutcome(int snapshotsLoaded, int fixingSeriesLoaded, int fixingVersionsLoaded,
                List<String> notFound, List<IngestRejection> rejections)
```

### 4.9 Lineage (`fx-api`, package `result`) -- final class, not a record (A-13)

```java
final class Lineage {
    // identity of the pinned state
    String tenantId();
    String marketSnapshotId();
    Instant fixingKnowledgeCut();
    long referenceGeneration();
    long fixingGeneration();                 // A-15
    SignOffStatus snapshotSignOff();

    // policy and library identity
    String policyId(); int policyVersion(); String inlinePolicyDigest();  // nullable
    String libraryVersion(); String apiSchemaVersion();

    // inputs
    Optional<PdrRef> pdrRef();
    SortedMap<String, String> calendarVersions();   // calendarRef -> versionId
    ReplayableRequest replayKey();

    // derived, lazily memoised
    String inputsHash();                     // SHA-256 hex over RFC 8785 canonical form
}

record ReplayableRequest(Purpose purpose, RunMode runMode, LocalDate valuationDate,
                AmountType amountType, SettlementAmountState settlementAmountState,
                ItemType itemType, String accountingUnitId,
                CurrencyCode fromCcy, CurrencyCode toCcy, BigDecimal fromAmount,
                boolean isUnitPrice, PolicyRef policy,
                TradeDates tradeDates, Map<EventType, EventDate> events,
                AccountingDates accountingDates, PricingDaySet pricingSet,
                List<ObservationPrice> prices)
```

`ReplayableRequest` is the design answer to `assessCorrectionImpact(Lineage previous, FxSnapshot current)` (FS S14.1): the lineage must be **self-sufficient for recompute**. FS S14.3 does not list such a field; this spec adds it because without it the signature cannot be honoured for inline policies or PDR-linked series. `PolicyRef.Inline` is embedded verbatim, so a trade-terms policy replays without a reference lookup; `inlinePolicyDigest()` lets hosts index on it. See S6.16 and OQ-T04.

### 4.10 Ingest Types (`fx-api`, package `ingest`)

```java
record FxIngestRecord(String eventId, FxEntityType entityType, Scope scope, String tenantId,
                String naturalKey, long sequence, Object payload, Instant publishedAt)

record IngestOutcome(int applied, int rejected, int duplicates,
                Map<FxStoreKind, Long> newGenerations,   // -1 when unchanged
                List<IngestRejection> rejections, List<String> staleKeys,
                List<String> snapshotsNowResolvable)

record IngestRejection(String eventId, String versionId, FxIngestCode code, String message)

record FixingCorrection(String tenantId, String sourceCode, CurrencyPair pair,
                LocalDate fixingDate, String cutoff,
                String oldVersionId, String newVersionId,
                BigDecimal oldValue, BigDecimal newValue, Instant recordedAt)

record SnapshotAvailability(String tenantId, String marketSnapshotId, SnapshotKind kind,
                LocalDate asOfDate, Instant fixingKnowledgeCut, SignOffStatus signOffStatus)

record MarketWatermark(String tenantId, Instant recordedAtHighWatermark,
                String lastSnapshotId)

record MarketDataChangeSet(List<FixingVersion> fixings,
                List<MarketSnapshotPayload> snapshots, MarketWatermark watermark)
```

### 4.11 CDM Event Envelope (`fx-cdm`)

```java
record CdmFxEvent(String eventId, String entityType, String scope, String tenantId,
                String naturalKey, long sequence, Map<String, Object> payload,
                Instant publishedAt)
```

`fx-cdm` maps `CdmFxEvent -> FxIngestRecord` via `CdmFxEventMapper` plus per-entity mappers. Pure functions, no transport, no I/O (Pattern #42 Data Mapper).

### 4.12 Configuration (`fx-api`)

```java
record FxConfig(
    BootstrapMode bootstrapMode,            // EAGER | LAZY
    List<String> eagerTenantIds,
    Duration bootstrapTimeout,
    Duration reconciliationInterval,
    int fixingHotWindowYears,               // default 3 (FS S6.3, OQ-08)
    int retainedSnapshotsPerTenant,         // default 8 (OQ-08)
    boolean memoEnabled,                    // default true
    int memoMaxEntriesPerSnapshot,          // default 50_000
    int forwardMemoMaxEntriesPerCurve,      // default 4_096
    int decimalWorkingPrecision,            // default 60 (Appendix D)
    boolean eagerInputsHash,                // default false (A-13)
    boolean allowImplicitPin,               // default true for AD_HOC, never for OFFICIAL
    boolean failOnStale,                    // default false
    int maxFallbackStalenessDays)           // OQ-07 placeholder, default 3

enum BootstrapMode { EAGER, LAZY }
```

---

## S5 -- Ports (Interfaces)

All port interfaces live in `fx-api`. Internal ports live inside `fx-core` and are not exported.

### 5.1 Public API Ports

```java
// fx-api -- Pattern #14 Facade
public interface FxConverter {
    FxSnapshot pin(String marketSnapshotId);
    FxSnapshot pin(String marketSnapshotId, Instant knowledgeCut);
    PrewarmOutcome prewarm(PrewarmRequest request);          // explicit, off the resolution path

    RateResult        rate(RateRequest request);
    ConversionResult  convert(ConversionRequest request);
    SeriesResult      convertSeries(SeriesRequest request);
    ChainResult       convertChain(ChainRequest request);
    RevaluationResult revalue(MonetaryRevaluationRequest request);
    CorrectionImpact  assessCorrectionImpact(Lineage previous, FxSnapshot current);
    List<FxResult>    batch(List<? extends FxRequest> requests);   // A-10
}

// fx-api -- Pattern #1 Value Object over Pattern #14; immutable pinned handle
public interface FxSnapshot extends FxConverter {
    String tenantId();
    String marketSnapshotId();
    SnapshotKind kind();
    LocalDate asOfDate();
    Instant fixingKnowledgeCut();
    SignOffStatus signOffStatus();
    long referenceGeneration();
    long fixingGeneration();
}
```

Semantics, binding:

- Business failures are returned as results, never thrown. `orThrow()` is the opt-in. `batch` never fails as a whole; one `FxResult` per input, index-aligned (FS S14.1).
- `FxSnapshot extends FxConverter` mirrors `UomSnapshot extends UomConverter`. When a request is issued through a snapshot facade **and** `context.snapshot()` is non-null, the **request field wins**; if they denote different `marketSnapshotId` or different tenants the result is `FX_E_TENANT_MISMATCH` only on a tenant difference, otherwise the request's snapshot is used silently. No new code is minted for snapshot disagreement (OQ-T05).
- `prewarm` may perform I/O **through the loader SPIs**. It is not on the resolution path, so D-01 holds: `fx-core` itself contains no I/O classes (AR-02).
- `pin` with no knowledge cut uses the snapshot's own `fixingKnowledgeCut` (FS S6.4). The two-argument form exists only for audit replay.

### 5.2 SPI Ports (implemented by the host)

```java
// fx-api -- Pattern #11 Strategy
public interface TenantContextProvider { Optional<String> currentTenant(); }

// fx-api -- Pattern #11 Strategy  (shape per UOM FS v2.0, re-declared, A-08 rationale)
public interface ReferenceDataLoader {
    List<FxIngestRecord> loadGlobal();
    List<FxIngestRecord> loadTenant(String tenantId);
    List<FxIngestRecord> loadChangesSince(String tenantId, Instant watermark);
    List<FxIngestRecord> loadKey(String tenantId, FxEntityType entityType, String naturalKey);
}

// fx-api -- Pattern #11 Strategy  (FS S6.3)
public interface MarketDataLoader {
    Optional<MarketSnapshotPayload> loadSnapshot(String marketSnapshotId);
    List<FixingVersion> loadFixings(Set<String> sourceCodes, Set<CurrencyPair> pairs,
                                    LocalDateRange dateRange, Instant knowledgeCut);
    MarketDataChangeSet loadChangesSince(MarketWatermark watermark);
}

// fx-api -- Pattern #18 Observer. All methods have no-op defaults.
public interface FxEventListener {
    default void onFixingCorrected(FixingCorrection correction) {}
    default void onRejected(FxIngestRecord record, FxIngestCode code, String message) {}
    default void onSnapshotAvailable(SnapshotAvailability availability) {}
    static FxEventListener noop() { return new FxEventListener() {}; }
}

// fx-api -- Pattern #11 Strategy
public interface FxMetrics {
    void ingestApplied(String tenantId, FxStoreKind store, int count);
    void ingestRejected(String tenantId, FxIngestCode code);
    void generationAdvanced(String tenantId, FxStoreKind store, long generation);
    void snapshotPinned(String tenantId, String marketSnapshotId);
    void memoHit(String tenantId);
    void memoMiss(String tenantId);
    void curveBuilt(String tenantId, String marketSnapshotId, long nanos);
    void resolutionLatency(String tenantId, Purpose purpose, long nanos);   // fed by A-11 decorator
    void resolutionError(String tenantId, FxErrorCode code);
    void resolutionWarning(String tenantId, FxWarningCode code);
    void fallbackUsed(String tenantId, FallbackStepKind kind);
    static FxMetrics noop() { /* no-op singleton */ }
}
```

`FxEventListener` has exactly the three methods of FS S14.1. Generation advancement is reported through `FxMetrics`, not the listener, so that the listener contract stays exactly as specified.

### 5.3 Ingestion and Health Ports

```java
// fx-api -- Pattern #17 Command/UseCase
public interface FxIngestor { IngestOutcome apply(List<FxIngestRecord> records); }

// fx-api
public interface FxHealth {
    TenantHealthStatus status(String tenantId);
    Optional<String> latestSnapshotId(String tenantId);
    Set<String> staleKeys(String tenantId);
}
```

### 5.4 Internal Ports (`fx-core`, package-internal -- NOT public API)

Documented for design completeness. These are the seams the implementation engineer must preserve, because the testkit's stage-level tests bind to them.

```java
// Pattern #21 Repository (in-memory, not JPA)
interface ReferenceStore {
    ReferenceCatalogue catalogue(String tenantId);                 // latest
    ReferenceCatalogue cataloguePinned(String tenantId, Instant knowledgeCut);
    long generation(String tenantId);
    boolean swap(String tenantId, ReferenceCatalogue next);        // compareAndSet
}

// Pattern #21 Repository (in-memory, bitemporal, columnar)
interface FixingStore {
    FixingView view(String tenantId);                              // immutable generation handle
    long generation(String tenantId);
    boolean swap(String tenantId, FixingView next);
    LocalDateRange loadedWindow(String tenantId);
}

// Pattern #21 Repository (in-memory, immutable, LRU-bounded)
interface MarketSnapshotStore {
    Optional<MarketSnapshot> get(String tenantId, String marketSnapshotId);
    Optional<MarketSnapshot> latest(String tenantId, SnapshotKind kind);
    void put(String tenantId, MarketSnapshot snapshot);            // immutable; rejects replace
}

// Pattern #11 Strategy -- one per resolution stage
interface DateRuleResolver { ResolvedDates resolve(ResolvedPolicy p, PinnedState s, FxRequestContext c); }
interface PairResolver     { PairRoute route(CurrencyCode from, CurrencyCode to, ResolvedPolicy p, PinnedState s, LocalDate fxDate); }
interface RateSelector     { RateQuote select(PairRoute route, LocalDate fxDate, ResolvedPolicy p, PinnedState s); }
interface FallbackChainRunner { Optional<RateQuote> run(RateLookupMiss miss, ResolvedPolicy p, PinnedState s); }
interface ForwardCurveCache { ForwardCurve curve(CurrencyPair pair, PinnedState s); }
interface AveragingEngine  { SeriesOutcome average(ObservationSet set, ResolvedPolicy p, PinnedState s); }
interface ChainEngine      { ChainResult chain(ChainRequest r, PinnedState s); }
interface EntitlementResolver { FilteredSources filter(List<String> priority, LocalDate fxDate, PinnedState s); }
interface PrecisionEngine  { BookedAmount book(BigDecimal unrounded, CurrencyCode ccy, RoundingSpec spec, PinnedState s); }
interface LineageBuilder   { Lineage build(ResolvedContext ctx, List<PathStep> path); }
interface DecimalTranscendentals { BigDecimal ln(BigDecimal x); BigDecimal exp(BigDecimal x); }
```

`PinnedState` is the single immutable carrier threaded through every stage: `(tenantId, ReferenceCatalogue global, ReferenceCatalogue tenant, FixingView fixings, MarketSnapshot snapshot, Instant knowledgeCut, long refGeneration, long fixingGeneration, ResolutionMemo memo)`. No stage reaches back to a store; this is what makes the resolution path pure and the pin meaningful.

---

## S6 -- Core Engine Components

`fx-core` provides the canonical implementation of every internal port. These are not adapters in the hexagonal sense -- they are the engine. No JPA, no Redis, no Kafka, no Spring, no Guice (D-01, MC-1).

### 6.1 Component Inventory

| Component | Package | Pattern # | Responsibility |
|-----------|---------|-----------|----------------|
| `DefaultFxConverter` | `core` | #14 Facade | Resolves tenant, pins or validates the snapshot, dispatches to the pipeline |
| `PinnedFxSnapshot` | `core` | #1 VO + #14 | Immutable handle over `PinnedState`; delegates with the pin fixed |
| `ConversionPipeline` | `core` | #11 Strategy (composed) | Fixed-order stage sequence (6.2); the only place stage order is encoded |
| `DefaultFxIngestor` | `core` | #17 Command | Single-writer-per-tenant ingestion across three stores, atomic swap |
| `DefaultFxHealth` | `core` | -- | Readiness per tenant, stale-key reporting |
| `DateRuleResolver`, `PublicationDateResolver`, `ValueDateResolver`, `SpotDateCalculator`, `DeliveryDayMapper`, `CutoffInstantResolver` | `core.date` | #11 Strategy | FS S7 date resolution, all before rate lookup (D-02) |
| `CalendarIndex`, `JointCalendarIndex` | `core.date` | #1 VO | Bitmap calendars, O(1) membership and prev/next publication day |
| `PairResolver` + nine `PairResolutionStep`s | `core.pair` | #11 Strategy (ordered composition) | FS S11.2 nine-step order |
| `RateSelector`, `RateCase`, `FixingResolver`, `FixingVersionSelector`, `SpotResolver` | `core.rate` | #11 Strategy + decision table | FS S11.1 matrix, FS S6.1 version selection |
| `FallbackChainRunner` + four steps | `core.rate` | #11 Strategy (ordered composition) | FS S11.3 |
| `ForwardCurve`, `ForwardCurveBuilder`, `ForwardCurveCache`, three interpolators, `CipForwardCalculator`, `ShortEndAdjuster`, `Extrapolator`, `DayCount` | `core.curve` | #11 Strategy + #1 VO | FS S11.4, D-15 |
| `FxMath`, `DecimalLn`, `DecimalExp`, `DecimalSqrt`, `DecimalConstants`, `RationalMath` | `core.decimal` | #11 Strategy | D-09 and Appendix D |
| `ObservationSetBuilder`, `AveragingEngine`, `RateAverageStrategy`, `PriceMatchedStrategy`, `WeightResolver`, `PricingSetAdapter`, `PartialPeriodAggregator` | `core.averaging` | #11 Strategy | D-07, D-08, FS S10 |
| `ChainEngine`, four leg handlers, `FunctionalCurrencyResolver`, `RevaluationEngine` | `core.leg` | #11 Strategy | FS S9.2-9.4, D-04, D-16 |
| `PrecisionEngine`, `LargestRemainderAllocator`, `RoundingPolicyResolver` | `core.precision` | #11 Strategy | FS S15 |
| `ReferenceStore`/`ReferenceCatalogue`/`Timeline`, `FixingStore`/`FixingView`/`FixingSeries`, `MarketSnapshotStore`, `HotWindowPolicy`, `CatalogueBuilder` | `core.cache` | #21 Repository (in-memory) | FS S5, S6.1, S6.3, D-03 |
| `MarketSnapshot`, `SnapshotAssembler`, `SnapshotCompletionTracker`, `PinnedState` | `core.snapshot` | #1 VO | FS S6.2, S6.4, D-03 |
| `EntitlementResolver`, `RightsSet`, `RestrictionPropagator` | `core.entitlement` | #11 Strategy | D-10, FS S12 |
| `PolicyMatrix`, `RequestValidator`, `PolicyValidator`, `IngestValidator`, `FixingSequenceValidator` | `core.validation` | #11 Strategy + #1 VO | FS S8.3, S9.1, S16 |
| `LineageBuilder`, `CanonicalJson`, `InputsHasher`, `CorrectionImpactAssessor` | `core.lineage` | #1 VO + #11 Strategy | FS S13, S14.3 |
| `ResolutionMemo`, `MemoKey` | `core.memo` | #22 Cache-Aside (in-process) | Per-pinned-snapshot memoisation |
| `CdmFxEventMapper` + per-entity mappers | `fx-cdm` | #42 Data Mapper | CDM payload to `FxIngestRecord` |
| `FxModule`, `MeteredFxConverter` | `fx-guice` | #9 DI via Guice Modules, #13 Decorator | Wiring only; latency instrumentation (A-11) |

Pattern #22 and #13 are cited as the nearest ADR-0001-2 entries for the memo and the metering decorator. **No new pattern is proposed** by this spec; if the catalogue numbers these differently the citations are mechanical to correct.

### 6.2 Pipeline Order (binding)

```
convert(request)
  1  TENANT        TenantContextProvider.currentTenant()   -> FX_E_NO_TENANT_CONTEXT
  2  PIN           context.snapshot, else implicit latest   -> FX_E_TENANT_MISMATCH,
                                                               FX_E_TENANT_NOT_READY,
                                                               FX_V_UNSIGNED_SNAPSHOT
  3  POLICY        PolicyRef -> ResolvedPolicy (purpose defaults applied)
  4  VALIDATE      PolicyMatrix: rule x leg x purpose x itemType x amountType
                   -> FX_V_INVALID_POLICY, FX_V_AMOUNT_TYPE_MISMATCH,
                      FX_V_SOURCE_NOT_ALLOWED, FX_V_PRICE_SERIES_MISMATCH
  5  MEMO          MemoKey lookup on the pinned snapshot's memo
  6  DATES         raw -> offset -> calendar -> resolved      (D-02)
                   -> FX_E_NON_PUBLICATION_DATE, FX_E_MISSING_EVENT_DATE,
                      FX_E_DATA_NOT_LOADED, FX_W_DATE_RULE_ADJUSTED
  7  OBSERVATIONS  ObservationSetBuilder (NONE => exactly one observation)
  8  ENTITLEMENT   filter rateSourcePriority                 (D-10)
                   -> FX_E_SOURCE_NOT_ENTITLED, FX_W_SOURCE_SKIPPED_NOT_ENTITLED
  9  PAIR          nine-step chain      -> FX_E_NO_FX_PATH, FX_E_INACTIVE_CURRENCY
 10  RATE          RateSelector matrix per route leg
 11  FALLBACK      only on a miss on an OPEN publication day
                   -> FX_E_RATE_NOT_FOUND, FX_W_FALLBACK_USED
 12  FORWARD       ForwardCurveCache + interpolation
                   -> FX_E_EXTRAPOLATION_LIMIT, FX_W_EXTRAPOLATED
 13  AVERAGE       AveragingEngine (RATE_AVERAGE / PRICE_MATCHED)
 14  APPLY         amount x rate (or / rate), DECIMAL128, fixed operation order
 15  PRECISION     PrecisionEngine + LargestRemainderAllocator
 16  RESTRICTION   RestrictionPropagator: intersect rights of contributing sources
 17  LINEAGE       LineageBuilder; inputsHash lazy (A-13)
 18  MEMO PUT
```

Stage 6 strictly precedes stages 9-12. **That ordering is D-02.** A non-publication day is moved at stage 6 and the resulting rate is CONFIRMED; stage 11 is reachable only from a miss on a day the calendar reports open. Enforced structurally: `FallbackChainRunner` accepts only a `RateLookupMiss`, whose compact constructor asserts `calendarIndex.isOpen(resolvedDate)`.

`convertChain` runs 1-5 once, then 6-17 per leg via `ChainEngine`. `convertSeries` runs 6-12 per observation and 13-17 once.

### 6.3 Stages 1-2: Tenancy and Pinning

- `currentTenant()` is called on **every** public method; no method takes a tenant parameter (FS S12). Empty -> `FX_E_NO_TENANT_CONTEXT`.
- `status(tenant) != READY` -> `FX_E_TENANT_NOT_READY`. In `LAZY` mode the first call triggers `loadTenant` and blocks up to `bootstrapTimeout`.
- `FxSnapshot.tenantId()` differing from context -> `FX_E_TENANT_MISMATCH`, checked on the facade and on `context.snapshot()`.
- Implicit pin (`context.snapshot() == null`) is permitted only when `runMode == AD_HOC` and `FxConfig.allowImplicitPin`; it pins `latest(tenant, EOD)` and that snapshot's own cut, and records the id in lineage so the run stays replayable.
- `runMode == OFFICIAL` + `UNREALISED_MTM` + `UNSIGNED` snapshot -> `FX_V_UNSIGNED_SNAPSHOT` (FS S6.3).
- A pin captures five references at once (A-15): tenant catalogue, GLOBAL catalogue, fixing view, market snapshot, knowledge cut. `PinnedState` is built once and no stage re-reads a store.

### 6.4 Stages 3-4: Policy Resolution and the Policy Matrix

`ResolvedPolicy` = `PolicyRef` resolved (`ById` through the catalogue at `valuationDate` + knowledge cut, or `Inline` verbatim) + purpose defaults + `FixingVersionSelection` normalisation (A-16). It is immutable and is hashed **in full** into `inputsHash`, so defaulting can never silently change a replay.

| Purpose | `fixingVersionSelection` | Date rule | `spotAdjustment` | `futureDateTreatment` |
|---------|--------------------------|-----------|------------------|------------------------|
| CONTRACT_SETTLEMENT | `FirstOfficial` | trade terms (no library default) | NONE | FORWARD |
| UNREALISED_MTM | `LatestCorrected` | VALUATION_DATE | TO_VALUATION_DATE (OQ-06) | FORWARD |
| CASH_PROJECTION | `LatestCorrected` | PAYMENT_DATE | NONE | FORWARD |
| ACCOUNTING_RECOGNITION | `LatestCorrected` | entity policy (OQ-02) | NONE | FORWARD |
| ACCOUNTING_REVALUATION | `LatestCorrected` | CLOSING_RATE | NONE | FORWARD |
| ACCOUNTING_SETTLEMENT | `LatestCorrected` | SETTLEMENT_DATE | NONE | FORWARD |
| TRANSLATION | `LatestCorrected` | entity/group policy | NONE | FORWARD |
| MANAGEMENT_VIEW | `LatestCorrected` | VALUATION_DATE | TO_VALUATION_DATE | FORWARD |

Cells reading "trade terms" or "entity policy" are **not** defaulted by the library; absence is `FX_V_INVALID_POLICY`. FS OQ-01/OQ-02/OQ-06 are the open items behind firm-wide defaults and stay unresolved (S14.1).

`PolicyMatrix` is a static immutable set of `EnumMap`/`EnumSet` tables built at class init, encoding:

1. **Rule x Leg** -- FS S8.3 verbatim.
2. **Purpose -> allowed legs**, **Purpose -> required `AmountType`** -- FS S9.1.
3. **Purpose x `ItemType`** -- FS S9.2.
4. **Purpose -> forbidden fallback steps** -- `INTERPOLATE_FIXINGS` forbidden for CONTRACT_SETTLEMENT and all four ACCOUNTING_* purposes (FS S11.3).
5. **Purpose -> allowed `usageClass`** -- `MTM_ONLY` rejected for CONTRACT_SETTLEMENT and ACCOUNTING_SETTLEMENT -> `FX_V_SOURCE_NOT_ALLOWED` (FS S11.5, vector X09).
6. **Purpose x `AmountType`** -- UNREALISED_MTM requires PRESENT_VALUE, CASH_PROJECTION requires NOMINAL_FUTURE, others NOMINAL -> `FX_V_AMOUNT_TYPE_MISMATCH` (D-05, vector F07).

A class-init self-test asserts the tables are **total** over the enum cross-product, so a new enum constant fails fast instead of defaulting to "allowed". The matrix is exported read-only to `fx-testkit`, which makes the FS S20 "every rule x leg x purpose x item type" requirement an executable generated test (S12.3).

### 6.5 Stage 6: Date Resolution (FS S7, D-02)

Each calendar version compiles once, at catalogue-build time, into:

```
CalendarIndex (immutable)
 +-- epochDay0 : long        coverage start
 +-- bits      : long[]      1 bit/day, set = publication/business day
 +-- prevOffset: short[]     days back to previous set bit (0 if set)
 +-- nextOffset: short[]     days forward to next set bit (0 if set)
 +-- calendarRef, versionId, zone, coverage
```

`isOpen(d)` is a bit test; `previous(d)`/`next(d)` are one array read; `plusBusinessDays(d,n)` walks offsets. Joint calendars are produced by `JointCalendarIndex.intersect(...)` and memoised per sorted `(calendarRef,versionId)` set inside the `ReferenceCatalogue` generation, so each joint calendar is built at most once per generation. A query outside `coverage` -> `FX_E_DATA_NOT_LOADED` with `details.calendarRef` and the bounds (A-14, OQ-T02). Nothing is ever lazily loaded on the resolution path.

**Order of operations (FS S7.1):**

1. `RawDateDeriver` maps `DateRule` + context to raw date(s):

| Rule | Raw date source | Missing-input error |
|------|-----------------|---------------------|
| TRADE_DATE / SPECIFIC_DATE / PAYMENT_DATE / DELIVERY_DATE | corresponding `tradeDates` field | `FX_V_INVALID_POLICY` |
| PRICING_SET | each `PricingObservation.observationDate` via `fxDateFromObservation` (SAME_DATE default, OFFSET(n)) | `FX_V_INVALID_POLICY` |
| DELIVERY_DAYS | each `deliveryDays[].day`, mapped per 6.5.1 | `FX_V_INVALID_POLICY` |
| EVENT | highest-ranked `events[type]` by `sourceRank`; `estimated` honours `estimatedEventHandling` (USE_ESTIMATE default, FAIL) | `FX_E_MISSING_EVENT_DATE` |
| RECOGNITION_DATE / SETTLEMENT_DATE / FAIR_VALUE_DATE / HISTORICAL_RATE | corresponding `accountingDates` field | `FX_V_INVALID_POLICY` |
| VALUATION_DATE | `context.valuationDate` | n/a |
| AVERAGE_RATE | every open day of the source's publication calendar in `[periodStart, periodEnd]` (IAS 21.22) | `FX_V_INVALID_POLICY` |
| CLOSING_RATE | last open publication day `<= periodEnd` | `FX_V_INVALID_POLICY` |

EVENT ranking consumes the rank supplied on `EventDate` (PDR S6.6 convention) and is not re-derived.

2. `OffsetSpec` applied -- business days on `FIXING_SOURCE` / `PAIR_SETTLEMENT_JOINT` / `CURRENCY` / `CUSTOM`, or plain `CALENDAR_DAYS`.
3. Calendar resolution, split by rate kind:
   - **Fixing dates** -> publication calendar (or policy joint calendar). Not open: `USE_PREVIOUS` -> `prev()`, `USE_NEXT` -> `next()` (both emit `FX_W_DATE_RULE_ADJUSTED` + reason `DATE_RULE_ADJUSTED`); `SKIP_OBSERVATION` -> drop, `skipped = true`, weights exactly renormalised (6.11), legal **only** inside an average else `FX_V_INVALID_POLICY`; `FAIL` -> `FX_E_NON_PUBLICATION_DATE`.
   - **Value dates** (spot/forward) -> joint settlement calendars with `rollConvention`; `MODIFIED_FOLLOWING` rolls back on month-end crossing, `MODIFIED_PRECEDING` mirrors it.
4. `ResolvedDates` records `(rawDate, resolvedDate, calendarRef, versionId, handlingApplied)` per observation plus the `(calendarRef -> versionId)` map that flows into `Lineage.calendarVersions`.

`SpotDateCalculator` applies `spotLag` business days over `intersect(spotCalendars)`. USNY membership for USD crosses is reference data, not code, so the USD-holiday rule is configuration (FS S5). T+1 pairs fall out of `spotLag = 1`.

Vectors C01 (Good Friday, USE_PREVIOUS -> Thu 2-Apr, CONFIRMED), C02 (USE_NEXT -> Tue 7-Apr, skipping Easter Monday), C03 (FAIL -> `FX_E_NON_PUBLICATION_DATE`) and C05 (Sat gas delivery -> Fri) are all stage-6 outcomes with no fallback involvement.

#### 6.5.1 Delivery-day and gas-day mapping (FS S7.2)

- **Power**: the local delivery date in `tradeDates.marketZone`; already a `LocalDate` in market time, no instant conversion.
- **Gas**: the gas-day start date in the market zone (06:00 D to 06:00 D+1 maps to D).
- Each mapped date then goes through step 3 with `nonPublicationDayHandling` (default `USE_PREVIOUS`).
- Under `Weighting.VOLUME`, `DeliveryDay.volume` becomes the observation weight as an exact `Rational` over its decimals.

### 6.6 Stage 8: Entitlement Filtering (D-10, FS S12)

1. For each `sourceCode` in `rateSourcePriority`, resolve `SourceEntitlement(tenantId, sourceCode)` at `fxDate` and the pinned cut; TENANT overlays GLOBAL at the same key.
2. Keep iff `rights` contains `VALUATION`. Drops emit `FX_W_SOURCE_SKIPPED_NOT_ENTITLED` with `details.sourceCode` (vector X05).
3. Empty **and** no entitled fallback -> `FX_E_SOURCE_NOT_ENTITLED` (vector X06).
4. Only the filtered list is threaded past stage 8, so no downstream stage can reach an unentitled source.

`RestrictionPropagator` folds rights with `intersect` at stage 16:

| Derivation | Rights of the result |
|------------|----------------------|
| Direct quote | rights of that source |
| Inverse quote | rights of that source (inversion adds no source) |
| Cross / triangulation | intersect of both legs' sources |
| Forward from points | intersect of spot source and the points source |
| Forward from CIP | intersect of spot source and both discount-curve sources |
| Average | intersect over all observations |
| Fixed factor, contract rate, manual override, identity | unrestricted -- reference data, not licensed market data |
| Leg chain | intersect over legs; exposed per leg **and** chain-level |

The library reports restrictions; it never enforces display rules (FS S12).

### 6.7 Stage 9: Pair Resolution (FS S11.2)

`PairResolver` holds an ordered, fixed `List<PairResolutionStep>`. The order is the specification and is asserted by a test comparing the step class list against a literal.

| # | Step | Behaviour | Reason / code |
|---|------|-----------|---------------|
| 1 | `IdentityStep` | `from == to` -> factor 1, no market data touched | `IDENTITY` |
| 2 | `FixedFactorNormaliser` (pre) | normalise minor units and `preferOverMarket` legal pegs to `majorCurrency`; applied **first** only | `FIXED_FACTOR` |
| 3 | `IdentityStep` again | post-normalisation identity (GBp vs GBP) | `IDENTITY` |
| 4 | `ContractRateStep` | `policy.contractRate` matching the normalised pair within `effectiveFrom/To`; `quotedIn` parsed and normalised before matching; CONTRACT leg only | `CONTRACT_RATE` |
| 5 | `ManualOverrideStep` | approved `ManualRateOverride` for `(scope, pair, fxDate, sourceCode?)`, only when `policy.allowOverrides` | `MANUAL_OVERRIDE` |
| 6 | `DirectQuoteStep` | market-convention quote from the entitled source list | `FIXING`/`SPOT`/`FORWARD_RATE` |
| 7 | `InverseQuoteStep` | `ONE.divide(rate, DECIMAL128)`, `inverted = true` | `INVERTED` |
| 8 | `ConfiguredCrossStep` | `PairConvention.triangulationVia`; `policy.forceCrossVia` outranks it | `TRIANGULATED` |
| 9 | `MajorCrossStep` | USD, then EUR | `TRIANGULATED` |
| -- | exhausted | `FX_E_NO_FX_PATH` | -- |
| -- | `FixedFactorNormaliser` (post) | denormalise to the requested target minor unit; applied **last** only | `FIXED_FACTOR` |

Invariants asserted in `PairRoute`'s compact constructor:

- **At most one intermediate currency** (`legs().size() <= 2`).
- **Fixed factors never mid-cross**: a `FIXED_FACTOR` `PathStep` may occupy only index 0 or n-1. This produces vector G03: `85.50 GBp -> 0.855 GBP -> 1.08585 USD -> 90.559890 INR`, effective GBp->INR `1.05918`.
- **Same date, source and cut-off on both legs** unless `allowMixedSources`, then `FX_W_MIXED_SOURCE` + reason `MIXED_SOURCE`.
- **Forward crosses per maturity**: each leg forwarded to the *same* value date, then crossed. Crossing spots then forwarding is not permitted.
- Currency validity checked at `fxDate` -> `FX_E_INACTIVE_CURRENCY` (HRK 2023-01-01, BGN 2026-01-01). Vector G09 terminates at step 2 with `LEGAL_PEG` 1.95583 and never touches the market.
- G01 `EUR/JPY = 1.0850 x 149.20 = 161.8820` is step 9; G02 `GBP/AUD = 1.2700 / 0.6600 = 1.924242...` is step 9 with the second leg inverted.

### 6.8 Stages 10-11: Rate Selection and Fallback (FS S11.1, S11.3)

`RateSelector` is a **decision table**, not nested conditionals. Each row is a `RateCase` with predicate `(dateComparison, fixingState, routeKind)` and outcome `(rateType, finality, reason)`. Rows evaluate in declared order; each row is addressable from tests, so FS S11.1 maps one-to-one onto parameterised cases.

| Row | FX date vs valuation date | Fixing state at the pinned cut | Outcome | Finality (reason) |
|-----|---------------------------|-------------------------------|---------|-------------------|
| R1 | before | OFFICIAL/CORRECTED per selection | fixing | CONFIRMED (`FIXING`) |
| R2 | before | PRELIMINARY only | preliminary fixing | ESTIMATED (`PRELIM_FIXING`) |
| R3 | before | missing on an **open** publication day | fallback chain | ESTIMATED (`FALLBACK_*`) or UNRESOLVED (`RATE_NOT_FOUND`) |
| R4 | equal | `recordedAt <= cut` | fixing | CONFIRMED (`FIXING`) |
| R5 | equal | not yet published at the cut | forward to the fixing's value date, or spot per policy | ESTIMATED (`PRE_PUBLICATION`) |
| R6 | after | -- | forward per `futureDateTreatment` | ESTIMATED (`FORWARD_RATE`) |
| R7 | any | fixed-factor route from stage 9 | factor | CONFIRMED (`FIXED_FACTOR`) |
| R8 | any | contract rate from stage 9 | contract rate | CONFIRMED (`CONTRACT_RATE`) |
| R9 | any | approved override from stage 9 | override | CONFIRMED (`MANUAL_OVERRIDE`) |

R7-R9 read the route kind rather than re-deciding precedence, because stage 9 already chose. "Published at or before the cut" is `recordedAt <= knowledgeCut` -- **never** a wall-clock comparison against `cutoffTime`, because there is no clock on the resolution path (D-01). `CutoffInstantResolver` exists only to compute a fixing's nominal publication instant for lineage and `loadFixings` windows (S10c).

**Fixing version selection** (`FixingVersionSelector`, FS S6.1) over versions of one `(source, pair, fixingDate, cutoff)` key with `recordedAt <= cut`:

| Selection | Chosen version |
|-----------|----------------|
| `FirstOfficial` | earliest OFFICIAL |
| `LatestCorrected` | latest OFFICIAL or CORRECTED |
| `AsOfKnowledge(t)` | latest OFFICIAL or CORRECTED with `recordedAt <= min(t, cut)` |
| none of the above present | latest PRELIMINARY -> ESTIMATED (`PRELIM_FIXING`) |

This one method produces X01 (FirstOfficial keeps 1.0800 after the correction), X02/X03 (cut 2-Apr 18:00 -> 1.0800; 3-Apr 18:00 -> 1.0810) and X04 (replay bit-identical).

**Fallback chain.** The policy's ordered `List<FallbackStep>` runs; each step returns `Optional<RateQuote>` + reason. Every success emits `FX_W_FALLBACK_USED` and downgrades finality to ESTIMATED.

| Step | Implementation | Reason |
|------|----------------|--------|
| `ALT_SOURCE` | next **entitled** source in the filtered list, same resolved date and cut-off class | `FALLBACK_ALT_SOURCE` |
| `PREVIOUS_PUBLICATION_DAY(maxSteps)` | same source, step back via `prevOffset` up to `maxSteps` (default 3); staleness in days recorded in `PathStep` details for FS OQ-07 | `FALLBACK_STALE` |
| `TRIANGULATE` | cross from legs of the **same source and same resolved date**; reuses `PairResolver` steps 8-9 with the source pinned | `FALLBACK_TRIANGULATED` |
| `INTERPOLATE_FIXINGS` | linear in the rate between nearest surrounding fixings of the same source; rejected at stage 4 for CONTRACT_SETTLEMENT and all ACCOUNTING_* purposes | `FALLBACK_INTERPOLATED` |
| `FAIL` | terminal -> `FX_E_RATE_NOT_FOUND`, UNRESOLVED | `RATE_NOT_FOUND` |

Vector C04 (WMR outage on a scheduled day, policy `[WMR, ECB]`) exercises `ALT_SOURCE`; because the row carries `FALLBACK_ALT_SOURCE` and a later cut finds the real fixing, every ESTIMATED fallback row is upgradeable (FS S11.1), via `assessCorrectionImpact` (6.16).

### 6.9 Stage 12: Forward Construction and Interpolation (FS S11.4, D-15)

`ForwardCurve` is immutable and built once per `(tenant, marketSnapshotId, pair)`, cached **inside the `MarketSnapshot`**, so cache lifetime equals snapshot lifetime and no invalidation exists:

```
ForwardCurve (immutable)
 +-- pair, marketSnapshotId, method, interpolation
 +-- spot : BigDecimal          spotDate : LocalDate      pointsScale : BigDecimal
 +-- valueDates : LocalDate[]   (ascending)
 +-- outrights  : BigDecimal[]  (points converted at build time)
 +-- t          : Rational[]    (ACT/365F = days(spotDate, valueDate) / 365, exact)
 +-- lnCarry    : BigDecimal[]  (ln(F_i/spot); LOG_LINEAR_CARRY only)
 +-- cubicSlopes: BigDecimal[]  (Hyman-filtered; MONOTONE_CUBIC_POINTS only)
 +-- onPoints, tnPoints : BigDecimal (nullable)
 +-- maxExtrapolationDate : LocalDate      sourceRights : RightsSet
 +-- memo : ConcurrentHashMap<LocalDate, BigDecimal>  (bounded, 6.9.3)
```

**Construction.** `POINTS`: `F_i = S + P_i / pointsScale`. `CIP`: `F_i = (S * DF_base) / DF_quote` -- multiply before dividing, one division. `HYBRID`: points to the last pillar, then CIP **anchored** there: `F(t) = F_last * (DF_base(t)/DF_base(t_last)) / (DF_quote(t)/DF_quote(t_last))`, rearranged to one division; reason `HYBRID_ANCHORED`. Time is `Rational(days, 365)` throughout, never a decimal division (FS S11.4 "exact rational"). `lnCarry[i]` is computed at **build** time, so a 20-pillar curve costs ~20 `ln` calls, not one per query -- this is what makes the FS S18 "<= 1 ms per pair per snapshot" reachable (S10a.2).

**Query** `outright(valueDate)`:

1. `valueDate <= spotDate` -> short end: `F(T+1) = S - TN/pointsScale`, `F(T+0) = S - (ON + TN)/pointsScale`; used by `ShortEndAdjuster` for `spotAdjustment = TO_VALUATION_DATE`, reason `SPOT_ADJUSTED`. ON/TN absent -> fall back to `S`; FS S11.4 gives the formulas but not the absent-data behaviour (OQ-T06).
2. Binary search `valueDates` for the bracket.
3. Interpolate:
   - `LOG_LINEAR_CARRY` (default, D-15): `y = lnCarry[i] + (lnCarry[i+1]-lnCarry[i]) * (t-t_i)/(t_{i+1}-t_i)`, the time ratio exact in `Rational` and converted to decimal once; `F = S * exp(y)`.
   - `LINEAR_POINTS`: linear in points against time. Vector G04: `35 + 33*28/91 = 45.153846...` points -> `1.0895153846...` at `pointsScale = 10000`.
   - `MONOTONE_CUBIC_POINTS`: Fritsch-Carlson slopes with the Hyman filter, pure DECIMAL128/rational arithmetic, no transcendentals.
4. Extrapolate: before the first pillar, from spot with zero carry; after the last, flat implied carry scaled linearly in `t` up to `maxExtrapolationYears` (default 2Y) -> `FX_W_EXTRAPOLATED`; beyond that, CIP when both discount curves exist, else `FX_E_EXTRAPOLATION_LIMIT`.
5. `interpolation`, bracketing `pillars` and method are written into `PathStep` (FS S11.4 "recorded in lineage"). D-15 is honoured by exposing this as the only forward source; FX Exposure consumes it rather than re-implementing.

**Forward crosses.** A two-leg `PairRoute` with a future value date resolves each leg's curve to the **same** value date and crosses outrights. The value date is computed once by `ValueDateResolver` over the joint settlement calendar of the whole route (both currencies plus the intermediate), not per leg.

#### 6.9.3 Query memoisation

`ForwardCurve.memo` is a bounded `ConcurrentHashMap<LocalDate, BigDecimal>` filled by `computeIfAbsent`. This keeps the FS S18 p99 honest: a LOG_LINEAR_CARRY query costs one `exp` on first touch of a value date and a hash lookup thereafter. The curve is immutable and snapshot-scoped, so the memo needs no invalidation and is tenant-safe by containment. Bound `FxConfig.forwardMemoMaxEntriesPerCurve` (default 4,096); on overflow new entries are refused rather than evicted, keeping behaviour deterministic. **Risk R1/R2.**

### 6.10 Deterministic Decimal `ln` and `exp` (D-09)

Summary here; algorithm, constants and verification in **Appendix D**.

- `FxMath` owns the **only** `MathContext` instances: `DECIMAL128` (34 digits, HALF_EVEN) for published results and `WORKING` (`FxConfig.decimalWorkingPrecision`, default 60 digits, HALF_EVEN) for intermediates. No `BigDecimal` arithmetic method is ever called without an explicit `MathContext` (AR-07).
- `DecimalLn.ln(x)`: extract `x = m * 10^k`; `r` rounds of exact-integer square root until `|y-1| <= 1e-3`; `atanh` series `ln y = 2 * sum_{n odd} z^n/n`, `z = (y-1)/(y+1)`; recombine `ln x = k*LN10 + 2^r * ln y`.
- `DecimalExp.exp(x)`: `n = round(x/LN10)` so `|r| <= LN10/2`; `m = 11` halvings to `|r'| <= 1.2e-3`; Taylor series; `m` squarings; `scaleByPowerOfTen(n)`.
- `DecimalSqrt` uses `BigInteger.sqrt()` on a scaled unscaled value -- **never** `BigDecimal.sqrt(MathContext)`, which seeds Newton from `Math.sqrt(double)` and would inject a floating-point dependency into the accuracy argument.
- Accuracy contract: relative error of the DECIMAL128-rounded result `<= 1e-33`, three orders inside the FS S11.4 requirement of 1e-30.
- JVM independence: integer and `BigDecimal` only; every precision and rounding mode explicit; no `double`, `float`, `Math`, `StrictMath`, `doubleValue()`, `BigDecimal.sqrt`, `Random`, or hash-order iteration. Enforced by AR-05/AR-06 and the bytecode scanner (S12.5).

### 6.11 Stages 7 and 13: Observation Sets and Averaging (D-07, D-08, FS S10)

`ObservationSetBuilder` emits an ordered `List<Observation>`, each `(sequence, observationDate, rawFxDate, resolvedFxDate, Rational weight, price?, volume?, duplicate, skipped)`. `averagingMethod = NONE` yields exactly one observation, so single-rate and averaging share one code path -- there is no bypass.

| `observationSet` | Observations | Weights |
|------------------|--------------|---------|
| `FROM_PRICING_SET` | `pricingSet.observations`, order and duplicates preserved | `FROM_PRICING_SET` uses PDR `Rational` weights **exactly**; EQUAL/VOLUME/CUSTOM also permitted |
| `FX_FIXING_DAYS_IN_WINDOW` | every open day of the source's publication calendar in the resolved window, `lag` in publication days | EQUAL, VOLUME, CUSTOM |
| `DELIVERY_DAYS` | `tradeDates.deliveryDays` mapped per 6.5.1 | VOLUME or EQUAL |
| `EXPLICIT` | `AveragingSpec.explicitDates` | EQUAL or CUSTOM |

`window` is required unless `observationSet = FROM_PRICING_SET`; absent -> `FX_V_INVALID_POLICY`.

**Weighted average arithmetic (binding order).** `RationalMath.commonDenominator(...)` brings weights to a common denominator `q` (exact `BigInteger` lcm), giving integer numerators `p_i`. Then

```
weightedSum = sum_i ( p_i * X_i )     // integer x decimal, DECIMAL128
average     = weightedSum / q         // exactly ONE division, DECIMAL128
```

per FS S10.2. Multiply-before-divide is fixed, so results are digit-identical across services (FS S15).

- `SKIP_OBSERVATION` drops the observation and recomputes `q` over survivors -- exact, because the weights are rational (FS S7.1). Reason `SKIPPED_OBSERVATION`.
- `RATE_AVERAGE`: average the **rate in market convention** for the pair, then apply to the period amount or average price, inverting at the point of application when the requested direction is the inverse. `averageInverted = true` averages inverted rates and emits `AVERAGE_INVERTED`. Vector G08: average 1.09, `82 / 1.09 = 75.229357798...`.
- `PRICE_MATCHED`: convert each observation at its own resolved FX date's rate, then weight. `prices[]` must key exactly onto the PDR `sequence` set; missing or extra -> `FX_V_PRICE_SERIES_MISMATCH` (vector X11). Vector G07: `80/1.10, 81/1.08, 82/1.12, 83/1.10, 84/1.05` equally weighted -> `75.279220779...`. The G07-G08 spread `0.049862981` is asserted explicitly in the testkit.
- **Hybrid PDR components**: observations partitioned by `componentRef`, each resolved with its own source and calendar, component totals recombined by PDR weights; the partition is stable and recorded per `ObservationResult`.
- **Interval observations** map by `observationDate` only; no sub-daily handling exists (S10c).

`PartialPeriodAggregator` (FS S10.3):

```
confirmedAverage = sum(p_i*X_i : CONFIRMED)     / sum(p_i : CONFIRMED)
estimatedAverage = sum(p_i*X_i : not CONFIRMED) / sum(p_i : not CONFIRMED)
confirmedPortion = Rational( sum(p_i : CONFIRMED), q )     // reduced
finality         = RateFinality.weakest(all observation finalities)
```

Reason `PARTIAL_PERIOD` whenever `confirmedPortion < 1`. Vector G06: `(10*1.0840 + 12*1.0872)/22 = 1.085745454...`, `confirmedPortion = 10/22 = 5/11`, ESTIMATED.

`roundAverage` applies to the average rate only when the contract fixes it, and is recorded in `PathStep`. `pdrRef` is copied verbatim into `Lineage.pdrRef` and into `inputsHash` (D-08), so a new PDR version makes stale results detectable without the library tracking PDR state.

### 6.12 Stage 15: Precision, Rounding, Allocation (FS S15)

- `toAmountUnrounded` is the full-precision DECIMAL128 value and is **always** returned.
- `toAmountBooked = unrounded.setScale(currency.decimals, rounding.amountRounding)` -- rounded exactly **once**; `HALF_UP` default, `HALF_EVEN` configurable per policy. Minor-unit currencies use their own `decimals`.
- Unit prices rounded only when `roundUnitPrice` is set; rates only when `roundRate` is set; both recorded in lineage.
- Identity collapse returns the input amount unchanged but still books to the target currency scale.

`LargestRemainderAllocator`: aggregate at full precision, round the **total** once; floor each line to currency scale; `residualUnits = (totalBooked - sum(floors)) / 10^-d` (exact integer); distribute one unit each to the largest fractional remainders, **ties to earliest sequence** (FS S15) so the allocation is deterministic and map-order independent; `AllocationResidual` reports the signed difference against independently converted line totals, line count, scale and tolerance.

`ReconciliationTolerance.of(d, lines, amount)` in `fx-api` computes `max(0.5 * 10^-d * lines, 1e-9 * |amount|)` so consumers use the library's formula. Property test (FS S20): `sum(line.toAmountBooked) == totalBooked` exactly.

### 6.13 Stages 16-17: Restriction, Lineage, `inputsHash`

**`inputsHash` = SHA-256 over the RFC 8785 (JCS) canonical JSON of one object with exactly these members, and nothing else:**

| Member | Content |
|--------|---------|
| `apiSchemaVersion` | `fx-api` schema version constant |
| `libraryVersion` | `FxVersion.VALUE` |
| `tenantId` | from `TenantContextProvider` |
| `marketSnapshotId` | pinned snapshot id |
| `fixingKnowledgeCut` | ISO-8601 instant, UTC, nanosecond-normalised |
| `referenceGeneration`, `fixingGeneration` | decimal integers as strings |
| `policy` | the **`ResolvedPolicy`** -- every field after defaulting, including `policyId`, `policyVersion`, and for inline policies the full field set |
| `request` | the `ReplayableRequest` projection: purpose, runMode, valuationDate, amountType, settlementAmountState, itemType, accountingUnitId, fromCcy, toCcy, fromAmount, isUnitPrice, tradeDates, events, accountingDates, prices |
| `pdrRef` | `(eventId, version, inputsHash)` only -- **not** the observation list, which PDR's own `inputsHash` already covers (D-08) |

**Excluded**: `requestId` (correlation only, FS S14.2); `calendarVersions` and `path` (outputs -- the generations already pin them); metrics; listeners; any wall-clock value.

**Binding canonicalisation rules:**

1. Every decimal is emitted as a **JSON string**, never a JSON number. RFC 8785 canonicalises numbers through ECMAScript IEEE-754 double serialisation, which would silently destroy 34-digit precision and break determinism. A test asserts a 34-digit rate survives canonicalisation byte-for-byte.
2. Decimal strings are normalised by `stripTrailingZeros().toPlainString()`, `"0"` for zero, leading `-` for negatives -- so `1.50` and `1.5` hash identically. This is the single place where A-07's scale sensitivity is resolved for hashing.
3. Members ordered by UTF-16 code-unit ordering of names; arrays keep source order; no insignificant whitespace; RFC 8785 string escaping.
4. `null`-valued members are **omitted**, so adding an optional field with no value does not change existing hashes.
5. Enums emit `name()`; dates `yyyy-MM-dd`; instants `...Z` nanosecond-normalised.

`CanonicalJson` is purpose-built in `core.lineage` -- no Jackson, no Gson (D-01 JDK-only) -- writes into a reusable `StringBuilder`, and is invoked lazily (A-13).

### 6.14 Legs, Chain and Management View (FS S9, D-04, D-16)

`ChainEngine` runs CONTRACT -> ACCOUNTING_TRANSACTION -> TRANSLATION, then zero or more MANAGEMENT_VIEW computations.

- **Identity collapse**: equal currencies **after minor-unit normalisation** short-circuit to identity, zero market-data access, CONFIRMED, reason `IDENTITY`.
- **Functional currency**: `FunctionalCurrencyResolver` reads `AccountingUnit` valid on the **leg's accounting date** (recognition, closing, settlement or valuation) and never back-applies a change (IAS 21.35). Missing -> `FX_E_FUNCTIONAL_CCY_NOT_FOUND`. Vector F08 resolves USD for a 2027-01-05 recognition.
- **No cross-leg collapse**: each leg resolves its own pair, date, rate and lineage; the engine never composes two legs into one direct cross even when arithmetically equivalent.
- **Leg ordering**: if leg *n* is UNRESOLVED, leg *n+1* is not computed; its `LegResult` is UNRESOLVED with reason `UPSTREAM_UNRESOLVED` and no market data is touched.
- **Leg-2 input** (FS S9.4), keyed on `settlementAmountState`, never on finality: `UNINVOICED` -> leg-1 `toAmountUnrounded` (or the caller's PV for UNREALISED_MTM); `INVOICED`/`SETTLED` -> leg-1 `toAmountBooked`, the invoice amount.
- **Item type** gates the ACCT_TXN leg via `PolicyMatrix`: MONETARY allows recognition/closing/settlement; NON_MONETARY_HISTORICAL allows the recognition rate only (CLOSING_RATE revaluation -> `FX_V_INVALID_POLICY`, vector F09); NON_MONETARY_FAIR_VALUE requires FAIR_VALUE_DATE.
- **TRANSLATION**: CLOSING_RATE for assets/liabilities, AVERAGE_RATE or transaction rate for income/expense, HISTORICAL_RATE for equity. The library converts what it is given per line; CTA/OCI is not computed (FS S1.2). Line-category batch translation is FS OQ-11 and is **not** designed here. Vector F05: `299,015.75 GBP x 1.1700 = 349,848.4275 -> 349,848.43 EUR`.
- **MANAGEMENT_VIEW** (D-16): computed on read, never persistable. One `ManagementViewResult` per requested report currency (A-18), each `persistable = false` with reason `VIEW_ONLY`. `LegResult.persistable` is the structural guard against a host persisting a view as a leg.
- **D-05**: UNREALISED_MTM requires PRESENT_VALUE; NOMINAL_FUTURE -> `FX_V_AMOUNT_TYPE_MISMATCH` (F07). CASH_PROJECTION is the only purpose that converts an undiscounted future amount at a forward to its payment date. Vectors F01 (`10,000 x 35.00 EUR x 1.0850 = 379,750.00 USD`), F02 (`379,750 / 1.2700 = 299,015.748... -> 299,015.75 GBP`), F06 (`379,000 / 1.2500 = 303,200.00 GBP`).

`RevaluationEngine.revalue(...)` (FS S9.3, D-04):

```
rateUsed               = rate for rule (CLOSING_RATE | SETTLEMENT_DATE) on the leg's date
newFunctionalUnrounded = signedForeignAmount converted at rateUsed    // signed throughout
newFunctionalBooked    = PrecisionEngine.book(newFunctionalUnrounded, functionalCcy, spec)
carrying               = carryingFunctionalAmount, or signedForeignAmount * carryingRate
difference             = newFunctionalUnrounded - carrying            // signed
classification         = CLOSING_RATE -> UNREALISED_FX_PNL ; SETTLEMENT_DATE -> REALISED_FX_PNL
any other rule         -> FX_V_INVALID_POLICY
```

The signed convention makes payables and receivables symmetric with no special cases. The library holds no balances and never derives realised FX against the original recognition implicitly (FS S9.3). Vectors F03 (`303,800.00 GBP`, `+4,784.25` UNREALISED) and F04 (`301,388.89 GBP`, `-2,411.11` REALISED) pin the sign convention.

### 6.15 Memoisation (`core.memo`)

`ResolutionMemo` lives **on the `PinnedFxSnapshot` instance**, not on the converter:

- Invalidation is structural -- a new snapshot or generation is a new `FxSnapshot` with an empty memo. No TTL, no invalidation event.
- Cross-tenant reuse is impossible because the snapshot is tenant-bound (FS S12).
- `MemoKey = (routeKindHint, fromCcy, toCcy, resolvedFxDate, valueDate, policyDigest, sourcePriorityDigest, fixingSelectionDigest)`. It keys the **rate**, not the amount, so `batch` over 1M conversions on 60 pairs resolves each distinct rate once (FS S18). Amount application and rounding are per-item and never memoised.
- `policyDigest` is a 128-bit digest of the `ResolvedPolicy` canonical form, computed once per instance and cached on it, so lookups do not re-canonicalise.
- Decimal fields in keys are `stripTrailingZeros()`-normalised, avoiding the A-07 trap.
- Bounded by `FxConfig.memoMaxEntriesPerSnapshot` (default 50,000); access-ordered LRU behind a 16-way striped lock -- the only lock on the read path, never held across a computation. Correctness never depends on the memo; a property test asserts identical results memo-on and memo-off.

### 6.16 Corrections and `assessCorrectionImpact` (D-06, FS S13)

**At ingest.** Applying a `CORRECTED` version first passes `FixingSequenceValidator` (a prior OFFICIAL must exist; `recordedAt` must not regress, else `FX_I_FIXING_SEQUENCE`). On success the ingestor calls `FxEventListener.onFixingCorrected(FixingCorrection)` with old/new version ids and values (vector X03), **after** the atomic swap, so a host that reacts sees the new generation.

**On demand.** `assessCorrectionImpact(Lineage previous, FxSnapshot current)`:

1. Rebuild the original request from `previous.replayKey()`. Absent (e.g. older library version) -> `CorrectionImpact{replayable = false}` with an `FxError`; the library does not guess.
2. **Self-check**: re-canonicalise the reconstructed request against `previous`'s generations and snapshot id and compare to `previous.inputsHash()`. A mismatch means the lineage is not faithful -> `replayable = false`, mismatch reported. This turns a class of silent-wrong-answer bugs into a loud failure.
3. Re-run against `current` (new cut and generations).
4. `changedInputs` lists, per fixing key on the old path, old and new `(versionId, value)` -- exactly what a host needs to locate affected rows.
5. `difference = newAmount - previousAmount`; `materiallyChanged = difference.signum() != 0` after booking to the target scale. No tolerance is applied; materiality belongs to the caller.
6. Nothing is overwritten, persisted or emitted (FS S13.3).

**Host contract (normative for consumers, FS S13.4).** Rows with `settlementAmountState in {INVOICED, SETTLED}` MUST be locked by the host. The library has no write path to consumer data. A CONTRACT leg under `FirstOfficial` is arithmetically immune to a later correction (vector X01) -- the primary defence; the lock is the secondary one.

### 6.17 `fx-cdm` Mapping

`CdmFxEventMapper.map(CdmFxEvent) -> FxIngestRecord` dispatches on `entityType` to `CdmReferenceMapper` (eleven reference entities), `CdmFixingMapper` (`FixingVersion`) or `CdmSnapshotMapper` (`MarketSnapshotPayload`, incl. `chunkIndex`/`chunkCount`/`completionMarker`). Pure functions over `Map<String, Object>`; decimals parsed with `new BigDecimal(String)` and never `valueOf(double)`; malformed input yields a rejection record, not an exception. No transport, no I/O, no scheduler, no dependency on `fx-core`.

### 6.18 Code-to-Component Map

Ingest codes (FS S16), all returned in `IngestOutcome.rejections` and mirrored to `FxEventListener.onRejected`:

| Code | Raised by | Condition | Vector |
|------|-----------|-----------|--------|
| `FX_I_APPROVAL_INVALID` | `IngestValidator` | `approvedBy` null, `approvedBy == authoredBy`, or `approvedAt != recordedAt`; reference records and `ManualRateOverride` (D-11) | X07 |
| `FX_I_SCOPE_VIOLATION` | `IngestValidator` | scope/tenant mismatch with channel; TENANT record shadowing GLOBAL where not allowed | -- |
| `FX_I_SNAPSHOT_IMMUTABLE` | `MarketSnapshotStore.put` via `IngestValidator` | any change to a published `marketSnapshotId`; corrections arrive as a new id (`-v3`) | -- |
| `FX_I_SNAPSHOT_INCOMPLETE` | `SnapshotCompletionTracker` | chunks missing at the completion marker | -- |
| `FX_I_FIXING_SEQUENCE` | `FixingSequenceValidator` | CORRECTED without prior OFFICIAL, or `recordedAt` regression | -- |
| `FX_I_OVERLAP` | `IngestValidator` | overlapping validity at the same `recordedAt` for one natural key | -- |
| `FX_I_INVALID_VALUE` | `IngestValidator` | non-positive rate or factor, malformed decimal, `decimals < 0`, empty calendar coverage | -- |
| `FX_I_SEQUENCE_GAP` | `DefaultFxIngestor` | `sequence` gap; key marked STALE, `loadKey` invoked, `FX_W_STALE_KEY` on later resolution | -- |

Resolution codes (FS S17):

| Code | Raised by | Stage |
|------|-----------|-------|
| `FX_E_NO_TENANT_CONTEXT` | `DefaultFxConverter` | 1 |
| `FX_E_TENANT_MISMATCH` | `DefaultFxConverter` / `PinnedFxSnapshot` | 2 |
| `FX_E_TENANT_NOT_READY` | `DefaultFxHealth` via converter | 2 |
| `FX_V_UNSIGNED_SNAPSHOT` | `RequestValidator` | 2 |
| `FX_V_INVALID_POLICY` | `PolicyValidator` + `PolicyMatrix` | 4 (also 6 for `SKIP_OBSERVATION` outside an average) |
| `FX_V_AMOUNT_TYPE_MISMATCH` | `PolicyMatrix` | 4 |
| `FX_V_SOURCE_NOT_ALLOWED` | `PolicyMatrix` (usageClass rule) | 4 |
| `FX_V_PRICE_SERIES_MISMATCH` | `RequestValidator` precheck | 4 |
| `FX_E_MISSING_EVENT_DATE` | `RawDateDeriver` | 6 |
| `FX_E_NON_PUBLICATION_DATE` | `PublicationDateResolver` | 6 |
| `FX_E_DATA_NOT_LOADED` | `CalendarIndex`, `FixingView`, `MarketSnapshotStore` | 2, 6, 10 |
| `FX_E_SOURCE_NOT_ENTITLED` | `EntitlementResolver` | 8 |
| `FX_E_INACTIVE_CURRENCY` | `PairResolver` | 9 |
| `FX_E_NO_FX_PATH` | `PairResolver` (exhausted) | 9 |
| `FX_E_RATE_NOT_FOUND` | `FallbackChainRunner` (`FAIL`) | 11 |
| `FX_E_EXTRAPOLATION_LIMIT` | `Extrapolator` | 12 |
| `FX_E_FUNCTIONAL_CCY_NOT_FOUND` | `FunctionalCurrencyResolver` | per leg |
| `FX_W_DATE_RULE_ADJUSTED` | `PublicationDateResolver` / `ValueDateResolver` | 6 |
| `FX_W_SOURCE_SKIPPED_NOT_ENTITLED` | `EntitlementResolver` | 8 |
| `FX_W_MIXED_SOURCE` | `PairResolver` cross steps | 9 |
| `FX_W_FALLBACK_USED` | `FallbackChainRunner` | 11 |
| `FX_W_EXTRAPOLATED` | `Extrapolator` | 12 |
| `FX_W_STALE_KEY` | `ReferenceCatalogue` / `FixingView` on a STALE key | 3, 6, 10 |

---

## S7 -- Data Model Impact

**No database tables.** The library is entirely in-memory; reference and market data are owned by source systems. Stores are rebuilt from CDM events and loader SPIs at startup.

### 7.1 In-Memory Data Structures

#### 7.1.1 `Timeline<V>` (reference data)

Sorted versions for one natural key, ordered `(validFrom ASC, recordedAt ASC)`, immutable once built. Resolution for `(date d, knowledge cut k)`: binary search for candidates with `validFrom <= d < validTo`, scan backward for the greatest `recordedAt <= k`; a `RETIRED` winner means "no value". O(log n). Identical to UOM tech spec S7.1.1 -- deliberately, because FS S5 mandates the same envelope.

#### 7.1.2 `ReferenceCatalogue` (immutable, one generation)

```
ReferenceCatalogue (generation N)
 +-- currencies        : Map<String, Timeline<Currency>>
 +-- pairConventions   : Map<String, Timeline<PairConvention>>        key: "EUR/USD"
 +-- fixedFactors      : Map<String, Timeline<FixedFactor>>           key: "GBp>GBP"
 +-- fixingSources     : Map<String, Timeline<FixingSource>>
 +-- publicationCals   : Map<String, Timeline<PublicationCalendar>>
 +-- settlementCals    : Map<String, Timeline<SettlementCalendar>>
 +-- accountingUnits   : Map<String, Timeline<AccountingUnit>>
 +-- fxPolicies        : Map<String, Timeline<FxPolicy>>
 +-- accountingPolicies: Map<String, Timeline<AccountingFxPolicy>>    key: "(unitId,purpose)"
 +-- entitlements      : Map<String, Timeline<SourceEntitlement>>     key: "(tenantId,source)"
 +-- overrides         : Map<String, Timeline<ManualRateOverride>>    key: "(scope,pair,fxDate,source)"
 +-- calendarIndexes   : Map<String, CalendarIndex>                   compiled, per versionId
 +-- jointIndexes      : ConcurrentHashMap<String, JointCalendarIndex>  memoised intersections
 +-- generation : long        recordedAtHighWatermark : Instant       staleKeys : Set<String>
```

GLOBAL is a separate `ReferenceCatalogue` held once and shared read-only across tenants (FS S6.3 "physical sharing"). Resolution merges GLOBAL and TENANT with TENANT winning at the same key (D-13). Tenant catalogues hold a reference to the GLOBAL catalogue and always read the latest GLOBAL atomically (same choice as UOM TI-03).

#### 7.1.3 `FixingView` and `FixingSeries` (bitemporal, columnar) -- D-03

A `FixingVersion` object per fixing would not fit the memory budget at a 3-year hot window (S10a.3). Layout instead:

```
FixingView (immutable, generation M)
 +-- series : Map<SeriesKey, FixingSeries>     SeriesKey = (sourceCode, pair, cutoff)
 +-- window : LocalDateRange                   hot window actually loaded
 +-- dayBuckets : NavigableMap<YearMonth, Set<SeriesKey>>   for O(buckets) window pruning
 +-- generation : long

FixingSeries (immutable, one source/pair/cutoff)
 +-- dayOffset   : int[]        days since window start, ascending, one entry per fixing DATE
 +-- versionFrom : int[]        index into the version arrays (CSR-style row pointer)
 +-- values      : BigDecimal[] all versions of all dates, grouped by date,
 |                              within a date ordered by recordedAt ASC
 +-- recordedAt  : long[]       epoch nanos, parallel to values
 +-- status      : byte[]       PRELIMINARY | OFFICIAL | CORRECTED
 +-- versionIds  : String[]
 +-- valueDates  : int[]        day offsets
 +-- correctionOf: String[]
```

Resolution for `(fixingDate, knowledgeCut, FixingVersionSelection)`:

1. Binary search `dayOffset` for the date -> row `i`; absent -> miss (stage 11 eligible only if the calendar says the day is open).
2. Version slice is `[versionFrom[i], versionFrom[i+1])`.
3. Binary search `recordedAt` within the slice for the upper bound `<= knowledgeCut`.
4. Apply the FS S6.1 selection rule over the prefix (6.8). O(log n) with no allocation.

A compressed-sparse-row layout rather than nested maps is the design decision that makes the FS S18 memory target plausible; it also makes the whole series one object for GC purposes.

#### 7.1.4 `MarketSnapshot` (immutable) and `MarketSnapshotStore`

```
MarketSnapshot (immutable)
 +-- marketSnapshotId, kind, asOfDate, fixingKnowledgeCut, signOffStatus, scope, tenantId
 +-- spots         : Map<CurrencyPair, SpotQuote>
 +-- pillars       : Map<CurrencyPair, ForwardPillar[]>          ascending by valueDate
 +-- discountCurves: Map<CurrencyCode, DiscountCurve>            compiled, with log-DF pillars
 +-- curves        : ConcurrentHashMap<CurrencyPair, ForwardCurve>   lazily built, 6.9
 +-- sourceRights  : Map<CurrencyPair, RightsSet>
```

`MarketSnapshotStore` is `Map<String tenantId, LinkedHashMap<String snapshotId, MarketSnapshot>>` bounded to `retainedSnapshotsPerTenant` (LRU, default 8, FS OQ-08). A published snapshot is **never** mutated; `put` on an existing id -> `FX_I_SNAPSHOT_IMMUTABLE`. `SnapshotCompletionTracker` holds incoming chunks in a staging area keyed by `(snapshotId)`; the snapshot becomes **resolvable only** when the completion marker arrives and all `chunkCount` chunks are present (FS S6.3). Incomplete -> `FX_I_SNAPSHOT_INCOMPLETE`; the staging area is never visible to readers.

`ForwardCurve` instances live inside the snapshot, so pinning a snapshot pins its curves. `curves` is the only mutable field in an otherwise immutable object; it is populated by `computeIfAbsent` with an idempotent, side-effect-free builder, so concurrent construction can at worst duplicate work, never produce divergent values.

#### 7.1.5 `PinnedState` and `FxSnapshot`

```
PinnedState (immutable)
 +-- tenantId
 +-- global     : ReferenceCatalogue      (shared, read-only)
 +-- tenant     : ReferenceCatalogue
 +-- fixings    : FixingView
 +-- snapshot   : MarketSnapshot
 +-- knowledgeCut : Instant
 +-- refGeneration, fixingGeneration : long
 +-- memo       : ResolutionMemo
```

`PinnedFxSnapshot` wraps one `PinnedState`. Pinning is five reference reads and one allocation -- O(1), no copying (FS S6.4). A-15: pinning the fixing **generation** as well as the cut is what makes replay physically exact when a backfill with `recordedAt <= cut` lands after the pin.

### 7.2 What Is Computed, Not Stored

| Artefact | Built | Cached where | Invalidated by |
|----------|-------|--------------|----------------|
| `CalendarIndex` | at catalogue build | `ReferenceCatalogue.calendarIndexes` | new reference generation |
| `JointCalendarIndex` | first use | `ReferenceCatalogue.jointIndexes` | new reference generation |
| `ForwardCurve` | first use per pair | `MarketSnapshot.curves` | never (snapshot immutable) |
| Forward outright per value date | first use | `ForwardCurve.memo` | never |
| `ResolvedPolicy` + `policyDigest` | per request, cached on the instance | -- | -- |
| Rate resolution result | per request | `ResolutionMemo` on the pin | new pin |
| `inputsHash` | first access | memoised in `Lineage` (A-13) | -- |

---

## S8 -- Event Flow

The library produces no events and has no Kafka dependency. It consumes reference and market data through its ingestion pipeline. Transport is the host's (FS S1.2).

### 8.1 Inbound

```
Host (NOT library):
  List<FxIngestRecord> records = cdmEvents.stream().map(cdmFxEventMapper::map).toList();
  IngestOutcome outcome = fxIngestor.apply(records);
```

### 8.2 Ingest Sequence

1. **Partition** the batch by `tenantId` and by `FxStoreKind` (reference / fixing / snapshot). Each tenant partition is processed under that tenant's writer lock (S10.4).
2. **Deduplicate** by `versionId` (reference, fixings) and by `marketSnapshotId` + `chunkIndex` (snapshots). Already-seen -> counted as duplicates, no-op. Idempotency is by identity, not by sequence (FS S6.3).
3. **Validate** per FS S16 -- the eight checks of 6.18, in that order. Rejections never abort the batch; they accumulate.
4. **Sequence check** per `(tenantId, entityType, naturalKey)`: contiguity of `sequence`. A gap marks the key STALE, invokes `ReferenceDataLoader.loadKey(...)` and records `FX_I_SEQUENCE_GAP`. Resolution against a STALE key still succeeds but adds `FX_W_STALE_KEY` (or fails if `FxConfig.failOnStale`). Ordering and gap rules follow UOM FS S8.2 as FS S6.3 requires.
5. **Build** the next generation copy-on-write: only affected `Timeline`s, `FixingSeries` and compiled indexes are rebuilt; everything else is shared structurally.
6. **Swap** atomically per store (`AtomicReference.compareAndSet`). Readers in flight continue on the old generation; new readers see the new one. A reader can never observe a partial ingest.
7. **Notify** -- `onFixingCorrected` per correction, `onSnapshotAvailable` per snapshot that became resolvable, `onRejected` per rejection. All **after** the swap.
8. **Metrics** -- `ingestApplied`, `ingestRejected`, `generationAdvanced`.

`IngestOutcome.newGenerations` reports per-store generations so hosts can correlate; `snapshotsNowResolvable` lets a host pin immediately without polling.

### 8.3 Bootstrap

- **EAGER**: `loadGlobal()`, then `loadTenant(t)` for each `eagerTenantIds` entry, then `MarketDataLoader.loadSnapshot` for the configured latest ids and `loadFixings` for the hot window. Blocks until complete.
- **LAZY**: first call for an unknown tenant triggers `loadTenant` and blocks up to `bootstrapTimeout`; timeout -> `FX_E_TENANT_NOT_READY`.
- GLOBAL reference data is always eager (shared and small).
- The fixing hot window is loaded by `loadFixings(sources, pairs, [today - hotWindowYears, today], cut)`. The "today" bound is supplied by the **host** at bootstrap, not read from a clock in `fx-core` (D-01); `prewarm`/bootstrap requests carry explicit date ranges.

### 8.4 Reconciliation and Hot-Window Maintenance

- A periodic task (`FxConfig.reconciliationInterval`) calls `ReferenceDataLoader.loadChangesSince(tenantId, watermark)` and `MarketDataLoader.loadChangesSince(marketWatermark)` to catch missed events. Watermarks are the stores' `recordedAtHighWatermark`.
- `HotWindowPolicy` prunes fixing day-buckets outside the window and evicts snapshots beyond `retainedSnapshotsPerTenant` on the same cadence, under the writer lock.
- The scheduler is a `ScheduledExecutorService` owned by `DefaultFxIngestor` and started/stopped through a `Closeable` lifecycle hook (TI-04). Hosts that prefer their own scheduler bind `reconciliationInterval = ZERO` and call the loaders themselves.

### 8.5 Replay Outside the Hot Window

Resolution never loads lazily (FS S6.3). A fixing or snapshot outside the loaded window -> `FX_E_DATA_NOT_LOADED` with the requested key and the loaded window in `details`. The host must call `prewarm(PrewarmRequest)` first; `prewarm` loads through the SPIs, applies the same validation, and advances generations exactly as a normal ingest. `PrewarmOutcome.notFound` lists keys the loader could not supply.

---

## S9 -- Guice Wiring

### 9.1 `fx-guice/FxModule`

```java
// fx-guice -- Guice 7 AbstractModule, Pattern #9
public final class FxModule extends AbstractModule {
    private final FxConfig config;
    @Override protected void configure() {
        bind(FxConfig.class).toInstance(config);

        bind(ReferenceStore.class).to(InMemoryReferenceStore.class).in(Singleton.class);
        bind(FixingStore.class).to(InMemoryFixingStore.class).in(Singleton.class);
        bind(MarketSnapshotStore.class).to(InMemoryMarketSnapshotStore.class).in(Singleton.class);

        bind(DateRuleResolver.class).to(DefaultDateRuleResolver.class).in(Singleton.class);
        bind(PairResolver.class).to(DefaultPairResolver.class).in(Singleton.class);
        bind(RateSelector.class).to(DefaultRateSelector.class).in(Singleton.class);
        bind(FallbackChainRunner.class).to(DefaultFallbackChainRunner.class).in(Singleton.class);
        bind(ForwardCurveCache.class).to(SnapshotForwardCurveCache.class).in(Singleton.class);
        bind(AveragingEngine.class).to(DefaultAveragingEngine.class).in(Singleton.class);
        bind(ChainEngine.class).to(DefaultChainEngine.class).in(Singleton.class);
        bind(EntitlementResolver.class).to(DefaultEntitlementResolver.class).in(Singleton.class);
        bind(PrecisionEngine.class).to(DefaultPrecisionEngine.class).in(Singleton.class);
        bind(LineageBuilder.class).to(DefaultLineageBuilder.class).in(Singleton.class);
        bind(DecimalTranscendentals.class).to(FxMath.class).in(Singleton.class);

        bind(FxIngestor.class).to(DefaultFxIngestor.class).in(Singleton.class);
        bind(FxHealth.class).to(DefaultFxHealth.class).in(Singleton.class);

        // FxConverter is provided below so the metering decorator can wrap it
        requireBinding(TenantContextProvider.class);
        requireBinding(ReferenceDataLoader.class);
        requireBinding(MarketDataLoader.class);

        OptionalBinder.newOptionalBinder(binder(), FxEventListener.class)
            .setDefault().toInstance(FxEventListener.noop());
        OptionalBinder.newOptionalBinder(binder(), FxMetrics.class)
            .setDefault().toInstance(FxMetrics.noop());
    }

    @Provides @Singleton
    FxConverter fxConverter(DefaultFxConverter core, FxMetrics metrics, FxConfig cfg) {
        return cfg.meteringEnabled() ? new MeteredFxConverter(core, metrics) : core;   // A-11, #13
    }
}
```

- Three required SPI bindings (tenant, reference loader, market loader); two optional with no-op defaults.
- `MeteredFxConverter` is the **only** class in the reactor permitted to call `System.nanoTime()`, which is why it lives in `fx-guice` and not `fx-core` (A-11, AR-04).
- `fx-core` contains **no** Guice types. `jakarta.inject.@Inject` on constructors only (A-04). `fx-guice` depends on `fx-core`; the reverse dependency does not exist (Appendix A).
- No Spring anywhere in the reactor (MC-1, D-13 platform). A future `valuation-engine` adapter, when specified, binds `FxConverter` into `valuation-guice` as an anti-corruption layer (Pattern #15) and never introduces a `new` call in a `@Bean` method.

### 9.2 Host Composition

The host creates one `Injector` installing `FxModule` plus its own module binding the three SPIs. A host embedding both UOM and FX installs `UomModule` and `FxModule`; the two `TenantContextProvider` types are distinct interfaces in distinct packages, so the host binds both to one underlying tenant holder. There is no shared artifact between the reactors and there must not be one (MC-4).

---

## S10 -- Cross-Cutting

### 10.1 Tenant Handling (D-13, FS S12)

- Tenant comes from `TenantContextProvider` on every public call; **no public method takes a tenant parameter**. Absent -> `FX_E_NO_TENANT_CONTEXT`.
- Tenant `t` sees `GLOBAL union TENANT(t)`; TENANT outranks GLOBAL at the same natural key. Tenant-private market data (e.g. `INTERNAL_EOD`) is TENANT-scoped and structurally invisible to other tenants -- it lives in that tenant's `FixingView`/snapshot map, not in a shared one with a filter.
- `FxSnapshot`, `ResolutionMemo`, `ForwardCurve.memo` and listener notifications are all tenant-bound. Cross-tenant memo reuse is impossible by containment, not by key discipline alone.
- GLOBAL reference and GLOBAL market data are held once, read-only, and **filtered per tenant entitlement** at stage 8 (FS S6.3, S12) -- sharing bytes is not sharing access.
- Ingest records whose `scope`/`tenantId` disagree with the channel -> `FX_I_SCOPE_VIOLATION`.
- No hardcoded tenant id exists anywhere in the reactor; `fx-testkit`'s `InMemoryTenantContextProvider` is the only place literal tenant ids appear, and an ArchUnit rule (AR-10) confines it to test scope.

### 10.2 Bitemporal Invariants (D-03, FS S5, S6.1)

- Reference data: `(validFrom, validTo)` business time, `recordedAt` knowledge time, `APPROVED`/`RETIRED`, `authoredBy != approvedBy`, `correctionOf` + `reasonCode`. Versions are immutable; every change is a new version.
- Fixings: versioned `(PRELIMINARY | OFFICIAL | CORRECTED, recordedAt, versionId, correctionOf)`. Selection against a pinned cut per FS S6.1. Append-only within a generation; generations swap atomically.
- Market snapshots: immutable and versioned by id. A correction is a **new id**, never a mutation (FS S6.2).
- **No in-place mutation anywhere.** The only mutable fields in the whole engine are the three documented memo maps (snapshot curves, curve outrights, resolution memo), all of which are idempotent caches whose contents are a pure function of immutable inputs.
- Platform note: this mirrors the platform's bitemporal convention (knowledge-time append-only, supersede rather than update) even though the library owns no database. S5b-style ephemeral current-state data has no analogue here: every rate the library returns is attributable to a pinned, versioned input.

### 10.3 Transaction Boundaries

No database transactions. Write path: single-writer-per-tenant via explicit `ReentrantLock`, copy-on-write construction, atomic per-store swap. Read path: lock-free, readers hold immutable references. Pinning: `pin()` captures references and is immune to concurrent ingest.

### 10.4 Concurrency Model (FS S4.2, S18)

- **Lock-free reads.** Every read path dereferences `AtomicReference`s once into a `PinnedState` and then touches only immutable structures. The sole exception is the `ResolutionMemo` striped lock (6.15), never held across a computation.
- **Single writer per tenant.** `DefaultFxIngestor` holds `ConcurrentHashMap<String, ReentrantLock>`; a batch for tenant `t` locks before building the next generation. Three stores, one lock per tenant covering all three, so a batch touching reference data and fixings produces one consistent set of generations.
- **GLOBAL writes** are serialised by a dedicated GLOBAL lock. Tenant catalogues hold a reference to the GLOBAL catalogue and read it atomically, so a GLOBAL release does not rebuild tenant catalogues.
- **Snapshot staging** (chunked publication) is per-snapshot-id and only published into the store on completion, so partially assembled snapshots are never reachable.
- **No shared mutable state** between stages; `PinnedState` is passed by reference and never written.

### 10.5 Determinism (FS S18, vectors X04, X10)

Same request + same pinned snapshot + same library version -> bit-identical result. Guaranteed by: no clock on the resolution path; no I/O on the resolution path; explicit `MathContext` on every operation; fixed operation order (multiply before divide, one division per cross/average); exact `Rational` weights and day counts; deterministic decimal `ln`/`exp` (Appendix D); ordered collections everywhere a result depends on iteration order (`SortedMap`/`SortedSet`/`List`, never `HashMap` iteration); deterministic tie-breaks (earliest sequence for allocation, declared order for pair and fallback steps).

The **logical** determinism boundary is the knowledge cut; the **physical** one is the pinned generation (A-15). The residual gap -- a first-ever-seen fixing key backfilled with `recordedAt <= cut` after a result was produced -- is not closable inside the library and is raised as OQ-T03.

### 10.6 `BigDecimal` Scale Discipline (A-07)

`BigDecimal.equals` compares scale; record-generated `equals`/`hashCode` therefore do not express value equality. Binding rules:

1. Value comparison in library code and tests uses `compareTo` (or `FxAssertions.assertDecimalEquals`), never `equals`.
2. Any decimal entering a hash or a memo key is first `stripTrailingZeros()`-normalised (6.13, 6.15).
3. Published decimals keep the scale the specification implies: `toAmountBooked` at currency decimals, market quotes at source scale, derived rates at full DECIMAL128.
4. An ArchUnit rule (AR-08) forbids `BigDecimal.equals` and `Objects.equals` on `BigDecimal`-typed expressions in `fx-core`; records containing `BigDecimal` carry a Javadoc warning generated from a single template.

---

## S10a -- Performance Profile

### 10a.1 Resolution

| Metric | FS S18 target | Design notes |
|--------|---------------|--------------|
| Single convert, warm, curve cached | p99 <= 20 us | Memo hit: one striped-lock `LinkedHashMap.get` plus amount multiply and one `setScale`, well inside budget. Memo miss on a **fixing** path: two binary searches in a `FixingSeries`, a bit test on a `CalendarIndex`, one multiply -- single-digit microseconds. Memo miss on a **forward** path with a cold value date: one `exp` (~15-40 us, Appendix D.4) -- **over budget on first touch**, amortised by `ForwardCurve.memo`. See R1/R2. |
| Chain (three legs) | not specified | ~3x the single-leg cost plus `FunctionalCurrencyResolver` timeline lookups. Lineage built once per leg. |
| Series, 30 observations | not specified | 30 rate resolutions (mostly memo hits after the first few dates) plus exact rational weight arithmetic. `BigInteger` lcm over 30 small denominators is negligible. |
| Lineage `inputsHash` | not specified | ~1-3 us canonicalisation plus ~1 us SHA-256 for a typical request. **Lazy** (A-13), so it is outside the convert budget unless the host asks for it. |
| Batch, 1M conversions, 60 pairs | <= 5 s on one 8-core node | Distinct rates resolved once per snapshot via `MemoKey` (6.15). 1M amount applications at ~0.5-1 us each is 0.5-1 s single-threaded; `batch` is parallelisable by the host across snapshots because `FxSnapshot` is thread-safe. Dominated by `BigDecimal` allocation, not by resolution. |
| Memo hit | -- | < 1 us |

### 10a.2 Curve Build and Ingest

| Metric | FS S18 target | Design notes |
|--------|---------------|--------------|
| Forward curve per pair per snapshot | <= 1 ms | 20 pillars x one `ln` (~15-40 us) = 0.3-0.8 ms. **Tight.** Mitigations: build curves on first use rather than at snapshot assembly (so the cost is spread), and allow `FxConfig.decimalWorkingPrecision` to be lowered to 45 digits (still 1e-33 accurate) if measurement demands it. R1. |
| Fixing event applied | p99 <= 10 ms | Copy-on-write of one `FixingSeries` (array copy of one series, typically a few hundred entries) plus an atomic swap. Comfortable. |
| Snapshot available after completion marker, 200 pairs | <= 2 s | Assembly is parsing plus array sorting; curves are **not** built eagerly, which is what makes 2 s achievable. If eager curve build were required, 200 pairs x 0.5 ms = 0.1 s, also fine -- so eager build is a config option. |
| Bootstrap, 3-year hot window, 1,000 series | not specified | Dominated by loader I/O in the host, not by the library. |

### 10a.3 Memory Budget

| Component | Budget | Basis |
|-----------|--------|-------|
| Market snapshot, 200 pairs x 20 pillars | <= 50 MB (FS S18) | Pillars: 200 x 20 x ~120 B = ~0.5 MB. Spots and discount curves: ~1 MB. `ForwardCurve` per pair with `lnCarry` and slopes: 200 x ~6 KB = ~1.2 MB. Curve outright memos at the 4,096 default: 200 x 4,096 x ~80 B = **~65 MB** -- the dominant term and the one that breaches the budget if every pair is fully exercised. Mitigation: default `forwardMemoMaxEntriesPerCurve` to 512 (200 x 512 x 80 B = ~8 MB) and document the trade-off; 4,096 is only safe for a small pair subset. **This spec therefore sets the default to 512** and flags R2. |
| Reference catalogue per tenant | <= 5 MB | Thousands of records (A-03) plus compiled calendars: a 10-year `CalendarIndex` is ~3,650 bits + two `short[3650]` = ~15 KB; 50 calendars = ~0.8 MB. |
| GLOBAL reference catalogue (shared) | <= 2 MB | Currencies, pair conventions, calendars, fixed factors. |
| Fixing hot window, 3 years, 1,000 series, ~750 publication days, ~1.3 versions/fixing | ~60-90 MB | Columnar (7.1.3): per series, `int[750]` + `BigDecimal[1000]` + `long[1000]` + `byte[1000]` + `String[1000]` ~ 70-90 KB -> 70-90 MB for 1,000 series. **Not covered by any FS S18 budget** (FS defers it to OQ-08). With 5,000 series it is 350-450 MB, which is a host sizing decision, not a library one. R3. |
| Resolution memo, 50,000 entries | ~10 MB per pinned snapshot | Key ~100 B + `RateQuote` ~200 B + map overhead. Multiple concurrent pins multiply this; default lowered to 20,000 with the same reasoning as above. |

### 10a.4 No External Caching

No Redis, no external cache, no TTL. All caching is in-process and **generation-based**: a new ingest produces a new generation; a new generation means a new `FxSnapshot` and therefore a new, empty memo. Nothing requires an invalidation message, which is the direct benefit of D-03's immutable-snapshot model. No connection pool exists, because there is no connection.

Platform note: if a platform service later fronts this library over HTTP, Redis TTLs, cursor pagination and SSE fan-out belong to **that** service's spec, not this one, and must not be retrofitted into `fx-core`.

### 10a.5 Targets Considered At Risk

| Target | Verdict |
|--------|---------|
| p99 <= 20 us single convert | **At risk** on cold forward paths (one `exp`) and on any path where the host forces eager `inputsHash`. Safe on fixing paths and on memo hits. Requires JMH validation before the target is accepted (R1, R2). |
| Forward curve <= 1 ms | **At risk**; 0.3-0.8 ms measured-by-estimate, no margin. Lower working precision is the lever. |
| <= 50 MB per snapshot | **Achievable only with the memo bound set to 512**, which this spec adopts as the default. |
| Fixing hot window | **Unbounded by the FS.** 60-90 MB per tenant per 1,000 series is the design's own number; OQ-08 must set the policy. |
| 1M batch <= 5 s | **Achievable**, dominated by `BigDecimal` allocation; no design concern. |
| Determinism | **Achievable**, with the OQ-T03 caveat. |

---

## S10b -- Real-Time Push

**Not applicable.** This is an in-process library, not a service. There are no HTTP endpoints, no SSE, no WebSocket, no Kafka producer. The library is embedded by a host; real-time distribution of rate changes is the host's concern.

The library supplies the hooks a pushing host needs:

- `FxEventListener.onFixingCorrected` -- a correction arrived; the host may recompute and push.
- `FxEventListener.onSnapshotAvailable` -- a new snapshot is resolvable; the host may re-mark and push.
- `IngestOutcome.newGenerations` / `snapshotsNowResolvable` -- synchronous equivalents for a host that ingests in its own thread.

If a platform service exposes FX rates to a UI, that service's design -- not this one -- owns the SSE endpoint, the per-tenant connection scoping, the latest-value-wins throttle, the `Last-Event-ID` reconnection contract and the REST polling fallback. Two constraints bind that future design and are recorded here so they are not forgotten: **(a)** push payloads must carry `marketSnapshotId` and `fixingKnowledgeCut`, or the consumer cannot tell a correction from a tick; **(b)** `distributionRestriction.displayAllowed` must be honoured before any rate reaches a UI (FS S12, D-10) -- pushing a `displayAllowed = false` rate to a browser is a licence breach of the same severity as a cross-tenant leak.

---

## S10c -- DST Handling

**Partially applicable.** The library has no 15-minute intervals, no daily profile and no gate closure, so the platform's 92/96/100-interval rules do not arise. It does have three genuine time-zone concerns.

### 10c.1 Time Zone Convention

| Field class | Type | Convention |
|-------------|------|------------|
| FX dates, fixing dates, value dates, delivery days, accounting dates, calendar entries | `LocalDate` | Local to the relevant market or calendar. No zone conversion is performed on them, and none is needed: a publication calendar is a set of local dates in its own `zone`. |
| `recordedAt`, `knowledgeCut`, `approvedAt`, `publishedAt` | `Instant` | UTC always. These are knowledge-time values and are never rendered in local time by the library. |
| `FixingSource.cutoffTime` + `cutoffZone` | `LocalTime` + `ZoneId` | The only local-time-of-day data in the library. |

Results therefore contain no ambiguous timestamp: dates are local-by-definition, instants are UTC.

### 10c.2 Cut-off Instants and DST Gaps/Overlaps

`CutoffInstantResolver.instantOf(fixingDate, source)` = `ZonedDateTime.of(fixingDate, cutoffTime, cutoffZone).toInstant()`. On the spring-forward day a cut-off nominally inside the missing hour (e.g. 02:30 CET on the last Sunday of March) has **no** instant; on the fall-back day a cut-off inside the repeated hour has **two**. Binding resolution:

- **Gap**: the JDK shifts forward by the gap length (02:30 becomes 03:30 local). Accepted and documented.
- **Overlap**: the JDK selects the **earlier** offset (summer time). Accepted and documented.
- Both behaviours are deterministic, specified by `java.time`, and asserted by tests at the 2026 EU transition dates (2026-03-29 and 2026-10-25).

This matters only for lineage display and for choosing `loadFixings` windows. It **never** affects rate selection, because selection compares `recordedAt <= knowledgeCut` (both UTC instants) and never compares a clock to a cut-off (6.8). A DST ambiguity therefore cannot change a conversion result -- a deliberate consequence of the D-01 "no clock" rule.

### 10c.3 Gas Day and Power Delivery Day Across a Transition

- A **power delivery day** is a local `LocalDate` in `tradeDates.marketZone`. The 23-hour and 25-hour days remain one date and map to one FX date. Hourly or quarter-hourly structure is the caller's problem (and the platform's S6/S6b caches'), not this library's.
- A **gas day** maps to its local start date (6.5.1). On the fall-back day the gas day is 25 hours long; it is still one date and one FX date. On the spring-forward day it is 23 hours; likewise.
- No interval count is ever returned by this library, so the 92/96/100 rule has no surface here. If a caller supplies `deliveryDays[]` with volumes, the library weights by the supplied volumes and does not infer interval counts.

### 10c.4 Accounting Periods

`AVERAGE_RATE` and `CLOSING_RATE` enumerate publication days between `periodStart` and `periodEnd` inclusive, in the publication calendar's own local-date space. Period boundaries are `LocalDate` inputs from the caller; the library never derives them from an instant and so never needs a day-boundary time zone. There is no `timezone` request parameter, and none is needed.

---

## S11 -- Regulatory Impact

The library is infrastructure: it converts currency amounts and reports lineage. It submits no reports and holds no reportable records. The impact is indirect but real, because converted values and the rates behind them end up in reportable fields.

| Regulation | Relevance | What this design contributes |
|------------|-----------|------------------------------|
| REMIT (Regulation 1227/2011) | Transaction reporting of wholesale energy contracts carries price, price currency and notional. Converted settlement amounts and the FX rate behind them can enter Table 1 fields and fundamental-data submissions. | Deterministic replay (D-03, S10.5) and per-result lineage (`marketSnapshotId`, `fixingKnowledgeCut`, `fixingVersionId`, `calendarVersions`, `inputsHash`) let a firm reproduce any historical conversion on demand, which is what a REMIT data-quality query requires. Timeliness is unaffected: the library is in-process and adds microseconds. |
| EMIR | OTC derivative reporting requires notional, notional currency, and for currency derivatives the exchange rate and the rate basis. | `rateType`, `rateFinality`, `source`, `cutoff`, `rawDate`/`resolvedDate` and `fixingVersionId` in `PathStep` supply exactly the rate-provenance fields a reporting layer needs. Corrections are explicit and detectable (`assessCorrectionImpact`), so a rate restatement can be traced to the reports that used it. |
| MiFID II RTS 22 | Transaction reporting includes price and price currency; conversion errors propagate into reported prices. | Same determinism and provenance argument. `toAmountBooked` versus `toAmountUnrounded` is explicit, so a reporting layer never has to guess which value was booked. |
| IAS 21 / Ind AS 21 / ASC 830 | Accounting standards, not market regulation, but they are the binding rules for the ACCT_TXN and TRANSLATION legs. | Functional-currency effective dating applied prospectively only (6.14), monetary/non-monetary item treatment, AVERAGE_RATE practical expedient, realised/unrealised classification. |
| Market-data licensing (WMR, ECB, exchange rulebooks) | Not a regulation but a contractual obligation with comparable consequences. | D-10 entitlement filtering, most-restrictive propagation, `distributionRestriction` on every result (6.6). The library reports; the host enforces. |

**No direct reporting obligation falls on this library.** Two design elements are flagged as reporting-sensitive and must not be weakened without review: the completeness of `PathStep` provenance, and the faithfulness of `inputsHash` (an `inputsHash` that omits an input makes two different conversions look identical in an audit).

---

## S12 -- Testing Strategy

### 12.1 Test Location by Module

| Module | Test class | Type | Covers |
|--------|-----------|------|--------|
| `fx-api` | `ValueTypeValidationTest` | JUnit 5 | Compact-constructor rules: `VersionEnvelope` four-eyes, `Rational` reduction, `CurrencyPair`, `PricingDaySet` sequence monotonicity |
| `fx-api` | `ReconciliationToleranceTest` | JUnit 5 | FS S15 tolerance formula |
| `fx-api` | `SealedExhaustivenessTest` | JUnit 5 | Every `FxRequest`/`FxResult`/`PolicyRef`/`FixingVersionSelection` permit is handled by the testkit's exhaustive switches |
| `fx-core` | `DateResolutionTest` | JUnit 5 | FS S7 order of operations, all four `nonPublicationDayHandling` values, four roll conventions, offsets, coverage-window failure |
| `fx-core` | `CalendarIndexTest` | JUnit 5 | Bitmap correctness vs a naive reference implementation, joint intersection, prev/next at coverage edges |
| `fx-core` | `PairResolutionOrderTest` | JUnit 5 | Step order asserted against a literal list; fixed-factor first-and-last invariant; at-most-one-intermediate |
| `fx-core` | `RateSelectionMatrixTest` | JUnit 5 parameterised | One case per row R1-R9 of 6.8 |
| `fx-core` | `FixingVersionSelectionTest` | JUnit 5 | All three selection policies x all status combinations x knowledge cuts |
| `fx-core` | `FallbackChainTest` | JUnit 5 | Each step, step ordering, forbidden-step rejection per purpose, exhaustion |
| `fx-core` | `ForwardCurveTest` | JUnit 5 | POINTS/CIP/HYBRID construction, three interpolators, short end, both extrapolation branches, forward cross per maturity |
| `fx-core` | `AveragingTest` | JUnit 5 | Four observation sets, four weightings, both methods, both output shapes, skip-and-renormalise, hybrid components, duplicate preservation |
| `fx-core` | `PrecisionAllocationTest` | JUnit 5 | Largest-remainder allocation, earliest-sequence tie-break, residual reporting |
| `fx-core` | `LegChainTest` | JUnit 5 | Identity collapse, leg ordering, `UPSTREAM_UNRESOLVED`, leg-2 input selection, item-type gating, management-view non-persistability |
| `fx-core` | `RevaluationTest` | JUnit 5 | Signed differences for receivable and payable, both classifications, carrying-rate vs carrying-amount inputs |
| `fx-core` | `InputsHashTest` | JUnit 5 | Member set exactness; `requestId` exclusion; 34-digit survival; `1.50` == `1.5`; null omission; member ordering; a frozen golden hash for a reference request |
| `fx-core` | `IngestValidationTest` | JUnit 5 | All eight `FX_I_*` codes |
| `fx-core` | `BitemporalTest` | JUnit 5 | Timeline resolution, retirement, correction chains, snapshot immutability, replay after correction |
| `fx-core` | `TenancyIsolationTest` | JUnit 5 | GLOBAL/TENANT overlay, tenant-private market data invisibility, memo isolation, snapshot tenant mismatch, scope violation on ingest |
| `fx-core` | `EntitlementTest` | JUnit 5 | Filtering, skip warnings, no-entitled-source failure, restriction propagation through inverse/cross/forward/average/chain |
| `fx-core` | `ConcurrencyTest` | JUnit 5 | Readers during swaps, concurrent pins, concurrent curve construction converging on identical values, reconciliation racing with events, single-writer enforcement |
| `fx-core` | `CorrectionImpactTest` | JUnit 5 | Replayable and non-replayable lineage, self-check mismatch detection, `changedInputs` content |
| `fx-cdm` | `CdmMapperTest` | JUnit 5 | Per-entity mapping, decimal parsing, malformed-payload rejection |
| `fx-guice` | `WiringTest` | JUnit 5 + Guice 7 | `FxModule` installs; required SPIs enforced by `requireBinding`; optional defaults applied; golden vectors pass through the injected `FxConverter`; metering decorator applied and bypassed per config |
| `fx-testkit` | `GoldenArithmeticVectorTest` | JUnit 5 parameterised | **G01-G09** |
| `fx-testkit` | `CalendarVectorTest` | JUnit 5 parameterised | **C01-C05** |
| `fx-testkit` | `ChainVectorTest` | JUnit 5 parameterised | **F01-F09** |
| `fx-testkit` | `CorrectionEntitlementVectorTest` | JUnit 5 parameterised | **X01-X11** |
| `fx-testkit` | `PropertyBasedTest` | JUnit 5 + jqwik | FS S20 properties (12.2) |
| `fx-testkit` | `DecimalMathConformanceTest` | JUnit 5 + jqwik | Appendix D.5 reference table, identities, frozen digest |
| `fx-testkit` | `PolicyMatrixExhaustionTest` | JUnit 5 generated | Every rule x leg x purpose x itemType x amountType combination, allowed and disallowed |
| `fx-testkit` | `CalendarEdgeCaseTest` | JUnit 5 | Source vs currency holidays, Good Friday/Easter Monday, US-only and UK-only holidays, T+1 pairs, HRK 2023-01-01 and BGN 2026-01-01 redenominations, 2026 DST transitions (S10c.2) |
| `fx-testkit` | `ArchitectureTest` | JUnit 5 + ArchUnit | AR-01 .. AR-10 (12.5) |
| `fx-testkit` | `NoFloatingPointBytecodeTest` | JUnit 5 + ASM | Opcode scan (12.5) |
| `fx-testkit` | `JvmMatrixDeterminismTest` | JUnit 5, CI matrix | **X10**: G05 and the decimal digest on Temurin, Zulu, GraalVM and OpenJ9 |

`fx-testkit` also ships `src/main` doubles for host conformance use: `InMemoryTenantContextProvider`, `InMemoryReferenceDataLoader`, `InMemoryMarketDataLoader`, `GoldenReferenceData` (currencies, calendars, sources, pairs, entities, policies, entitlements), `GoldenSnapshots`, `VectorRunner`, `FxAssertions`, `DecimalReferenceTable`.

### 12.2 Property Tests (FS S20)

| Property | Assertion |
|----------|-----------|
| Round trip | `A -> B -> A` within `1e-28` relative |
| Cross consistency | `cross(A,B) * cross(B,C) == cross(A,C)` at the same observation, within `1e-28` |
| Forward convergence | `F(t) -> S` as `t -> 0`; monotone in `t` for a monotone carry |
| Series allocation | `sum(line.toAmountBooked) == totalBooked` exactly |
| PDR weights | reconstructed weights equal the input rationals exactly; `sum(weights) == 1` |
| Memo neutrality | identical results with the memo enabled and disabled |
| Finality monotonicity | `weakest()` is associative, commutative and idempotent |
| Restriction monotonicity | adding an input never widens `distributionRestriction` |
| Decimal identities | `ln(a*b) == ln a + ln b`; `exp(a+b) == exp a * exp b`; `exp(ln x) == x`; `ln(10^k) == k*LN10` |
| Hash stability | adding a `null`-valued optional field leaves `inputsHash` unchanged; changing any hashed input changes it |

### 12.3 Generated Matrix Tests

`PolicyMatrixExhaustionTest` enumerates the full cross-product from the exported `PolicyMatrix` view and asserts, for each cell, either a successful resolution against `GoldenReferenceData` or the exact expected `FxErrorCode`. The expectation table is data (a CSV resource in `fx-testkit`), not code, so FS S8.3/S9.1/S9.2 changes are a resource edit plus a matrix edit -- and a disagreement between the two fails the build.

### 12.4 Parallel Run (FS S20)

A full month-end against the legacy FX logic before cut-over, breaks > 0.01 % investigated individually, is a **host** exercise: it needs production data the library does not own. The library's contribution is `fx-testkit`'s `VectorRunner` plus a `ParallelRunHarness` that accepts a CSV of legacy inputs and outputs and reports per-row differences classified by stage (date, pair, rate, rounding), so breaks are attributable rather than merely counted.

### 12.5 Architecture Enforcement (D-01, D-09)

ArchUnit rules in `fx-testkit/ArchitectureTest`, all scoped to `fx-api` and `fx-core`:

| # | Rule |
|---|------|
| AR-01 | Classes in `com.power.fx.api..` and `com.power.fx.core..` depend only on `java..`, `javax..`, `jakarta.inject..` and `com.power.fx..`. No Spring, no Guice, no Jackson, no logging facade, no Apache Commons. |
| AR-02 | No dependency on `java.io..`, `java.nio.file..`, `java.net..`, `java.sql..`, `javax.sql..`, `java.lang.ProcessBuilder`. Allowlist: `java.security.MessageDigest` (A-12). |
| AR-03 | No class in `fx-api`/`fx-core` may reference `java.util.Random`, `java.util.concurrent.ThreadLocalRandom` or `java.util.UUID.randomUUID`. |
| AR-04 | No call to `System.currentTimeMillis`, `System.nanoTime`, `Instant.now`, `LocalDate.now`, `Clock.*`, `ZonedDateTime.now`. The only permitted zone/time use is `ZonedDateTime.of(...)` in `CutoffInstantResolver`. `MeteredFxConverter` in `fx-guice` is outside the rule's scope by design (A-11). |
| AR-05 | No field, parameter, return type or local variable of type `double`, `float`, `Double` or `Float`; no call to any method of `java.lang.Math` or `java.lang.StrictMath`; no call to `Number.doubleValue`/`floatValue`, `BigDecimal.valueOf(double)`, `BigDecimal.sqrt`, `BigDecimal.pow(int, MathContext)` with a non-literal exponent, or `Double.parseDouble`. |
| AR-06 | No `BigDecimal` constructor taking `double`; `new BigDecimal(String)` and `BigDecimal.valueOf(long)` only. |
| AR-07 | Every call to `BigDecimal.divide`, `multiply`, `add`, `subtract`, `pow` in `fx-core` passes a `MathContext` argument (the no-`MathContext` overloads are forbidden), except `setScale` which takes an explicit `RoundingMode`. |
| AR-08 | No `BigDecimal.equals` and no `Objects.equals` on `BigDecimal` operands in `fx-core` (S10.6). |
| AR-09 | No enum constant in `fx-api` is named `FIXED` (D-12). |
| AR-10 | No string literal matching a tenant-id pattern in `fx-api`/`fx-core` main sources; `fx-testkit` is exempt. |

ArchUnit cannot see local variables or primitive opcodes, so `NoFloatingPointBytecodeTest` complements AR-05 with an ASM `ClassVisitor` over the `fx-api` and `fx-core` class files, failing on any of: `DADD DSUB DMUL DDIV DREM DNEG DCMPG DCMPL D2F D2I D2L I2D L2D F2D DCONST_* DLOAD DSTORE DRETURN FADD FSUB FMUL FDIV FREM FNEG FCMPG FCMPL F2D F2I F2L I2F L2F FCONST_* FLOAD FSTORE FRETURN`, any `LDC` of a `Double`/`Float` constant, and any field or method descriptor containing `D` or `F` at a type position. This is the only mechanically complete check that D-09 holds, and it is cheap: one pass over a few hundred class files.

### 12.6 No Testcontainers, No H2, No Database

The library has no database. All tests run against in-memory structures. No Testcontainers, no H2, no PostgreSQL. Consistent with D-01 and with the platform rule that **H2 never appears in an integration test path** -- here the rule is satisfied vacuously, and the implementation engineer must not introduce a database to test the caches.

---

## S13 -- Constraint Compatibility

### 13.1 FX Functional Spec Decisions D-01 .. D-16

| Decision | Status | Implementation |
|----------|--------|----------------|
| D-01 Library-first, JDK-only, no I/O or clock on the resolution path | Compatible | `fx-api`/`fx-core` depend on `java.*` + `jakarta.inject` only. AR-01..AR-04 and the bytecode scan enforce it. I/O exists only behind loader SPIs, called from `prewarm`/bootstrap/ingest, never from stages 6-17. Latency timing is in `fx-guice` (A-11). |
| D-02 Dates resolved against publication/settlement calendars before rate lookup; holidays never reach the fallback chain | Compatible | Stage 6 precedes stages 9-12 (6.2). `RateLookupMiss`'s constructor asserts the resolved date is an open publication day, so the fallback chain is structurally unreachable from a holiday. Vectors C01-C05. |
| D-03 Bitemporal versioned fixings; immutable versioned snapshots; a pin fixes both curve set and knowledge cut | Compatible | 7.1.3 columnar bitemporal store; 7.1.4 immutable snapshots with new-id corrections; `PinnedState` fixes catalogue, fixing view, snapshot, cut and generations (A-15). Vectors X01-X04. |
| D-04 Functional currency mandatory, effective-dated; six settlement->functional rules; revaluation helper | Compatible | `AccountingUnit` with envelope-based effective dating; `FunctionalCurrencyResolver` (prospective only); `PolicyMatrix` admits RECOGNITION_DATE, AVERAGE_RATE, CLOSING_RATE, SETTLEMENT_DATE, VALUATION_DATE, FAIR_VALUE_DATE on the ACCT_TXN leg; `RevaluationEngine` (6.14). Vectors F02-F04, F08. |
| D-05 MTM requires PV at the valuation-date rate; undiscounted amounts only under CASH_PROJECTION; mismatches rejected | Compatible | `PolicyMatrix` purpose x amountType rule -> `FX_V_AMOUNT_TYPE_MISMATCH`; `spotAdjustment = TO_VALUATION_DATE` default for UNREALISED_MTM via `ShortEndAdjuster`. Vectors F06, F07. |
| D-06 `fixingVersionPolicy` per policy; corrections cannot change contract-settled amounts; impact events; host locks invoiced rows | Compatible | `FixingVersionSelector` (6.8); CONTRACT default `FirstOfficial` (6.4); `onFixingCorrected` at ingest; `assessCorrectionImpact` (6.16); host contract restated. Vectors X01-X03. |
| D-07 One averaging model | Compatible | `AveragingSpec` with orthogonal `method`/`observationSet`/`weighting`/`outputShape`; `NONE` is a one-observation set, so there is one code path and no bypass (6.11). |
| D-08 PDR sets consumed immutably; lineage records `(eventId, version, inputsHash)` | Compatible | `PricingDaySet` projection (A-08), exact `Rational` weights, duplicates preserved, `pdrRef` in lineage and in `inputsHash` (6.13). Vectors G07, G08, X11. |
| D-09 All arithmetic decimal in DECIMAL128, including interpolation, `ln` and `exp`; no `double`/`float`/`Math`/`StrictMath` | Compatible | `FxMath` owns the only `MathContext`s; `DecimalLn`/`DecimalExp`/`DecimalSqrt` (Appendix D); AR-05..AR-07 plus the opcode scan. Vectors G04, G05, X10. |
| D-10 Entitlements are reference data; priorities filtered; derived rates inherit the most restrictive rights; results carry a distribution restriction | Compatible | Stage 8 filter, `RestrictionPropagator` table (6.6), `DistributionRestriction` on every result. Vectors X05, X06. |
| D-11 Manual overrides are four-eyes-approved reference data, time-boxed, reason-coded; contract rates are distinct trade terms | Compatible | `ManualRateOverride` with `VersionEnvelope` four-eyes validation -> `FX_I_APPROVAL_INVALID`; separate `ContractRate` on the policy, CONTRACT leg only, step 4 vs step 5 of 6.7. Vectors X07, X08. |
| D-12 No overloaded "FIXED"; `FIXED_FACTOR` rate type; `rateFinality` CONFIRMED/ESTIMATED/UNRESOLVED | Compatible | Enum set of 4.1; AR-09 asserts no constant is named `FIXED`. |
| D-13 Implicit tenancy via `TenantContextProvider`; GLOBAL+TENANT overlays; tenant-private market data | Compatible | 10.1. No public method takes a tenant parameter; AR-10 forbids literal tenant ids. |
| D-14 Every request declares a `purpose`, which determines allowed legs, date rules, amount types and defaults | Compatible | `purpose` is non-null in `FxRequestContext`; `PolicyMatrix` (6.4) is the single authority for purpose-driven admissibility and defaults. |
| D-15 Forward interpolation default LOG_LINEAR_CARRY, configurable per pair; consumers obtain forwards from this library | Compatible | `PairConvention.interpolation` default LOG_LINEAR_CARRY; `ForwardCurve` is the only forward source and records its method in lineage (6.9). Vector G05. |
| D-16 Management view computed on read, never a persisted leg; multiple presentation and reporting currencies | Compatible | `ManagementViewResult` with `persistable = false` and reason `VIEW_ONLY`; `List` of views (A-18); `ChainRequest.presentationCurrencies` is a list. |

### 13.2 Platform Constraints (CLAUDE.md D-1 .. D-14) -- separate numbering space

These govern the `valuation-engine` codebase. The FX library is a separate reactor; the rows below record compatibility for the day an integration adapter is written.

| # | Constraint | Status | Notes |
|---|-----------|--------|-------|
| D-2 (platform) | PriceExpression sealed hierarchy; fixed price is a degenerate expression | Not applicable | The FX library has no price expressions. If an FX-converted price enters S2, the conversion is applied to the evaluated expression result, not inside the hierarchy. |
| D-3 (platform) | S5b forward marks are ephemeral current-state only | Not applicable | The FX library holds no marks. Note for integrators: an FX rate used to produce a forward mark must be captured in the mark's lineage, because the mark itself is not bitemporal. |
| D-5 (platform) | Numeric precision is a domain port (`PRICE` 8, `MONETARY` 4, `INTERMEDIATE` 10) | Compatible, with a seam | The FX library has its own precision system (FS S15, DECIMAL128 with per-currency booking). The two must not be mixed silently. A future adapter in `valuation-guice` MUST apply `NumericPrecision` to values it hands to `valuation-domain`, and MUST NOT ask `fx-core` to pre-round to platform scales -- `toAmountUnrounded` is the correct handoff value. Flagged as OQ-T07. |
| D-9 (platform) | Outbox-in-same-transaction | Not applicable | The library produces no events and has no transaction. A host that emits an event on `onFixingCorrected` must write it to its own outbox inside its own `UnitOfWork`, never produce directly from the listener callback. Recorded here because the listener is an easy place to get this wrong. |
| D-11 (platform) | Unified volume (`VolumeReference x multiplier -> VolumeSeries`) | Not applicable | The library accepts `DeliveryDay.volume` only as an averaging weight and never constructs a volume series. |
| D-12 (platform) | Commodity-neutral core | Compatible | Nothing in the library is commodity-aware. Gas-day and power-delivery-day mapping is parameterised by `marketZone` and a day-start convention, not by commodity type. |
| D-13 (platform) | Library-first, Spring-free libraries, Guice 7 canonical | Compatible | `fx-api`, `fx-core`, `fx-cdm` have zero framework dependencies; `fx-guice` uses Guice 7 only; no Spring anywhere in the reactor. A future adapter lives in `valuation-guice`, never in `valuation-domain`, and is wired by Guice, never by a `new` call in a `@Bean` method. |
| D-14 (platform) | `valuation-app` is a simulator, not the production host | Compatible | No part of this library is designed into `valuation-app`. Production FX ingestion (CDM transport, scheduling, readiness gating, snapshot distribution) is a **production hosting layer** responsibility; the library supplies `FxIngestor`, `FxHealth` and the loader SPIs and nothing else. Named explicitly in S2.2 rather than smuggled into the simulator. |

### 13.3 Module Conventions (MC-1 .. MC-8)

| # | Rule | Status |
|---|------|--------|
| MC-1 | No Spring in library modules | Compatible. `fx-api`, `fx-core`, `fx-cdm` have no framework dependency; `fx-guice` uses Guice 7 only. |
| MC-2 | Guice is the primary DI | Compatible. `fx-guice/FxModule` is the only wiring artefact (S9). |
| MC-3 | `-app` modules are non-production | Not applicable -- the FX reactor has no `-app` module, by design (S2.2). |
| MC-4 | Package seams respected; no cross-reactor leakage | Compatible. `fx-api` re-declares the version envelope and the PDR projection rather than depending on `uom-api` or `pdr-api` (A-08). The two reactors share no artifact. |
| MC-5 | No adapter-to-adapter dependencies | Compatible. `fx-cdm` does not depend on `fx-core`; `fx-guice` depends on `fx-core` only; nothing depends on `fx-testkit` outside test scope (Appendix A). |
| MC-6 | Testcontainers-PostgreSQL, never H2 | Not applicable -- no database. Satisfied vacuously; see 12.6. |
| MC-7 | Multi-tenancy via a tenant context, never a parameter | Compatible. `TenantContextProvider`; no public method takes a tenant (10.1). |
| MC-8 | No Spring `@Transactional` | Compatible. No transactions; locking is explicit (10.3, 10.4). |

---

## S14 -- Open Items

### 14.1 Functional Spec Open Questions (surfaced, NOT resolved by this spec)

| ID | Question | Impact on this technical design |
|----|----------|---------------------------------|
| OQ-01 | Firm-wide default source for ACCT_TXN and MGMT_VIEW legs: WMR 4pm or INTERNAL_EOD (MTM only)? | `rateSourcePriority` is reference data with no library default. If the default is INTERNAL_EOD, `usageClass = MTM_ONLY` makes it invalid for ACCOUNTING_SETTLEMENT (`FX_V_SOURCE_NOT_ALLOWED`), so the answer must be purpose-specific. 6.4's defaults table deliberately leaves these cells empty. |
| OQ-02 | Per accounting unit: AVERAGE_RATE (IAS 21.22) or actual transaction rates for P&L recognition? | `AccountingFxPolicy` carries the choice; `PolicyMatrix` admits both. ACCOUNTING_RECOGNITION's date rule is intentionally un-defaulted; absence is `FX_V_INVALID_POLICY` rather than a silent choice. |
| OQ-03 | Contract template audit: which contracts use PRICE_MATCHED vs RATE_AVERAGE vs NONE+PAYMENT_DATE, and which opt into LATEST_CORRECTED | No design impact -- all three are implemented. It determines which code paths carry production volume, hence where JMH benchmarks should concentrate (R1, R2). |
| OQ-04 | Discount curves for CIP forwards: OIS or firm funding curves, and which source owns them | `DiscountCurvePayload` carries pillars as discount factors **or** zero rates (the record admits both via `discountFactor`; a zero-rate variant needs a second field). Until OQ-04 is answered, `CipForwardCalculator` is specified for discount factors only, and `ForwardMethod.CIP` for a pair without loaded curves is `FX_E_DATA_NOT_LOADED`. |
| OQ-05 | Onshore vs offshore: separate currency codes (CNY/CNH) vs source-level distinction (INR RBI vs NDF) | Both are representable: separate `Currency` records, or one currency with distinct `FixingSource`s and `rateSourcePriority` per policy. The design does not choose. If separate codes are chosen, a `FixedFactor` between them must NOT be defined (they are not convertible at par) -- worth stating explicitly in the reference-data standard. |
| OQ-06 | Default `spotAdjustment` for MTM: TO_VALUATION_DATE (proposed) vs NONE | 6.4 adopts TO_VALUATION_DATE as the FS proposes, which makes `ShortEndAdjuster` and ON/TN points a required part of every MTM snapshot. If NONE wins, ON/TN become optional and OQ-T06 disappears. |
| OQ-07 | Maximum fallback staleness before an EOD sign-off is blocked | `FxConfig.maxFallbackStalenessDays` exists with a default of 3 matching `PREVIOUS_PUBLICATION_DAY`'s default, and staleness in days is recorded in `PathStep` details. The library does **not** block sign-off -- that is a host decision, and no code is minted for it. |
| OQ-08 | Fixing hot-window length and number of snapshots retained in memory | Directly drives S10a.3. Defaults: `fixingHotWindowYears = 3`, `retainedSnapshotsPerTenant = 8`. The fixing window is the library's largest memory term (60-90 MB per 1,000 series) and is outside any FS S18 budget. **This is risk R3.** |
| OQ-09 | Ownership and change approval of FX policies, accounting FX policies, calendars and entitlements | No design impact -- all arrive as four-eyes-approved reference data. It determines who is paged when `FX_I_APPROVAL_INVALID` fires. |
| OQ-10 | Is `revalue` sufficient for the accounting feed, or does the ERP need more fields (e.g. GL account hints)? | `RevaluationResult` is designed to FS S9.3 exactly. Extra fields would be additive. GL account hints would be the first piece of accounting-chart knowledge in the library and should be resisted -- a host mapping layer is the better home. |
| OQ-11 | Translation: does any consumer need line-category batch translation, or does consolidation own it entirely? | **Not designed here.** `convertChain` translates one amount per call; a batch line-category API would be a new request/result pair plus a line-category enum. Deliberately omitted to avoid building an unrequested feature. |

### 14.2 New Technical Open Questions (raised by this spec)

Numbered `OQ-T*` to keep the FX functional spec's `OQ-*` space intact.

| ID | Question | Why it matters |
|----|----------|----------------|
| OQ-T01 | Should `fx-api` keep its own PDR projection (A-08), or should a shared `pdr-api` artifact be depended upon at provided scope? | Duplication risks drift against PDR v2.0; a dependency risks `fx-api` ceasing to be JDK-only. Recommendation: keep the projection and add a PDR-conformance test fed by a PDR-published fixture set. |
| OQ-T02 | Is reusing `FX_E_DATA_NOT_LOADED` for "date outside calendar coverage" acceptable, or is a distinct code required? | A calendar-coverage failure is an operational data-loading problem, like a missing fixing, so the reuse is defensible; but it conflates two very different remediations (extend the calendar vs prewarm fixings). A new code would need an FS amendment, which this spec will not make unilaterally. |
| OQ-T03 | How is a first-ever-seen fixing key, backfilled with `recordedAt <= an already-pinned knowledge cut`, prevented from changing a replay? | `FX_I_FIXING_SEQUENCE` catches `recordedAt` regression **within** a key, but a brand-new key has no prior version to regress against. Options: (a) a per-tenant global `recordedAt` floor that rejects any record below the highest cut ever pinned; (b) accept it and rely on source-system discipline; (c) record a fixing-store content digest in lineage. Needs a platform decision. |
| OQ-T04 | For inline (trade-terms) policies, is embedding the full policy in `Lineage.replayKey` acceptable to hosts that persist lineage, given the row size? | A full `FxPolicy` is a few hundred bytes of JSON. The alternative -- a digest only -- makes `assessCorrectionImpact` impossible for inline policies, which is most contract-leg traffic. Recommendation: embed, and let hosts normalise policies into their own table if size matters. |
| OQ-T05 | When a request carries a `snapshot` **and** is issued through a different `FxSnapshot` facade, should the disagreement be an error? | This spec takes the request's snapshot silently (5.1). An error would need a new code. A warning would need a new `FX_W_*`. Silent precedence is the only option that mints nothing, and it is the one chosen -- but it is a trap for callers. |
| OQ-T06 | What is the correct behaviour when `spotAdjustment = TO_VALUATION_DATE` but the snapshot carries no ON/TN points? | FS S11.4 gives the formulas but not the absent-data case. This spec falls back to spot unadjusted with no additional reason code, which is silent. Alternatives: a new warning, or `FX_E_DATA_NOT_LOADED`. Needs a decision before OQ-06 is closed. |
| OQ-T07 | At which boundary does the platform's `NumericPrecision` port (D-5 platform) meet the FX library's precision system? | The intended answer is: `fx-core` returns `toAmountUnrounded`; the `valuation-guice` adapter applies `NumericPrecision`. Needs confirming when the integration adapter is specified, so that no value is rounded twice. |
| OQ-T08 | Is eager forward-curve construction at snapshot assembly required (for the FS S18 "2 s after completion marker" guarantee to include curves), or is lazy-per-pair construction acceptable? | Lazy is what makes the 2 s target comfortable but moves cost onto the first query of each pair, which interacts with the 20 us p99 (R1). A config flag exists; the default needs an operational decision. |

### 14.3 Technical Inputs Needed

| ID | Item | Input required |
|----|------|----------------|
| TI-01 | CDM schema artifact coordinates | Maven GAV for the CDM event schema that `fx-cdm` compiles against, plus the event/topic naming per entity type and the payload field names for all thirteen `FxEntityType` values. |
| TI-02 | `jakarta.inject` in `fx-core` | Confirm provided-scope `jakarta.inject` is acceptable (A-04), matching the UOM precedent. If not, `fx-guice` must use `@Provides` methods throughout and `fx-core` constructors become annotation-free. |
| TI-03 | GLOBAL catalogue refresh model | Confirm option (a): tenant catalogues hold a reference to the GLOBAL catalogue and read it atomically, rather than embedding a copy. This spec assumes (a), as UOM TI-03 did. |
| TI-04 | Reconciliation and hot-window scheduling | Confirm `ScheduledExecutorService` owned by `DefaultFxIngestor` behind a `Closeable` lifecycle, versus a host-supplied scheduler SPI. A scheduler in a library is a smell; a host-supplied one is more work for every host. |
| TI-05 | Library version injection | Maven resource filtering into a properties file versus a generated `FxVersion` class. Affects `inputsHash` reproducibility across builds: the version string must change on every released artifact and must not change between a local build and CI of the same commit. |
| TI-06 | Decimal reference-table provenance | Which tool generates the `ln`/`exp` reference values (MPFR, mpmath, Boost.Multiprecision), at what precision, and who cross-checks them against a second independent implementation. Without this, Appendix D's accuracy claim is unverified. |
| TI-07 | JVM matrix for the determinism gate | Which vendors and versions CI must run for X10 (proposal: Temurin 21 + 25, Zulu 21, GraalVM 21, OpenJ9 21), and whether a vendor-specific failure blocks release. |
| TI-08 | Pair-level provenance in snapshots | FS S6.2 does not say which source a snapshot's spot and forward points came from per pair. `RestrictionPropagator` needs it to compute forward rights (6.6). Either `MarketSnapshotPayload` gains a per-pair `sourceCode`, or all snapshot-derived rates inherit a single snapshot-level source. **This is a functional-spec gap, not a design choice.** |

---

## Appendix A -- Module Dependency Graph

```
fx-api            (JDK 21 only; jakarta.inject provided)
  ^        ^
  |        |
fx-core    fx-cdm          fx-core depends on fx-api ONLY
(JDK only) (fx-api +        fx-cdm does NOT depend on fx-core
           CDM schema)
  ^    ^
  |    |
  |    fx-guice   (fx-core + Guice 7)
  |
fx-testkit        (fx-api + fx-core + JUnit 5 + jqwik + ArchUnit + ASM; test scope for consumers)
```

Binding dependency rules, enforced by the reactor POMs and by AR-01:

| From | To | Scope | Rationale |
|------|----|-------|-----------|
| `fx-core` | `fx-api` | compile | the only compile dependency `fx-core` has |
| `fx-cdm` | `fx-api` | compile | mapper targets are API types |
| `fx-cdm` | CDM schema artifact | compile | TI-01 |
| `fx-cdm` | `fx-core` | **forbidden** | mappers are pure; a dependency here would let transport concerns reach the engine |
| `fx-guice` | `fx-core` | compile | wiring needs implementation classes |
| `fx-guice` | Guice 7 | compile | the only module that may see Guice |
| `fx-testkit` | `fx-api`, `fx-core` | compile | doubles and vector runners |
| `fx-testkit` | JUnit 5, jqwik, ArchUnit, ASM | compile | shipped so hosts can run the conformance suite |
| anything | `fx-testkit` | test only | a production dependency on the testkit is a build failure |

Prospective valuation-engine integration (**not in scope for v1.0**, S2.2):

```
valuation-guice  --(compile)--> fx-api        for the FxConverter interface
valuation-guice  --(compile)--> fx-core       transitively, for the implementation
valuation-guice  --(compile)--> fx-guice      to install FxModule
valuation-domain --(no dependency)            the anti-corruption layer absorbs all translation
```

`valuation-domain` must gain **no** dependency on `fx-api`; the adapter in `valuation-guice` maps between `valuation-domain` types and FX types entirely at that layer, exactly as the UOM integration does (UOM tech spec Appendix A).

---

## Appendix B -- Maven Reactor POM Structure

```
fx-conversion/                           (separate git repository or monorepo module)
+-- pom.xml                              (reactor POM, groupId: com.power.fx, A-01)
+-- fx-api/
|   +-- pom.xml                          (artifactId: fx-api; JDK 21 + jakarta.inject provided)
|   +-- src/main/java/com/power/fx/api/
|       +-- FxConverter.java
|       +-- FxSnapshot.java
|       +-- FxIngestor.java
|       +-- FxHealth.java
|       +-- FxConfig.java
|       +-- FxVersion.java                      (generated library-version constant, TI-05)
|       +-- spi/
|       |   +-- TenantContextProvider.java
|       |   +-- ReferenceDataLoader.java
|       |   +-- MarketDataLoader.java
|       |   +-- FxEventListener.java
|       |   +-- FxMetrics.java
|       +-- model/
|       |   +-- CurrencyCode.java
|       |   +-- CurrencyPair.java
|       |   +-- Rational.java
|       |   +-- LocalDateRange.java
|       |   +-- VersionEnvelope.java
|       |   +-- Scope.java
|       |   +-- VersionStatus.java
|       |   +-- FxEntityType.java
|       |   +-- FxStoreKind.java
|       |   +-- TenantHealthStatus.java
|       |   +-- Purpose.java
|       |   +-- Leg.java
|       |   +-- DateRule.java
|       |   +-- AmountType.java
|       |   +-- SettlementAmountState.java
|       |   +-- ItemType.java
|       |   +-- RunMode.java
|       |   +-- RateType.java
|       |   +-- RateFinality.java
|       |   +-- FixingStatus.java
|       |   +-- FixingVersionPolicy.java
|       |   +-- FixingVersionSelection.java     (sealed: FirstOfficial|LatestCorrected|AsOfKnowledge)
|       |   +-- NonPublicationDayHandling.java
|       |   +-- RollConvention.java
|       |   +-- OffsetCalendarKind.java
|       |   +-- ForwardMethod.java
|       |   +-- InterpolationMethod.java
|       |   +-- FutureDateTreatment.java
|       |   +-- SpotAdjustment.java
|       |   +-- AveragingMethod.java
|       |   +-- ObservationSetKind.java
|       |   +-- WindowKind.java
|       |   +-- Weighting.java
|       |   +-- OutputShape.java
|       |   +-- FxDateFromObservation.java
|       |   +-- FallbackStepKind.java
|       |   +-- FixedFactorKind.java
|       |   +-- UsageClass.java
|       |   +-- SourceRight.java
|       |   +-- SnapshotKind.java
|       |   +-- SignOffStatus.java
|       |   +-- EventType.java
|       |   +-- FxDifferenceClass.java
|       |   +-- EstimatedEventHandling.java
|       |   +-- Currency.java
|       |   +-- PairConvention.java
|       |   +-- FixedFactor.java
|       |   +-- FixingSource.java
|       |   +-- PublicationCalendar.java
|       |   +-- SettlementCalendar.java
|       |   +-- AccountingUnit.java
|       |   +-- FxPolicy.java
|       |   +-- OffsetSpec.java
|       |   +-- FallbackStep.java
|       |   +-- RoundingSpec.java
|       |   +-- ContractRate.java
|       |   +-- AveragingSpec.java
|       |   +-- WindowSpec.java
|       |   +-- AccountingFxPolicy.java
|       |   +-- SourceEntitlement.java
|       |   +-- ManualRateOverride.java
|       |   +-- FixingVersion.java
|       |   +-- SpotQuote.java
|       |   +-- ForwardPillar.java
|       |   +-- DiscountCurvePayload.java
|       |   +-- DiscountPillar.java
|       |   +-- MarketSnapshotPayload.java
|       |   +-- PdrRef.java
|       |   +-- PricingObservation.java
|       |   +-- PricingDaySet.java
|       |   +-- TradeDates.java
|       |   +-- DeliveryDay.java
|       |   +-- EventDate.java
|       |   +-- AccountingDates.java
|       +-- request/
|       |   +-- FxRequest.java                  (sealed)
|       |   +-- FxRequestContext.java
|       |   +-- PolicyRef.java                  (sealed: ById|Inline)
|       |   +-- RateRequest.java
|       |   +-- ConversionRequest.java
|       |   +-- SeriesRequest.java
|       |   +-- ChainRequest.java
|       |   +-- ManagementViewSpec.java
|       |   +-- MonetaryRevaluationRequest.java
|       |   +-- PrewarmRequest.java
|       |   +-- ObservationPrice.java
|       +-- result/
|       |   +-- FxResult.java                   (sealed)
|       |   +-- RateResult.java
|       |   +-- ConversionResult.java
|       |   +-- SeriesResult.java
|       |   +-- ObservationResult.java
|       |   +-- ChainResult.java
|       |   +-- LegResult.java
|       |   +-- ManagementViewResult.java
|       |   +-- RevaluationResult.java
|       |   +-- CorrectionImpact.java
|       |   +-- FixingVersionChange.java
|       |   +-- PrewarmOutcome.java
|       |   +-- PathStep.java
|       |   +-- PillarRef.java
|       |   +-- Lineage.java
|       |   +-- ReplayableRequest.java
|       |   +-- DistributionRestriction.java
|       |   +-- AllocationResidual.java
|       |   +-- ReconciliationTolerance.java
|       +-- error/
|       |   +-- FxErrorCode.java
|       |   +-- FxWarningCode.java
|       |   +-- FxIngestCode.java
|       |   +-- FxReason.java
|       |   +-- FxError.java
|       |   +-- FxWarning.java
|       |   +-- FxException.java
|       +-- ingest/
|           +-- FxIngestRecord.java
|           +-- IngestOutcome.java
|           +-- IngestRejection.java
|           +-- FixingCorrection.java
|           +-- SnapshotAvailability.java
|           +-- MarketWatermark.java
|           +-- MarketDataChangeSet.java
+-- fx-core/
|   +-- pom.xml                          (depends on fx-api only)
|   +-- src/main/java/com/power/fx/core/
|       +-- DefaultFxConverter.java
|       +-- PinnedFxSnapshot.java
|       +-- DefaultFxIngestor.java
|       +-- DefaultFxHealth.java
|       +-- ConversionPipeline.java
|       +-- ResolvedPolicy.java
|       +-- ResolvedContext.java
|       +-- date/
|       |   +-- DateRuleResolver.java
|       |   +-- DefaultDateRuleResolver.java
|       |   +-- RawDateDeriver.java
|       |   +-- PublicationDateResolver.java
|       |   +-- ValueDateResolver.java
|       |   +-- SpotDateCalculator.java
|       |   +-- RollConventions.java
|       |   +-- DeliveryDayMapper.java
|       |   +-- CutoffInstantResolver.java
|       |   +-- CalendarIndex.java
|       |   +-- JointCalendarIndex.java
|       |   +-- ResolvedDates.java
|       +-- pair/
|       |   +-- PairResolver.java
|       |   +-- DefaultPairResolver.java
|       |   +-- PairResolutionStep.java
|       |   +-- IdentityStep.java
|       |   +-- FixedFactorNormaliser.java
|       |   +-- ContractRateStep.java
|       |   +-- ManualOverrideStep.java
|       |   +-- DirectQuoteStep.java
|       |   +-- InverseQuoteStep.java
|       |   +-- ConfiguredCrossStep.java
|       |   +-- MajorCrossStep.java
|       |   +-- PairRoute.java
|       |   +-- QuotedInParser.java
|       +-- rate/
|       |   +-- RateSelector.java
|       |   +-- DefaultRateSelector.java
|       |   +-- RateCase.java
|       |   +-- RateQuote.java
|       |   +-- RateLookupMiss.java
|       |   +-- FixingResolver.java
|       |   +-- FixingVersionSelector.java
|       |   +-- SpotResolver.java
|       |   +-- FallbackChainRunner.java
|       |   +-- DefaultFallbackChainRunner.java
|       |   +-- AltSourceStep.java
|       |   +-- PreviousPublicationDayStep.java
|       |   +-- TriangulateStep.java
|       |   +-- InterpolateFixingsStep.java
|       +-- curve/
|       |   +-- ForwardCurve.java
|       |   +-- ForwardCurveBuilder.java
|       |   +-- ForwardCurveCache.java
|       |   +-- SnapshotForwardCurveCache.java
|       |   +-- PointsInterpolator.java
|       |   +-- LogLinearCarryInterpolator.java
|       |   +-- MonotoneCubicInterpolator.java
|       |   +-- HymanFilter.java
|       |   +-- CipForwardCalculator.java
|       |   +-- DiscountCurve.java
|       |   +-- ShortEndAdjuster.java
|       |   +-- Extrapolator.java
|       |   +-- DayCount.java
|       +-- decimal/
|       |   +-- FxMath.java
|       |   +-- DecimalLn.java
|       |   +-- DecimalExp.java
|       |   +-- DecimalSqrt.java
|       |   +-- DecimalConstants.java
|       |   +-- RationalMath.java
|       +-- averaging/
|       |   +-- ObservationSetBuilder.java
|       |   +-- Observation.java
|       |   +-- ObservationSet.java
|       |   +-- AveragingEngine.java
|       |   +-- DefaultAveragingEngine.java
|       |   +-- RateAverageStrategy.java
|       |   +-- PriceMatchedStrategy.java
|       |   +-- WeightResolver.java
|       |   +-- PricingSetAdapter.java
|       |   +-- PartialPeriodAggregator.java
|       |   +-- SeriesOutcome.java
|       +-- leg/
|       |   +-- ChainEngine.java
|       |   +-- DefaultChainEngine.java
|       |   +-- ContractLeg.java
|       |   +-- AccountingTransactionLeg.java
|       |   +-- TranslationLeg.java
|       |   +-- ManagementViewLeg.java
|       |   +-- FunctionalCurrencyResolver.java
|       |   +-- RevaluationEngine.java
|       +-- precision/
|       |   +-- PrecisionEngine.java
|       |   +-- DefaultPrecisionEngine.java
|       |   +-- LargestRemainderAllocator.java
|       |   +-- RoundingPolicyResolver.java
|       |   +-- BookedAmount.java
|       +-- cache/
|       |   +-- ReferenceStore.java
|       |   +-- InMemoryReferenceStore.java
|       |   +-- ReferenceCatalogue.java
|       |   +-- Timeline.java
|       |   +-- CatalogueBuilder.java
|       |   +-- FixingStore.java
|       |   +-- InMemoryFixingStore.java
|       |   +-- FixingView.java
|       |   +-- FixingSeries.java
|       |   +-- SeriesKey.java
|       |   +-- MarketSnapshotStore.java
|       |   +-- InMemoryMarketSnapshotStore.java
|       |   +-- HotWindowPolicy.java
|       +-- snapshot/
|       |   +-- MarketSnapshot.java
|       |   +-- SnapshotAssembler.java
|       |   +-- SnapshotCompletionTracker.java
|       |   +-- PinnedState.java
|       +-- entitlement/
|       |   +-- EntitlementResolver.java
|       |   +-- DefaultEntitlementResolver.java
|       |   +-- RightsSet.java
|       |   +-- FilteredSources.java
|       |   +-- RestrictionPropagator.java
|       +-- validation/
|       |   +-- PolicyMatrix.java
|       |   +-- RequestValidator.java
|       |   +-- PolicyValidator.java
|       |   +-- IngestValidator.java
|       |   +-- FixingSequenceValidator.java
|       +-- lineage/
|       |   +-- LineageBuilder.java
|       |   +-- DefaultLineageBuilder.java
|       |   +-- CanonicalJson.java
|       |   +-- InputsHasher.java
|       |   +-- CorrectionImpactAssessor.java
|       +-- memo/
|           +-- ResolutionMemo.java
|           +-- MemoKey.java
+-- fx-cdm/
|   +-- pom.xml                          (depends on fx-api + CDM schema artifact; NOT fx-core)
|   +-- src/main/java/com/power/fx/cdm/
|       +-- CdmFxEvent.java
|       +-- CdmFxEventMapper.java
|       +-- CdmReferenceMapper.java
|       +-- CdmFixingMapper.java
|       +-- CdmSnapshotMapper.java
|       +-- CdmDecimalCodec.java
+-- fx-testkit/
|   +-- pom.xml                          (depends on fx-api + fx-core + JUnit 5 + jqwik + ArchUnit + ASM)
|   +-- src/main/java/com/power/fx/testkit/
|   |   +-- InMemoryTenantContextProvider.java
|   |   +-- InMemoryReferenceDataLoader.java
|   |   +-- InMemoryMarketDataLoader.java
|   |   +-- GoldenReferenceData.java
|   |   +-- GoldenSnapshots.java
|   |   +-- VectorRunner.java
|   |   +-- FxAssertions.java
|   |   +-- DecimalReferenceTable.java
|   |   +-- ParallelRunHarness.java
|   +-- src/main/resources/
|   |   +-- vectors/arithmetic-G01-G09.csv
|   |   +-- vectors/calendar-C01-C05.csv
|   |   +-- vectors/chain-F01-F09.csv
|   |   +-- vectors/corrections-X01-X11.csv
|   |   +-- vectors/policy-matrix-expectations.csv
|   |   +-- decimal/ln-reference-70dp.csv
|   |   +-- decimal/exp-reference-70dp.csv
|   +-- src/test/java/com/power/fx/testkit/
|       +-- GoldenArithmeticVectorTest.java     (G01-G09)
|       +-- CalendarVectorTest.java             (C01-C05)
|       +-- ChainVectorTest.java                (F01-F09)
|       +-- CorrectionEntitlementVectorTest.java (X01-X11)
|       +-- PropertyBasedTest.java
|       +-- DecimalMathConformanceTest.java
|       +-- PolicyMatrixExhaustionTest.java
|       +-- CalendarEdgeCaseTest.java
|       +-- ArchitectureTest.java               (AR-01..AR-10, ArchUnit)
|       +-- NoFloatingPointBytecodeTest.java    (ASM opcode scan)
|       +-- JvmMatrixDeterminismTest.java       (X10)
+-- fx-guice/
    +-- pom.xml                          (depends on fx-core + Guice 7)
    +-- src/main/java/com/power/fx/guice/
    |   +-- FxModule.java
    |   +-- MeteredFxConverter.java              (A-11; the only System.nanoTime caller)
    |   +-- FxLifecycle.java                     (Closeable start/stop for reconciliation, TI-04)
    +-- src/test/java/com/power/fx/guice/
        +-- WiringTest.java
```

Per-module test classes named in S12.1 that are not listed above (`fx-api/ValueTypeValidationTest`, `fx-core/*Test`, `fx-cdm/CdmMapperTest`) live under each module's own `src/test/java` mirroring its main package layout.

---

## Appendix C -- Resolution Algorithm Pseudocode

Non-normative. Aids the implementation engineer; S6 is the binding text.

```
function convert(request):
    // 1-2 context and pin
    tenant = tenantContextProvider.currentTenant() orElse fail(FX_E_NO_TENANT_CONTEXT)
    if health.status(tenant) != READY: fail(FX_E_TENANT_NOT_READY)
    pin = request.context.snapshot ?? implicitPin(tenant, request.context.runMode)
    if pin.tenantId != tenant: fail(FX_E_TENANT_MISMATCH)
    if request.purpose == UNREALISED_MTM and request.runMode == OFFICIAL
       and pin.signOffStatus == UNSIGNED: fail(FX_V_UNSIGNED_SNAPSHOT)
    state = pin.state                       // PinnedState: no store is read after this line

    // 3-4 policy and validation
    policy = resolvePolicy(request.context.policy, state) then applyPurposeDefaults(request.purpose)
    PolicyMatrix.validate(request, policy)  // may fail FX_V_*

    // 5 memo
    key = MemoKey(policy, request)
    if state.memo.contains(key): quote = state.memo.get(key)
    else:
        // 6 DATES BEFORE RATES  (D-02)
        dates = dateRuleResolver.resolve(policy, state, request.context)   // raw -> offset -> calendar
        // 7 observations
        obs = observationSetBuilder.build(policy, request.context, dates)
        // 8 entitlement
        sources = entitlementResolver.filter(policy.rateSourcePriority, dates.primary, state)
        if sources.allowed.isEmpty() and policy.fallbackChain has no entitled step:
            fail(FX_E_SOURCE_NOT_ENTITLED)

        quotes = []
        for each o in obs where not o.skipped:
            // 9 pair
            route = pairResolver.route(from, to, policy, state, o.resolvedFxDate)   // 9 steps
            // 10 rate, per route leg
            q = rateSelector.select(route, o.resolvedFxDate, policy, state)         // R1..R9
            // 11 fallback, only on a miss on an OPEN publication day
            if q.isMiss(): q = fallbackChainRunner.run(q.asMiss(), policy, state)
                                 orElse unresolved(FX_E_RATE_NOT_FOUND)
            // 12 forward
            if q.needsForward(): q = forwardCurveCache.curve(route.pair, state)
                                         .outright(o.valueDate)            // memoised exp
            quotes.add(q)

        // 13 averaging (one observation for NONE)
        quote = averagingEngine.average(obs, quotes, policy, state)
        state.memo.put(key, quote)

    // 14-15 apply and round
    unrounded = FxMath.apply(request.amount, quote, DECIMAL128)   // x or / , fixed order
    booked    = precisionEngine.book(unrounded, toCcy, policy.rounding, state)

    // 16-17 restriction and lineage
    restriction = restrictionPropagator.of(quote)
    lineage     = lineageBuilder.build(resolvedContext, quote.path)   // inputsHash lazy
    return ConversionResult(..., unrounded, booked, quote.effectiveRate, restriction, lineage)


function selectFixing(series, fixingDate, cut, selection):
    i = binarySearch(series.dayOffset, fixingDate)
    if i < 0: return MISS                                  // stage 11 eligible iff calendar open
    slice = series.versions[versionFrom[i] .. versionFrom[i+1])
    visible = slice where recordedAt <= cut                // binary search upper bound
    switch selection:
        FirstOfficial     -> first(visible where status == OFFICIAL)
        LatestCorrected   -> last(visible where status in {OFFICIAL, CORRECTED})
        AsOfKnowledge(t)  -> last(visible where status in {OFFICIAL, CORRECTED}
                                              and recordedAt <= min(t, cut))
    if none: return last(visible where status == PRELIMINARY) as ESTIMATED(PRELIM_FIXING)
```

---

## Appendix D -- Deterministic Decimal `ln` and `exp` (D-09)

Normative for accuracy and determinism; the particular series and reduction counts are the recommended implementation.

### D.1 Precision Model

| Context | Value | Use |
|---------|-------|-----|
| `FxMath.DECIMAL128` | `new MathContext(34, HALF_EVEN)` | every published decimal |
| `FxMath.WORKING` | `new MathContext(FxConfig.decimalWorkingPrecision, HALF_EVEN)`, default 60 | transcendental intermediates |
| Series truncation | terms below `10^-(working+4)` relative | loop termination |

Accuracy contract: for `x` in `[1e-30, 1e30]`, the DECIMAL128-rounded result of `ln` and `exp` has relative error `<= 1e-33`. The FS S11.4 requirement is 1e-30; the margin exists so that downstream interpolation (which subtracts two `lnCarry` values and can cancel leading digits) still meets 1e-30 at the forward level.

### D.2 `ln(x)`, `x > 0`

```
1. x <= 0                     -> IllegalArgumentException (a rate or carry is never <= 0)
2. decompose  x = m * 10^k    with m in [1,10)
      k = x.precision() - x.scale() - 1
      m = x.movePointLeft(k)                       // exact, no rounding
3. reduce     y = m ; r = 0
      while |y - 1| > 1e-3 and r < 16:
          y = DecimalSqrt.sqrt(y, WORKING) ; r++
      // m in [1,10) => r <= 12 ; y - 1 ~ 5.6e-4 at r = 12
4. series     z = (y - 1) / (y + 1)                 // |z| <= 2.8e-4
      s = z ; zz = z*z ; term = z ; n = 3
      repeat: term = term * zz ; s = s + term / n ; n += 2
      until |term/n| < 10^-(working+4)
      lnY = 2 * s                                   // ~9 terms at |z| = 2.8e-4
5. recombine  ln x = k * LN10 + (2^r) * lnY         // 2^r exact as BigDecimal
6. round to DECIMAL128
```

Error budget at working = 60: series truncation `< 1e-64`; `r <= 12` square roots each contributing `<= 1 ulp` at 60 digits, amplified by `2^r = 4096` in step 5, giving `< 1e-56`; `LN10` exact to 70 digits. Total `< 1e-55`, far inside 1e-33.

### D.3 `exp(x)`

```
1. x == 0                     -> ONE (exact)
2. reduce by LN10   n = round(x / LN10, WORKING) as integer     // |rem| <= LN10/2 ~ 1.1513
                    rem = x - n * LN10
3. halve            h = 11 ; r = rem / 2^11                     // |r| <= 5.7e-4
4. Taylor           t = ONE ; s = ONE ; k = 1
      repeat: t = t * r / k ; s = s + t ; k++
      until |t| < 10^-(working+4)                                // ~14 terms
5. square           repeat h times: s = s * s                    // relative error x 2^11
6. scale            result = s.scaleByPowerOfTen(n)              // exact
7. round to DECIMAL128
```

Error budget at working = 60: Taylor truncation `< 1e-64`; 11 squarings amplify by `2^11 = 2048`, giving `< 1e-57`; the `n * LN10` subtraction in step 2 can cancel at most 1 leading digit for `|x| <= 70`, costing one digit. Total `< 1e-55`.

**Range**: `|x| <= 70` covers every realistic `lnCarry` (a 10% carry over 2 years is `|ln| < 0.2`). `|x| > 700` is rejected with `IllegalArgumentException`, which is an engine invariant violation rather than a business error, and therefore not an `FxErrorCode`.

### D.4 `DecimalSqrt` and Cost

```
sqrt(a, mc):                                  // a > 0
  shift a's unscaled value left so that the target has >= 2*mc.precision digits
  and the resulting scale is even
  root = BigInteger.sqrt(scaledUnscaled)      // exact integer sqrt, JDK 9+, deterministic
  return new BigDecimal(root, shiftedScale/2).round(mc)
```

No floating point, no `Math.sqrt`, no `BigDecimal.sqrt`. `BigInteger.sqrt` is a pure-integer Newton method in the JDK and produces the same value on every JVM.

Indicative cost at 60 digits (~200 bits), to be replaced by JMH measurement (TI-06, R1):

| Operation | Estimate |
|-----------|----------|
| `BigInteger.sqrt` at 200 bits | ~1-3 us |
| `ln`: 12 sqrt + ~9 series terms | ~15-40 us |
| `exp`: ~14 Taylor terms + 11 squarings | ~10-25 us |
| 20-pillar curve build (20 `ln`) | ~0.3-0.8 ms |

Levers if measurement disappoints: lower `decimalWorkingPrecision` to 45 (still `< 1e-40` error); replace the square-root reduction with a 100-entry table of `ln(1 + j/100)` constants to 70 digits (removes all square roots at the cost of 100 verified constants); cache `lnCarry` differences per pillar pair.

### D.5 Constants and Verification

`DecimalConstants.LN10` is a 70-significant-digit string literal. Verification, all in `DecimalMathConformanceTest`:

1. **Independent reference**: `ln-reference-70dp.csv` and `exp-reference-70dp.csv` in `fx-testkit`, 2,000 points each, log-spaced over `[1e-30, 1e30]` for `ln` and `[-70, 70]` for `exp`, generated offline at 80 digits and **cross-checked against a second independent arbitrary-precision implementation** before being committed (TI-06). Assertion: relative error at WORKING precision `< 1e-50`, and exact string equality of the DECIMAL128-rounded value against the reference rounded to 34 digits.
2. **Constant self-check**: `exp(LN10)` computed at 70 digits equals `10` within `1e-65`; `ln(BigDecimal.TEN)` equals `LN10` within `1e-65`.
3. **Algebraic identities** (jqwik, 12.2): `ln(a*b) == ln a + ln b`, `exp(a+b) == exp a * exp b`, `exp(ln x) == x`, `ln(10^k) == k*LN10`, `ln(1) == 0` exactly.
4. **Frozen digest**: a single SHA-256 over the concatenated `toPlainString()` of all 4,000 reference evaluations, asserted against a committed golden digest. One line fails if anything in the decimal layer changes, which is the cheapest possible regression canary.
5. **Cross-JVM** (`JvmMatrixDeterminismTest`, vector X10): G05 and the frozen digest run on the CI JVM matrix (TI-07). Any vendor disagreement is a release blocker.

### D.6 Interpolation Error at the Forward Level

`LOG_LINEAR_CARRY` computes `lnCarry[i+1] - lnCarry[i]`. For closely spaced pillars with similar carry this subtraction cancels leading digits: two values agreeing to 6 digits lose 6, leaving ~1e-27 relative in the difference at a 1e-33 input error. After `exp` and multiplication by spot, the forward retains better than 1e-25 relative accuracy -- more than sufficient against DECIMAL128 output and against any market-meaningful precision, but it is the reason the `ln`/`exp` accuracy target is set three orders tighter than the FS requires. `ForwardCurveTest` asserts G05 reproduces `1.0895143` and that repeated evaluation is bit-identical.

---

*End of Technical Specification.*

*Hand-off: when approved, pass to **implementation-engineer** for implementation of the `fx-conversion` reactor per this specification. Open items in S14 must be routed as follows: FS OQ-01..OQ-11 to the functional owner (functional-expert) and the business owners named in each row; OQ-T01..OQ-T08 and TI-01..TI-08 to the platform architecture group before implementation of the affected component begins. TI-08 (per-pair source provenance in market snapshots) is a functional-spec gap and blocks the entitlement propagation design for forwards.*
