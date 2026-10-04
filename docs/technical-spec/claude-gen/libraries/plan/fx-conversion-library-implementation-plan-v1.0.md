# FX Conversion Library -- Implementation Plan v1.0

| Field | Value |
|-------|-------|
| Derived from | `docs/technical-spec/claude-gen/libraries/fx-conversion-library-v1.0.md` (sole authority) |
| Structural precedent | `docs/requirements/uom-conversion-library-v1.0.md` (UOM reactor; shape only -- not built code) |
| Status | DRAFT -- planning deliverable only. No code, no `pom.xml`, no tests are produced by this document. |
| Repository state | Greenfield. No `pom.xml`, no `src/main/java` exists anywhere in this repo today. |
| Scope note | This repository has **no** `CLAUDE.md`, no ADR files, and no platform D-1..D-14 catalogue. Those constraints, where cited, belong to a different (valuation-engine) codebase and are inapplicable here except where the FX tech spec itself cross-references them for a **future, out-of-scope** integration adapter (S2.2, S13.2, Appendix A). The only binding decision numbers in this plan are the FX functional spec's own `D-01..D-16`, this tech spec's assumptions `A-01..A-18`, and its open items `OQ-*`/`OQ-T*`/`TI-*`. |

---

## 1. Scope Classification

This is a **standalone library reactor** (`fx-conversion`, groupId `com.power.fx`), not a module inside the valuation-engine platform reactor. All five modules (`fx-api`, `fx-core`, `fx-cdm`, `fx-testkit`, `fx-guice`) are library-scope in the sense the tech spec itself defines (S1 Layer row): no framework in `fx-api`/`fx-core`/`fx-cdm`, Guice-only wiring in `fx-guice`, no Spring anywhere, no database, no production hosting layer (S2.2, S10b). There is no `valuation-app`-equivalent simulator module in this reactor and none is to be added (S13.3 MC-3: not applicable). Full D-01 (JDK-only, no I/O/clock on the resolution path) and D-09 (no floating point) discipline applies to `fx-api` and `fx-core` for the entire build, enforced mechanically by ArchUnit + an ASM bytecode scan (S12.5), not by convention alone.

---

## 2. Build Phase Overview

| Phase | Modules | Depends on | Task count | Driving tech-spec sections |
|-------|---------|-----------|-----------|------------------------------|
| 1 | `fx-api` | none (JDK 21 + `jakarta.inject` provided) | 16 | S4, S5.1-5.3, Appendix B |
| 2 | `fx-core` | Phase 1 (`fx-api` compile) | 18 | S6, S7, S10, S10a, Appendix C, Appendix D |
| 3a | `fx-cdm` | Phase 1 + Phase 2 complete (compile dep on `fx-api` only; `fx-core` dependency is **forbidden**, Appendix A) | 4 | S4.11, S6.17 |
| 3b | `fx-testkit` | Phase 1 + Phase 2 complete (compile dep on `fx-api` + `fx-core`) | 8 | S12, Appendix D.5 |
| 4 | `fx-guice` | Phase 2 complete (compile dep on `fx-core` + Guice 7) | 4 | S9 |

Phases 3a and 3b run **in parallel** -- they share no code (`fx-cdm` does not depend on `fx-core`; Appendix A marks that dependency direction "forbidden"), and nothing in Phase 4 needs Phase 3a or 3b to compile. `fx-testkit`'s golden-vector suites are, however, the mechanism that closes out the acceptance gates for Phases 1, 2 and 4 (see Section 4), so Phase 3b's early tasks (3b.1, architecture/bytecode enforcement) are **pulled forward in substance** during Phase 2, per the shift-left practice below.

Total: 50 engineering tasks across 5 phases.

### 2.1 Shift-left architecture gate (new practice, not mandated verbatim by the tech spec)

S12.5's `ArchitectureTest` (AR-01..AR-10) and `NoFloatingPointBytecodeTest` formally live in `fx-testkit`, which only exists from Phase 3b onward. Waiting until Phase 3b to run them against `fx-api`/`fx-core` means hundreds of lines of D-01/D-09-violating code could be written in Phase 2 before the first mechanical check runs. This plan therefore recommends standing up a **throwaway, module-local** ArchUnit + ASM check (same ten rules, same opcode list) as a non-shipped test fixture from Phase 2 Task 2.1 onward, run on every subsequent `fx-core` task. Phase 3b Task 3b.1 then builds the real, shipped `ArchitectureTest`/`NoFloatingPointBytecodeTest` in `fx-testkit` and the throwaway fixture is deleted -- behaviour is identical, only the packaging changes. This is flagged explicitly because it is **new** (not specified by the tech spec, which places these tests only in `fx-testkit`) and because it changes nothing about what ships; it only moves the point of first failure earlier.

---

## 3. Phase 1 -- `fx-api`

Package tree, class names and file layout are taken verbatim from Appendix B; nothing here is invented.

### Task 1.1 -- Module scaffold
Create the `fx-api` module shell: `pom.xml` (artifactId `fx-api`, groupId `com.power.fx`, JDK 21 release, `jakarta.inject` at `provided` scope per A-04/TI-02), and the package skeleton `com.power.fx.api.{spi,model,request,result,error,ingest}` per Appendix B.
- Implements: S1 (Layer row), Appendix A, Appendix B.
- Depends on: none.
- Acceptance: module builds empty; reactor POM declares `fx-api` as the first module. (Actual POM content is a follow-on implementation task, not produced by this plan.)

### Task 1.2 -- Core enumerations (`model` package)
All enums of S4.1: `Purpose`, `Leg`, `DateRule`, `AmountType`, `SettlementAmountState`, `ItemType`, `RunMode`, `RateType`, `RateFinality`, `FixingStatus`, `FixingVersionPolicy`, `NonPublicationDayHandling`, `RollConvention`, `OffsetCalendarKind`, `ForwardMethod`, `InterpolationMethod`, `FutureDateTreatment`, `SpotAdjustment`, `AveragingMethod`, `ObservationSetKind`, `WindowKind`, `Weighting`, `OutputShape`, `FxDateFromObservation`, `FallbackStepKind`, `FixedFactorKind`, `UsageClass`, `SourceRight`, `SnapshotKind`, `SignOffStatus`, `EventType`, `FxDifferenceClass`, `EstimatedEventHandling`, `Scope`, `VersionStatus`, `TenantHealthStatus`, `FxEntityType`, `FxStoreKind`. Includes `RateFinality.weakest(RateFinality...)` (Pattern #3 behaviour).
- Implements: S4.1, D-12 (no `FIXED` constant anywhere -- self-discipline now, mechanically checked by AR-09 in Phase 3b).
- Depends on: Task 1.1.
- Acceptance: compiles; a throwaway unit test asserts `RateFinality.weakest()` is associative/commutative/idempotent over all 3-element combinations of `{CONFIRMED, ESTIMATED, UNRESOLVED}` and returns `UNRESOLVED > ESTIMATED > CONFIRMED` ordering -- this is the "finality monotonicity" property of S12.2, pulled forward here because every downstream combinator depends on it being correct from day one; the full `jqwik`-based property test is re-asserted in Phase 3b Task 3b.6.

### Task 1.3 -- Code enumerations and error/warning types (`error` package)
`FxErrorCode`, `FxWarningCode`, `FxIngestCode`, `FxReason`, plus `FxError`, `FxWarning` records and `FxException` (final class).
- Implements: S4.2.
- Depends on: Task 1.1.
- Acceptance: `FxErrorCode.category()` discriminator correctly separates the `FX_E_*` and `FX_V_*` prefixes; exhaustiveness of this switch is re-verified by `SealedExhaustivenessTest` in Phase 3b once `fx-testkit` exists (S4.2's `FxErrorCode` is a plain enum, not sealed, so this is a completeness check rather than a compiler-enforced one -- flag as a manual-discipline item, not a blocker).

### Task 1.4 -- Version envelope and common value types (`model` package)
`VersionEnvelope` (with the full compact-constructor validation list from S4.3: `approvedBy != null`, `!approvedBy.equals(authoredBy)`, `approvedAt.equals(recordedAt)`, `correctionOf != null => reasonCode != null`, scope/tenantId pairing, `validFrom.isBefore(validTo)`), `CurrencyCode`, `CurrencyPair` (with `inverse()`, `isIdentity()`, `canonical()`), `Rational` (with `plus`/`times`/`dividedBy`/`toDecimal`, compact-constructor gcd reduction and sign normalisation), `LocalDateRange`.
- Implements: S4.3, D-09 (via `Rational` exactness, used throughout averaging and day-count arithmetic).
- Depends on: Tasks 1.1, 1.2 (`Scope`, `VersionStatus`).
- Acceptance: covers the first slice of `ValueTypeValidationTest` (S12.1): `VersionEnvelope` four-eyes rule rejects `authoredBy == approvedBy`; `Rational.of(6,4)` reduces to `3/2`; `Rational` sign normalises onto the numerator for negative denominators; `CurrencyPair("EUR","EUR").isIdentity() == true` and `.canonical() == "EUR/USD"` format is exact.

### Task 1.5 -- Reference data value objects (`model` package)
`Currency`, `PairConvention`, `FixedFactor`, `FixingSource`, `PublicationCalendar`, `SettlementCalendar`, `AccountingUnit`, `FxPolicy`, `OffsetSpec`, `FallbackStep`, `RoundingSpec`, `ContractRate`, `AveragingSpec`, `WindowSpec`, `AccountingFxPolicy`, `SourceEntitlement`, `ManualRateOverride`, plus the sealed `FixingVersionSelection` (`FirstOfficial`, `LatestCorrected`, `AsOfKnowledge(Instant)`, A-16).
- Implements: S4.4, A-16.
- Depends on: Tasks 1.2, 1.4.
- Acceptance: every record embeds `VersionEnvelope`; `FixingVersionSelection` is `sealed` with exactly three permitted records (compiler-enforced exhaustiveness, re-verified by `SealedExhaustivenessTest` in Phase 3b).

### Task 1.6 -- Market data value objects (`model` package)
`FixingVersion`, `SpotQuote`, `ForwardPillar`, `DiscountCurvePayload`, `DiscountPillar`, `MarketSnapshotPayload`.
- Implements: S4.5.
- Depends on: Tasks 1.4, 1.5.
- Acceptance: compiles; note carried forward -- `DiscountCurvePayload`/`DiscountPillar` admit only a discount-factor representation pending OQ-04 (see Section 6); `MarketSnapshotPayload` carries no per-pair source field pending TI-08 (see Section 6) -- both are accepted limitations for v1.0, not defects of this task.

### Task 1.7 -- PDR projection (`model` package, A-08)
`PdrRef`, `PricingObservation`, `PricingDaySet`, with the compact-constructor rule that `sequence` values are distinct and strictly ascending and all weight denominators are positive.
- Implements: S4.6, D-08.
- Depends on: Task 1.4 (`Rational`).
- Acceptance: covers the `PricingDaySet` slice of `ValueTypeValidationTest` -- out-of-order or duplicate `sequence` values throw in the compact constructor; a zero or negative weight denominator throws. Carried-forward risk: OQ-T01 (projection duplication vs a shared `pdr-api`) -- see Section 6.

### Task 1.8 -- Request types (`request` package)
Sealed `FxRequest` (`RateRequest`, `ConversionRequest`, `SeriesRequest`, `ChainRequest`, `MonetaryRevaluationRequest`), `FxRequestContext`, sealed `PolicyRef` (`ById`, `Inline`), `TradeDates`, `DeliveryDay`, `EventDate`, `AccountingDates`, `ObservationPrice`, `ManagementViewSpec`, `PrewarmRequest`.
- Implements: S4.7, A-09, A-10, A-18.
- Depends on: Tasks 1.2, 1.4, 1.5, 1.7.
- Acceptance: `FxRequest` and `PolicyRef` are `sealed` with permits matching S4.7 exactly; a switch over either without a `default` branch fails to compile if a permit is missing (manually verified now; mechanically asserted by `SealedExhaustivenessTest` in Phase 3b).

### Task 1.9 -- Result types (`result` package, part 1)
Sealed `FxResult` (`RateResult`, `ConversionResult`, `SeriesResult`, `ChainResult`, `RevaluationResult`), `ObservationResult`, `LegResult`, `ManagementViewResult`, `PathStep`, `PillarRef`, `DistributionRestriction`, `AllocationResidual`, `CorrectionImpact`, `FixingVersionChange`, `PrewarmOutcome`.
- Implements: S4.8.
- Depends on: Tasks 1.2-1.8.
- Acceptance: `FxResult.isSuccess()`/`orThrow()` default methods compile against every permitted type; `AllocationResidual`/`DistributionRestriction` field sets match S4.8 exactly (these are the fields `ReconciliationTolerance` and `RestrictionPropagator` will later depend on in `fx-core`).

### Task 1.10 -- Lineage (`result` package, A-13)
`Lineage` (final class, **not** a record) with the lazily-memoised `inputsHash()` field, and `ReplayableRequest`.
- Implements: S4.9, A-13.
- Depends on: Tasks 1.8, 1.9.
- Acceptance: `Lineage` compiles as a plain final class with a private, lazily-populated `String` field for the hash (single-check idiom, not computed in the constructor); a unit test asserts the hash field is `null`/unset immediately after construction and is computed exactly once even under repeated calls (a simple call-counter double on the hash supplier). **Open design seam carried forward**: `Lineage` cannot depend on `fx-core`'s `CanonicalJson`/`InputsHasher` (module direction: `fx-core -> fx-api`, never the reverse, Appendix A). This task must therefore define `Lineage`'s constructor to accept a pre-built canonical-form string (or an internal `Supplier<String>`) from its caller, with `Lineage` itself owning only the final `MessageDigest.getInstance("SHA-256")` step (A-12, JDK-only, legal in `fx-api` by the same AR-02 allowlist reasoning that permits it in `fx-core`). See Section 7, new gap #1.

### Task 1.11 -- Ingest types (`ingest` package)
`FxIngestRecord`, `IngestOutcome`, `IngestRejection`, `FixingCorrection`, `SnapshotAvailability`, `MarketWatermark`, `MarketDataChangeSet`.
- Implements: S4.10.
- Depends on: Tasks 1.2, 1.4-1.6.
- Acceptance: compiles; `IngestOutcome.newGenerations` typed as `Map<FxStoreKind, Long>` exactly per spec (the `-1` "unchanged" sentinel is a `DefaultFxIngestor` concern in Phase 2, not an API-level constraint).

### Task 1.12 -- `FxConfig` (top level, `api` package)
`FxConfig` record and `BootstrapMode` enum, with every default from S4.12 **as corrected by S10a.3** (see Section 7, new gap #2): `fixingHotWindowYears = 3`, `retainedSnapshotsPerTenant = 8`, `memoMaxEntriesPerSnapshot = 20_000` (S10a.3 revision, not the `50_000` literal in S4.12), `forwardMemoMaxEntriesPerCurve = 512` (S10a.3 revision, not the `4_096` literal in S4.12), `decimalWorkingPrecision = 60`, `eagerInputsHash = false`, `allowImplicitPin` true-for-AD_HOC-only, `failOnStale = false`, `maxFallbackStalenessDays = 3` (OQ-07 placeholder).
- Implements: S4.12, S10a.3.
- Depends on: Task 1.1.
- Acceptance: Javadoc on `forwardMemoMaxEntriesPerCurve` and `memoMaxEntriesPerSnapshot` cites S10a.3 explicitly and records that it overrides the literal default in S4.12, so a future reader is not misled by the earlier section. No `eagerCurveBuild`-style field is added despite S10a.2/OQ-T08 asserting "a config flag exists" -- see Section 7, new gap #3; this task **does not invent** that field.

### Task 1.13 -- Public API ports (`api` package, S5.1)
`FxConverter`, `FxSnapshot extends FxConverter`.
- Implements: S5.1, Pattern #14 Facade.
- Depends on: Tasks 1.8, 1.9, 1.11.
- Acceptance: interfaces compile; Javadoc captures the four binding semantics bullets of S5.1 verbatim in substance: results never thrown except via `orThrow()`; `batch` is index-aligned and never fails wholesale; request-snapshot vs facade-snapshot precedence (OQ-T05, silent precedence to the request field, `FX_E_TENANT_MISMATCH` only on tenant disagreement); `prewarm` is the only method permitted to perform I/O.

### Task 1.14 -- SPI ports (`spi` package, S5.2)
`TenantContextProvider`, `ReferenceDataLoader`, `MarketDataLoader`, `FxEventListener` (with `noop()`), `FxMetrics` (with `noop()`).
- Implements: S5.2.
- Depends on: Tasks 1.4 (`LocalDateRange`, `CurrencyPair`), 1.6, 1.11.
- Acceptance: `FxEventListener`'s three methods (`onFixingCorrected`, `onRejected`, `onSnapshotAvailable`) all have no-op default bodies, matching FS S14.1 exactly (no fourth method is added); `FxMetrics.noop()` returns a singleton that is safe to call from any thread.

### Task 1.15 -- Ingestion and health ports (`api` package, S5.3)
`FxIngestor`, `FxHealth`.
- Implements: S5.3.
- Depends on: Tasks 1.2 (`TenantHealthStatus`), 1.11.
- Acceptance: compiles; `FxIngestor.apply` signature matches exactly (`List<FxIngestRecord> -> IngestOutcome`).

### Task 1.16 -- `FxVersion` generated constant (A-06, TI-05)
A generated-at-build-time class exposing `FxVersion.VALUE`, mechanism TBD between Maven resource filtering and a template-generated class (TI-05, unresolved -- see Section 6).
- Implements: A-06.
- Depends on: Task 1.1.
- Acceptance: **blocked pending TI-05.** Placeholder acceptance for planning purposes: whichever mechanism is chosen, `FxVersion.VALUE` must differ between released artifacts and must be stable between a local build and a CI build of the same commit (the explicit TI-05 requirement), because it feeds `inputsHash` (Task 2.15) and an unstable value would silently break replay determinism (S10.5, vector X04).

### Phase 1 acceptance gate

| Gate | Test class (home module) | Status at end of Phase 1 |
|------|---------------------------|---------------------------|
| Compact-constructor validation | `fx-api/ValueTypeValidationTest` | Runs fully now (no `fx-core`/`fx-testkit` dependency) |
| Reconciliation tolerance formula | `fx-api/ReconciliationToleranceTest` | Runs fully now |
| Sealed exhaustiveness | `fx-api/SealedExhaustivenessTest` | **Partially gated** -- the test's exhaustive switches can be written now, but S12.1 places this test's full intent (checking the *testkit's* exhaustive switches) against `fx-testkit`, which does not exist yet; re-run and close out in Phase 3b |
| AR-09 (no `FIXED` enum constant) | `fx-testkit/ArchitectureTest` | Deferred to the shift-left fixture (Section 2.1), run manually at the end of Phase 1 even though the shipped test lives in Phase 3b |
| AR-01/AR-02/AR-03 subset applicable to `fx-api` | shift-left fixture | Run manually at the end of Phase 1 |

Phase 1 is not declared complete until the compact-constructor tests pass and the shift-left ArchUnit fixture reports zero violations against `fx-api`.

---

## 4. Phase 2 -- `fx-core`

### Task 2.1 -- Decimal foundations spike (SPIKE / RISK GATE, Appendix D)
Build `core.decimal`: `FxMath` (owns the only two `MathContext` instances, `DECIMAL128` and `WORKING`), `DecimalConstants` (70-digit `LN10` literal), `DecimalSqrt` (`BigInteger.sqrt`-based, never `BigDecimal.sqrt`), `DecimalLn`, `DecimalExp`, `RationalMath` (exact `BigInteger` lcm/gcd for common-denominator arithmetic).
- Implements: S6.10, D-09, Appendix D.1-D.4.
- Depends on: Phase 1 complete (needs only `java.math`/`java.util` -- no `fx-api` model types are actually touched by this package, but it lives in `fx-core` per Appendix B and so the module must exist and compile against `fx-api`).
- **This is the mandatory early spike required before the forward-curve build-out (Task 2.11) is considered committed** -- R1, the architect's top identified risk.
- Acceptance (correctness, required before any downstream use):
  1. `exp(ln(x)) == x` and `ln(exp(x)) == x` to within `1e-33` relative at DECIMAL128, for `x` spanning `[1e-30, 1e30]` (ln argument) and `[-70, 70]` (exp argument).
  2. `ln(a*b) == ln(a) + ln(b)`, `exp(a+b) == exp(a) * exp(b)`, `ln(10^k) == k * LN10`, `ln(1) == 0` exactly -- the Appendix D.5 algebraic identities, checked against a **provisional** reference set of at least 50 log-spaced points (the full 2,000-point, independently cross-checked table is a Phase 3b deliverable gated on TI-06; this task's provisional set must be clearly marked non-authoritative in its test source).
  3. `DecimalSqrt` never calls `Math.sqrt`, `BigDecimal.sqrt`, or constructs a `BigDecimal` from a `double` -- verified by the shift-left ASM fixture (Section 2.1) run specifically against the `core.decimal` package before this task is marked done.
- Acceptance (performance, **JMH-style benchmark gate, not a correctness check**):
  1. Single `ln()` call: median latency within the Appendix D.4 estimate band (~15-40 us at `WORKING` = 60 digits). Single `exp()` call: ~10-25 us.
  2. A synthetic 20-pillar-curve-equivalent workload -- 20 sequential, non-memoised `ln()` calls at distinct arguments, run back-to-back with no caching -- must complete at **p99 <= 1 ms** per invocation of the workload. This directly rehearses the S10a.2 "forward curve per pair per snapshot <= 1 ms" NFR *before* Task 2.11 commits to a design around it.
  3. If the p99 <= 1 ms benchmark fails, this task's exit criteria require evaluating, in order, the three levers Appendix D.4 names: (a) lower `decimalWorkingPrecision` to 45 (re-verify the `< 1e-40` error bound still holds), (b) replace the square-root argument-reduction in `DecimalLn` with a 100-entry table of `ln(1 + j/100)` constants to 70 digits (removing all square roots), (c) a cache for `lnCarry` pillar-pair differences -- and re-benchmark. Task 2.11 **does not start** until one lever combination clears the gate or the risk is explicitly accepted and documented by solutions-architect.
  4. Run the above benchmarks on at least two JVM vendors locally (e.g., Temurin and one of Zulu/GraalVM) as an early, informal cross-check; the formal CI cross-vendor gate (X10, `JvmMatrixDeterminismTest`) is Phase 3b Task 3b.8 and is blocked on TI-07 (vendor list not yet confirmed).

### Task 2.2 -- Reference catalogue substrate
`core.cache`: `Timeline<V>`, `ReferenceCatalogue`, `CatalogueBuilder`, `ReferenceStore`, `InMemoryReferenceStore`; `core.date`: `CalendarIndex`, `JointCalendarIndex` (compiled at catalogue-build time per S6.5, even though their query operations are exercised in Task 2.7).
- Implements: S7.1.1, S7.1.2, D-03, D-13.
- Depends on: Task 2.1 is not a hard dependency, but this task should not start until the module itself compiles (trivially true once Task 2.1 lands).
- Acceptance: `Timeline` resolution (`validFrom <= d < validTo`, greatest `recordedAt <= k`, `RETIRED` wins as absent) matches S7.1.1's binary-search contract; GLOBAL and TENANT catalogues are distinct `ReferenceCatalogue` instances, with TENANT catalogues holding a **reference** to GLOBAL rather than a copy (TI-03 assumption, carried forward in Section 6); `CalendarIndex.isOpen`/`.previous`/`.next` are O(1) bit operations verified against a naive brute-force reference implementation over a synthetic 10-year calendar (`CalendarIndexTest`, pulled forward from S12.1's `fx-core` list); `JointCalendarIndex.intersect(...)` is memoised per sorted `(calendarRef, versionId)` set and built at most once per generation.

### Task 2.3 -- Bitemporal fixing store (columnar, D-03)
`core.cache`: `FixingStore`, `InMemoryFixingStore`, `FixingView`, `FixingSeries`, `SeriesKey`.
- Implements: S7.1.3, D-03.
- Depends on: Task 2.2 (shares the generation/swap discipline), Phase 1 Task 1.6 (`FixingVersion` ingest shape).
- Acceptance: the compressed-sparse-row layout (`dayOffset[]`, `versionFrom[]`, `values[]`, `recordedAt[]`, `status[]`, `versionIds[]`) resolves `(fixingDate, knowledgeCut, FixingVersionSelection)` in O(log n) with the exact algorithm of S7.1.3 (binary search date -> version slice -> binary search `recordedAt` upper bound -> apply selection rule over the prefix); a bitemporal correctness test (per the agent's own template: insert v1, supersede with v2, as-of-t1 returns v1, as-of-t2 returns v2, as-of-now returns v2) is written here even though `BitemporalTest` is formally an S12.1 `fx-core` test name -- same content, built alongside the production code rather than deferred. **Carried-forward risk**: OQ-T03 (first-ever-seen key backfilled below an already-pinned cut) and OQ-08 (hot-window sizing, R3) both bear directly on this task -- see Section 6.

### Task 2.4 -- Immutable market snapshot store
`core.snapshot`: `MarketSnapshot`, `SnapshotAssembler`, `SnapshotCompletionTracker`; `core.cache`: `MarketSnapshotStore`, `InMemoryMarketSnapshotStore`, `HotWindowPolicy`.
- Implements: S7.1.4, D-03.
- Depends on: Tasks 2.2, 2.3.
- Acceptance: `put` on an existing `marketSnapshotId` raises `FX_I_SNAPSHOT_IMMUTABLE`; `SnapshotCompletionTracker` stages chunks keyed by `marketSnapshotId` and the snapshot becomes resolvable **only** when `chunkCount` chunks plus the completion marker have all arrived (`FX_I_SNAPSHOT_INCOMPLETE` otherwise); `MarketSnapshotStore` is LRU-bounded to `retainedSnapshotsPerTenant` (default 8, OQ-08). `MarketSnapshot.curves` field exists as a `ConcurrentHashMap<CurrencyPair, ForwardCurve>` but is only populated starting in Task 2.11 -- this task ships it empty-and-correct (`computeIfAbsent` wiring present, no builder yet). **Carried-forward risk**: OQ-08 (retained-snapshot count, R3) and OQ-T08 (eager-vs-lazy curve build affects `SnapshotAssembler`) -- see Section 6.

### Task 2.5 -- `PinnedState` and snapshot pinning
`core.snapshot`: `PinnedState`; `core`: `PinnedFxSnapshot`.
- Implements: S7.1.5, A-15.
- Depends on: Tasks 2.2, 2.3, 2.4.
- Acceptance: pinning captures exactly five references (`global`, `tenant`, `fixings`, `snapshot`, `knowledgeCut`) plus `refGeneration`/`fixingGeneration`/`memo` in one allocation, O(1), with **no** copying and **no** re-read of any store afterward -- asserted by a test that swaps a store generation mid-flight and confirms an already-pinned `PinnedFxSnapshot` is unaffected (this is the physical half of A-15 and is re-exercised at full pipeline scale by `ConcurrencyTest` in Task 2.18).

### Task 2.6 -- Policy resolution and validation substrate
`core`: `ResolvedPolicy`, `ResolvedContext`; `core.validation`: `PolicyMatrix`, `RequestValidator`, `PolicyValidator`.
- Implements: S6.4, FS S8.3/S9.1/S9.2, D-14.
- Depends on: Tasks 1.2 (enums), 2.2 (catalogue lookup for `ById` policy refs), 2.5 (`PinnedState`).
- Acceptance: `PolicyMatrix`'s six static `EnumMap`/`EnumSet` tables (rule x leg, purpose x allowed-legs/required-amountType, purpose x itemType, purpose x forbidden-fallback-steps, purpose x allowed-usageClass, purpose x amountType) are built at class-init with a **self-test asserting totality over the enum cross-product** -- a new enum constant must fail fast rather than silently default to "allowed" (S6.4 binding requirement). The purpose-defaults table of S6.4 is implemented with the firm-wide-default cells (`rateSourcePriority` for ACCT_TXN/MGMT_VIEW per OQ-01, date rule for ACCOUNTING_RECOGNITION per OQ-02) **deliberately left un-defaulted**, raising `FX_V_INVALID_POLICY` on absence -- this task must not invent a default the tech spec explicitly withholds. `RequestValidator` raises `FX_V_UNSIGNED_SNAPSHOT` per A-09/S6.3's `runMode`/`UNREALISED_MTM`/`UNSIGNED` combination and `FX_V_PRICE_SERIES_MISMATCH` as a precheck ahead of the averaging engine (Task 2.12).

### Task 2.7 -- Date resolution engine (FS S7, D-02)
`core.date`: `RawDateDeriver`, `PublicationDateResolver`, `ValueDateResolver`, `SpotDateCalculator`, `RollConventions`, `DeliveryDayMapper`, `CutoffInstantResolver`, `DateRuleResolver`, `DefaultDateRuleResolver`, `ResolvedDates` (plus completing `CalendarIndex`/`JointCalendarIndex` query-path usage from Task 2.2).
- Implements: S6.5, S6.5.1, D-02, S10c.1/S10c.2.
- Depends on: Tasks 2.2 (compiled calendars), 2.6 (`ResolvedPolicy` shape).
- Acceptance: **G-independent, calendar-only vectors C01-C05 pass end to end**: C01 (Good Friday, `USE_PREVIOUS` -> Thu 2-Apr, CONFIRMED), C02 (`USE_NEXT` -> Tue 7-Apr, skipping Easter Monday), C03 (`FAIL` -> `FX_E_NON_PUBLICATION_DATE`), C05 (Sat gas delivery -> Fri). A query outside a calendar's `coverage` window raises `FX_E_DATA_NOT_LOADED` with `details.calendarRef` and the bounds (A-14/OQ-T02). `CutoffInstantResolver` is exercised at the 2026 EU DST transition dates (2026-03-29, 2026-10-25) per S10c.2, confirming the JDK's documented gap/overlap behaviour and confirming this **never** feeds into rate selection (only into lineage display). Structural invariant: `DateRuleResolver` runs to completion before any pair/rate component exists in the reactor at all (Tasks 2.9/2.10 are not started yet), which is itself the mechanical proof of D-02's "dates strictly before rate lookup" ordering at the *build* level, not just the *runtime* level.

### Task 2.8 -- Entitlement filtering and restriction propagation (initial slice)
`core.entitlement`: `EntitlementResolver`, `DefaultEntitlementResolver`, `RightsSet`, `FilteredSources`, `RestrictionPropagator` (direct-quote and inverse-quote rows of the S6.6 propagation table only; cross/forward/average rows are completed incrementally in Tasks 2.9, 2.11, 2.12, 2.14 as those derivations come into existence).
- Implements: S6.6, D-10.
- Depends on: Task 2.2 (`SourceEntitlement` timeline, TENANT-over-GLOBAL overlay), Task 2.7 (resolved `fxDate`).
- Acceptance: filtering keeps a source iff `rights` contains `VALUATION`; a drop emits `FX_W_SOURCE_SKIPPED_NOT_ENTITLED` with `details.sourceCode` (vector X05); an empty filtered list with no entitled fallback step raises `FX_E_SOURCE_NOT_ENTITLED` (vector X06); the direct-quote and inverse-quote rows of the propagation table are unit-tested now, the remaining five rows (cross/triangulation, forward-from-points, forward-from-CIP, average, leg chain) are tracked as **open sub-tasks of this task**, closed out by name in Tasks 2.9/2.11/2.12/2.14's acceptance criteria respectively. **Carried-forward risk**: TI-08 (no per-pair source provenance in `MarketSnapshotPayload`) means the forward/CIP propagation rows (closed in Task 2.11) can only use a single snapshot-level source-rights assumption rather than true per-pillar provenance -- see Section 6, this is the example the architect flagged explicitly.
- **Implementation-seam note (new, not in the tech spec -- see Section 7, new gap #5)**: the internal port signature `PairResolver.route(from, to, policy, state, fxDate)` (S5.4) does not take a `FilteredSources` parameter, yet S6.6 point 4 and S6.7 step 6 (`DirectQuoteStep`) both require that only entitled sources ever reach pair resolution. This task must decide, and document for Task 2.9 and for `fx-testkit`'s stage-level tests (which bind to these internal seams per S5.4's own warning), whether the pipeline narrows `ResolvedPolicy.rateSourcePriority` to the filtered list before calling `pairResolver.route(...)`, or whether `DirectQuoteStep` calls `EntitlementResolver` itself via `PinnedState`. Either is internally consistent with S5.4's "documented for design completeness" latitude; this plan does not choose for the implementer but flags it as a decision that must be recorded in code review.

### Task 2.9 -- Pair resolution engine (nine-step chain, FS S11.2)
`core.pair`: `PairResolver`, `DefaultPairResolver`, `PairResolutionStep` + `IdentityStep` (used at steps 1 and 3), `FixedFactorNormaliser` (pre at step 2, post after step 9), `ContractRateStep`, `ManualOverrideStep`, `DirectQuoteStep`, `InverseQuoteStep`, `ConfiguredCrossStep`, `MajorCrossStep`, `PairRoute`, `QuotedInParser`.
- Implements: S6.7, FS S11.2.
- Depends on: Task 2.2 (`FixedFactor`, `ContractRate` via policy, `ManualRateOverride`, `PairConvention` reference data), Task 2.7 (resolved `fxDate`), Task 2.8 (filtered source list per the seam note above).
- Acceptance: **vectors G01, G02, G03, G09 pass**: G01 (`EUR/JPY = 1.0850 x 149.20 = 161.8820`, step 9 major cross), G02 (`GBP/AUD = 1.2700 / 0.6600`, step 9 with inversion), G03 (`85.50 GBp -> 0.855 GBP -> 1.08585 USD -> 90.559890 INR`, fixed-factor-first-and-last invariant), G09 (HRK/BGN legal-peg termination at step 2, 1.95583, never touching the market). `PairResolutionOrderTest` asserts the nine-step order against a literal class list (not re-derived); `PairRoute`'s compact constructor enforces at-most-one-intermediate-currency and "fixed factor only at index 0 or n-1"; currency-validity-at-`fxDate` checks correctly reject HRK after 2023-01-01 and BGN after 2026-01-01 with `FX_E_INACTIVE_CURRENCY`; exhausting all nine steps raises `FX_E_NO_FX_PATH`.

### Task 2.10 -- Rate selection, fixing version selection and fallback chain (FS S11.1, S11.3)
`core.rate`: `RateSelector`, `DefaultRateSelector`, `RateCase`, `RateQuote`, `RateLookupMiss`, `FixingResolver`, `FixingVersionSelector`, `SpotResolver`, `FallbackChainRunner`, `DefaultFallbackChainRunner`, `AltSourceStep`, `PreviousPublicationDayStep`, `TriangulateStep`, `InterpolateFixingsStep`.
- Implements: S6.8, FS S11.1, FS S11.3.
- Depends on: Task 2.3 (`FixingSeries` lookup), Task 2.9 (`PairRoute`), Task 2.7 (resolved dates/cutoff), Task 2.8 (filtered sources for `AltSourceStep`).
- Acceptance: all nine rows R1-R9 of the S6.8 decision table pass as individually addressable parameterised cases (`RateSelectionMatrixTest`); `RateLookupMiss`'s compact constructor structurally asserts `calendarIndex.isOpen(resolvedDate)`, making the fallback chain unreachable from a holiday (the mechanical proof of D-02's second half); `FixingVersionSelector` passes `FirstOfficial`/`LatestCorrected`/`AsOfKnowledge(t)`/none-present x all status combinations (`FixingVersionSelectionTest`) and produces vectors **X01** (FirstOfficial immune to later correction), **X02/X03** (cut-dependent value changes at 1.0800 vs 1.0810), **X04** (replay bit-identical); the fallback chain's four steps run in declared order with forbidden-step rejection per `PolicyMatrix` (Task 2.6) for CONTRACT_SETTLEMENT/ACCOUNTING_* purposes, exhaustion raising `FX_E_RATE_NOT_FOUND`; vector **C04** (WMR outage, `[WMR, ECB]` fallback) exercises `ALT_SOURCE` end to end.

### Task 2.11 -- Forward curve construction and interpolation (GATED on Task 2.1's benchmark, FS S11.4, D-15)
`core.curve`: `ForwardCurve`, `ForwardCurveBuilder`, `ForwardCurveCache`, `SnapshotForwardCurveCache`, `PointsInterpolator`, `LogLinearCarryInterpolator`, `MonotoneCubicInterpolator`, `HymanFilter`, `CipForwardCalculator`, `DiscountCurve`, `ShortEndAdjuster`, `Extrapolator`, `DayCount`.
- Implements: S6.9, S6.9.3, D-15.
- Depends on: **Task 2.1's JMH gate must be green (or its risk explicitly accepted) before this task starts** -- this is the binding instance of the "early spike before the forward-curve build-out is considered committed" requirement. Also depends on Task 2.4 (curves live inside `MarketSnapshot`), Task 2.10 (rate-selection row R6 invokes the forward path).
- Acceptance: **vectors G04, G05 pass**: G04 (`LINEAR_POINTS`, `35 + 33*28/91 = 45.153846...` points at `pointsScale = 10000` -> `1.0895153846...`), G05 (`LOG_LINEAR_CARRY` default reproduces `1.0895143`, bit-identical on repeated evaluation -- `ForwardCurveTest`). POINTS/CIP/HYBRID construction all implemented, with CIP restricted to discount-factor pillars per the OQ-04 placeholder (an unloaded discount curve raises `FX_E_DATA_NOT_LOADED`, not a new code). `Rational(days, 365)` day-count used throughout, never a decimal division. `ForwardCurve.memo` bounded at `forwardMemoMaxEntriesPerCurve` (512 per the corrected Task 1.12 default), refusing new entries on overflow rather than evicting. Extrapolation implements both branches (flat implied carry to `maxExtrapolationYears`, then CIP-if-both-curves-exist else `FX_E_EXTRAPOLATION_LIMIT`). Forward crosses resolve both legs to the *same* value date before crossing outrights. Closes the remaining "forward from points" and "forward from CIP" rows of Task 2.8's `RestrictionPropagator` table (intersect of spot source + points source; intersect of spot source + both discount-curve sources). **Performance re-check**: a full curve build (20 pillars, LOG_LINEAR_CARRY) must independently clear the <= 1 ms p99 target using the real `ForwardCurveBuilder`, not just Task 2.1's synthetic 20-call rehearsal -- if it does not, the same lever list from Task 2.1 applies, plus the OQ-T08 eager/lazy trade-off (Section 6). **Carried-forward risk**: OQ-T06 (ON/TN absent under `TO_VALUATION_DATE`) -- this task implements the spec's own fallback (spot unadjusted, no new reason code) as the accepted-for-now behaviour, flagged for revisit when OQ-06/OQ-T06 close.

### Task 2.12 -- Averaging engine (FS S10, D-07, D-08)
`core.averaging`: `ObservationSetBuilder`, `Observation`, `ObservationSet`, `AveragingEngine`, `DefaultAveragingEngine`, `RateAverageStrategy`, `PriceMatchedStrategy`, `WeightResolver`, `PricingSetAdapter`, `PartialPeriodAggregator`, `SeriesOutcome`.
- Implements: S6.11, D-07, D-08.
- Depends on: Task 2.10 (per-observation rate resolution), Task 2.1 (`RationalMath.commonDenominator`), Task 1.7 (`PricingDaySet` projection).
- Acceptance: **vectors G06, G07, G08 pass, and the G07-G08 spread (`0.049862981`) is asserted explicitly**: G06 (`PartialPeriodAggregator`, `(10*1.0840+12*1.0872)/22 = 1.085745454...`, `confirmedPortion = 5/11`, ESTIMATED), G07 (`PRICE_MATCHED`, `80/1.10,...` equally weighted -> `75.279220779...`), G08 (`RATE_AVERAGE`, average 1.09, `82/1.09 = 75.229357798...`). `AveragingMethod.NONE` demonstrably produces exactly one observation through the same code path as a multi-observation average (no bypass branch exists -- a structural test asserts `ObservationSetBuilder` is the only caller of the averaging strategies, even for single-rate `ConversionRequest`s). Weighted-average arithmetic follows the binding order exactly: common denominator via exact `BigInteger` lcm, integer-times-decimal sum, **one** division. `SKIP_OBSERVATION` renormalises weights exactly over survivors. Vector **X11** (`PRICE_MATCHED` sequence mismatch -> `FX_V_PRICE_SERIES_MISMATCH`) passes. Closes the "average" row of Task 2.8's `RestrictionPropagator` table (intersect over all observations).

### Task 2.13 -- Precision, rounding and allocation (FS S15)
`core.precision`: `PrecisionEngine`, `DefaultPrecisionEngine`, `LargestRemainderAllocator`, `RoundingPolicyResolver`, `BookedAmount`.
- Implements: S6.12, FS S15.
- Depends on: Task 2.1 (decimal contexts), minimal coupling otherwise -- can run in parallel with Tasks 2.9-2.12 if resourcing allows, though this plan sequences it after averaging since `SeriesResult.residual` consumes it.
- Acceptance: `toAmountUnrounded` always full DECIMAL128; `toAmountBooked` rounded exactly once at `currency.decimals`; `LargestRemainderAllocator` ties break to earliest sequence, deterministic and map-order-independent; property test "`sum(line.toAmountBooked) == totalBooked` exactly" (S12.2) passes against randomised line sets (`jqwik`-backed, pulled forward here and re-run in Phase 3b's `PropertyBasedTest`).

### Task 2.14 -- Legs, chain and revaluation (FS S9, D-04, D-16)
`core.leg`: `ChainEngine`, `DefaultChainEngine`, `ContractLeg`, `AccountingTransactionLeg`, `TranslationLeg`, `ManagementViewLeg`, `FunctionalCurrencyResolver`, `RevaluationEngine`.
- Implements: S6.14, D-04, D-05, D-16.
- Depends on: Tasks 2.6-2.13 (a full single-leg conversion must already work end to end), Task 2.2 (`AccountingUnit` timeline for functional-currency resolution).
- Acceptance: **vectors F01-F09 all pass**: F01/F02/F06 (chain arithmetic), F03/F04 (`RevaluationEngine` signed-difference, UNREALISED vs REALISED classification), F05 (TRANSLATION, `299,015.75 x 1.1700 -> 349,848.43 EUR`), F07 (D-05 amount-type mismatch rejection), F08 (functional-currency effective dating, prospective-only), F09 (NON_MONETARY_HISTORICAL rejecting a CLOSING_RATE revaluation). Identity collapse short-circuits post-minor-unit-normalisation with zero market-data access. Leg ordering halts on the first UNRESOLVED leg (`UPSTREAM_UNRESOLVED`, no market data touched for subsequent legs). Leg-2 input selection keys on `settlementAmountState` only, never finality. `ManagementViewResult.persistable == false` always, reason `VIEW_ONLY` (D-16, A-18 multiple report currencies as a `List`). Closes the "leg chain" row of Task 2.8's `RestrictionPropagator` table (intersect over legs, exposed per-leg and chain-level). **Scope confirmation carried forward**: OQ-11 (line-category batch translation) is explicitly **not** built here -- `convertChain` translates one amount per call, matching the tech spec's deliberate omission (Section 6).

### Task 2.15 -- Lineage construction and `inputsHash` (FS S13, S14.3)
`core.lineage`: `LineageBuilder`, `DefaultLineageBuilder`, `CanonicalJson`, `InputsHasher`, `CorrectionImpactAssessor`.
- Implements: S6.13, S6.16, D-06.
- Depends on: Task 1.10 (resolves the `Lineage` constructor seam identified there), Task 2.6 (`ResolvedPolicy` is hashed in full), Task 1.16 (`FxVersion.VALUE`, blocked on TI-05).
- Acceptance: `InputsHashTest` passes the full member-set-exactness check (S6.13's table: `apiSchemaVersion`, `libraryVersion`, `tenantId`, `marketSnapshotId`, `fixingKnowledgeCut`, both generations, `policy`, `request`, `pdrRef`); `requestId` is excluded; a 34-digit rate survives RFC 8785 canonicalisation byte-for-byte (decimals as JSON strings, never numbers); `1.50` and `1.5` hash identically (`stripTrailingZeros`); member ordering is UTF-16 code-unit; `null`-valued members are omitted; a frozen golden hash for a reference request is asserted. `assessCorrectionImpact` implements the five-step algorithm of S6.16 exactly, including the **self-check** (re-canonicalise and compare to `previous.inputsHash()` before trusting the replay) -- a mismatch forces `replayable = false` rather than guessing. `inputsHash` is lazy (A-13) end to end: a micro-benchmark confirms a `convert()` call that never touches `Lineage.inputsHash()` pays no canonicalisation or SHA-256 cost.

### Task 2.16 -- Resolution memoisation (`core.memo`)
`ResolutionMemo`, `MemoKey`.
- Implements: S6.15.
- Depends on: Tasks 2.5 (lives on `PinnedFxSnapshot`), 2.6 (`policyDigest`).
- Acceptance: `MemoKey` keys the **rate**, not the amount; bounded by `memoMaxEntriesPerSnapshot` (20,000 per the corrected Task 1.12 default) with a 16-way striped-lock LRU, never held across a computation; decimal fields in keys are `stripTrailingZeros()`-normalised (A-07 discipline); property test "memo neutrality" (S12.2) -- identical results memo-on and memo-off -- passes against the full battery of vectors already implemented through Task 2.15.

### Task 2.17 -- Ingestion engine and health (FS S16, S8.2-8.5)
`core`: `DefaultFxIngestor`, `DefaultFxHealth`; `core.validation`: `IngestValidator`, `FixingSequenceValidator`.
- Implements: S6.1 (`DefaultFxIngestor`/`DefaultFxHealth` rows), S8.1-8.5, FS S16.
- Depends on: Tasks 2.2-2.4 (all three stores), Task 2.6 (`PolicyValidator` reused for reference-record validation where applicable).
- Acceptance: `IngestValidationTest` covers all eight `FX_I_*` codes (S6.18 table); the ingest sequence (partition by tenant+store kind -> deduplicate by identity -> validate -> sequence-contiguity check -> copy-on-write build -> atomic per-store swap -> notify -> metrics) matches S8.2 exactly, with listener notifications firing **after** the swap (vector X03's correction-notification ordering); single-writer-per-tenant via `ConcurrentHashMap<String, ReentrantLock>` covering all three stores per tenant, plus a dedicated GLOBAL lock; `FX_I_SEQUENCE_GAP` marks a key STALE and invokes `ReferenceDataLoader.loadKey(...)`; `TenancyIsolationTest`'s ingest-side assertions (scope violation, tenant-private market data invisibility) pass.

### Task 2.18 -- Conversion pipeline assembly and facade (FS S6.2, Appendix C)
`core`: `ConversionPipeline`, `DefaultFxConverter`.
- Implements: S6.2 (the full 18-stage binding pipeline order), Appendix C pseudocode.
- Depends on: **all of Tasks 2.1-2.17** -- this is the integration task.
- Acceptance: the Appendix C pseudocode's `convert()` function is realised stage-for-stage with no reordering; `convertChain` runs stages 1-5 once then 6-17 per leg via `ChainEngine`; `convertSeries` runs 6-12 per observation and 13-17 once; `batch` is index-aligned and never fails wholesale (A-10). **Full end-to-end vector suite passes through `DefaultFxConverter` directly** (ahead of the Guice-wired version in Phase 4): all of G01-G09, C01-C05, F01-F09, X01-X11. `ConcurrencyTest` passes: readers during swaps, concurrent pins, concurrent curve construction converging on identical values (idempotent `computeIfAbsent`), reconciliation racing with events, single-writer enforcement. Property tests "round trip" and "cross consistency" (S12.2) pass end to end now that the full pipeline exists.

### Phase 2 acceptance gate

| Gate | Mechanism | Status at end of Phase 2 |
|------|-----------|---------------------------|
| Golden vectors G01-G09, C01-C05, F01-F09, X01-X11 | Shift-left `VectorRunner`-equivalent harness (throwaway, promoted to `fx-testkit` in Phase 3b) driven directly against `DefaultFxConverter` | All pass |
| Property tests (round trip, cross consistency, forward convergence, series allocation, PDR weights, memo neutrality, finality monotonicity, restriction monotonicity, decimal identities, hash stability) | Shift-left `jqwik` harness | All pass except decimal identities' full 2,000-point form (blocked on TI-06, provisional set only) |
| AR-01..AR-10 + bytecode scan | Shift-left fixture (Section 2.1) | Zero violations against `fx-api` + `fx-core` |
| Forward curve <= 1 ms p99 | JMH (real `ForwardCurveBuilder`, Task 2.11) | Pass or explicitly risk-accepted |
| Single convert <= 20 us p99 | JMH, memo-hit and fixing-miss paths only (cold-forward-path risk explicitly carried as R1/R2, not required to close in Phase 2) | Memo-hit and fixing paths pass; cold forward path tracked as open risk into Phase 4's end-to-end gate |

Phase 2 is not declared complete until every vector and property test above is green against `DefaultFxConverter` directly (pre-Guice) and the shift-left architecture fixture reports zero violations.

---

## 5. Phase 3a -- `fx-cdm`

### Task 3a.1 -- CDM event envelope and decimal codec
`CdmFxEvent`, `CdmDecimalCodec` (parses with `new BigDecimal(String)`, never `valueOf(double)`).
- Implements: S4.11, S6.17 (decimal-parsing rule).
- Depends on: Phase 1 complete (compile dependency on `fx-api` only -- **not** `fx-core`, which is structurally forbidden per Appendix A).
- **Blocked on TI-01** (CDM schema artifact GAV, event/topic naming, payload field names for all thirteen `FxEntityType` values) -- this task cannot be completed, only scaffolded with a stub schema dependency, until TI-01 is answered. See Section 6.

### Task 3a.2 -- Reference entity mappers
`CdmReferenceMapper`, covering all eleven reference `FxEntityType` values (`CURRENCY`, `PAIR_CONVENTION`, `FIXED_FACTOR`, `FIXING_SOURCE`, `PUBLICATION_CALENDAR`, `SETTLEMENT_CALENDAR`, `ACCOUNTING_UNIT`, `FX_POLICY`, `ACCOUNTING_FX_POLICY`, `SOURCE_ENTITLEMENT`, `MANUAL_RATE_OVERRIDE`).
- Implements: S6.17.
- Depends on: Task 3a.1 (also blocked on TI-01).

### Task 3a.3 -- Fixing and snapshot mappers
`CdmFixingMapper` (-> `FixingVersion`), `CdmSnapshotMapper` (-> `MarketSnapshotPayload`, including `chunkIndex`/`chunkCount`/`completionMarker`).
- Implements: S6.17.
- Depends on: Task 3a.1 (also blocked on TI-01).

### Task 3a.4 -- Dispatcher
`CdmFxEventMapper.map(CdmFxEvent) -> FxIngestRecord`, dispatching on `entityType` to Tasks 3a.2/3a.3's mappers; malformed input yields a rejection record, never an exception.
- Implements: S6.17.
- Depends on: Tasks 3a.1-3a.3.
- Acceptance: `CdmMapperTest` covers per-entity mapping for all thirteen `FxEntityType` values, decimal-parsing correctness, and malformed-payload rejection; an ArchUnit rule confirms `fx-cdm` has zero compile-time dependency on `fx-core` (Appendix A, MC-5).

### Phase 3a acceptance gate
`CdmMapperTest` green for every mapper **once TI-01 is answered**. Until then, Phase 3a's exit state is "scaffolded and blocked," not "complete" -- this must not be silently marked done.

---

## 6. Phase 3b -- `fx-testkit`

Runs in parallel with Phase 3a; both depend only on Phases 1-2.

### Task 3b.1 -- Architecture and bytecode enforcement (promotes the Section 2.1 shift-left fixture)
`ArchitectureTest` (ArchUnit, AR-01..AR-10) and `NoFloatingPointBytecodeTest` (ASM opcode scan) as shipped, packaged tests.
- Implements: S12.5.
- Depends on: Phase 2 complete.
- Acceptance: identical rule set and opcode list to the shift-left fixture used throughout Phase 2; this task is largely a **migration and consolidation**, not new rule-writing, since the fixture should already have caught any violation. A clean pass here is the formal closure of the "shift-left" gate opened in Section 2.1. **New gap carried forward** (Section 7, new gap #6): AR-10 ("no string literal matching a tenant-id pattern") has no concrete pattern definition in the tech spec; this task must define one (and document the definition, since `fx-testkit`'s own `InMemoryTenantContextProvider` is explicitly exempted and needs a clear line to sit on the right side of).

### Task 3b.2 -- Test doubles
`InMemoryTenantContextProvider` (the one place literal tenant ids are permitted, AR-10 exempt), `InMemoryReferenceDataLoader`, `InMemoryMarketDataLoader`.
- Implements: S12.1 (`fx-testkit src/main` doubles).
- Depends on: Phase 1 (SPI interfaces), Phase 2 (nothing structural, but exercised against it).

### Task 3b.3 -- Golden fixtures and harness
`GoldenReferenceData` (currencies, calendars, sources, pairs, entities, policies, entitlements -- built directly from the tech spec's own G/C/F/X vector inputs; **no platform reference-deal convention such as `T-7788`/`TN_0042` is used here**, since this repo has no such convention and the FX tech spec defines its own vector fixtures), `GoldenSnapshots`, `VectorRunner`, `FxAssertions` (the `compareTo`-based decimal-equality helper mandated by A-07/AR-08), `DecimalReferenceTable`, `ParallelRunHarness`.
- Implements: S12.1, S10.6 (A-07 `FxAssertions`).
- Depends on: Phase 1, Phase 2.
- **Partially blocked on TI-06** for `DecimalReferenceTable`'s authoritative content (see Task 3b.4).

### Task 3b.4 -- CSV vector resources
`vectors/arithmetic-G01-G09.csv`, `vectors/calendar-C01-C05.csv`, `vectors/chain-F01-F09.csv`, `vectors/corrections-X01-X11.csv`, `vectors/policy-matrix-expectations.csv`, `decimal/ln-reference-70dp.csv`, `decimal/exp-reference-70dp.csv`.
- Implements: S12.1, Appendix D.5.
- Depends on: Phase 2 (vectors were already validated informally in Tasks 2.9-2.18; this task is the authoritative, data-as-resource codification referenced by S12.3).
- **Blocked on TI-06** for the two decimal reference CSVs: "generated offline at 80 digits and cross-checked against a second independent arbitrary-precision implementation" requires naming the tool (MPFR, mpmath, Boost.Multiprecision) and the cross-checker, which TI-06 leaves open. The G/C/F/X CSVs are not blocked -- their values are given verbatim in the tech spec body (S6.7, S6.9, S6.11, S6.14, S6.8).

### Task 3b.5 -- Golden vector test suites
`GoldenArithmeticVectorTest` (G01-G09), `CalendarVectorTest` (C01-C05), `ChainVectorTest` (F01-F09), `CorrectionEntitlementVectorTest` (X01-X11), `CalendarEdgeCaseTest` (source-vs-currency holidays, Good Friday/Easter Monday, US-only/UK-only holidays, T+1 pairs, HRK 2023-01-01, BGN 2026-01-01, 2026 DST transitions).
- Implements: S12.1.
- Depends on: Tasks 3b.3, 3b.4, Phase 2.
- Acceptance: every vector listed in S2.1 item 19 passes against the shipped `fx-testkit` harness, not just the shift-left throwaway harness from Phase 2 -- this is a real re-run, not a rubber stamp, because the shipped `GoldenReferenceData`/`VectorRunner` are new production code paths of their own.

### Task 3b.6 -- Property-based and decimal conformance tests
`PropertyBasedTest` (`jqwik`, full S12.2 table), `DecimalMathConformanceTest` (Appendix D.5 full 2,000-point reference table, constant self-checks, algebraic identities, frozen SHA-256 digest).
- Implements: S12.2, Appendix D.5.
- Depends on: Task 3b.4 (blocked on TI-06 for the authoritative reference CSVs -- this task can run against the Task 2.1 provisional 50-point set in the interim, clearly labelled as such, but the frozen golden digest described in Appendix D.5 item 4 **cannot be finalised** until the authoritative 2,000-point table lands).
- **New gap carried forward** (Section 7, new gap #4): OQ-T01 recommends "a PDR-conformance test fed by a PDR-published fixture set" but no such test class appears in S12.1's table. This task should add one if a PDR-published fixture set is available by this point; if not, this is logged as an unmet recommendation, not silently dropped.

### Task 3b.7 -- Policy matrix exhaustion
`PolicyMatrixExhaustionTest`, generated from `PolicyMatrix`'s exported read-only view (Task 2.6) against `policy-matrix-expectations.csv` (Task 3b.4).
- Implements: S12.3.
- Depends on: Tasks 2.6, 3b.4.
- Acceptance: every cell of the rule x leg x purpose x itemType x amountType cross-product resolves to either a successful `GoldenReferenceData` resolution or the exact expected `FxErrorCode`; a disagreement between the CSV and the matrix fails the build (this is the generated-test mechanism S12.3 specifies, not a hand-written enumeration).

### Task 3b.8 -- Cross-JVM determinism
`JvmMatrixDeterminismTest` (vector X10: G05 and the frozen decimal digest across the CI JVM matrix).
- Implements: S12.1, S10.5.
- Depends on: Task 3b.6 (frozen digest must exist), Task 2.11 (G05).
- **Blocked on TI-07** (which JVM vendors/versions CI must run -- proposal: Temurin 21+25, Zulu 21, GraalVM 21, OpenJ9 21 -- and whether a vendor-specific failure blocks release). Scaffold the test to run against whatever matrix is available locally (at minimum two vendors, carried over from Task 2.1's informal check) and mark the full gate open until TI-07 is answered.

### Phase 3b acceptance gate
All of S12.1's `fx-testkit` test classes exist and pass, **except**: `DecimalMathConformanceTest`'s fully-authoritative 2,000-point form (blocked TI-06) and `JvmMatrixDeterminismTest`'s full vendor matrix (blocked TI-07). Both blockers are tracked, not silently waived -- a release gate must explicitly accept or close them before v1.0 ships.

---

## 7. Phase 4 -- `fx-guice`

### Task 4.1 -- `FxModule`
The `AbstractModule` of S9.1, verbatim: bindings for `ReferenceStore`/`FixingStore`/`MarketSnapshotStore` to their `InMemory*` implementations, all ten internal-port-to-default-implementation bindings (`DateRuleResolver`, `PairResolver`, `RateSelector`, `FallbackChainRunner`, `ForwardCurveCache`, `AveragingEngine`, `ChainEngine`, `EntitlementResolver`, `PrecisionEngine`, `LineageBuilder`, `DecimalTranscendentals -> FxMath`), all in `Singleton` scope; `FxIngestor`/`FxHealth` bindings; three `requireBinding` calls for the host-supplied SPIs; `OptionalBinder` defaults for `FxEventListener`/`FxMetrics` to their `noop()` singletons; the `@Provides @Singleton FxConverter` method that conditionally wraps `DefaultFxConverter` in `MeteredFxConverter`.
- Implements: S9.1.
- Depends on: Phase 2 complete (every bound class must exist).
- **Blocked / informed by TI-02**: if `jakarta.inject` on `fx-core` constructors is rejected, every binding in this module must become a `@Provides` method with explicit constructor argument wiring instead of relying on `@Inject`-annotated constructors -- a materially larger task. This plan assumes TI-02's A-04 precedent (provided-scope `jakarta.inject`, matching the UOM reactor) holds, consistent with the tech spec's own assumption, but flags the fallback cost here rather than silently assuming no fallback is needed.

### Task 4.2 -- `MeteredFxConverter` decorator (A-11)
The **only** class in the entire reactor permitted to call `System.nanoTime()`.
- Implements: S9.1, A-11, Pattern #13 Decorator.
- Depends on: Task 4.1 (wraps `DefaultFxConverter`), Phase 1 Task 1.14 (`FxMetrics.resolutionLatency`).
- Acceptance: wraps every `FxConverter` method, timing each and reporting via `FxMetrics.resolutionLatency(tenantId, purpose, nanos)`; AR-04's "no clock" rule is scoped to exclude this class by design, verified by `ArchitectureTest` (Task 3b.1) still passing with this class present (the rule's exclusion list, not a blanket pass).

### Task 4.3 -- `FxLifecycle` (reconciliation/hot-window scheduling, TI-04)
A `Closeable` start/stop lifecycle hook around a `ScheduledExecutorService` driving `FxConfig.reconciliationInterval`-cadence calls to `ReferenceDataLoader.loadChangesSince`/`MarketDataLoader.loadChangesSince` and `HotWindowPolicy` pruning (Task 2.4).
- Implements: S8.4, S9.1 (component inventory lists `FxLifecycle.java` under `fx-guice`).
- Depends on: Tasks 2.4, 2.17 (ingestion/reconciliation logic), 4.1.
- **Blocked / informed by TI-04**: whether the library owns this `ScheduledExecutorService` at all, versus requiring a host-supplied scheduler SPI, is unresolved. Build to the tech spec's stated default (library-owned, `Closeable`-bound) but flag that a TI-04 reversal would relocate scheduling ownership out of `fx-guice` entirely -- document this as a design reviewer checkpoint before release, not a silent assumption.

### Task 4.4 -- `WiringTest`
Guice 7 injector test: `FxModule` installs cleanly; the three required SPI bindings are enforced by `requireBinding` (installing the module without them must fail injector creation); `FxEventListener`/`FxMetrics` optional defaults resolve to their `noop()` singletons when unbound; **the full golden vector suite (G/C/F/X) passes through the Guice-injected `FxConverter`**, not just the hand-wired `DefaultFxConverter` from Phase 2; the metering decorator is present when `cfg.meteringEnabled()` and bypassed (plain `DefaultFxConverter` returned) otherwise.
- Implements: S12.1 (`fx-guice/WiringTest`).
- Depends on: Tasks 4.1-4.3, Phase 3b (for `VectorRunner`/`GoldenReferenceData`/the vector CSVs).
- Acceptance: this is the **v1.0 end-to-end exit gate**. Every vector passes through the real DI-wired stack exactly as a host would consume it; this is also where the single-convert p99 <= 20 us target (S10a.1) must be measured end-to-end for the first time with the metering decorator active, since that decorator is the only legal place in the reactor to take the measurement (A-11).

### Phase 4 acceptance gate / v1.0 exit criteria
- `WiringTest` green, including the full G/C/F/X vector suite through the Guice-wired `FxConverter`.
- All of Phase 2's acceptance gate items still green (no regression from wiring).
- `ArchitectureTest`/`NoFloatingPointBytecodeTest` (Task 3b.1) green against `fx-api`+`fx-core`, with `fx-guice` correctly excluded from AR-04's clock rule only for `MeteredFxConverter`.
- `CdmMapperTest` green **if** TI-01 has been answered by this point; otherwise `fx-cdm` ships as a documented-blocked module, not silently as "done."
- Every item in Section 8 below is either closed, explicitly risk-accepted by solutions-architect, or still open and tracked -- none is silently resolved by this plan or by the implementation it describes.

---

## 8. Carried-Forward Open Items

Per instruction, none of the tech spec's open items are resolved here. Each is mapped to the phase(s) it blocks or shapes.

### 8.1 Functional-spec open questions (OQ-01..OQ-11)

| ID | Question | Phase(s) affected | How it shapes the plan |
|----|----------|--------------------|---------------------------|
| OQ-01 | Firm-wide default source for ACCT_TXN/MGMT_VIEW legs | Phase 2 Task 2.6 | `PolicyMatrix` defaults table leaves these cells empty by design (`FX_V_INVALID_POLICY` on absence); Phase 3b Task 3b.3's `GoldenReferenceData` must supply an explicit `rateSourcePriority` for every test policy since no library default exists to fall back on. |
| OQ-02 | AVERAGE_RATE vs actual transaction rates for P&L recognition | Phase 2 Tasks 2.6, 2.14 | ACCOUNTING_RECOGNITION's date rule is intentionally un-defaulted in `PolicyMatrix`; `AccountingFxPolicy` (Phase 1 Task 1.5) carries the per-unit choice once made. |
| OQ-03 | Which averaging modes (PRICE_MATCHED/RATE_AVERAGE/NONE+PAYMENT_DATE) carry production volume | Phase 2 Tasks 2.1, 2.11; Phase 3b Task 3b.6 | No design impact (all three paths are built regardless), but determines where JMH benchmark effort and property-test depth should concentrate once production volume data is available. |
| OQ-04 | Discount curve ownership: OIS vs firm funding curves, source | Phase 1 Task 1.6; Phase 2 Task 2.11 | `DiscountCurvePayload`/`DiscountPillar` (Phase 1) admit only a discount-factor representation; `CipForwardCalculator` (Phase 2) is specified for discount factors only, with `FX_E_DATA_NOT_LOADED` for an unloaded curve. A zero-rate variant would need a schema addition, not made here. |
| OQ-05 | Onshore/offshore representation: separate currency codes (CNY/CNH) vs source-level distinction | Phase 1 Task 1.5; Phase 2 Task 2.9; Phase 3b Task 3b.3 | Both representations are structurally supported; this plan does not choose. If separate codes are chosen, `FixedFactorNormaliser` (Task 2.9) must never see a `FixedFactor` defined between them (they are not convertible at par) -- a reference-data governance rule, not a code change. `GoldenReferenceData` must pick one representation to exercise, without presuming the production answer. |
| OQ-06 | Default `spotAdjustment` for MTM: `TO_VALUATION_DATE` (proposed) vs `NONE` | Phase 2 Tasks 2.6, 2.11 | This spec adopts `TO_VALUATION_DATE`, making `ShortEndAdjuster` a required part of every MTM snapshot; linked directly to OQ-T06 below. |
| OQ-07 | Maximum fallback staleness before EOD sign-off is blocked | Phase 1 Task 1.12; Phase 2 Task 2.10 | `FxConfig.maxFallbackStalenessDays` (default 3) exists as a placeholder; `PreviousPublicationDayStep` records staleness in `PathStep` details but the library does not block sign-off -- that remains a host decision with no library-side enforcement built. |
| OQ-08 | Fixing hot-window length and retained-snapshot count | Phase 1 Task 1.12; Phase 2 Tasks 2.3, 2.4 | Directly drives the largest unbounded memory term in the design (60-90 MB per 1,000 fixing series, R3). Defaults (`fixingHotWindowYears=3`, `retainedSnapshotsPerTenant=8`) are built in, but the cache-design tasks (2.3, 2.4) cannot be sized with confidence until this is answered operationally. |
| OQ-09 | Ownership/approval of FX policies, calendars, entitlements | No phase impact (governance/runbook only) | All such data arrives as four-eyes-approved reference data regardless of who owns the approval workflow; determines who is paged on `FX_I_APPROVAL_INVALID`, not what is built. |
| OQ-10 | Is `revalue` sufficient for the ERP feed, or does it need GL account hints | Phase 2 Task 2.14 (informational only) | `RevaluationResult` is built exactly to FS S9.3; this plan does not add GL-account-hint fields, consistent with the tech spec's own recommendation to resist that addition. |
| OQ-11 | Line-category batch translation | Phase 2 Task 2.14 | Explicitly **not** designed or built; `convertChain` remains one-amount-per-call. Confirmed as a scope boundary in Task 2.14's acceptance criteria. |

### 8.2 New technical open questions raised by the tech spec (OQ-T01..OQ-T08)

| ID | Question | Phase(s) affected | How it shapes the plan |
|----|----------|--------------------|---------------------------|
| OQ-T01 | Own PDR projection (A-08) vs shared `pdr-api` dependency | Phase 1 Task 1.7; Phase 3b Task 3b.6 | This plan keeps the projection (per the tech spec's own recommendation) and schedules a PDR-conformance test in Phase 3b if a PDR-published fixture set becomes available -- flagged as an unmet recommendation otherwise (see Section 7 new gap #4; no such test is named in S12.1). |
| OQ-T02 | Reuse `FX_E_DATA_NOT_LOADED` for calendar-coverage failures, or mint a new code | Phase 2 Task 2.7 | A-14's reuse is accepted for v1.0, implemented as-is in `CalendarIndex` coverage checks; an FS amendment would be required to change this, which this plan does not make. |
| OQ-T03 | First-ever-seen fixing key backfilled below an already-pinned knowledge cut | Phase 2 Tasks 2.3, 2.17 | The residual determinism gap (S10.5) is built with no additional guard beyond `FX_I_FIXING_SEQUENCE`'s within-key regression check; none of the three options (global `recordedAt` floor, accept-and-rely-on-source-discipline, content-digest-in-lineage) is chosen by this plan -- a platform decision is required before Task 2.3/2.17 can be considered final rather than provisional. |
| OQ-T04 | Embedding the full inline policy in `Lineage.replayKey` | Phase 1 Task 1.10; Phase 2 Task 2.15 | This plan embeds the full policy verbatim (the tech spec's own recommendation), accepting the row-size cost; a digest-only alternative is not implemented. |
| OQ-T05 | Request-snapshot vs facade-snapshot disagreement | Phase 2 Task 2.18 | Silent precedence to the request's snapshot field is implemented exactly as specified; no new error or warning code is minted. Documented as a caller trap in `DefaultFxConverter`'s Javadoc. |
| OQ-T06 | ON/TN points absent under `TO_VALUATION_DATE` | Phase 2 Task 2.11 | Falls back to spot-unadjusted silently, per the tech spec's stated (if explicitly flagged-as-silent) behaviour; linked to OQ-06 above and must be revisited together. |
| OQ-T07 | Boundary between the platform's `NumericPrecision` port and this library's precision system | Out of scope for this plan -- affects only a **future, not-yet-specified** `valuation-guice` integration adapter (S2.2) | No phase in this plan is directly affected; `toAmountUnrounded` is already the correct handoff value per the tech spec's own intended answer, but this cannot be finally confirmed until that future adapter is itself specified. |
| OQ-T08 | Eager vs lazy forward-curve construction at snapshot assembly | Phase 2 Tasks 2.4, 2.11 | Interacts directly with R1 (p99 20 us risk) and the <= 2 s post-completion-marker NFR. This plan builds the lazy (`computeIfAbsent`) default per S6.9.3, consistent with the body text, but see Section 7 new gap #3: the config flag S10a.2/OQ-T08 both assert "exists" is **not** present in `FxConfig`'s S4.12 field list, and this plan does not add it unilaterally. |

### 8.3 Technical inputs needed (TI-01..TI-08)

| ID | Item needed | Phase(s) affected | Blocking severity |
|----|-------------|--------------------|---------------------|
| TI-01 | CDM schema artifact GAV + payload field names for all 13 `FxEntityType` values | Phase 3a (entirely) | **Hard blocker** -- `fx-cdm` cannot compile without it. |
| TI-02 | Confirm `jakarta.inject` provided-scope acceptable in `fx-core` (A-04) | Phase 2 (all `@Inject`-annotated constructors); Phase 4 Task 4.1 | **Soft blocker** -- if rejected, Task 4.1's bindings must become `@Provides` methods throughout, a materially larger wiring task. Assumed accepted (UOM precedent) for this plan's task sizing. |
| TI-03 | Confirm GLOBAL-catalogue-by-reference (not by copy) model | Phase 2 Task 2.2 | **Assumption, not blocker** -- this plan builds option (a) per the tech spec's own stated assumption; a reversal would require rework of `ReferenceCatalogue`'s tenant/GLOBAL relationship. |
| TI-04 | Reconciliation/hot-window scheduler ownership: library-owned vs host-supplied SPI | Phase 2 Tasks 2.3, 2.4; Phase 4 Task 4.3 | **Soft blocker** -- built to the library-owned default; a reversal relocates scheduling out of `fx-guice` into a new host-facing SPI, a design-level change requiring review before release. |
| TI-05 | Library version injection mechanism (resource filtering vs generated class) | Phase 1 Task 1.16; Phase 2 Task 2.15 | **Hard blocker for determinism guarantees** -- `inputsHash` reproducibility (vector X04) depends on `FxVersion.VALUE` being stable across a local build and a CI build of the same commit; mechanism must be chosen before Task 2.15 can be considered final. |
| TI-06 | Decimal reference-table provenance/tooling and independent cross-check | Phase 2 Task 2.1 (provisional set only); Phase 3b Tasks 3b.4, 3b.6 | **Hard blocker for the authoritative accuracy claim** -- Appendix D's 1e-33 accuracy contract is unverified without it. This plan proceeds with a provisional, clearly-labelled 50-point set through Phase 2 and flags the full gate as open until TI-06 resolves. |
| TI-07 | JVM vendor/version matrix for the determinism gate (X10) | Phase 3b Task 3b.8 | **Hard blocker for the formal cross-JVM gate** -- scaffolded against at least two vendors informally from Task 2.1 onward, but the release-blocking matrix itself is undefined without TI-07. |
| TI-08 | Pair-level source provenance missing from `MarketSnapshotPayload` | Phase 1 Task 1.6 (schema); Phase 2 Task 2.8 (forward/CIP propagation rows, closed in Task 2.11) | **Functional-spec gap, not a design choice** (the tech spec's own words). `RestrictionPropagator`'s forward/CIP rows can only use a single snapshot-level source-rights approximation until this is resolved upstream; this is the example the architect named explicitly and this plan does not silently work around it with an invented per-pair field. |

---

## 9. Newly Identified Gaps and Risks (not present in the tech spec)

These were found while sequencing the build and are flagged for solutions-architect / code-reviewer attention; none is resolved by this plan.

1. **`Lineage` self-hashing layering tension.** `Lineage` (Phase 1 Task 1.10) is a final class in `fx-api`, but `CanonicalJson`/`InputsHasher` (the components that actually build the canonical form and compute the hash) are declared as `fx-core` components (S6.1, package `core.lineage`). Since `fx-api` cannot depend on `fx-core` (Appendix A dependency direction is strictly `fx-core -> fx-api`), `Lineage` cannot call them directly. The tech spec does not state how the lazily-memoised `inputsHash()` field is populated without this dependency. This plan's working assumption -- `Lineage`'s constructor accepts a pre-built canonical-form string or supplier from `fx-core`'s `LineageBuilder`, and `Lineage` itself owns only the final `MessageDigest.getInstance("SHA-256")` step -- is a reasonable resolution but is an **implementation decision this plan is making on the architect's behalf**, flagged here rather than silently baked into Tasks 1.10/2.15 as if it were spec-mandated.

2. **`FxConfig` default-value contradiction between S4.12 and S10a.3.** S4.12 states `forwardMemoMaxEntriesPerCurve` defaults to `4_096` and `memoMaxEntriesPerSnapshot` defaults to `50_000`. S10a.3's memory-budget analysis explicitly revises both downward ("**This spec therefore sets the default to 512**" and "default lowered to 20,000 with the same reasoning") because the S4.12 literals breach the <= 50 MB per-snapshot budget. The document never updates S4.12's own field-default text to match. Phase 1 Task 1.12 follows S10a.3 (the corrective, binding analysis) rather than S4.12's stale literal, but this is a direct internal contradiction in the source document that a future reader of S4.12 alone would miss.

3. **OQ-T08 and S10a.2 both assert a config flag exists for eager-vs-lazy forward-curve construction, but `FxConfig`'s S4.12 field list has no such field.** Quote, S10a.2: "eager build is a config option." Quote, OQ-T08: "A config flag exists; the default needs an operational decision." No `eagerCurveBuild`-shaped field appears among `FxConfig`'s fourteen listed fields in S4.12. This plan does **not** invent the field (Phase 1 Task 1.12 explicitly declines to), and flags the absence for solutions-architect to either add via an approved spec amendment or clarify that the "config option" language was aspirational rather than literal.

4. **No S12.1 test class exists for OQ-T01's own recommendation.** OQ-T01 recommends "a PDR-conformance test fed by a PDR-published fixture set" to catch drift between `fx-api`'s own `PricingDaySet` projection (A-08) and the real PDR library's schema. S12.1's exhaustive test-class table (which names every other test class precisely) has no entry for this. Phase 3b Task 3b.6 schedules it conditionally, but it is worth solutions-architect confirming whether this was an intentional omission (e.g., PDR-conformance belongs to a cross-library integration suite outside this reactor) or a documentation gap.

5. **`PairResolver.route(...)`'s signature does not carry a `FilteredSources` parameter, despite S6.6 and S6.7 requiring that pair resolution only ever see entitled sources.** S5.4 lists the internal port as `PairRoute route(CurrencyCode from, CurrencyCode to, ResolvedPolicy p, PinnedState s, LocalDate fxDate)`. S6.6 point 4 states "only the filtered list is threaded past stage 8, so no downstream stage can reach an unentitled source," and S6.7 step 6 (`DirectQuoteStep`) draws "from the entitled source list." Appendix C's pseudocode computes `sources = entitlementResolver.filter(...)` before calling `pairResolver.route(...)` but never shows `sources` being passed into that call. Two internally-consistent resolutions exist (narrow `ResolvedPolicy.rateSourcePriority` before the call; or have `DirectQuoteStep` call `EntitlementResolver` itself via `PinnedState`), and S5.4 grants the implementer latitude here ("documented for design completeness... the implementation engineer must preserve the seams"). This plan flags the choice as a required, explicit decision at the Phase 2 Task 2.8/2.9 boundary rather than letting it be resolved implicitly by whichever engineer happens to write the code first -- because `fx-testkit`'s stage-level tests bind to these internal seams and a later change would be a breaking refactor.

6. **AR-10's "tenant-id pattern" has no concrete definition.** S12.5 states: "No string literal matching a tenant-id pattern in `fx-api`/`fx-core` main sources." No regex, naming convention, or example is given anywhere in the document for what counts as a "tenant-id pattern" (contrast with AR-09, which is precise: no enum constant literally equal to `FIXED`). Phase 3b Task 3b.1 must define this pattern concretely before `ArchitectureTest` can mechanically enforce it, and that definition is this plan's (or the implementer's) choice, not the tech spec's.

7. **The single-conversion pipeline has a build-order dependency on the averaging package that is easy to miss.** Even a plain `ConversionRequest` (not a `SeriesRequest`) passes through stage 7 (`ObservationSetBuilder`, "NONE => exactly one observation") per S6.2's binding pipeline order. This means `DefaultFxConverter`/`ConversionPipeline` (Phase 2 Task 2.18) has a real compile-and-correctness dependency on `core.averaging` (Task 2.12) even for the simplest single-rate conversion, not only for `SeriesRequest`s as the package name might suggest. This plan's task ordering (2.12 before 2.14/2.18) already accounts for this, but it is called out explicitly so an implementer does not attempt to shortcut a "simple" conversion path around the averaging package and inadvertently create the exact bypass branch D-07 forbids.

---

## 10. Traceability Summary

- **D-01..D-16** (FX functional spec decisions): every decision is traced to a specific task above via its implementing component; none is reopened or reinterpreted by this plan (matching S13.1's "Compatible" verdicts).
- **A-01..A-18** (tech-spec assumptions): followed as written; where an assumption is itself internally contradicted by a later section (A-07 defaults vs S10a.3, see Section 9 item 2), the later, corrective section is treated as binding and the contradiction is flagged rather than silently picked.
- **AR-01..AR-10** (architecture rules): enforced continuously from Phase 2 Task 2.1 onward via the shift-left practice (Section 2.1), formally shipped in Phase 3b Task 3b.1.
- **Golden vectors**: G01-G09 close out across Phase 2 Tasks 2.9, 2.11, 2.12; C01-C05 close out in Phase 2 Task 2.7; F01-F09 close out in Phase 2 Task 2.14; X01-X11 close out across Phase 2 Tasks 2.10, 2.12, 2.17. All are re-verified end-to-end in Phase 2 Task 2.18 and again through the Guice-wired stack in Phase 4 Task 4.4.
- **Open items**: all 11 `OQ-*`, all 8 `OQ-T*`, and all 8 `TI-*` items are carried forward in Section 8, each mapped to the phase(s) it shapes or blocks. None is resolved by this plan.
- **New findings**: 7 gaps/risks identified during planning are recorded in Section 9, each clearly marked as new and not attributable to the tech spec.

*End of Implementation Plan.*
