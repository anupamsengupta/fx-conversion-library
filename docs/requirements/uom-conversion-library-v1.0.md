# Technical Specification -- UOM Conversion Library v1.0

## S1 -- Metadata & Status

| Field | Value |
|-------|-------|
| Author | solutions-architect |
| Status | DRAFT |
| Version | 1.0 |
| Date | 2026-10-02 |
| Depends On | UOM Conversion Library Functional Spec v2.0, ADR-0010 (CalendarProvider and UnitConversionProvider SPIs), ADR-0001-2 (Pattern Catalog), CLAUDE.md (platform conventions) |
| Layer | Separate Maven reactor (`uom-conversion`). Library-scope -- no framework, no I/O on resolution path. Integration with `valuation-engine` via an adapter in `valuation-guice`. |
| Subsystems Touched | None directly (this is a standalone library). Valuation Engine integration touches the `UnitConversionProvider` SPI surface only. |
| Functional Spec Decisions | D-01 through D-20 (all normative, all implemented) |
| Open Questions | OQ-01 through OQ-10 (all surfaced, none resolved) |

---

## S2 -- Scope & Non-Scope

### 2.1 In Scope

1. **Separate Maven reactor** `uom-conversion` with five modules: `uom-api`, `uom-core`, `uom-cdm`, `uom-testkit`, `uom-guice`.
2. **Conversion graph engine** -- nodes as `(Dimension, QualifierTuple)`, edges as unit/commodity-defined/qualifier/bridge, path search with hop limits, deterministic path selection per FS v2.0 S13.
3. **Bitemporal catalogue store** -- in-memory, per-tenant + shared GLOBAL, copy-on-write snapshots, knowledge-time pinning.
4. **Reference data model** -- all value objects per FS v2.0 S5: dimensions, units, rate units, reference conditions, calorific references, material hierarchy, material properties, precision policies, common version envelope.
5. **Qualifier system** -- six orthogonal qualifiers replacing the overloaded `basis` field (FS v2.0 S10, D-11).
6. **Property normalisation** -- API gravity to SG to density, lb-based to kg, percentage conversions (FS v2.0 S11.3).
7. **Transaction override system** -- contract factors normalised to graph edge values (D-07), measured properties at transaction precedence (FS v2.0 S14).
8. **Public API** -- `UomConverter`, `UomSnapshot`, `UomIngestor`, request/result value types, SPIs (`TenantContextProvider`, `ReferenceDataLoader`, `VolumeCorrectionProvider`, `IngestListener`, `UomMetrics`).
9. **Ingestion pipeline** -- CDM event processing, idempotent on `versionId`, sequence-ordered with gap detection/repair, validation per FS v2.0 S17, atomic snapshot swap.
10. **Resolution memo** -- bounded LRU, keyed by `(generation, knowledgePin, requestFingerprint, valuationDate)`.
11. **Error code system** -- `UOM_E_*`, `UOM_W_*`, `UOM_I_*` as enums with structured error/warning payloads.
12. **Precision** -- factors in `MathContext.DECIMAL128`, single final rounding per precision policy cascade.
13. **Tenant isolation** -- `TenantContextProvider` SPI, no fallback to default, all caches tenant-keyed.
14. **Testkit** -- conformance vectors (G01-G15, B01-B12), property-based tests, architecture tests.
15. **Valuation Engine integration adapter** -- `UomBackedUnitConversionProvider` in `valuation-guice`.

### 2.2 Defers To

| Item | Owner |
|------|-------|
| Reference data capture, four-eyes workflow, persistence | Source reference data system |
| Time/calendar conversions (MW to MWh, DST, profiles) | Calendar service / caller |
| Quality/spec adjustment to price (spec-miss premiums) | Pricing / quality module |
| ASTM D1250 / API MPMS 11.1 volume correction computation | `VolumeCorrectionProvider` implementor |
| FX conversion | FX library |
| Network API / standalone deployment | Not provided |
| CDM event transport (Kafka consumer, SQS, etc.) | Host service |
| Production hosting layer for the valuation engine | Separate future deliverable |
| Persisting conversion results | Calling service |

---

## S3 -- Assumptions & Gaps

| # | Assumption / Gap | Impact |
|---|------------------|--------|
| A-01 | Reference data per tenant is small (thousands of records, not millions). Full version history fits in memory. | Drives copy-on-write snapshot design. |
| A-02 | The CDM schema artifact exists or will be created separately. `uom-cdm` depends on it. | `uom-cdm` cannot compile without the CDM artifact. |
| A-03 | GLOBAL catalogue releases are distributed by the source system. This spec does not design the distribution mechanism. | See OQ-10. |
| A-04 | `jakarta.inject` (`@Inject`, `@Named`) is permitted in `uom-core` as a provided-scope dependency, consistent with platform convention for `valuation-domain`. | If rejected, constructor injection must be manual (no annotation). |
| A-05 | Java 21 is the minimum JDK. `ScopedValue` (preview in 21, finalised in 25) may be used by hosts for `TenantContextProvider`. The library itself does not depend on `ScopedValue`. | The SPI contract returns `Optional<String>`, implementation is host's choice. |
| A-06 | The library version string is available at compile time via a generated constant (Maven resource filtering or similar). | Stamped into `FactorResult.lineage.libraryVersion`. |

---

## S4 -- Domain Model Additions

All types are Java 21 `record`s with validated compact constructors (Pattern #1 VO from ADR-0001-2). Sealed hierarchies use Pattern #2. Enums with behaviour use Pattern #3.

### 4.1 Core Enumerations

```
// uom-api
enum Dimension { MASS, VOLUME, ENERGY }
enum DefinitionType { STANDARD, COMMODITY_DEFINED }
enum ReferenceConditionPolicy { NONE, REQUIRED, DEFAULT }  // DEFAULT carries a conditionCode
enum Scope { GLOBAL, TENANT }
enum VersionStatus { APPROVED, RETIRED }
enum PropertyType { DENSITY, API_GRAVITY, SPECIFIC_GRAVITY, CV_MASS, CV_VOLUME,
    GCV_NCV_RATIO, VOLUME_CORRECTION, BUSHEL_WEIGHT, BALE_WEIGHT,
    MOISTURE_PCT, ASSAY_PCT, SW_PCT }
enum EnergyBasis { GROSS, NET }
enum MoistureBasis { WET, DRY }
enum MassContent { GROSS, CONTAINED }  // CONTAINED carries element code
enum VolumeType { GSV, NSV }
enum PrecisionDomain { QUANTITY, PRICE, INTERMEDIATE, AMOUNT }
enum ConversionStage { FINAL, INTERMEDIATE }
enum TenantHealthStatus { NOT_LOADED, LOADING, READY, STALE }
enum EdgeType { UNIT, COMMODITY_UNIT, QUALIFIER, BRIDGE }
enum EdgeSource { CATALOGUE, REFERENCE, MEASURED, CONTRACT }
```

### 4.2 Error/Warning/Info Code Enums

```
// uom-api
enum UomErrorCode {
    UOM_E_NO_TENANT_CONTEXT, UOM_E_TENANT_NOT_READY, UOM_E_TENANT_MISMATCH,
    UOM_E_UNKNOWN_UNIT, UOM_E_UNSUPPORTED_CONVERSION,
    UOM_E_RATE_DENOMINATOR_MISMATCH, UOM_E_MATERIAL_CONTEXT_REQUIRED,
    UOM_E_PROPERTY_NOT_FOUND, UOM_E_QUALIFIER_MISMATCH,
    UOM_E_AMBIGUOUS_PATH, UOM_E_PATH_TOO_LONG, UOM_E_INVALID_OVERRIDE,
    UOM_E_KNOWLEDGE_PIN_UNAVAILABLE
}
enum UomWarningCode {
    UOM_W_CLASS_DEFAULT_USED, UOM_W_PROPERTY_SHADOWED_BY_CONTRACT,
    UOM_W_EQUIVALENT_PATHS, UOM_W_STALE_KEY
}
enum UomIngestCode {
    UOM_I_APPROVAL_INVALID, UOM_I_IMMUTABLE_VIOLATION, UOM_I_SCOPE_VIOLATION,
    UOM_I_INVALID_FACTOR, UOM_I_OVERLAP, UOM_I_UNKNOWN_REFERENCE,
    UOM_I_SEQUENCE_GAP, UOM_I_PLAUSIBILITY
}
```

### 4.3 Version Envelope (common to all entities)

```
// uom-api
record VersionEnvelope(
    Scope scope,
    String tenantId,          // null iff scope = GLOBAL
    String naturalKey,
    String versionId,         // globally unique, source-assigned
    LocalDate validFrom,      // inclusive
    LocalDate validTo,        // exclusive, null = open
    Instant recordedAt,       // knowledge time = approval instant
    VersionStatus status,
    String authoredBy,
    String approvedBy,        // MUST differ from authoredBy
    Instant approvedAt,       // MUST equal recordedAt
    String sourceSystem,
    String correctionOf,      // versionId of corrected version, nullable
    String reasonCode,        // mandatory when correctionOf is set
    String catalogueRelease   // GLOBAL only, nullable
)
```

Compact constructor enforces: `approvedBy != null`, `!approvedBy.equals(authoredBy)`, `approvedAt.equals(recordedAt)`, `correctionOf != null => reasonCode != null`, `scope == GLOBAL => tenantId == null`, `scope == TENANT => tenantId != null`.

### 4.4 Reference Data Value Objects

```
// uom-api
record UnitDefinition(
    VersionEnvelope envelope,
    String unitCode,            // naturalKey
    Dimension dimension,
    DefinitionType definitionType,
    String factorToBase,        // decimal string, exact. Required for STANDARD. null for COMMODITY_DEFINED
    String definingProperty,    // e.g. BUSHEL_WEIGHT. Required for COMMODITY_DEFINED
    ReferenceConditionPolicy referenceConditionPolicy,
    String defaultConditionCode // non-null only when policy = DEFAULT
)

record RateUnitDefinition(
    VersionEnvelope envelope,
    String rateUnitCode,        // naturalKey
    String numeratorUnit,       // e.g. BBL
    String denominatorTimeUnit, // e.g. D, H
    String numeratorMultiplier  // decimal string, e.g. "1000" for KBD
)

record ReferenceCondition(
    VersionEnvelope envelope,
    String code,                // naturalKey
    String temperature,         // e.g. "15 degC", "60 degF"
    String pressure             // e.g. "101.325 kPa"
)

record CalorificReference(
    VersionEnvelope envelope,
    String code,                // naturalKey
    String combustionTemperature,
    String meteringCondition    // a reference condition code
)

record CommodityClassDef(
    VersionEnvelope envelope,
    String classCode,           // naturalKey
    String name,
    boolean volumeTemperatureSensitive
)

record CommodityDef(
    VersionEnvelope envelope,
    String commodityCode,       // naturalKey
    String name,
    String classCode            // parent
)

record GradeDef(
    VersionEnvelope envelope,
    String gradeCode,           // naturalKey
    String name,
    String commodityCode        // parent
)

record LocationDef(
    VersionEnvelope envelope,
    String locationCode,        // naturalKey
    String name
)

record MaterialProperty(
    VersionEnvelope envelope,
    PropertyType propertyType,
    String value,               // decimal string
    String valueUnit,           // unit expression, e.g. "kg/m3"
    QualifierTuple qualifiers,
    String commodityClass,      // specificity coordinate, nullable
    String commodity,           // specificity coordinate, nullable
    String grade,               // specificity coordinate, nullable
    String location,            // specificity coordinate, nullable
    boolean allowClassDefault,  // meaningful only at class level
    String element,             // for ASSAY_PCT
    String provenance           // assay ID, exchange rulebook, etc.
)

record PrecisionPolicy(
    VersionEnvelope envelope,
    PrecisionDomain domain,
    String commodity,           // nullable
    String unitCode,            // nullable
    int scale,
    String roundingMode         // HALF_UP (default) or HALF_EVEN
)
```

### 4.5 Qualifier System

```
// uom-api
record QualifierTuple(
    String referenceCondition,      // code from S5.5, nullable
    EnergyBasis energyBasis,        // nullable
    String calorificReference,      // code from S5.6, nullable
    MoistureBasis moistureBasis,    // nullable
    MassContentSpec massContent,    // nullable
    VolumeType volumeType           // nullable
)

// Sealed hierarchy for mass content (Pattern #2)
sealed interface MassContentSpec {
    record Gross() implements MassContentSpec {}
    record Contained(String element) implements MassContentSpec {}
}
```

`QualifierTuple` is a value object (Pattern #1). All fields nullable. Two tuples are equal by structural equality. A `QualifierTuple.EMPTY` constant has all fields null.

### 4.6 Material Context

```
// uom-api
record MaterialContext(
    String commodityClass,
    String commodity,       // nullable
    String grade,           // nullable
    String location         // nullable
)
```

### 4.7 Transaction Overrides

```
// uom-api
record ContractFactor(
    String fromUnit,
    QualifierTuple fromQualifiers,
    String toUnit,
    QualifierTuple toQualifiers,
    String factor,          // decimal string
    String reference        // contract or clause ID
)

record MeasuredProperty(
    PropertyType propertyType,
    String value,           // decimal string
    String valueUnit,
    QualifierTuple qualifiers,
    String element,         // for ASSAY_PCT, nullable
    String reference        // document ID
)
```

### 4.8 Request/Result Value Objects

```
// uom-api
record FactorRequest(
    String fromUnit,
    String toUnit,
    QualifierTuple fromQualifiers,  // nullable, defaults to EMPTY
    QualifierTuple toQualifiers,    // nullable, defaults to EMPTY
    LocalDate valuationDate,
    MaterialContext material,       // conditionally required
    List<ContractFactor> contractFactors,
    List<MeasuredProperty> measuredProperties
)

record FactorResult(
    String quantityFactor,          // full precision decimal string
    String priceFactor,             // full precision decimal string
    List<PathEdge> path,
    Lineage lineage,
    List<UomWarning> warnings,
    UomError error                  // present instead of factors on failure
) {
    boolean isSuccess();
    FactorResult orThrow();         // throws UomException if error present
}

record PathEdge(
    EdgeType edgeType,
    String from, String to,
    String value,                   // decimal string
    EdgeSource source,
    int specificityRank,
    String versionId,               // nullable (not for CONTRACT/MEASURED)
    Instant recordedAt,             // nullable
    String reference                // contract ID or document ID, nullable
)

record Lineage(
    String tenant,
    long generation,
    Instant knowledgePin,           // nullable
    String catalogueRelease,
    String libraryVersion
)

record UomWarning(UomWarningCode code, String message, Map<String, String> details)
record UomError(UomErrorCode code, String message, Map<String, String> details)
```

Quantity, price, and amount requests extend the factor request concept:

```
// uom-api
record QuantityRequest(
    FactorRequest factorRequest,
    String quantity,                // decimal string
    ConversionStage stage,          // FINAL or INTERMEDIATE
    PrecisionOverride precisionOverride  // nullable
)

record QuantityResult(
    FactorResult factorResult,
    String convertedQuantity,       // rounded per policy
    String unroundedQuantity,       // full precision
    PrecisionApplied precisionApplied
)

record PriceRequest(
    FactorRequest factorRequest,
    String price,                   // decimal string
    ConversionStage stage,
    PrecisionOverride precisionOverride
)

record PriceResult(
    FactorResult factorResult,
    String convertedPrice,
    String unroundedPrice,
    PrecisionApplied precisionApplied
)

record AmountRequest(
    String quantity,
    String quantityUnit,
    String unitPrice,
    String priceUnit,
    QualifierTuple qualifiers,
    LocalDate valuationDate,
    MaterialContext material,
    List<ContractFactor> contractFactors,
    List<MeasuredProperty> measuredProperties,
    PrecisionOverride precisionOverride
)

record AmountResult(
    String amount,
    String unroundedAmount,
    QuantityResult quantityResult,  // if unit conversion was needed
    PriceResult priceResult,        // if unit conversion was needed
    PrecisionApplied precisionApplied
)

record PrecisionOverride(int scale, String roundingMode)
record PrecisionApplied(PrecisionDomain domain, int scale, String roundingMode, String source)
```

### 4.9 Ingestion Types

```
// uom-api
record VersionRecord(
    String eventId,             // unique, used for dedupe
    String entityType,          // UNIT, RATE_UNIT, etc.
    Scope scope,
    String tenantId,
    String naturalKey,
    long sequence,              // monotonic per (tenantId, entityType, naturalKey)
    Object payload,             // one of the S4.4 value objects
    Instant publishedAt         // informational only
)

record IngestOutcome(
    int applied,
    int rejected,
    int duplicates,
    long newGeneration,         // -1 if no change
    List<IngestRejection> rejections,
    List<String> staleKeys      // keys awaiting gap repair
)

record IngestRejection(
    String versionId,
    UomIngestCode code,
    String message
)
```

### 4.10 CDM Event Envelope

```
// uom-cdm
record CdmUomEvent(
    String eventId,
    String entityType,
    String scope,
    String tenantId,
    String naturalKey,
    long sequence,
    Map<String, Object> payload,    // raw CDM fields
    Instant publishedAt
)
```

The `uom-cdm` module maps `CdmUomEvent` to `VersionRecord` via a `CdmEventMapper` class. This is a pure function, no I/O.

---

## S5 -- Ports (Interfaces)

All port interfaces live in `uom-api`. Pattern numbers reference ADR-0001-2.

### 5.1 Public API Ports

```
// uom-api -- Pattern #14 Facade
interface UomConverter {
    UomSnapshot pin();
    UomSnapshot pin(Instant knowledgePin);
    FactorResult factor(FactorRequest request);
    List<FactorResult> factors(List<FactorRequest> requests);
    QuantityResult convertQuantity(QuantityRequest request);
    PriceResult convertPrice(PriceRequest request);
    AmountResult extendedAmount(AmountRequest request);  // see OQ-01
}

// uom-api -- extends UomConverter with snapshot identity
interface UomSnapshot extends UomConverter {
    long generation();
    Optional<Instant> knowledgePin();
}
```

### 5.2 SPI Ports (implemented by host)

```
// uom-api -- Pattern #11 Strategy
interface TenantContextProvider {
    Optional<String> currentTenant();
}

// uom-api -- Pattern #11 Strategy
interface ReferenceDataLoader {
    List<VersionRecord> loadGlobal();
    List<VersionRecord> loadTenant(String tenantId);
    List<VersionRecord> loadChangesSince(String tenantId, Instant watermark);
    List<VersionRecord> loadKey(String tenantId, String entityType, String naturalKey);
}

// uom-api -- Pattern #11 Strategy
interface VolumeCorrectionProvider {
    Optional<VcfResult> vcf(
        MaterialContext material,
        String fromConditionCode,
        String toConditionCode,
        String densityValue,        // decimal string
        String densityUnit,
        LocalDate valuationDate
    );
}

record VcfResult(String factor, String method)  // decimal string + provenance

// uom-api -- Pattern #18 Observer
interface IngestListener {
    void onApplied(String tenantId, long newGeneration, List<String> appliedVersionIds);
    void onRejected(VersionRecord record, UomIngestCode code, String message);
}

// uom-api -- Pattern #11 Strategy
interface UomMetrics {
    void ingestApplied(String tenantId, int count);
    void ingestRejected(String tenantId, UomIngestCode code);
    void generationAdvanced(String tenantId, long generation);
    void memoHit(String tenantId);
    void memoMiss(String tenantId);
    void resolutionLatency(String tenantId, long nanos);
    void resolutionError(String tenantId, UomErrorCode code);

    // No-op default
    static UomMetrics noop() { /* returns no-op implementation */ }
}
```

### 5.3 Ingestion Port

```
// uom-api -- Pattern #17 Command/UseCase
interface UomIngestor {
    IngestOutcome apply(List<VersionRecord> records);
}
```

### 5.4 Health Port

```
// uom-api
interface UomHealth {
    TenantHealthStatus status(String tenantId);
}
```

### 5.5 Internal Ports (uom-core internal, not public API)

These are package-internal to `uom-core`. They are NOT in `uom-api`. Documented here for design completeness.

```
// uom-core internal -- Pattern #21 Repository (in-memory, not JPA)
interface CatalogueStore {
    TenantCatalogue snapshot(String tenantId);
    TenantCatalogue snapshotPinned(String tenantId, Instant knowledgePin);
    long currentGeneration(String tenantId);
    void swap(String tenantId, TenantCatalogue newCatalogue);
}

// uom-core internal -- Pattern #11 Strategy
interface GraphResolver {
    FactorResult resolve(ResolvedRequest request, TenantCatalogue catalogue);
}

// uom-core internal -- Pattern #11 Strategy
interface PropertyResolver {
    Optional<ResolvedProperty> resolve(
        PropertyType type,
        QualifierTuple qualifiers,
        MaterialContext material,
        LocalDate valuationDate,
        Instant knowledgePin,   // nullable
        TenantCatalogue catalogue
    );
}

// uom-core internal
interface PrecisionResolver {
    PrecisionApplied resolve(
        PrecisionDomain domain,
        MaterialContext material,
        String unitCode,
        PrecisionOverride override,     // nullable
        TenantCatalogue catalogue,
        LocalDate valuationDate,
        Instant knowledgePin
    );
}
```

---

## S6 -- Adapters

### 6.1 `uom-core` Internal Implementations

`uom-core` provides the canonical implementations of all internal ports. These are NOT adapters in the hexagonal sense -- they are the core engine. No JPA, no Redis, no Kafka.

| Component | Pattern # | Description |
|-----------|-----------|-------------|
| `DefaultUomConverter` | #14 Facade | Implements `UomConverter`. Resolves tenant via `TenantContextProvider`, delegates to `GraphResolver`, applies precision. |
| `PinnedUomSnapshot` | #1 VO | Implements `UomSnapshot`. Immutable handle bound to `(tenantId, generation, knowledgePin)`. Delegates all calls to `DefaultUomConverter` with pinned catalogue. |
| `InMemoryCatalogueStore` | #21 Repository (in-memory) | Thread-safe store using `ConcurrentHashMap<String, AtomicReference<TenantCatalogue>>`. GLOBAL catalogue as a shared `volatile` reference. `swap()` is an atomic `compareAndSet`. |
| `DefaultGraphResolver` | #11 Strategy | Builds the conversion graph, enumerates paths (max 2 bridge + 3 qualifier edges), selects per S13 rules, computes factor in `DECIMAL128`. |
| `SpecificityTriePropertyResolver` | #11 Strategy | Resolves properties via the `SpecificityTrie` index. Implements the five-rank specificity cascade (S12) with TENANT-over-GLOBAL tiebreak. |
| `CascadePrecisionResolver` | #11 Strategy | Implements the six-level precision cascade (S15.3). |
| `DefaultUomIngestor` | #17 Command | Single-writer per tenant (explicit `ReentrantLock` per tenant). Validates, deduplicates, detects sequence gaps, builds new catalogue via copy-on-write, swaps atomically. |
| `CdmEventMapper` | #42 Data Mapper | In `uom-cdm`. Maps `CdmUomEvent` to `VersionRecord`. Pure function. |

### 6.2 `uom-guice` Wiring

```
// uom-guice -- Pattern #9 DI via Guice Modules
class UomModule extends AbstractModule {
    // Binds UomConverter -> DefaultUomConverter (singleton)
    // Binds UomIngestor -> DefaultUomIngestor (singleton)
    // Binds UomHealth -> DefaultUomHealth (singleton)
    // Binds CatalogueStore -> InMemoryCatalogueStore (singleton)
    // Binds GraphResolver -> DefaultGraphResolver (singleton)
    // Binds PropertyResolver -> SpecificityTriePropertyResolver (singleton)
    // Binds PrecisionResolver -> CascadePrecisionResolver (singleton)
    // Expects external binding for: TenantContextProvider, ReferenceDataLoader
    // Optional external bindings: VolumeCorrectionProvider, IngestListener, UomMetrics
    // Default no-op bindings for optionals when not provided
}
```

### 6.3 Valuation Engine Integration Adapter

```
// Lives in valuation-engine/valuation-guice -- Pattern #15 Anti-Corruption Layer
class UomBackedUnitConversionProvider implements UnitConversionProvider {
    // Constructor-injected: UomConverter, TenantContext (valuation-domain port)
    // Adapts valuation-domain's UnitConversionProvider.factor(Unit, Unit, ConversionContext)
    // to UomConverter.factor(FactorRequest)
    //
    // Mapping:
    //   Unit.code() -> FactorRequest.fromUnit / toUnit
    //   ConversionContext.commodity() -> MaterialContext.commodity
    //   ConversionContext.qualitySpecRef() -> MaterialContext.grade (or lookup)
    //   ConversionContext.densityBasis() -> QualifierTuple.referenceCondition or MeasuredProperty
    //
    // Returns BigDecimal from FactorResult.quantityFactor
    // Throws IllegalArgumentException (matching current SPI contract) on UomError
}
```

This adapter lives in `valuation-guice` (not `valuation-domain`), preserving D-13. The `valuation-domain` module gains a `provided`-scope dependency on `uom-api` for type references only if the SPI interface signature changes. Under the current design, **no changes to `valuation-domain` source code are required** -- the adapter maps entirely at the `valuation-guice` layer.

---

## S7 -- Data Model Impact

**No database tables.** This library is entirely in-memory. Reference data is owned by source systems. The catalogue store is an in-memory data structure rebuilt from events/loaders on startup.

### 7.1 In-Memory Data Structures

#### 7.1.1 Timeline<V>

A sorted collection of versions for a single natural key.

- Sorted by `(validFrom ASC, recordedAt ASC)`.
- Resolution: binary search for candidates where `validFrom <= d < validTo`, then scan backwards for greatest `recordedAt <= k`. O(log n) for the interval lookup.
- Immutable once constructed (part of a `TenantCatalogue` generation). New ingests build a new `Timeline` via copy-on-write.

#### 7.1.2 TenantCatalogue

Immutable snapshot for one tenant at one generation.

```
TenantCatalogue (immutable, generation N)
 +-- units:         Map<String, Timeline<UnitDefinition>>
 +-- rateUnits:     Map<String, Timeline<RateUnitDefinition>>
 +-- refConditions: Map<String, Timeline<ReferenceCondition>>
 +-- calRefs:       Map<String, Timeline<CalorificReference>>
 +-- classes:       Map<String, Timeline<CommodityClassDef>>
 +-- commodities:   Map<String, Timeline<CommodityDef>>
 +-- grades:        Map<String, Timeline<GradeDef>>
 +-- locations:     Map<String, Timeline<LocationDef>>
 +-- properties:    PropertyIndex
 +-- precision:     PrecisionIndex
 +-- generation:    long
 +-- recordedAtHighWatermark: Instant
```

GLOBAL catalogue is a separate `TenantCatalogue` instance with `generation` tracking catalogue releases. Resolution merges GLOBAL + TENANT views with TENANT winning at equal specificity.

#### 7.1.3 PropertyIndex

```
PropertyIndex
 +-- index: Map<PropertyType, Map<QualifierTuple, SpecificityTrie>>

SpecificityTrie
 +-- classLevel:     Map<String, Timeline<MaterialProperty>>  // keyed by classCode
 +-- commodityLevel: Map<String, Timeline<MaterialProperty>>  // keyed by commodityCode
 +-- gradeLevel:     Map<String, Timeline<MaterialProperty>>  // keyed by gradeCode
 +-- locationIndex:  Map<String, Map<String, Timeline<MaterialProperty>>>
                     // keyed by (entityCode, locationCode) at each level
```

Lookup: for a `(PropertyType, QualifierTuple, MaterialContext)`, check in order:
1. `grade + location` (rank 1)
2. `grade` (rank 2)
3. `commodity + location` (rank 3)
4. `commodity` (rank 4)
5. `class` with `allowClassDefault = true` (rank 5, emits `UOM_W_CLASS_DEFAULT_USED`)

At each rank, resolve the `Timeline` bitemporally. First TENANT hit wins; if no TENANT hit, check GLOBAL at same rank.

#### 7.1.4 PrecisionIndex

```
PrecisionIndex
 +-- index: Map<PrecisionDomain, List<PrecisionPolicy>>
     // sorted by specificity: (domain, commodity, unit) > (domain, unit) > (domain) > GLOBAL
```

#### 7.1.5 Resolution Memo

```
ResolutionMemo (per tenant, mutable, bounded LRU)
 +-- cache: LinkedHashMap<MemoKey, FactorResult> (access-ordered, bounded)
 +-- MemoKey = (generation, knowledgePin, requestFingerprint, valuationDate)
```

- `requestFingerprint` is a hash of `(fromUnit, toUnit, fromQualifiers, toQualifiers, materialContext, contractFactorsHash, measuredPropertiesHash)`.
- Excludes tenant (implicit in partition).
- New generation implicitly invalidates: a `get` with a non-matching generation returns a miss.
- Configurable max size (default: 10,000 per tenant). Eviction is LRU.
- Optional: can be disabled entirely. Correctness never depends on the memo.

### 7.2 Conversion Graph (Computed, Not Stored)

The conversion graph is built lazily per resolution request from the catalogue. It is not a persistent data structure.

**Nodes:** `(Dimension, QualifierTuple)`. Created dynamically based on the request's units and resolved qualifiers.

**Edges:**

| Edge Type | Source | Weight | Notes |
|-----------|--------|--------|-------|
| `UnitEdge` | Unit catalogue | `factorToBase(A) / factorToBase(B)` | Exact, same node. Not "property-based" for path selection. |
| `CommodityDefinedUnitEdge` | BU/BALE defining property | `propertyValue * lbFactor / 1` (normalised to kg) | Counts as property-based for path selection. |
| `QualifierEdge` | Property (MOISTURE_PCT, ASSAY_PCT, SW_PCT, GCV_NCV_RATIO, VOLUME_CORRECTION) | Derived from property value | Same dimension, different qualifier. Property-based. |
| `BridgeEdge` | Property (DENSITY, CV_MASS, CV_VOLUME) | Derived from property value | Cross-dimension. Property-based. |

**Transaction overrides** inject edges at the highest precedence:
- `ContractFactor` is normalised to a bridge/qualifier edge value per D-07: `edge(D1->D2) = k * f_B / f_A`.
- `MeasuredProperty` replaces the property value at transaction precedence.

---

## S8 -- Event Flow

This library does not produce events. It consumes reference data events via its ingestion pipeline.

### 8.1 Inbound Event Processing

The host service (e.g., a Kafka consumer) receives CDM events from the reference data system and calls:

```
// Host code (NOT in the library):
CdmEventMapper mapper = new CdmEventMapper();
List<VersionRecord> records = events.stream()
    .map(mapper::map)
    .toList();
IngestOutcome outcome = uomIngestor.apply(records);
```

The library itself has no Kafka dependency, no event transport. Transport is the host's responsibility (FS v2.0 S1.2).

### 8.2 Ingest Processing Sequence

1. **Deduplicate** by `versionId`. Already-seen IDs are no-ops (D-02, B11).
2. **Validate** per S17:
   - Approval check: `approvedBy` present, `approvedBy != authoredBy`, `approvedAt == recordedAt`. Fail: `UOM_I_APPROVAL_INVALID` (B05).
   - GLOBAL immutability: changes without higher catalogue release. Fail: `UOM_I_IMMUTABLE_VIOLATION` (B06).
   - Scope match: tenant/scope consistency. Fail: `UOM_I_SCOPE_VIOLATION`.
   - Factor validity: `factorToBase > 0`, base unit factor = 1. Fail: `UOM_I_INVALID_FACTOR`.
   - Overlap: two versions of same natural key with same `recordedAt` and overlapping validity. Fail: `UOM_I_OVERLAP`.
   - Reference integrity: unknown unit, condition, material, element references. Fail: `UOM_I_UNKNOWN_REFERENCE`.
   - Plausibility: density 750-1000 kg/m3 for crude, CV_MASS 50-56 MJ/kg GROSS for LNG, bushel weight 20-70 lb. Warning: `UOM_I_PLAUSIBILITY`.
3. **Sequence check**: for each natural key, verify sequence is contiguous. Gap detected: mark key `STALE`, invoke `ReferenceDataLoader.loadKey(...)`, emit `UOM_I_SEQUENCE_GAP` (B12).
4. **Build new catalogue**: copy-on-write from current catalogue, add new version records, rebuild affected indexes.
5. **Swap**: atomically replace the tenant's catalogue reference. Readers in flight continue on the old generation. New readers see the new generation (B10).
6. **Notify**: call `IngestListener.onApplied(...)` or `IngestListener.onRejected(...)`.
7. **Metrics**: increment counters via `UomMetrics`.

### 8.3 Bootstrap

- **EAGER mode**: on library initialisation, call `ReferenceDataLoader.loadGlobal()` then `loadTenant(tenantId)` for configured tenant IDs. Block until complete.
- **LAZY mode**: on first `UomConverter` call for a tenant, if `status(tenantId) == NOT_LOADED`, trigger `loadTenant(tenantId)` and block up to `bootstrapTimeout`. If timeout: `UOM_E_TENANT_NOT_READY`.
- GLOBAL catalogue is always loaded eagerly (it's shared and small).

### 8.4 Reconciliation

A periodic task (configurable interval, default OQ-05) calls `ReferenceDataLoader.loadChangesSince(tenantId, watermark)` for each loaded tenant. This catches missed events. The watermark is the `recordedAtHighWatermark` from the current catalogue.

---

## S9 -- Guice Wiring

### 9.1 `uom-guice/UomModule`

```
// uom-guice -- Guice 7 AbstractModule
class UomModule extends AbstractModule {
    private final UomConfig config;

    @Override
    protected void configure() {
        // Core singletons (Pattern #9)
        bind(CatalogueStore.class).to(InMemoryCatalogueStore.class).in(Singleton.class);
        bind(GraphResolver.class).to(DefaultGraphResolver.class).in(Singleton.class);
        bind(PropertyResolver.class).to(SpecificityTriePropertyResolver.class).in(Singleton.class);
        bind(PrecisionResolver.class).to(CascadePrecisionResolver.class).in(Singleton.class);
        bind(UomConverter.class).to(DefaultUomConverter.class).in(Singleton.class);
        bind(UomIngestor.class).to(DefaultUomIngestor.class).in(Singleton.class);
        bind(UomHealth.class).to(DefaultUomHealth.class).in(Singleton.class);

        // Config (Pattern #35)
        bind(UomConfig.class).toInstance(config);

        // Optional SPI defaults
        OptionalBinder.newOptionalBinder(binder(), VolumeCorrectionProvider.class);
        OptionalBinder.newOptionalBinder(binder(), IngestListener.class)
            .setDefault().toInstance(IngestListener.noop());
        OptionalBinder.newOptionalBinder(binder(), UomMetrics.class)
            .setDefault().toInstance(UomMetrics.noop());

        // Required SPIs -- host MUST bind these
        requireBinding(TenantContextProvider.class);
        requireBinding(ReferenceDataLoader.class);
    }
}
```

### 9.2 `UomConfig` (Pattern #35)

```
// uom-api
record UomConfig(
    BootstrapMode bootstrapMode,        // EAGER or LAZY (OQ-05)
    List<String> eagerTenantIds,        // for EAGER mode
    Duration bootstrapTimeout,          // for LAZY mode
    Duration reconciliationInterval,    // OQ-05
    int memoMaxSizePerTenant,           // default 10_000
    boolean memoEnabled,                // default true
    String pathTolerance,               // default "1e-9"
    boolean failOnStale,                // default false
    boolean verifyCatalogueOnLoad       // default true
)
```

### 9.3 Valuation Engine Integration Wiring

In `valuation-engine/valuation-guice`:

```
// valuation-guice -- addition to existing wiring
class UomIntegrationModule extends AbstractModule {
    @Override
    protected void configure() {
        // Pattern #15 Anti-Corruption Layer
        bind(UnitConversionProvider.class)
            .to(UomBackedUnitConversionProvider.class)
            .in(Singleton.class);
        // UomConverter is expected to be bound by UomModule
        // which the host installs alongside this module
    }
}
```

The host creates a single Guice `Injector` installing both `UomModule` and `UomIntegrationModule` (plus the existing valuation modules). The `TenantContextProvider` binding is shared: the host maps `valuation-domain`'s `TenantContext` to `uom-api`'s `TenantContextProvider`.

---

## S10 -- Cross-Cutting

### 10.1 Tenant Handling

- Tenant resolved from `TenantContextProvider.currentTenant()` on every public API call.
- No tenant context: `UOM_E_NO_TENANT_CONTEXT`. Never fall back to a default tenant (D-04, FS v2.0 S7).
- Tenant `t` sees GLOBAL union TENANT(t). TENANT outranks GLOBAL at equal specificity.
- A snapshot is bound to its tenant. Using it under a different tenant context: `UOM_E_TENANT_MISMATCH` (FS v2.0 S7).
- All memo keys include tenant partition. Cross-tenant memo reuse is forbidden.
- GLOBAL data structures are shared read-only across all tenants.
- A tenant MUST NOT define a unit whose code exists in GLOBAL. Rejected with `UOM_I_SCOPE_VIOLATION`.

### 10.2 Bitemporal Invariants

- All reference data is versioned with `(validFrom, validTo)` for business time and `recordedAt` for knowledge time (D-02).
- Versions are immutable once approved. Every change is a new version.
- Resolution rule (FS v2.0 S6.1): candidates with `validFrom <= d < validTo` and `recordedAt <= k`, greatest `recordedAt` wins. If winner is `RETIRED`, key has no value for `d`.
- GLOBAL records are immutable. Changes only via higher `catalogueRelease` with `correctionOf` and `reasonCode` (D-05, FS v2.0 S6.4).
- No in-place mutation. The `Timeline` data structure is append-only within a generation, and generations are swapped atomically.

### 10.3 Transaction Boundaries

There are no database transactions. The library is entirely in-memory.

- **Write path (ingest):** single-writer per tenant via explicit `ReentrantLock`. Copy-on-write catalogue construction. Atomic snapshot swap via `AtomicReference.compareAndSet()`.
- **Read path (resolution):** lock-free. Readers hold a reference to an immutable `TenantCatalogue`. No synchronization needed.
- **Snapshot pinning:** `pin()` captures the current `TenantCatalogue` reference. All subsequent calls through the snapshot use that reference, immune to concurrent ingests (B10).

### 10.4 Concurrency Model

- **Lock-free reads.** Readers obtain a `TenantCatalogue` reference (a `volatile` read or `AtomicReference.get()`). The catalogue is immutable. No locking on the resolution path.
- **Single writer per tenant.** `DefaultUomIngestor` holds a `ConcurrentHashMap<String, ReentrantLock>` keyed by tenant ID. A batch for tenant `t` acquires `locks.computeIfAbsent(t, _ -> new ReentrantLock()).lock()` before building the new catalogue.
- **Readers never observe partial ingests.** The swap is a single `AtomicReference.set()` after the new catalogue is fully constructed.
- **GLOBAL catalogue writes** are serialised by a dedicated GLOBAL lock. After GLOBAL update, all tenant catalogues that reference GLOBAL are NOT rebuilt -- they hold a reference to the GLOBAL catalogue, which is also swapped atomically. Tenant resolution always reads the latest GLOBAL atomically.

### 10.5 Determinism

- Same request + same snapshot (generation + knowledge pin) produces bit-identical results across hosts and restarts (FS v2.0 S20).
- No system clock on the resolution path. Valuation date is always an input.
- Arithmetic in `MathContext.DECIMAL128` (34 significant digits, HALF_EVEN).
- Path selection is deterministic: fewest property edges, then highest min specificity, then lexicographic path signature (D-09).

---

## S10a -- Performance Profile

### 10a.1 Resolution Performance

| Metric | Target | Design Notes |
|--------|--------|--------------|
| Single factor (warm, unmemoised) | p99 <= 20 us | In-memory graph, no I/O. Path search bounded by hop limits (2 bridge + 3 qualifier). Property lookup via `SpecificityTrie` is O(1) per rank (hash map lookups). Timeline resolution is O(log n) binary search. |
| Batch 100k factors | <= 1 s | Same per-factor cost, no amortisation needed beyond JIT warmup. Batch method avoids per-call tenant resolution overhead. |
| Memo hit | < 1 us | `LinkedHashMap.get()` with hash lookup. |

### 10a.2 Ingestion Performance

| Metric | Target | Design Notes |
|--------|--------|--------------|
| Single event ingest (including snapshot swap) | p99 <= 50 ms | Copy-on-write: only affected `Timeline`s and indexes are rebuilt. For a single property change, most of the catalogue is shared via structural sharing (immutable maps). |
| Bootstrap (10k records per tenant) | < 5 s | Bulk construction of all timelines and indexes. |

### 10a.3 Memory Budget

| Component | Budget |
|-----------|--------|
| Per tenant, 10k records | <= 5 MB |
| GLOBAL catalogue (shared) | <= 1 MB (standard units + conditions + calorific refs) |
| Resolution memo (per tenant, default 10k entries) | ~2 MB (key + result per entry) |

### 10a.4 No External Caching

This library has no Redis or external cache dependency. All caching is in-process:
- `TenantCatalogue` is the "cache" -- an immutable in-memory copy of reference data.
- Resolution memo is an in-process LRU.
- No TTL-based invalidation. Invalidation is generation-based: new ingest = new generation = old memo entries are stale.

---

## S10b -- Real-Time Push

**Not applicable.** This is an in-process library, not a service. There are no HTTP endpoints, no SSE, no WebSocket. The library is embedded in a host service; if the host needs real-time push of conversion factor changes, that is the host's concern.

The library does provide `IngestListener.onApplied(...)` which the host can use to trigger downstream notifications (e.g., invalidate a host-level cache, publish an event).

---

## S10c -- DST Handling

**Not applicable.** This library deals with unit-of-measure conversions, not time-series data. There are no 15-minute intervals, no delivery days, no gate closure times.

The `valuationDate` input is a `LocalDate` (no time zone concern). Knowledge pins are `Instant` (UTC). No DST conversion is needed.

Per D-14: "There is no calendar or time-bridge functionality."
Per D-15: "There are no power-profile or sub-hourly concerns."

---

## S11 -- Regulatory Impact

**Minimal direct regulatory impact.** The UOM conversion library is infrastructure -- it converts between units of measure. It does not itself produce reportable data.

However, conversion accuracy has indirect regulatory implications:

| Regulation | Relevance |
|------------|-----------|
| REMIT (1227/2011) | Transaction quantities reported in standard units. Incorrect UOM conversion would produce incorrect reported quantities. The library's determinism guarantee (same inputs = same outputs) and bitemporal audit trail (lineage in every result) support regulatory reproducibility. |
| EMIR | OTC derivative notional amounts may involve UOM conversion. Same determinism and audit argument applies. |
| MiFID II RTS 22 | Transaction reporting includes quantity. Same. |

The library's `Lineage` record (tenant, generation, knowledge pin, catalogue release, library version) in every `FactorResult` provides the audit trail needed to reproduce any historical conversion for regulatory queries.

**No direct reporting obligation falls on this library.** The calling service is responsible for regulatory reporting.

---

## S12 -- Testing Strategy

### 12.1 Test Modules and Scope

| Module | Test Type | Runner | Description |
|--------|-----------|--------|-------------|
| `uom-api` | Unit tests | JUnit 5 | Compact constructor validation, equality, serialisation. |
| `uom-core` | Unit tests | JUnit 5 | All resolution logic, graph algorithms, property normalisation, precision, ingestion, bitemporal resolution. Mock `TenantContextProvider` and `ReferenceDataLoader` with in-memory test doubles. |
| `uom-core` | Golden vectors G01-G15 | JUnit 5 parameterised | Each vector from FS v2.0 S19 as a parameterised test case. |
| `uom-core` | Behavioural vectors B01-B12 | JUnit 5 | Each behavioural scenario from FS v2.0 S19.1. |
| `uom-core` | Property-based tests | JUnit 5 + jqwik | `A->B->A roundtrip = 1` within tolerance. `priceFactor * quantityFactor = 1` within tolerance. Results independent of memo on/off. |
| `uom-core` | Bitemporal tests | JUnit 5 | Corrections, retirements, pinned replay, overlap rejection. |
| `uom-core` | Tenant isolation tests | JUnit 5 | Cross-tenant leakage on resolution, memo, snapshots. Scope violation on ingest. |
| `uom-core` | Concurrency tests | JUnit 5 | Readers during snapshot swaps. Multiple concurrent resolutions. Reconciliation racing with events. |
| `uom-testkit` | Conformance suite | JUnit 5 | Packaged test vectors loadable by any host for conformance verification. Includes GLOBAL catalogue verification against Appendix A constants. |
| `uom-cdm` | Unit tests | JUnit 5 | `CdmEventMapper` mapping correctness. |
| `uom-guice` | Integration tests | JUnit 5 + Guice | Full wiring test: install `UomModule`, bind test SPIs, run golden vectors. |
| architecture | ArchUnit | JUnit 5 + ArchUnit | `uom-api` and `uom-core` depend only on `java.*` / `javax.*` / `jakarta.inject`. No Spring, no Guice in `uom-core`. |

### 12.2 Test Doubles in `uom-testkit`

```
// uom-testkit
class InMemoryTenantContextProvider implements TenantContextProvider
    // Settable thread-local tenant for testing

class InMemoryReferenceDataLoader implements ReferenceDataLoader
    // Loads from JSON fixture files or programmatic builders

class GoldenCatalogue
    // Pre-built GLOBAL catalogue with all Appendix A units
    // Pre-built tenant catalogues with golden vector data

class VectorRunner
    // Parameterised runner for G01-G15 and B01-B12
    // Verifiable assertions on factors, errors, warnings
```

### 12.3 Conformance Verification

`uom-testkit` includes a `CatalogueConformanceTest` that verifies the loaded GLOBAL catalogue against the definitional constants in Appendix A. Hosts MAY run this at startup (`verifyCatalogueOnLoad = true` in `UomConfig`). A mismatch fails readiness.

The conformance test verifies:
- All units in Appendix A exist in the loaded GLOBAL catalogue.
- `factorToBase` values match the spec values within `DECIMAL128` precision.
- Reference conditions and calorific references exist.
- Rate unit decompositions are correct.

### 12.4 No Testcontainers / No Database

This library has no database. All tests run with in-memory data structures. No Testcontainers, no H2, no PostgreSQL. This is consistent with the library-first design (D-01).

---

## S13 -- Constraint Compatibility

### 13.1 Functional Spec Decisions (D-01 through D-20)

| Decision | Status | Implementation |
|----------|--------|----------------|
| D-01 | Compatible | `uom-core` is pure Java 21, no framework deps, no I/O on resolution path. ArchUnit enforces. |
| D-02 | Compatible | All versions immutable. Corrections are new versions with later `recordedAt`. |
| D-03 | Compatible | Four-eyes validation in `DefaultUomIngestor`: rejects if `approvedBy` missing, equals `authoredBy`, or `approvedAt != recordedAt`. |
| D-04 | Compatible | `TenantContextProvider` SPI. No tenant = `UOM_E_NO_TENANT_CONTEXT`, never falls back. GLOBAL catalogue shared read-only. |
| D-05 | Compatible | Standard units are data in GLOBAL catalogue, not code literals. `uom-testkit` conformance vectors are for testing only. |
| D-06 | Compatible | Volume correction via transaction value, `VOLUME_CORRECTION` property, or `VolumeCorrectionProvider` SPI. Missing = `UOM_E_QUALIFIER_MISMATCH`. |
| D-07 | Compatible | `ContractFactor` normalised to graph edge value: `edge(D1->D2) = k * f_B / f_A`. |
| D-08 | Compatible | Single precedence order (S12): contract > measured > reference by specificity. |
| D-09 | Compatible | Path selection: fewest property edges, then highest min specificity, then tolerance check, then ambiguity error. |
| D-10 | Compatible | Specificity is material only: class > commodity > grade, + optional location. No counterparty/contract scope in reference data. |
| D-11 | Compatible | Six orthogonal qualifiers in `QualifierTuple`. |
| D-12 | Compatible | Qualifiers required only when the path depends on them. |
| D-13 | Compatible | Effective dating uses valuation date. Default = latest knowledge. Optional knowledge pin. Transaction overrides take precedence. |
| D-14 | Compatible | No calendar or time-bridge functionality. |
| D-15 | Compatible | No power-profile or sub-hourly concerns. |
| D-16 | Compatible | Library is stateful (holds catalogue). Resolution is a pure function of (request, pinned snapshot). |
| D-17 | Compatible | Cache indexed by tenant > entity type > natural key > bitemporal timeline. Memo keyed by generation. |
| D-18 | Compatible | No delivery-period input. |
| D-19 | Compatible | Quantity/price helpers apply precision policies. Factors never rounded. |
| D-20 | Compatible | Error codes are `UOM_E_*`, `UOM_W_*`, `UOM_I_*` enums, not HTTP numbers. |

### 13.2 Valuation Engine Platform Constraints (D-1 through D-14)

These apply to the `valuation-engine` codebase. The UOM library is a separate reactor, but the integration adapter must comply.

| # | Constraint | Status | Notes |
|---|-----------|--------|-------|
| D-2 (platform) | PriceExpression sealed hierarchy | Not applicable | UOM library does not deal with price expressions. |
| D-3 (platform) | Forward marks ephemeral | Not applicable | |
| D-5 (platform) | NumericPrecision port | Compatible | The `UomBackedUnitConversionProvider` adapter in `valuation-guice` returns `BigDecimal`. Callers in `valuation-domain` apply `NumericPrecision` per D-5. The UOM library has its own precision system (FS v2.0 S15) which is independent. |
| D-9 (platform) | Outbox-in-same-transaction | Not applicable | UOM library does not produce events. |
| D-11 (platform) | Unified volume | Not applicable | UOM library is a conversion utility, not a volume pipeline. |
| D-12 (platform) | Commodity-neutral core | Compatible | UOM library is commodity-neutral by design. Precision varies per commodity via `PrecisionPolicy`. |
| D-13 (platform) | Library-first, no Spring | Compatible | `uom-api` and `uom-core` have zero framework deps. `uom-guice` depends on Guice only. The adapter in `valuation-guice` uses `@Inject` (jakarta.inject). No Spring anywhere. |
| D-14 (platform) | Simulator confinement | Compatible | UOM library is not part of `valuation-app`. No simulator concerns. |

### 13.3 Module Constraints (MC-1 through MC-8)

| # | Rule | Status |
|---|------|--------|
| MC-1 | No Spring in library modules | Compatible. `uom-api`, `uom-core`, `uom-cdm` have zero external deps. `uom-guice` uses Guice only. |
| MC-2 | Guice is primary DI | Compatible. `uom-guice` provides `UomModule`. |
| MC-3 | `-app` is non-production | Not applicable (no `-app` module in UOM reactor). |
| MC-4 | Package seams | Compatible. Clear module boundaries. |
| MC-5 | No adapter-to-adapter deps | Not applicable (no adapters in hexagonal sense). |
| MC-6 | Testcontainers-Postgres, no H2 | Not applicable (no database). |
| MC-7 | Multi-tenancy via TenantContext | Compatible. `TenantContextProvider` SPI. |
| MC-8 | No Spring `@Transactional` | Compatible. No transactions at all. |

---

## S14 -- Open Items

### 14.1 Open Questions from Functional Spec (MUST NOT be resolved by this tech spec)

| ID | Question | Impact on Tech Spec |
|----|----------|---------------------|
| OQ-01 | Confirm the semantics of the INTERMEDIATE and AMOUNT precision domains, and whether `extendedAmount` belongs in this library. | `AmountRequest`/`AmountResult` and `extendedAmount()` are designed but marked provisional. If `extendedAmount` is removed, the API simplifies. The `PrecisionDomain.AMOUNT` enum value and `AmountRequest`/`AmountResult` types would be removed. |
| OQ-02 | Keep `location` as an optional property qualifier (gas CV per entry point, crude by load port), or fold location-specific qualities into grades? | `MaterialContext.location` and `SpecificityTrie` location indexes are designed. If location is folded into grades, the trie simplifies (no location dimension) and the specificity ranks reduce from 5 to 3. |
| OQ-03 | VCF source: which tenants license ASTM/API tables via a `VolumeCorrectionProvider`? Default is fail-without-VCF. | `VolumeCorrectionProvider` is an optional SPI. Default behaviour when not bound: `UOM_E_QUALIFIER_MISMATCH` when a VCF is needed but no transaction value or `VOLUME_CORRECTION` property exists. |
| OQ-04 | CDM event schema name, version and topic layout per entity type. | `uom-cdm` module's `CdmEventMapper` implementation depends on the schema definition. The mapper is designed as a pure function but cannot be implemented until the CDM schema artifact exists. |
| OQ-05 | Default bootstrap mode (EAGER vs LAZY) and reconciliation interval. | `UomConfig` carries both options. Defaults are placeholder: `LAZY` mode with 60-second reconciliation interval. Host overrides via Guice binding. |
| OQ-06 | In-memory knowledge history retention horizon (default: full history). | `Timeline` retains full history by default. If a retention horizon is introduced, old versions are pruned on ingest. A `UOM_E_KNOWLEDGE_PIN_UNAVAILABLE` error is returned for pins older than retention. |
| OQ-07 | Does the platform ship GLOBAL default properties (e.g. standard bushel weights), or are all properties tenant-owned? | The design supports both: properties can be `GLOBAL` or `TENANT` scoped. If GLOBAL default properties are shipped, they arrive as part of the GLOBAL catalogue release. If not, all properties are TENANT-scoped. |
| OQ-08 | Is rate-to-rate conversion across fixed-length time denominators (BBL/D to BBL/H) needed at all? Currently rejected. | Design rejects mismatched denominators with `UOM_E_RATE_DENOMINATOR_MISMATCH` (B09). If this changes, the rate unit decomposition logic must add a time-conversion step, which would depend on a time-unit catalogue. |
| OQ-09 | Which therm variants to seed (IT, EC, US), and how they are named. | Appendix A seeds `THERM_IT` only. Additional variants would be additional GLOBAL unit definitions with their own `factorToBase` values. |
| OQ-10 | Who approves GLOBAL catalogue releases, and how releases are distributed to tenants. | The library accepts GLOBAL releases via `ReferenceDataLoader.loadGlobal()` and CDM events. The approval and distribution mechanism is outside the library. |

### 14.2 Additional Open Items Identified During Design

| ID | Item | Question |
|----|------|----------|
| TI-01 | **CDM schema artifact coordinates** | What are the Maven GAV coordinates for the CDM event schema? `uom-cdm` has a compile-scope dependency on it. |
| TI-02 | **`jakarta.inject` in `uom-core`** | Is `jakarta.inject` (provided scope, for `@Inject` annotation) acceptable in `uom-core`, or must constructor wiring be annotation-free? Platform convention for `valuation-domain` allows it as provided. Recommendation: allow it. ArchUnit rule would be: `uom-core` imports only `java.*`, `javax.*`, `jakarta.inject.*`. |
| TI-03 | **GLOBAL catalogue refresh model** | When a new GLOBAL catalogue release arrives, how are existing tenant catalogues updated? Two options: (a) tenant catalogues hold a reference to GLOBAL and always read the latest GLOBAL atomically; (b) tenant catalogues embed a copy of GLOBAL and must be rebuilt. Option (a) is more memory-efficient but requires careful atomics. This spec assumes option (a). |
| TI-04 | **Reconciliation scheduling** | The periodic reconciliation task needs a scheduler. In-process options: `ScheduledExecutorService` (JDK), or host-provided scheduler. This spec assumes `ScheduledExecutorService` managed by `DefaultUomIngestor`, started/stopped via `Closeable`/lifecycle hooks. |
| TI-05 | **Library version injection** | How is the library version string injected? Options: Maven resource filtering into a properties file read at class-load time, or a generated `UomVersion` class. |
| TI-06 | **`UomBackedUnitConversionProvider` mapping completeness** | The current `ConversionContext` record has `commodity`, `qualitySpecRef`, and `densityBasis`. The mapping to `FactorRequest` needs clarification: does `qualitySpecRef` map to `grade`? Does `densityBasis` map to a qualifier or a measured property? This requires review of how valuation-domain callers populate `ConversionContext`. |
| TI-07 | **Plausibility band configuration** | The plausibility bands for `UOM_I_PLAUSIBILITY` warnings (crude density 750-1000 kg/m3, etc.) should be configurable per tenant or commodity class, not hardcoded. Design: include in `UomConfig` or as a separate `PlausibilityConfig` value object. |

---

## Appendix A -- Module Dependency Graph

```
uom-api          (JDK only)
  ^
  |
uom-core         (JDK only, depends on uom-api)
  ^         ^
  |         |
uom-cdm     uom-guice
(CDM schema   (Guice 7)
 + uom-api)
  ^
  |
uom-testkit     (uom-api + uom-core + JUnit 5 + jqwik)
```

Valuation engine integration:
```
valuation-domain  --(provided)--> uom-api   (for type references in adapter, if needed)
valuation-guice   --(compile)---> uom-api   (for UomConverter interface)
valuation-guice   --(compile)---> uom-core  (for DefaultUomConverter, transitively)
```

Note: if `valuation-domain` does not need to reference `uom-api` types (the adapter maps entirely at the `valuation-guice` layer using existing `Unit`, `ConversionContext`, `BigDecimal`), then `valuation-domain` has NO dependency on `uom-api`. This is the preferred design -- the anti-corruption layer in `valuation-guice` absorbs the translation completely.

---

## Appendix B -- Maven Reactor POM Structure

```
uom-conversion/                         (separate git repository or monorepo module)
+-- pom.xml                             (reactor POM, groupId: com.power.uom)
+-- uom-api/
|   +-- pom.xml                         (artifactId: uom-api)
|   +-- src/main/java/com/power/uom/api/
|       +-- UomConverter.java
|       +-- UomSnapshot.java
|       +-- UomIngestor.java
|       +-- UomHealth.java
|       +-- spi/
|       |   +-- TenantContextProvider.java
|       |   +-- ReferenceDataLoader.java
|       |   +-- VolumeCorrectionProvider.java
|       |   +-- IngestListener.java
|       |   +-- UomMetrics.java
|       +-- model/
|       |   +-- Dimension.java
|       |   +-- QualifierTuple.java
|       |   +-- MaterialContext.java
|       |   +-- VersionEnvelope.java
|       |   +-- UnitDefinition.java
|       |   +-- RateUnitDefinition.java
|       |   +-- ... (all S4.4 types)
|       +-- request/
|       |   +-- FactorRequest.java
|       |   +-- QuantityRequest.java
|       |   +-- PriceRequest.java
|       |   +-- AmountRequest.java
|       +-- result/
|       |   +-- FactorResult.java
|       |   +-- QuantityResult.java
|       |   +-- PriceResult.java
|       |   +-- AmountResult.java
|       |   +-- PathEdge.java
|       |   +-- Lineage.java
|       +-- error/
|       |   +-- UomErrorCode.java
|       |   +-- UomWarningCode.java
|       |   +-- UomIngestCode.java
|       |   +-- UomError.java
|       |   +-- UomWarning.java
|       |   +-- UomException.java
|       +-- ingest/
|           +-- VersionRecord.java
|           +-- IngestOutcome.java
|           +-- IngestRejection.java
+-- uom-core/
|   +-- pom.xml                         (depends on uom-api only)
|   +-- src/main/java/com/power/uom/core/
|       +-- DefaultUomConverter.java
|       +-- PinnedUomSnapshot.java
|       +-- DefaultUomIngestor.java
|       +-- DefaultUomHealth.java
|       +-- graph/
|       |   +-- DefaultGraphResolver.java
|       |   +-- ConversionGraph.java
|       |   +-- PathSearcher.java
|       |   +-- PathSelector.java
|       |   +-- EdgeBuilder.java
|       +-- property/
|       |   +-- SpecificityTriePropertyResolver.java
|       |   +-- PropertyNormaliser.java     (API->SG->density, lb->kg, percentages)
|       +-- precision/
|       |   +-- CascadePrecisionResolver.java
|       +-- catalogue/
|       |   +-- InMemoryCatalogueStore.java
|       |   +-- TenantCatalogue.java
|       |   +-- Timeline.java
|       |   +-- PropertyIndex.java
|       |   +-- SpecificityTrie.java
|       |   +-- PrecisionIndex.java
|       |   +-- CatalogueBuilder.java
|       +-- memo/
|       |   +-- ResolutionMemo.java
|       +-- validation/
|           +-- IngestValidator.java
|           +-- PlausibilityChecker.java
+-- uom-cdm/
|   +-- pom.xml                         (depends on uom-api + CDM schema artifact)
|   +-- src/main/java/com/power/uom/cdm/
|       +-- CdmEventMapper.java
+-- uom-testkit/
|   +-- pom.xml                         (depends on uom-api + uom-core + JUnit 5 + jqwik)
|   +-- src/main/java/com/power/uom/testkit/
|   |   +-- InMemoryTenantContextProvider.java
|   |   +-- InMemoryReferenceDataLoader.java
|   |   +-- GoldenCatalogue.java
|   |   +-- VectorRunner.java
|   +-- src/test/java/com/power/uom/testkit/
|       +-- GoldenVectorTest.java           (G01-G15 parameterised)
|       +-- BehaviouralVectorTest.java      (B01-B12)
|       +-- PropertyBasedTest.java          (roundtrip, reciprocal)
|       +-- BitemporalTest.java
|       +-- TenantIsolationTest.java
|       +-- ConcurrencyTest.java
|       +-- CatalogueConformanceTest.java
|       +-- ArchitectureTest.java           (ArchUnit)
+-- uom-guice/
    +-- pom.xml                         (depends on uom-core + Guice 7)
    +-- src/main/java/com/power/uom/guice/
        +-- UomModule.java
```

---

## Appendix C -- Conversion Algorithm Pseudocode

For reference. Not normative. Aids implementation-engineer understanding.

```
function resolve(request, catalogue):
    // 1. Context
    tenant = tenantContextProvider.currentTenant().orElseThrow(UOM_E_NO_TENANT_CONTEXT)
    snapshot = request.snapshot ?? catalogue.current(tenant)
    if snapshot.tenant != tenant: throw UOM_E_TENANT_MISMATCH

    // 2. Memo check
    key = memoKey(snapshot.generation, snapshot.knowledgePin, request.fingerprint, request.valuationDate)
    if memo.contains(key): return memo.get(key)

    // 3. Validate units
    fromDef = snapshot.resolveUnit(request.fromUnit, request.valuationDate, snapshot.knowledgePin)
        ?? throw UOM_E_UNKNOWN_UNIT
    toDef = snapshot.resolveUnit(request.toUnit, request.valuationDate, snapshot.knowledgePin)
        ?? throw UOM_E_UNKNOWN_UNIT

    // 4. Rate unit decomposition
    if fromDef is RateUnit or toDef is RateUnit:
        decompose both; check denominators match (else UOM_E_RATE_DENOMINATOR_MISMATCH)
        convert numerators; apply multiplier ratio

    // 5. Qualifier normalisation
    fromQ = normalise(request.fromQualifiers, fromDef)  // apply unit default condition
    toQ = normalise(request.toQualifiers, toDef)
    inherit(fromQ, toQ)  // silent side inherits from explicit side

    // 6. Identity check
    if fromDef.unitCode == toDef.unitCode and fromQ == toQ: return factor(1, 1)

    // 7. Build edge candidates
    edges = []
    // 7a. Unit edges (exact factors)
    if sameDimension(fromDef, toDef):
        edges.add(UnitEdge(fromDef.factorToBase / toDef.factorToBase))

    // 7b. Commodity-defined unit edges
    if fromDef.definitionType == COMMODITY_DEFINED:
        prop = resolveProperty(fromDef.definingProperty, request.material, ...)
        edges.add(CommodityDefinedUnitEdge(prop))

    // 7c. Bridge edges (cross-dimension)
    for each needed bridge:
        value = resolvePrecedence(edgePropertyType, request, snapshot)
        edges.add(BridgeEdge(value, source, specificityRank))

    // 7d. Qualifier edges (same dimension, qualifier change)
    for each qualifier difference:
        value = resolvePrecedence(qualifierPropertyType, request, snapshot)
        edges.add(QualifierEdge(value, source, specificityRank))

    // 8. Path search (max 2 bridge + 3 qualifier)
    paths = enumeratePaths(fromNode, toNode, edges, maxBridge=2, maxQualifier=3)
    if paths.empty: throw UOM_E_UNSUPPORTED_CONVERSION

    // 9. Path selection (S13)
    selected = selectPath(paths)  // fewest property edges, highest min specificity, tolerance, ambiguity

    // 10. Compute factor in DECIMAL128
    F = computeFactor(selected, MathContext.DECIMAL128)
    priceFactor = BigDecimal.ONE.divide(F, MathContext.DECIMAL128)

    // 11. Build result
    result = FactorResult(F.toPlainString(), priceFactor.toPlainString(), selected.path, lineage, warnings)
    memo.put(key, result)
    return result

function resolvePrecedence(propertyType, request, snapshot):
    // S12 precedence
    // 1. Transaction contractual factor covering this edge
    if request.contractFactors covers this edge:
        return normaliseContractFactor(...)   // D-07: edge = k * f_B / f_A

    // 2. Transaction measured property
    if request.measuredProperties has this propertyType:
        if also has contractFactor for same edge: emit UOM_W_PROPERTY_SHADOWED_BY_CONTRACT
        return normaliseProperty(measuredValue)

    // 3. Reference property by specificity
    for rank in [grade+location, grade, commodity+location, commodity, class(if allowDefault)]:
        prop = snapshot.properties.lookup(propertyType, qualifiers, rank, valuationDate, knowledgePin)
        if prop found:
            if rank == class: emit UOM_W_CLASS_DEFAULT_USED
            return normaliseProperty(prop)
        // At each rank: TENANT first, then GLOBAL

    // 4. No value
    return absent  // edge unavailable
```

---

## Appendix D -- Property Normalisation Formulas

All computations in `MathContext.DECIMAL128`.

| Input Property | Normalisation | Output | Notes |
|----------------|---------------|--------|-------|
| API gravity (degrees API) | `SG = 141.5 / (API + 131.5)` | Specific gravity at 60 degF | Exact formula per API standard |
| Specific gravity | `density = SG * 999.016` | kg/m3 at API_60F | Water density at 60 degF = 999.016 kg/m3 |
| Lb-based property (bushel weight, bale weight) | `kg = lb * catalogue_factor("lb")` | kg | Uses catalogue `lb` factor (0.45359237), NOT a literal |
| MOISTURE_PCT (m%) | `dryFactor = 1 - m/100` | Multiplier WET -> DRY | |
| ASSAY_PCT (a%, element) | `containedFactor = a/100` | Multiplier DRY -> CONTAINED(element) | Applied on DRY mass |
| SW_PCT (s%) | `nsvFactor = 1 - s/100` | Multiplier GSV -> NSV | |

---

*End of Technical Specification.*

*Hand-off: when approved, pass to implementation-engineer for implementation of `uom-conversion` reactor per this spec.*
