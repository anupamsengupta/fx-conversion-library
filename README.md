# FX Conversion Library

A Java 21 library that converts amounts and prices between currencies for a CTRM/ETRM (commodity trading) platform — correctly, deterministically, and with a full audit trail of *why* a particular rate was used.

If you've never touched this repo before, read this document top to bottom once. It's written for a junior developer or a new consumer of the library, not just for the people who built it.

> **Status: this is a working, test-covered v1.0 implementation, not a polished release.** It builds clean and 414 tests pass, but a few real gaps are called out honestly in [Known Limitations](#known-limitations) below — please read that section before you rely on this in anger.

---

## 1. What this library actually does

Say a power trading desk buys gas priced in GBP, invoices the customer in EUR, books it in a US legal entity whose home currency is USD, and reports group-wide in CHF. That's **one trade, four currencies**. This library is the one place in the platform that knows how to walk that chain correctly:

```
Price currency (GBP) → Settlement currency (EUR) → Functional currency (USD) → Presentation currency (CHF)
```

It also knows things that are easy to get subtly wrong if every team implements their own FX logic:

- **A holiday is not a missing rate.** If the ECB doesn't publish on Christmas Day, that's expected — the library resolves the date *before* it ever looks for a rate, so a holiday never falsely triggers "data is missing" logic.
- **Corrected rates don't silently rewrite history.** If EUR/USD was published as 1.0800 and corrected to 1.0810 the next day, an already-invoiced contract can legitimately keep using 1.0800 forever, while an accounting recalculation can legitimately pick up 1.0810 — the library tracks *both* the rate's business date and the moment it became known, so either answer is reproducible on demand.
- **Mark-to-market and cash forecasting use genuinely different math.** Converting a discounted present value at today's spot rate is right; converting an undiscounted future cash amount at today's spot rate is a real (and common) valuation bug. The library enforces which one you're doing.
- **Everything is decimal, never `float`/`double`.** Financial arithmetic here uses `BigDecimal` exclusively, all the way down to its own hand-written decimal `ln`/`exp` implementation for forward-curve interpolation, so two runs on two different machines produce bit-identical results.

It's a **library, not a service** — it runs embedded inside your application (no REST call, no network hop, no database query during a conversion). You feed it reference data and market data up front (via loader SPIs you implement), and it resolves conversions purely from memory.

---

## 2. Architecture

### 2.1 The five modules

The project is a Maven **reactor** (a parent project with sub-modules) at `groupId com.power.fx`, `artifactId fx-conversion`. The modules build in this order, each depending only on the ones before it:

```
fx-api  →  fx-core  →  ┌─ fx-cdm      (depends on fx-api only)
                        └─ fx-testkit (depends on fx-api + fx-core)
                              ↓
                           fx-guice (depends on fx-core + Guice 7)
```

| Module | What lives here | Depends on | You touch this if... |
|---|---|---|---|
| **`fx-api`** | Every public type: requests, results, enums, the `FxConverter` interface, and the SPI interfaces you must implement. Pure JDK — no frameworks, no third-party libraries. | nothing | You're reading what a method takes/returns, or implementing an SPI. |
| **`fx-core`** | The actual engine. Date resolution, pair resolution (direct/inverse/triangulated), rate selection, forward curves, averaging, the currency-chain logic, the decimal math, and the in-memory caches. | `fx-api` | You're debugging *why* a conversion produced the value it did. |
| **`fx-cdm`** | Maps CDM (Common Domain Model) event payloads into the library's ingest records. **Currently a placeholder/scaffold** — see [Known Limitations](#known-limitations). | `fx-api` only (never `fx-core`, by design) | You're wiring real market-data/reference-data events into the library. |
| **`fx-testkit`** | Everything needed to test against this library: in-memory fakes for the three required SPIs, a full set of reference-data fixtures, and the golden-vector test suites themselves. | `fx-api` + `fx-core` | You're writing a test, or you want copy-pasteable working example code (see §5.2). |
| **`fx-guice`** | Wires everything together with [Google Guice](https://github.com/google/guice) dependency injection: one `FxModule` you install in your application. | `fx-core` + Guice 7 | You're actually embedding this library into a running application. |

**Why this shape?** `fx-api` and `fx-core` are deliberately framework-free — no Spring, no Guice, no I/O, no system clock — so the *logic* of the library can never accidentally depend on how it's wired up or hosted. `fx-guice` is the only place dependency injection appears; a host that doesn't use Guice could write their own thin wiring layer instead, using `fx-core`'s classes directly.

### 2.2 Package layout inside `fx-core` (the part you'll spend the most time in)

```
com.power.fx.core
├── date         date resolution — "what date should I actually use?" (before any rate lookup)
├── pair         pair resolution — direct / inverse / triangulated / fixed-factor pegs
├── rate         rate selection — which fixing/spot/forward, and the fallback chain
├── curve        forward curves and interpolation (LOG_LINEAR_CARRY, points, CIP)
├── averaging    period averages (monthly, pricing-period, PDR-linked)
├── leg          the price→settlement→functional→presentation chain, and revaluation
├── precision    rounding and largest-remainder allocation
├── decimal      the hand-written decimal ln/exp math (no float/double anywhere)
├── cache        the in-memory bitemporal stores (reference data, fixings, snapshots)
├── snapshot     immutable market-data snapshots and the "pin" mechanism
├── entitlement  which tenants can see which market-data sources
├── validation   policy/request validation
├── lineage      the audit trail attached to every result (see §6)
└── memo         per-snapshot result memoisation (performance)
```

If you remember one thing about this layout: **a request always flows through these packages roughly left-to-right** — date, then pair, then rate (with curve/averaging as needed), then precision, then leg/lineage. §4 below walks through this in detail.

---

## 3. Key concepts, in plain English

You don't need to read the full spec to use this library, but these five ideas come up constantly:

| Term | Plain-English meaning |
|---|---|
| **Purpose** | *Why* you're converting — `CONTRACT_SETTLEMENT`, `UNREALISED_MTM`, `ACCOUNTING_RECOGNITION`, etc. The same currency pair can legitimately need a different rate, date, and rule set depending on purpose. You must always state one. |
| **Policy** (`FxPolicy`) | The "recipe" for a conversion: which market-data sources to try, what to do on a holiday, how to pick between a rate and its later correction, how to interpolate a forward curve. Either attached to a trade directly, or looked up by ID from reference data. |
| **Snapshot pinning** | Before converting anything, you "pin" a specific, frozen, timestamped set of market data (a `FxSnapshot`). Every conversion in that run uses exactly that data — so a month-end valuation re-run five years from now produces an identical answer, even if the real-world data has since changed or been corrected. |
| **Legs** | A conversion chain has up to three independent stages — `CONTRACT` (price→settlement), `ACCOUNTING_TRANSACTION` (settlement→functional), `TRANSLATION` (functional→presentation) — plus an on-read-only `MANAGEMENT_VIEW`. Each stage has its own rate, its own date, and its own rule set; they are never silently merged into one conversion. |
| **Rate finality** | Every result is tagged `CONFIRMED` (a real published rate was used), `ESTIMATED` (a fallback, interpolation, or preliminary rate was used), or `UNRESOLVED` (no usable rate could be found at all). |

---

## 4. How a conversion actually flows through the code

Walking through `FxConverter.convert(ConversionRequest)` (the simplest entry point — a single amount, one currency pair):

```
1. DATE RESOLUTION   (core.date)
   Turn the request's raw date into the correct FX date, resolved against the
   right publication/settlement calendar. A holiday moves the date per policy
   (e.g. "use the previous business day") — this NEVER triggers fallback logic;
   that only happens for a genuinely missing rate on a day a source should have
   published.

2. ENTITLEMENT FILTERING   (core.entitlement)
   Narrow the policy's source list down to only the sources this tenant is
   actually licensed to use.

3. PAIR RESOLUTION   (core.pair)
   Work out how to get from "from" currency to "to" currency: a direct quote,
   an inverse (1/rate), a triangulation through an intermediate currency
   (e.g. GBP→USD→INR), a legal-peg fixed factor, a contract rate, or a
   four-eyes-approved manual override. Tried in a fixed, documented order.

4. RATE SELECTION   (core.rate, + core.curve / core.averaging as needed)
   Given the resolved pair and date, pick the actual rate: a fixing (with its
   correction-handling policy applied), a spot rate, or a forward rate
   (built via core.curve's interpolation if the exact maturity isn't quoted).
   If the primary source has no data, walk the fallback chain here.

5. PRECISION & ROUNDING   (core.precision)
   Compute the full-precision result, then round it exactly once to the
   target currency's decimal places.

6. LINEAGE   (core.lineage)
   Attach a full audit record to the result: which source, which date, which
   rate version, which path was taken, and why.
```

`convertChain(...)` repeats steps 2-6 once per leg (CONTRACT, then ACCOUNTING_TRANSACTION, then TRANSLATION), stopping early if an earlier leg comes back `UNRESOLVED`. `convertSeries(...)` repeats steps 1-5 once per date in an averaging window, then combines them per the averaging method.

**The single most useful debugging habit**: every result carries a `Lineage` and a `List<PathStep> path` — read those before you read anything else. See §7.

---

## 5. Using this as a library consumer

### 5.1 Add the dependency

This hasn't been published to a shared Maven repository yet — for now, build it locally:

```bash
cd fx-conversion-library
mvn install -DskipTests
```

Then depend on the modules you need. Most hosts want both:

```xml
<dependency>
    <groupId>com.power.fx</groupId>
    <artifactId>fx-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.power.fx</groupId>
    <artifactId>fx-guice</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 5.2 Implement the three SPIs you must supply

The library never reaches out for data itself — you hand it data through three interfaces (all in `fx-api`'s `com.power.fx.api.spi` package):

| Interface | What it's for |
|---|---|
| `TenantContextProvider` | Tells the library which tenant the current call is for. |
| `ReferenceDataLoader` | Supplies currencies, currency pairs, calendars, policies, entitlements — the slow-changing setup data. |
| `MarketDataLoader` | Supplies fixings, spot rates, forward curves, market snapshots. |

**The library will refuse to start without all three bound** — that's deliberate (see `FxModule`'s `requireBinding` calls), so a host can't silently run with half-configured data.

Two more SPIs are optional and default to harmless no-ops if you don't supply them: `FxEventListener` (notified on corrections, rejections) and `FxMetrics` (latency/counter hooks).

**Don't write these from scratch** — `fx-testkit`'s `com.power.fx.testkit.doubles` package has working in-memory implementations (`InMemoryTenantContextProvider`, `InMemoryReferenceDataLoader`, `InMemoryMarketDataLoader`) you can either use directly for testing, or read as a template for your real, production-backed implementations.

### 5.3 Wire it up with Guice

```java
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.power.fx.api.FxConfig;
import com.power.fx.api.FxConverter;
import com.power.fx.api.spi.*;
import com.power.fx.guice.FxModule;

FxConfig config = new FxConfig(
    FxConfig.BootstrapMode.EAGER, List.of(), Duration.ofSeconds(5), Duration.ofMinutes(5),
    FxConfig.DEFAULT_FIXING_HOT_WINDOW_YEARS, FxConfig.DEFAULT_RETAINED_SNAPSHOTS_PER_TENANT,
    /* memoEnabled */ true, FxConfig.DEFAULT_MEMO_MAX_ENTRIES_PER_SNAPSHOT,
    FxConfig.DEFAULT_FORWARD_MEMO_MAX_ENTRIES_PER_CURVE, FxConfig.DEFAULT_DECIMAL_WORKING_PRECISION,
    FxConfig.DEFAULT_EAGER_INPUTS_HASH, /* allowImplicitPin */ true,
    FxConfig.DEFAULT_FAIL_ON_STALE, FxConfig.DEFAULT_MAX_FALLBACK_STALENESS_DAYS);

Injector injector = Guice.createInjector(
    new FxModule(config, /* meteringEnabled */ true),
    new AbstractModule() {
        @Override protected void configure() {
            bind(TenantContextProvider.class).toInstance(myTenantContextProvider);
            bind(ReferenceDataLoader.class).toInstance(myReferenceDataLoader);
            bind(MarketDataLoader.class).toInstance(myMarketDataLoader);
        }
    });

FxConverter converter = injector.getInstance(FxConverter.class);
```

`FxConfig` has 14 fields (it's a direct translation of the spec's own config table) — use the `FxConfig.DEFAULT_*` constants rather than guessing values; two of them (`memoMaxEntriesPerSnapshot`, `forwardMemoMaxEntriesPerCurve`) were deliberately corrected downward from an earlier draft of the spec to fit a memory budget, so don't "fix" them back up without reading the Javadoc on `FxConfig`.

### 5.4 Make a conversion

```java
FxSnapshot snapshot = converter.pin("EOD-2026-06-30");   // freeze the market data for this run

ConversionRequest request = new ConversionRequest(
    context,                     // an FxRequestContext — purpose, dates, policy, etc.
    new CurrencyCode("GBP"),
    new CurrencyCode("USD"),
    new BigDecimal("100000.00"),
    /* isUnitPrice */ false);

ConversionResult result = snapshot.convert(request);

if (result.isSuccess()) {
    System.out.println(result.toAmountBooked() + " " + result.toCcy());
} else {
    result.error().ifPresent(e -> System.out.println("Failed: " + e.code()));
}
```

**Building `FxRequestContext` and `FxPolicy` by hand is verbose** (15 and 20 fields respectively — this mirrors the breadth of real-world FX business rules, not accidental complexity). Rather than guessing the constructor shape from this README, copy a working example from `fx-testkit`:

- **`com.power.fx.testkit.fixtures.GoldenReferenceData`** — builds realistic currencies, calendars, policies, and entitlements.
- **`fx-guice/src/test/java/com/power/fx/guice/WiringTest.java`** — a complete, compiling, end-to-end example: build a catalogue, publish fixings and a snapshot, build a policy and a request, get a result — all through the exact DI-wired path a real host uses.
- **`fx-testkit/src/test/java/com/power/fx/testkit/vectors/*VectorTest.java`** — dozens of smaller, focused examples, one per business scenario.

### 5.5 Reading a result

Every `*Result` type (`ConversionResult`, `RateResult`, `SeriesResult`, `ChainResult`, `RevaluationResult`) shares the same shape:

| Field | Meaning |
|---|---|
| `toAmountUnrounded` / `toAmountBooked` | Full-precision value, and the once-rounded, bookable value. |
| `effectiveRate` | The rate actually used, full precision. |
| `finality` | `CONFIRMED` / `ESTIMATED` / `UNRESOLVED` — see §3. |
| `reasons` | Why the result looks the way it does (e.g. `FALLBACK_ALT_SOURCE`, `PARTIAL_PERIOD`). |
| `path` | The step-by-step resolution trail — source, date, version, whether inverted/triangulated. |
| `lineage` | The full audit record, including a `inputsHash()` for detecting whether two results came from identical inputs. |
| `error` | Present only on failure — never thrown as an exception. Call `.orThrow()` on the result if you want exception semantics instead. |

---

## 6. How to run the tests

From the repository root:

```bash
# Everything, all five modules
mvn clean test

# Just one module (fast inner-loop while working on fx-core)
mvn -pl fx-core -am test

# A single test class
mvn -pl fx-testkit test -Dtest=GoldenArithmeticVectorTest

# A single test method
mvn -pl fx-testkit test -Dtest=ChainVectorTest#f03_accountingRevaluation_closingRate
```

Current state: **414 tests across 5 modules, 0 failures.**

| Module | Test count | What they prove |
|---|---|---|
| `fx-api` | 36 | Value types validate their own invariants correctly (e.g. a four-eyes rule violation is rejected at construction). |
| `fx-core` | 57 | The engine's internals: calendar math, ingestion, decimal correctness. |
| `fx-cdm` | 27 | Event mapping and dispatch logic (against a placeholder schema — see §8). |
| `fx-testkit` | 255 | The bulk of functional correctness: every golden vector from the spec, property-based tests, architecture rules, policy-matrix exhaustion. |
| `fx-guice` | 39 | The same golden vectors again, but through real Guice dependency injection, end to end. |

---

## 7. How to debug something that looks wrong

This library **does not use a logging framework** — no SLF4J, no `java.util.logging` calls anywhere in `fx-api`/`fx-core`. That's deliberate: logging is a side effect, and the whole resolution path is built to have none. Debugging works differently here than in a typical service:

1. **Read the result's `path` and `lineage` first, before anything else.** Every `PathStep` tells you the source, the resolved date, the fixing version, and whether the rate was inverted or crossed through another currency. This is almost always enough to answer "why did I get this number."
2. **Check `reasons` and `warnings` on the result.** A surprising value is often explained by a reason code like `FALLBACK_ALT_SOURCE` (the primary source had no data) or `PARTIAL_PERIOD` (an average is based on incomplete data).
3. **If the result is `UNRESOLVED` or has an `error`**, the `FxErrorCode` tells you the category (no entitled source, no FX path found, data not loaded, policy misconfigured, etc.) — look that code up in `fx-api`'s `error` package, every code is documented there.
4. **If something structural is wrong** (a conversion that shouldn't be possible, a currency that behaves oddly), reproduce it as a small unit test using `fx-testkit`'s `GoldenReferenceData`/`VectorRunner` rather than against a real, opaque dataset — nearly every behavior in this library already has a golden-vector test that looks similar to whatever you're debugging; find and adapt the closest one.
5. **For wiring/DI problems** (a Guice injector that won't start, a binding that seems missing), the error from `Guice.createInjector(...)` is unusually readable — read the whole `CreationException` message, it names the exact missing binding.
6. **For "is this architecturally allowed" questions** (can this class call `Instant.now()`? import `java.io`? use `double`?), run `fx-testkit`'s `ArchitectureTest` and `NoFloatingPointBytecodeTest` — they mechanically enforce the no-I/O / no-clock / no-floating-point rules and will tell you exactly which class and rule you tripped.
7. **Use `FxMetrics`/`FxEventListener`** in a real deployment for operational visibility (latency, correction notifications) — these are the SPI-level hooks meant to replace what a logging framework would normally give you.

---

## 8. Top 5 tests to read first

If you only have time to look at five test classes to understand what this library actually guarantees, read these, in this order:

1. **`fx-testkit/.../vectors/GoldenArithmeticVectorTest.java`** (vectors G01-G09)
   The core math: direct conversion, inverse, triangulation, forward-curve interpolation, averaging, and a legal-peg fixed factor. This is the fastest way to see every rate-resolution shape the library supports, each with a hand-checkable expected number.

2. **`fx-testkit/.../vectors/ChainVectorTest.java`** (vectors F01-F09)
   The full price→settlement→functional→presentation chain on one realistic trade, plus revaluation (realised/unrealised FX), mark-to-market vs. cash-forecasting (the bug this library specifically prevents), and effective-dated functional currency changes. This is the best single test for understanding *why* the library exists.

3. **`fx-testkit/.../vectors/CalendarVectorTest.java`** (vectors C01-C05)
   Holiday handling, and the rule that a holiday never triggers fallback logic (§1's second bullet). Short, and it's the rule most new contributors get wrong on first instinct.

4. **`fx-testkit/.../vectors/CorrectionEntitlementVectorTest.java`** (vectors X01-X11)
   Corrected fixings not rewriting already-settled contracts, tenant entitlement filtering, and exact replay determinism after a correction. This is the test suite that proves the audit/compliance story actually holds up.

5. **`fx-guice/src/test/java/com/power/fx/guice/WiringTest.java`**
   The only test that exercises the library exactly as a real consuming application would: real Guice injector, real `FxModule`, the three required SPIs bound, a full conversion through the resulting `FxConverter`. If you're integrating this library into a host application, this is the one file to copy from.

(Honorable mention: `fx-testkit/.../vectors/PolicyMatrixExhaustionTest.java` mechanically checks all ~200 combinations of rule × leg × purpose × item-type behave correctly — not "top 5" reading, but worth knowing it exists if you ever add a new `Purpose` or `Leg` value.)

---

## 9. Known limitations

Read this before treating anything here as production-ready. None of this is hidden in the codebase — every item below has a code comment or test naming it explicitly.

- **The headline performance target is currently missed.** The spec's target is a single conversion completing in ≤20 microseconds (p99), measured warm with a reused pinned snapshot. The real, measured number in `WiringTest` is **~400-500 microseconds p99** — roughly 20-25x over target. This was measured honestly and is not hidden; a dedicated profiling pass is the recommended next step.
- **`fx-cdm` is a scaffold, not a finished integration.** There is no real CDM (Common Domain Model) schema to map against yet — the module demonstrates the mapping *shape* and dispatch logic against a clearly-labelled placeholder payload, not real field names.
- **`assessCorrectionImpact(...)` is stubbed.** It always reports `replayable = false` rather than running the real five-step comparison algorithm.
- **Four warning codes are defined but never actually raised**: `FX_W_FALLBACK_USED`, `FX_W_EXTRAPOLATED`, `FX_W_MIXED_SOURCE`, `FX_W_STALE_KEY`. Only two of the six specified warning codes are currently wired into the pipeline's output.
- **One golden vector (X07, a four-eyes-approval violation) can't be driven end-to-end** — the value type itself (`VersionEnvelope`) already refuses to construct an invalid record, so the ingestion-level check that was meant to catch it is effectively unreachable. This is a *good* kind of problem (an earlier safety net already catches it), just not the one the spec describes.
- **The decimal `ln`/`exp` reference table used for conformance testing is provisional** (~50 points), not the full, independently cross-checked 2,000-point table the spec calls for.
- **Cross-JVM determinism has only been checked on one JVM vendor** locally, not the full CI matrix the spec envisions.

Full detail on every open item, including decisions still waiting on a business/platform answer, lives in:

- `docs/technical-spec/claude-gen/libraries/fx-conversion-library-v1.0.md` — the technical specification (see §14, Open Items).
- `docs/technical-spec/claude-gen/libraries/plan/fx-conversion-library-implementation-plan-v1.0.md` — the implementation plan (see §8, Carried-Forward Open Items, and §9, Newly Identified Gaps).
- `docs/functional-spec/claude-gen/libraries/fx-conversion-library-spec.md` — the original functional specification (plain-English companion versions are alongside it in the same folder).

---

## 10. Quick reference

```bash
mvn clean install -DskipTests   # build everything without running tests
mvn clean test                  # build + run all 414 tests
mvn -pl fx-core -am test        # fast inner loop on one module
```

| I want to... | Look at... |
|---|---|
| Understand a single business rule in depth | The functional spec, `docs/functional-spec/claude-gen/libraries/` |
| Understand how a rule was turned into code | The technical spec, `docs/technical-spec/claude-gen/libraries/fx-conversion-library-v1.0.md` |
| See the engineering build order / design decisions | The implementation plan, `docs/technical-spec/claude-gen/libraries/plan/` |
| Write my own SPI implementation | `fx-testkit/.../doubles/InMemory*.java` as a template |
| Copy a working, compiling usage example | `fx-guice/src/test/java/com/power/fx/guice/WiringTest.java` |
| See every error/warning code and what it means | `fx-api/src/main/java/com/power/fx/api/error/` |
