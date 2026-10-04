# FX Conversion Library — Plain-English Summary

*A condensed, non-technical overview of the full functional specification (all 21 sections + introduction). For precise rules, see the three detailed layman documents or the authoritative technical spec.*

---

## What It Is, In One Sentence

The FX Conversion Library is the platform's single, shared engine for turning an amount in one currency into an amount in another currency — correctly, consistently, and in a way that can always be explained and reproduced later.

It runs **inside** other applications (valuation, settlement, accounting, reporting, exposure) as an embedded Java library, not as a separate service you call over the network. That makes it fast — no database calls, no REST calls, no waiting — because everything it needs is already loaded into memory.

---

## Why The Platform Needs One Shared Library

A single commodity trade can touch four different currencies at once:

```
Price Currency  →  Settlement Currency  →  Functional Currency  →  Presentation Currency
   (e.g. GBP)         (e.g. EUR)              (e.g. USD)              (e.g. CHF)
```

- **Price currency** — the currency the commodity is quoted in (e.g. Brent oil in USD).
- **Settlement currency** — the currency of the actual invoice and cash movement.
- **Functional currency** — the "home" operating currency of the legal entity doing the accounting (mandatory, can change over time).
- **Presentation currency** — the currency used in consolidated financial statements.
- **Management reporting currency** — a further display-only currency used purely for internal reports, calculated on the fly and never stored.

If every service (valuation, settlement, accounting, reporting) implemented its own FX logic, they would eventually disagree — different holiday handling, different forward curves, different correction behaviour — and that produces reconciliation failures and audit findings. This library exists to be the **one source of truth** that every consumer calls instead of writing its own conversion logic.

---

## Five Platform Principles Baked Into Everything

1. **In-memory processing.** No database or network call happens during a conversion. Everything needed is already cached.
2. **Bitemporal data.** The library remembers both *which date* a rate applies to and *when the platform found out about it*. This is what lets a correction issued six months later not silently rewrite a month-end result that already closed.
3. **Snapshot pinning.** A valuation run is tied to one fixed, immutable set of market data (a "snapshot"). Every conversion inside that run uses exactly the same rates, even if newer data arrives later. This is what makes historical results exactly reproducible.
4. **Decimal-only arithmetic.** All math uses exact decimal types (no `float`/`double`), because binary floating-point can produce tiny inconsistencies (0.1 + 0.2 is not always exactly 0.3) that are unacceptable in financial calculations.
5. **Four-eyes approval.** Manual overrides to a rate require one person to create the change and a *different* person to approve it — a standard financial control.

---

## Scope: What's In, What's Out

**In scope:** currency and currency-pair master data, FX fixings, spot and forward rates, date resolution against publication/settlement calendars, averaging, the full currency-role conversion chain, a monetary-item revaluation helper, correction handling, tenant entitlements, manual override approval, and full lineage/audit trail for replay.

**Explicitly out of scope** (owned by other systems): acquiring and cleansing raw market data, unit-of-measure conversion (owned by the UOM library), determining which days a pricing period covers (owned by the PDR library), discounting cash flows to present value (owned by Valuation), GL posting and consolidation (owned by the ERP), hedge accounting and FX exposure aggregation (owned by Risk), and option quanto adjustments (owned by the option pricer). The FX library converts currency — it does not do these other jobs.

---

## The Sixteen Foundational Decisions (D-01 to D-16)

These are treated as already settled — the technical design must implement them as-is, not debate them again.

| # | Decision, in plain terms |
|---|---|
| D-01 | Embedded library, not a microservice. No I/O during conversion. |
| D-02 | Figure out the *correct date* to use **before** looking for a rate — and never confuse "holiday" with "rate is missing." |
| D-03 | Rates are bitemporal and versioned; market snapshots are immutable, so results can always be replayed exactly. |
| D-04 | Every accounting entity must have a functional currency, and it can change over time (effective-dated). |
| D-05 | Mark-to-market valuation (uses *present value* × valuation-date rate) and cash-flow forecasting (uses *future amount* × forward rate) must never be mixed up. |
| D-06 | Correction-handling policy is explicit per use case — a contract settlement can ignore a later correction; accounting might not. |
| D-07 | One single averaging framework for the whole platform, not several competing ones. |
| D-08 | When a trade's pricing is already date-defined by the Pricing Day Resolver (PDR) library, FX averaging reuses those exact dates rather than recalculating its own. |
| D-09 | All math is exact decimal arithmetic — no floating point, anywhere, ever. |
| D-10 | Market-data access is controlled per tenant (licensing), and that restriction propagates through any rate derived from it. |
| D-11 | Manual rate overrides require four-eyes approval (creator ≠ approver) and are reason-coded. |
| D-12 | The overloaded old term "FIXED" is retired; "fixed factor" (e.g. a legal currency peg) and "rate finality" (confirmed/estimated/unresolved) are now two clearly separate concepts. |
| D-13 | Tenant data isolation is automatic and built-in; shared reference data (e.g. ECB rates) can still be common across tenants. |
| D-14 | Every conversion request must declare *why* it's being done (its "purpose") — the same currency pair can legitimately need a different rate for settlement vs. accounting vs. reporting. |
| D-15 | One platform-wide forward-curve interpolation method (`LOG_LINEAR_CARRY` by default) so every consumer agrees on future rates. |
| D-16 | Management reporting currency views are calculated live on read — never stored, so they're never stale. |

---

## Core Vocabulary

- **Currency pair** (e.g. `EUR/USD`): tells you how many units of the *quote* currency (USD) equal one unit of the *base* currency (EUR).
- **Direct rate**: the pair is published as-is by a source. **Inverse rate**: flip a published pair by dividing (1 / rate) when the reverse pair isn't published. **Triangulation**: derive an unpublished pair via a common intermediate currency (e.g. `GBP/INR` from `GBP/USD` × `USD/INR`) — this is what lets the platform support far more pairs than are directly quoted.
- **Spot rate**: today's rate, for immediate settlement. **Forward rate**: a rate agreed today for a *future* settlement date, which normally differs from spot because of interest-rate differences between the two currencies (not a prediction — a market-observed price).
- **Monetary item** (cash, receivables, payables) vs. **non-monetary item** (inventory, prepayments) — accounting standards apply different FX rules to each.
- **Fixing**: an officially published rate from a named source (ECB, WMR, BOE, RBI, etc.), which goes through a lifecycle: `PRELIMINARY` → `OFFICIAL` → sometimes later `CORRECTED`.
- **FX Policy**: the "recipe" that tells the engine exactly how to get a rate for a given situation — which source to try, what to do on a holiday, how to handle a correction, which interpolation method, how to average.

---

## Reference Data (The Slow-Changing Setup Information)

Reference data tells the library *how to behave*; it doesn't contain actual rates. It includes: the currency master (codes, minor units, validity); the currency-pair master (which pairs are supported, direct vs. triangulated); special currency relationships (pegs, fixed factors, e.g. the historic HRK→EUR conversion); publication calendars per source (so the engine knows when a source is *expected* to publish); the list of market-data sources and their priority/entitlement rules; accounting units (legal entities, each with a functional currency); accounting policies and FX policies; and versioning so that reference data can change over time while old calculations still use the data that was valid back then.

---

## Market Data and the Correction Problem

Spot and forward rates come from named sources (ECB, WMR, Bloomberg, Reuters, internal treasury). A fixing can be revised after first publication — e.g. `EUR/USD` published as `1.0800` at 14:15 gets corrected to `1.0810` the next morning. The library keeps **both** the business date the rate applies to *and* the moment the platform learned about each version (this is "bitemporal" data), so a month-end valuation that used `1.0800` can always be reproduced exactly, even years later, regardless of later corrections. A calculation can be deliberately "pinned" to one immutable market-data snapshot (e.g. `MONTH_END_2026_03`) so every conversion inside that run is guaranteed consistent.

---

## Date Resolution: Holiday vs. Missing Data

Before the engine can look up a rate, it must determine the *correct date* to use — and this is one of the most important distinctions in the whole specification:

- **Holiday** — the source genuinely never intended to publish on this date (e.g. ECB on Christmas Day). This is normal and is resolved by policy (use previous business day, use next, require exact date, or use nearest).
- **Missing data** — the source *should* have published on this date but didn't. This is a real market-data problem, not a calendar quirk.

Confusing the two is a common and serious bug in FX systems: a holiday must never trigger the "something's broken" fallback logic. Each source has its own publication calendar, and date resolution always uses the calendar of the *selected* source. This logic also governs averaging windows, so a holiday inside an averaging period doesn't create a false gap.

---

## FX Policies, Purposes, and the Conversion Chain

An **FX policy** bundles together the source-selection order, fallback rules, correction-handling rule, interpolation method, and averaging rule for a given scenario — it's configuration, not code, so behaviour can evolve without a software release.

Every conversion request must declare its **purpose** (Settlement, Accounting Recognition, Accounting Revaluation, Translation, Valuation, Exposure, Reporting, etc.), because the same `USD→EUR` conversion can legitimately need a different date and a different rate depending on *why* it's being done. For example, accounting recognition might use the recognition date's rate while valuation must use the valuation date's rate.

The library models the full **currency-role chain** as independent stages, each with its own rules — Price → Settlement → Functional → Presentation — rather than collapsing them into one conversion. This prevents a surprisingly common class of bug where systems blend currencies that should have been kept distinct, causing accounting errors, reporting mismatches, and audit findings.

---

## Averaging and Pricing-Day Integration

Many commodity and energy contracts price against an *average* rate over a period (e.g. "average EUR/USD across all June trading days") rather than a single rate. Instead of building a different averaging calculator for every team, the library has **one unified averaging framework** built from four interchangeable parts: which *dates* participate (the observation set), how much each date *counts* (equal, volume-weighted, or custom weighting), *how* the values combine (arithmetic or weighted average), and what *shape* the result takes (a single rate, a single converted amount, or a full day-by-day breakdown for audit).

Critically, when a commodity contract's pricing period is already defined by the platform's **Pricing Day Resolver (PDR)** — which determines things like "all EEX trading days in June" — the FX library reuses *exactly those same dates* rather than recomputing its own date set. If FX and pricing disagreed on which days counted, the commodity price average and the FX average would silently diverge, producing wrong valuations.

---

## The Rate Resolution Engine (The Core Logic)

Given a currency pair, a date, a policy, and a purpose, the engine works through a defined sequence: resolve the date → pick the policy → pick the source → find the pair (direct, inverse, or triangulated) → apply the correct fixing version → apply interpolation if needed → return the result. Every result carries a **rate finality** status:

- **CONFIRMED** — obtained directly from authoritative, settled data.
- **ESTIMATED** — derived via fallback, interpolation, or approximation.
- **UNRESOLVED** — no valid rate could be determined at all.

Alongside the number, every result carries full **audit information**: which source, which date, which fixing version, which resolution path (direct/inverse/triangulated), and which policy was applied — so any trader, accountant, or auditor asking "why did the system produce this number?" always gets a complete answer.

---

## Forward Curves and Interpolation

Forward curves give FX rates for future dates (e.g. 1M, 3M, 6M, 12M out), and they differ from spot mainly because of interest-rate differences between the two currencies — they are observed market prices, not predictions. When a requested maturity falls *between* two published points (e.g. asking for 2M when only 1M and 3M exist), the engine estimates it via **interpolation**; the platform standardises on one method (`LOG_LINEAR_CARRY`) everywhere, so valuation, risk, and settlement services can never silently disagree on a forward rate. Asking for a date *beyond* the curve's last point (**extrapolation**) is riskier and is controlled by policy (reject, use the last known point, or controlled extrapolation). Every forward calculation records which curve, which tenors, and which method were used.

---

## Entitlements and Data Governance

Market data is often commercially licensed, so not every tenant can see every source (e.g. Tenant A paid for WMR access, Tenant B didn't). Entitlements are checked *during* rate resolution, not after: if the only available rate comes from a source the current tenant isn't entitled to, access is denied even though the data technically exists. Crucially, restrictions **propagate through derived calculations** — if a triangulated or averaged rate was built partly from WMR data, the result still carries the WMR restriction, closing the loophole where someone could bypass a licensing limit just by requesting a "derived" value instead of the raw rate directly. The platform is also strictly multi-tenant: one tenant's overrides, policies, and private market data are never visible to another unless explicitly shared, while some reference data (e.g. ECB rates, currency definitions) is deliberately shared across all tenants.

---

## Corrections, Replay and Determinism

When a published rate is later corrected, different business processes are allowed to react differently — an already-invoiced settlement may legally stay on the original rate, while accounting or reporting may need to pick up the correction. This is why correction-handling is **policy-driven** with three named strategies: *First Official* (always use the first official fixing, ignore later corrections), *Latest Corrected* (always use the newest version), and *Knowledge-Cut* (use whatever version was known as of a specific historical point in time).

Two further guarantees sit on top of this: **replayability** (re-running a historical valuation against its pinned snapshot years later must produce the exact same answer) and **determinism** (the same inputs, policy, and snapshot must always produce the same output, on any server, at any time). Together, bitemporal data, snapshot pinning, and determinism are what let the platform satisfy audit requests like "show me exactly what was used for March's close" with total confidence.

---

## How Other Systems Use It (API Shape)

Services call the library directly in-process — no network hop. Every request must supply, at minimum: the amount, source currency, target currency, purpose, and date; some purposes need extra context (accounting unit, snapshot, forward date, observation set). The response is never just a converted number — it also returns the rate used, the source, the date, the policy, the rate's finality, and full audit metadata explaining the "why." The library also supports **batch** requests (convert thousands of values in one call, for portfolio valuation or settlement runs) and guarantees **idempotent** behaviour — the same request against unchanged data and policy always returns the same answer.

---

## Precision, Rounding, Errors and Non-Functional Expectations

**Precision.** All math uses exact decimal arithmetic end-to-end; rounding happens **once**, at the end of a calculation chain, to the correct number of decimals for the target currency (which varies — JPY has 0 decimals, USD/EUR have 2, KWD has 3). Rounding early and repeatedly introduces cumulative error, so the library deliberately avoids it.

**Errors.** The library fails fast and explicitly rather than guessing — an unknown currency or a missing rate returns a specific, actionable error code (e.g. `RATE_NOT_FOUND`, `UNSUPPORTED_CURRENCY`, `ACCESS_DENIED`, `DATE_RESOLUTION_FAILED`) rather than a silent default or a generic failure. Because the library is in-process, most failures are configuration or data problems, not transient network issues — retrying won't help, the underlying issue needs fixing.

**Non-functional requirements.** The library must support very high conversion volumes (potentially millions per day) with low, predictable, in-memory latency; it must keep working even if upstream market-data or reference-data systems are temporarily down (using cached data); it must be fully auditable without digging through logs; and new sources, policies, or currencies should be addable through configuration and reference data rather than code changes.

---

## Testing and Verification

The spec defines a layered testing strategy — unit tests for individual pieces (date resolver, interpolation, triangulation), component tests for groups working together, integration tests end-to-end, and a set of **golden scenarios**: fixed input/output pairs covering direct conversion, inverse conversion, triangulation, holiday resolution, each correction-policy variant, averaging, forward interpolation, entitlement denial, and historical replay. Any future implementation must reproduce these exact results. Determinism and replay are tested on an ongoing basis, not just once at launch.

---

## What's Deliberately Left Open

A handful of questions are flagged as business decisions still to be made, not resolved by this specification — for example, which default source MTM should use, how long FX fixings and snapshots should be retained in memory, and how onshore vs. offshore currency variants (like CNY vs. CNH) should be modelled. The specification requires the technical design to **surface** these questions, not quietly pick an answer. Longer-term, the architecture is intentionally left open to future extension — more market-data sources, additional interpolation and averaging methods, real-time streaming, and even support for other asset classes beyond FX — all without needing to redesign the core engine.

---

## The One-Paragraph Takeaway

The FX Conversion Library is the platform's single, embedded, auditable engine for every currency conversion a CTRM/ETRM system needs. It is fast because it never leaves memory, correct because it treats holidays, corrections, and purposes as genuinely different situations, reproducible because every calculation can be pinned to an immutable snapshot and replayed exactly, and trustworthy because every single number it returns comes with a complete, explainable record of exactly which rate, source, date, and policy produced it.
