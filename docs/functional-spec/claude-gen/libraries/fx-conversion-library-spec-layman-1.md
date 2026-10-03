# FX Conversion Library — Functional Specification (Layman Version)

## 0. How To Read This Document

### Purpose of This Document

This document explains the FX (Foreign Exchange) Conversion Library in plain English.

The original functional specification is written primarily for experienced engineers, architects, and technical specification generation tools. While precise, it contains many technical and financial concepts that may be difficult for a new engineer, business analyst, tester, or product owner to understand.

This layman version preserves the same requirements and business rules but explains them using:

- simpler language;
- practical examples;
- business context;
- CTRM/ETRM trading scenarios;
- explanations of why each requirement exists.

This document should help readers understand:

- what the library does;
- why it exists;
- how it will be used by other systems;
- what business problems it solves;
- what the important design decisions mean.

The original specification remains the authoritative source of requirements. This document is intended to improve understanding, not change behavior.

---

### Requirement Keywords

Throughout the specification, certain words have special meanings.

| Term | Meaning |
|--------|--------|
| MUST | Mandatory requirement. The implementation has no choice. |
| MUST NOT | Strictly prohibited. |
| SHOULD | Strong recommendation. Exceptions may exist but must be justified. |
| MAY | Optional capability. |

Example:

```text
The library MUST support effective-dated functional currencies.
```

This means every implementation is required to support this capability.

---

### Decisions vs Open Questions

The specification contains two important concepts:

#### Decisions (D-xx)

A decision represents something that has already been agreed and approved.

Example:

```text
D-01
The library must be embedded and perform no database calls during conversion.
```

The technical design must implement this exactly as specified.

These decisions are considered final.

---

#### Open Questions (OQ-xx)

Open questions are areas where the business has not yet made a final decision.

Example:

```text
Should MTM use WMR rates or internal EOD rates by default?
```

The technical specification must highlight these questions and seek clarification.

It must not silently choose an answer.

---

### What Is Meant By "Library First"?

The FX Conversion Library follows a platform principle called:

```text
Library First
```

This means the FX engine is not a standalone microservice.

Instead, it runs inside other applications.

Example:

```text
Valuation Service
    └── FX Library

Settlement Service
    └── FX Library

Reporting Service
    └── FX Library
```

When a system needs an FX conversion, it calls the library directly instead of making a network request to another service.

Benefits:

- Faster execution
- Lower infrastructure cost
- No network latency
- No REST failures
- Easier horizontal scaling

---

### Important Platform Concepts

The library follows several platform-wide design principles.

#### In-Memory Processing

The conversion path performs all calculations using data already loaded into memory.

During a conversion request, the library should not:

- call a database;
- call another service;
- call an external API;
- wait for network traffic.

This ensures consistent and predictable performance.

---

#### Bitemporal Data

FX rates sometimes change after they are published.

Example:

```text
2-Apr 14:15
EUR/USD = 1.0800

3-Apr 09:00
Correction issued
EUR/USD = 1.0810
```

The library must remember:

1. Which rate belongs to the business date.
2. When the system learned about that rate.

This allows historical results to be reproduced exactly.

---

#### Snapshot Pinning

A valuation run should always use a consistent set of market data.

Example:

```text
Month-End Valuation
Snapshot = SNAPSHOT_2026_03_31
```

Every conversion during that valuation run uses the same market data snapshot.

Even if newer FX rates arrive later, the valuation result remains reproducible.

---

#### Decimal Arithmetic

Financial calculations must be deterministic.

The library uses decimal arithmetic rather than floating-point calculations.

Reason:

```text
Financial numbers must produce identical results
on every machine and every execution.
```

---

#### Four-Eyes Approval

Certain reference data changes require approval by a second person.

Example:

```text
Person A creates an FX override.

Person B approves it.
```

The creator cannot approve their own change.

This is a common control requirement in regulated financial systems.

---

### What Business Problem Does This Library Solve?

Many CTRM/ETRM systems perform FX conversions in different ways.

Typical problems include:

- different teams using different rates;
- inconsistent holiday handling;
- different forward interpolation logic;
- accounting using one rate while reporting uses another;
- inability to reproduce historical results;
- audit issues caused by corrected fixings.

The goal of this library is to provide:

```text
One platform-wide source of truth
for all FX conversions.
```

Every consumer uses the same rules, the same calculations, and the same market data lineage.

---

## 1. Purpose And Scope

### What Is The FX Conversion Library?

The FX Conversion Library converts monetary values from one currency into another.

Examples:

```text
USD → EUR
GBP → INR
EUR → JPY
```

However, the library does much more than simple currency conversion.

It also understands:

- accounting rules;
- settlement rules;
- valuation requirements;
- market-data policies;
- FX fixing rules;
- forward curves;
- averaging methodologies;
- reporting currencies.

The library determines:

1. Which FX rate should be used.
2. Which date should be used.
3. Which market data source should be used.
4. Which conversion path should be used.
5. Why the conversion result was produced.

---

### Why Does A CTRM Need This?

Commodity trading systems work with multiple currencies simultaneously.

Example:

```text
Natural Gas Price:
GBP

Invoice Currency:
EUR

Legal Entity Functional Currency:
USD

Group Reporting Currency:
CHF
```

A single trade may therefore require multiple FX conversions.

Without a centralized engine:

- each application may calculate differently;
- accounting and valuation results may diverge;
- audits become difficult.

The FX Conversion Library ensures consistency across the platform.

---

### Currency Role Chain

One of the most important concepts in the specification is the currency-role chain.

```text
Price Currency
        ↓
Settlement Currency
        ↓
Functional Currency
        ↓
Presentation Currency
```

---

#### Price Currency

The currency in which a commodity price is quoted.

Example:

```text
Brent Oil = USD 80 / barrel
```

Price Currency = USD

---

#### Settlement Currency

The currency used for invoicing and cash movement.

Example:

```text
Commodity Price:
USD

Invoice:
EUR
```

Settlement Currency = EUR

---

#### Functional Currency

The operating currency of a legal entity.

Example:

```text
German Subsidiary:
EUR

Indian Subsidiary:
INR

US Subsidiary:
USD
```

Each entity may have a different functional currency.

This is important for accounting and financial reporting.

---

#### Presentation Currency

The currency used for financial statements.

Example:

```text
Global Group Reporting:
USD
```

Even if subsidiaries operate in many currencies, the final consolidated reports may be presented in USD.

---

### Where Is The Library Used?

The FX Conversion Library is designed to be embedded inside multiple business services.

Typical consumers include:

#### Valuation Service

Used to:

- convert present values;
- convert mark-to-market results;
- convert risk metrics.

---

#### Settlement Service

Used to:

- calculate invoice values;
- determine payment amounts;
- convert cash flows.

---

#### Accounting Service

Used to:

- convert transactions into functional currency;
- support revaluation;
- produce accounting feeds.

---

#### Reporting Service

Used to:

- display values in reporting currencies;
- generate management reports;
- support consolidated reporting.

---

#### Exposure Service

Used to:

- calculate FX exposures;
- aggregate positions;
- support risk calculations.

---

### What Is Included In Scope?

The library is responsible for the following capabilities.

---

#### Currency Master Data

Maintaining information about currencies.

Examples:

```text
USD
EUR
GBP
JPY
INR
```

Including:

- validity periods;
- minor units;
- currency relationships;
- legal pegs.

---

#### Pair Resolution

Determining how to obtain an FX rate.

Examples:

```text
EUR/USD
GBP/INR
EUR/JPY
```

including:

- direct rates;
- inverse rates;
- triangulated rates.

---

#### FX Fixings

Supporting official published FX rates.

Examples:

```text
ECB
WMR
BOE
RBI
```

including:

- preliminary versions;
- official versions;
- corrected versions.

---

#### Spot And Forward Rates

Supporting:

```text
Current FX Rates
(Spot)

Future FX Rates
(Forward)
```

including interpolation between forward maturities.

---

#### Date Resolution

Determining the correct FX date before looking up a rate.

Example:

```text
Requested Date:
Sunday

Resolved Date:
Previous Friday
```

according to policy.

---

#### Averaging

Supporting average FX calculations across multiple observations.

Examples:

```text
Monthly Average Rate

Pricing Period Average

Commodity Pricing Set Average
```

---

#### Accounting Conversions

Supporting:

- recognition;
- settlement;
- revaluation;
- valuation-date conversion;
- translation.

---

#### Correction Handling

Supporting the reprocessing of results when historical FX rates are corrected.

---

#### Entitlements

Ensuring users only access market data sources they are permitted to use.

---

#### Replay And Auditability

Supporting exact historical reproduction of prior calculations.

---

### What Is Not Included?

The library deliberately excludes several responsibilities.

These functions belong to other systems.

| Responsibility | Owned By |
|---------------|----------|
| Market-data acquisition | Market-data platform |
| UOM conversion | UOM Conversion Library |
| Pricing day determination | PDR Library |
| Discounting cashflows | Valuation Service |
| GL posting | ERP / Accounting System |
| Consolidation processing | Consolidation Platform |
| Hedge accounting | Accounting System |
| FX exposure aggregation | Risk Platform |
| Option pricing adjustments | Pricing Engine |

---

### Simple Example

Suppose a trader enters:

```text
Commodity:
Natural Gas

Price:
100 GBP

Settlement Currency:
EUR

Legal Entity Functional Currency:
USD

Reporting Currency:
CHF
```

The library may perform:

```text
GBP
  ↓
EUR
  ↓
USD
  ↓
CHF
```

while applying:

- correct market data;
- correct accounting policy;
- correct FX date;
- correct source entitlement;
- correct snapshot.

The result is a fully traceable and auditable conversion process.

---

### Key Takeaway

The FX Conversion Library is the platform's central engine for all foreign-exchange calculations.

Its primary goals are:

- consistency;
- auditability;
- determinism;
- replayability;
- accounting correctness;
- high performance.

Every CTRM service should obtain FX conversions from this library rather than implementing its own FX logic.

# 2. Key Design Decisions

## Why This Section Exists

Before building a library of this size, several important architectural and business decisions must be agreed.

Without these decisions:

- different developers may implement different behaviors;
- multiple teams may interpret requirements differently;
- future changes may accidentally break accounting or valuation logic.

The decisions in this section are considered **final and mandatory**.

The technical design must implement them exactly as described.

Think of these as the "constitution" of the FX Conversion Library.

---

# D-01 Library First Architecture

## What This Means

The FX Conversion Library is not a standalone microservice.

Instead, it is a Java library that runs directly inside other applications.

Example:

```text
Valuation Service
    └── FX Library

Settlement Service
    └── FX Library

Reporting Service
    └── FX Library
```

Applications call the library directly instead of making REST calls.

---

## Why We Chose This

A currency conversion is a very frequent operation.

A large CTRM platform may perform:

```text
Millions of FX conversions per day
```

Calling a remote service for every conversion would create:

- network latency;
- serialization overhead;
- scaling challenges;
- additional failure points.

Direct library calls are much faster and simpler.

---

## Important Rule

During a conversion:

```text
NO database calls
NO REST calls
NO message queues
NO external APIs
```

All required data must already be available in memory.

---

## Real-World Example

Bad:

```text
Valuation Service
        ↓
REST Call
        ↓
FX Service
        ↓
Database
```

Good:

```text
Valuation Service
        ↓
FX Library
        ↓
Memory Cache
```

---

# D-02 Date Resolution Happens Before Rate Lookup

## What This Means

Before looking for an FX rate, the library must first determine the correct FX date.

The requested date is not always a valid publication date.

Example:

```text
Requested Date:
Sunday

ECB publishes:
Monday-Friday only
```

The library must first move to a valid publication date.

Only after that may it search for an FX rate.

---

## Why This Matters

Many systems incorrectly treat holidays as missing market data.

These are different situations.

### Holiday

```text
ECB never intended to publish a rate.
```

Not an error.

---

### Missing Data

```text
ECB should have published a rate
but did not.
```

Potential problem.

---

The library must distinguish these cases.

---

## Example

Input:

```text
Date:
Sunday 8-Mar

Policy:
USE_PREVIOUS
```

Result:

```text
Resolved Date:
Friday 6-Mar
```

The Friday fixing is considered valid and confirmed.

No fallback processing occurs.

---

## Key Principle

```text
Holiday ≠ Missing Rate
```

A holiday should never trigger rate fallback logic.

---

# D-03 Bitemporal Fixings And Snapshot Pinning

## What This Means

FX rates can change after publication.

Example:

```text
2-Apr
EUR/USD = 1.0800

3-Apr
Correction:
EUR/USD = 1.0810
```

The system must remember both:

1. Which business date the fixing belongs to.
2. When the system learned about that fixing.

This is called:

```text
Bitemporal Data
```

---

## Why This Matters

Suppose month-end valuation ran on:

```text
31-Mar
```

using:

```text
EUR/USD = 1.0800
```

Six months later the rate is corrected.

Audit asks:

```text
Show me exactly what month-end used.
```

The system must still reproduce:

```text
1.0800
```

not the corrected value.

---

## Snapshot Pinning

A valuation run uses a specific market-data snapshot.

Example:

```text
SNAPSHOT_2026_03_31
```

Every conversion during that run uses:

- identical spot rates;
- identical forward curves;
- identical fixing versions.

---

## Result

Historical calculations become fully reproducible.

---

# D-04 Functional Currency Is Mandatory

## What This Means

Every accounting unit must have a functional currency.

Example:

```text
US Entity:
USD

German Entity:
EUR

Indian Entity:
INR
```

The functional currency represents the primary operating currency of the business.

---

## Why This Matters

Accounting rules such as:

```text
IAS 21
Ind AS 21
ASC 830
```

require transactions to be measured relative to the functional currency.

Without it:

- revaluation becomes impossible;
- realized FX cannot be calculated correctly;
- unrealized FX cannot be calculated correctly.

---

## Additional Requirement

Functional currencies may change over time.

Example:

```text
2024-01-01 → EUR

2027-01-01 → USD
```

The library must support effective-dated changes.

---

# D-05 MTM And Cash Projection Use Different Logic

## What This Means

Mark-to-market valuation and cash forecasting are different business activities.

The library must treat them differently.

---

## MTM Example

Suppose a future cashflow is:

```text
USD 1,000,000
```

and its discounted present value is:

```text
USD 950,000
```

MTM conversion must use:

```text
USD Present Value
        ×
Valuation-Date FX
```

---

## Cash Projection Example

For future cash forecasting:

```text
Future USD Amount
        ×
Forward FX Rate
```

is appropriate.

---

## Why This Matters

Many systems accidentally use:

```text
Future Cashflow
        ×
Today's Spot Rate
```

which can produce materially incorrect valuation results.

---

## Key Principle

```text
MTM → Present Value

Cash Projection → Future Amount
```

Never mix them.

---

# D-06 Correction Policies Must Be Explicit

## What This Means

Different business processes handle corrections differently.

Example:

```text
Contract Settlement
```

may use:

```text
First Official Rate
```

and never change later.

---

While:

```text
Accounting
```

may use:

```text
Latest Corrected Rate
```

and therefore reflect later corrections.

---

## Why This Matters

Not every correction should rewrite history.

A previously invoiced transaction may legally remain unchanged.

---

## Supported Policies

### First Official

Use the first official fixing.

Ignore later corrections.

---

### Latest Corrected

Always use the newest corrected fixing.

---

### As Of Knowledge

Use whatever version was known at a specified historical point in time.

---

## Business Benefit

Different consumers can apply different correction policies without changing the underlying market data.

---

# D-07 One Averaging Framework

## What This Means

Historically, many systems create multiple averaging mechanisms.

Examples:

```text
Monthly Average
Pricing Average
Weighted Average
Settlement Average
```

which eventually become difficult to maintain.

---

The library uses one unified framework.

Each average is built from:

```text
Observation Set
+
Weighting
+
 Averaging Method
+
Output Shape
```

---

## Why This Matters

New averaging styles can be added without redesigning the system.

---

## Example

Monthly average EUR/USD:

```text
Observation Set:
Business Days

Weighting:
Equal

Method:
Rate Average
```

Same framework.

---

# D-08 Pricing-Linked FX Uses PDR Data

## What This Means

Some commodity trades use pricing periods.

Example:

```text
Average of all trading days in June
```

The Pricing Day Resolver (PDR) already determines those dates.

The FX library must reuse those dates.

---

## Why This Matters

Without this rule:

```text
Pricing Engine
```

and

```text
FX Engine
```

could average different sets of days.

That would create valuation inconsistencies.

---

## Key Principle

```text
One pricing-day definition
used everywhere.
```

---

# D-09 Decimal Arithmetic Only

## What This Means

All calculations use decimal arithmetic.

Examples:

```text
BigDecimal
DECIMAL128
```

Floating-point arithmetic is forbidden.

---

## Why This Matters

Floating-point math can produce slightly different answers.

Example:

```text
0.1 + 0.2
```

is not always exactly:

```text
0.3
```

in binary floating-point systems.

---

## Financial Requirement

A valuation run must produce:

```text
Exactly the same answer
every time
on every server
```

---

## Key Principle

```text
Financial Accuracy
beats Computational Convenience
```

---

# D-10 Tenant Entitlements Control Market Data Access

## What This Means

Not every customer can access every market-data source.

Example:

```text
Tenant A:
WMR + ECB

Tenant B:
ECB Only
```

The library must respect those permissions.

---

## Why This Matters

Market-data vendors often license data separately.

A customer must not accidentally receive data they did not purchase.

---

## Example

If a rate is derived from:

```text
WMR
```

the resulting conversion inherits the same restriction.

---

## Key Principle

```text
Data Restrictions
must propagate through calculations.
```

---

# D-11 Manual Overrides Require Four-Eyes Approval

## What This Means

Sometimes a market-data administrator must manually override a rate.

Example:

```text
Published Rate:
1.0800

Temporary Override:
1.0815
```

---

## Requirement

The person creating the override cannot approve it.

Example:

```text
Alice creates override

Bob approves override
```

Valid.

```text
Alice creates override

Alice approves override
```

Not valid.

---

## Why This Matters

This is a standard financial control.

It reduces operational risk and audit findings.

---

# D-12 Remove Ambiguous Terminology

## What This Means

The old specification used the word:

```text
FIXED
```

for multiple meanings.

That caused confusion.

---

The new specification separates:

### Fixed Factor

A permanent conversion factor.

Example:

```text
BGN → EUR
```

using a legally defined rate.

---

### Rate Finality

Whether a result is:

```text
CONFIRMED
ESTIMATED
UNRESOLVED
```

---

## Benefit

Every term now has one meaning.

---

# D-13 Tenant Isolation Is Built In

## What This Means

The library automatically operates within the current tenant context.

Example:

```text
Tenant A
```

cannot see:

```text
Tenant B
```

data.

---

## Why This Matters

The platform is multi-tenant.

Strong isolation is mandatory.

---

## Additional Capability

Shared reference data may exist.

Example:

```text
ECB Rates
```

available to all tenants.

Tenant-specific data remains isolated.

---

# D-14 Every Conversion Must Have A Purpose

## What This Means

A conversion request must explain why it is being performed.

Example:

```text
Settlement
Valuation
Accounting
Reporting
Exposure
```

---

## Why This Matters

Different purposes require different rules.

The same currency pair may legitimately use different rates depending on the business objective.

---

## Example

```text
Accounting
```

may require:

```text
Recognition-Date FX
```

while

```text
Valuation
```

requires:

```text
Valuation-Date FX
```

---

# D-15 One Forward Curve Method For The Platform

## What This Means

All consumers must use the same forward interpolation methodology.

Default:

```text
LOG_LINEAR_CARRY
```

---

## Why This Matters

Different interpolation formulas produce different rates.

If every system implemented its own method:

```text
Valuation
Settlement
Exposure
```

could disagree.

---

## Key Principle

```text
One platform-wide
forward calculation method.
```

---

# D-16 Management Reporting Is A View, Not Stored Data

## What This Means

Management reports may require multiple display currencies.

Example:

```text
USD View
EUR View
INR View
```

---

The library calculates these on demand.

It does not permanently store them.

---

## Why This Matters

Stored reporting values become stale whenever FX rates change.

On-demand calculation ensures reports always reflect the intended reporting policy.

---

# Summary Of All Decisions

| Decision | Main Goal |
|-----------|------------|
| D-01 | Fast embedded architecture |
| D-02 | Correct date determination |
| D-03 | Full replayability and auditability |
| D-04 | Accounting correctness |
| D-05 | Correct MTM and forecasting behavior |
| D-06 | Controlled handling of corrections |
| D-07 | Unified averaging model |
| D-08 | Consistent pricing-day usage |
| D-09 | Deterministic financial calculations |
| D-10 | Entitlement compliance |
| D-11 | Operational control and auditability |
| D-12 | Clear terminology |
| D-13 | Multi-tenant isolation |
| D-14 | Purpose-driven behavior |
| D-15 | Consistent forward calculations |
| D-16 | Flexible reporting views |

These sixteen decisions form the foundation of the entire FX Conversion Library. Every subsequent section of the specification builds upon them.

# 3. Core Currency Concepts

## Why This Section Exists

Before discussing FX rates, forward curves, fixing sources, or accounting policies, we need to establish a common vocabulary.

Many FX conversion errors occur because different teams use the same words to mean different things.

For example:

```text
Currency
Rate
Fixing
Spot
Forward
Functional Currency
Settlement Currency
```

may mean different things to traders, accountants, risk managers, and software engineers.

This section defines the core concepts used throughout the library.

---

# 3.1 Currency

## What Is A Currency?

A currency is a legally recognized monetary unit.

Examples:

```text
USD  United States Dollar
EUR  Euro
GBP  British Pound
JPY  Japanese Yen
INR  Indian Rupee
```

Every monetary value handled by the library must have an associated currency.

Example:

```text
100 USD
250 EUR
10000 INR
```

A number without a currency is not meaningful in an FX system.

---

# 3.2 Currency Pair

## What Is A Currency Pair?

A currency pair describes the relationship between two currencies.

Examples:

```text
EUR/USD
GBP/USD
USD/JPY
EUR/GBP
```

The pair tells us:

```text
How many units of quote currency
equal one unit of base currency.
```

---

### Example

```text
EUR/USD = 1.1000
```

means:

```text
1 EUR = 1.10 USD
```

---

# 3.3 Base Currency And Quote Currency

Every FX pair contains:

```text
Base Currency
Quote Currency
```

Example:

```text
EUR/USD
```

Base Currency:

```text
EUR
```

Quote Currency:

```text
USD
```

---

Meaning:

```text
1 EUR = X USD
```

The FX rate expresses the value of the base currency in terms of the quote currency.

---

# 3.4 Direct And Inverse Rates

## Direct Rate

A direct rate exists when market data publishes the exact pair requested.

Example:

```text
EUR/USD
```

published directly by:

```text
ECB
WMR
Bloomberg
Reuters
```

---

## Inverse Rate

Sometimes the requested pair is not available.

Example:

Requested:

```text
USD/EUR
```

Available:

```text
EUR/USD = 1.1000
```

The inverse becomes:

```text
USD/EUR = 1 / 1.1000
         = 0.909091
```

---

# 3.5 Triangulation

## What Is Triangulation?

Triangulation means deriving a rate using an intermediate currency.

Example:

Requested:

```text
GBP/INR
```

Available:

```text
GBP/USD
USD/INR
```

The library can calculate:

```text
GBP/INR
=
GBP/USD × USD/INR
```

---

## Why This Matters

Many currency pairs are not actively traded.

Triangulation allows the platform to support a much larger set of currency conversions.

---

# 3.6 Spot Rate

## What Is A Spot Rate?

A spot rate is the FX rate for immediate settlement.

Example:

```text
EUR/USD = 1.1045
```

published today.

---

### Business Interpretation

If somebody exchanged currencies right now, the spot rate would normally be used.

Spot rates are typically used for:

- reporting;
- exposure calculations;
- current position valuation;
- accounting revaluation.

---

# 3.7 Forward Rate

## What Is A Forward Rate?

A forward rate is an FX rate for a future date.

Example:

```text
Today:
EUR/USD Spot = 1.1000

3-Month Forward:
EUR/USD = 1.1085
```

---

### Why Forward Rates Exist

Interest-rate differences between currencies create different values for future delivery dates.

Future EUR/USD is usually not identical to today's EUR/USD.

---

### Typical Usage

Forward rates are commonly used for:

- future settlements;
- forecasting;
- exposure modelling;
- projected cashflows.

---

# 3.8 Monetary And Non-Monetary Items

## Monetary Item

A monetary item is an amount that will be received or paid as money.

Examples:

```text
Cash
Receivables
Payables
Loans
Invoices
```

---

## Non-Monetary Item

A non-monetary item is not settled as a fixed amount of money.

Examples:

```text
Inventory
Physical Assets
Goodwill
Equipment
```

---

## Why This Matters

Accounting standards often apply different FX rules to monetary and non-monetary items.

---

# 3.9 Currency Roles

One of the most important concepts in the platform.

A single trade can involve several currencies simultaneously.

---

### Price Currency

Currency used to express commodity prices.

Example:

```text
Power Price = EUR/MWh
```

Price Currency:

```text
EUR
```

---

### Settlement Currency

Currency used for invoicing and payment.

Example:

```text
Commodity Price:
USD

Invoice:
GBP
```

Settlement Currency:

```text
GBP
```

---

### Functional Currency

Primary operating currency of an accounting entity.

Example:

```text
German Entity = EUR

UK Entity = GBP

US Entity = USD
```

---

### Presentation Currency

Currency used in management or financial reporting.

Example:

```text
Group Reporting Currency = USD
```

---

# 3.10 Conversion Chain

A conversion may involve multiple stages.

Example:

```text
Price Currency
      ↓
Settlement Currency
      ↓
Functional Currency
      ↓
Presentation Currency
```

The library treats these stages separately because different business rules may apply at each stage.

---

# 3.11 Fixing

A fixing is an officially published FX rate.

Examples:

```text
ECB Daily Rate
WMR Fix
BOE Rate
RBI Reference Rate
```

Fixings are considered authoritative market data.

---

# 3.12 FX Policy

An FX Policy tells the library how a conversion should be performed.

Example:

```text
Which source?
Which fallback?
Which interpolation?
Which correction policy?
```

The same currency pair may produce different results under different policies.

---

# 4. Reference Data Model

## Why This Section Exists

Reference data describes information that changes infrequently but is essential for conversion decisions.

Examples:

```text
Currencies
Currency Pairs
Accounting Units
Calendars
Policies
Sources
```

Without reference data the library cannot determine how a conversion should behave.

---

# 4.1 Currency Master

## Purpose

Stores information about currencies supported by the platform.

Example:

```text
USD
EUR
GBP
JPY
INR
```

---

### Typical Attributes

```text
Currency Code
Currency Name
Minor Units
Validity Period
Status
```

---

### Example

```text
Currency:
EUR

Minor Units:
2

Valid:
1999 onwards
```

---

# 4.2 Currency Pair Master

Defines supported currency pairs.

Examples:

```text
EUR/USD
GBP/USD
USD/JPY
EUR/GBP
```

---

### Why We Need It

The library must know:

- whether a pair is supported;
- whether it is direct;
- whether triangulation is required;
- whether special handling applies.

---

# 4.3 Currency Relationship Definitions

Certain currencies have special relationships.

Examples:

```text
Currency Pegs

Fixed Conversion Factors

Legal Monetary Unions
```

---

### Example

Historically:

```text
HRK → EUR
```

required legally defined conversion treatment.

---

# 4.4 Publication Calendars

## Purpose

Defines when a market-data source is expected to publish rates.

Example:

```text
ECB
Monday-Friday
```

---

### Why This Matters

The library must distinguish:

```text
Holiday
```

from

```text
Missing Data
```

Publication calendars enable this distinction.

---

# 4.5 Market Data Sources

Defines available FX providers.

Examples:

```text
ECB
WMR
Bloomberg
Reuters
Internal Treasury
```

---

### Stored Information

```text
Source Name
Priority
Entitlement Rules
Correction Rules
Availability
```

---

# 4.6 Accounting Units

Represents legal entities that perform accounting.

Example:

```text
UK Subsidiary

Functional Currency:
GBP
```

---

### Why This Matters

Accounting conversions depend heavily on the functional currency of the accounting unit.

---

# 4.7 Accounting Policies

Defines accounting-specific FX behaviour.

Examples:

```text
Recognition
Settlement
Revaluation
Translation
```

---

### Example

Accounting may require:

```text
Recognition Date FX
```

while reporting requires:

```text
Valuation Date FX
```

---

# 4.8 FX Policies

An FX policy defines the business rules used during conversion.

Examples:

```text
Source Selection

Fallback Rules

Forward Curve Rules

Correction Rules

Averaging Rules
```

---

# 4.9 Versioning And Effective Dating

## What This Means

Reference data can change over time.

Example:

```text
Functional Currency

EUR
    ↓
USD
```

effective:

```text
1-Jan-2027
```

---

### Requirement

The library must understand historical and future versions of reference data.

---

### Why This Matters

Historical calculations must use the reference data that was valid at that time.

---

# 5. Market Data And Fixings

## Why This Section Exists

Reference data tells us how to behave.

Market data provides the actual FX rates.

Without market data:

```text
EUR/USD
```

cannot be converted.

This section explains how the library manages FX rates and their lifecycle.

---

# 5.1 What Is Market Data?

Market data represents exchange rates published by market participants or market-data vendors.

Examples:

```text
EUR/USD
GBP/USD
USD/JPY
```

---

### Sources

Examples:

```text
ECB
WMR
Bloomberg
Reuters
```

---

# 5.2 Spot Rates

Spot rates represent current FX values.

Example:

```text
EUR/USD = 1.1025
```

---

### Usage

Commonly used for:

- reporting;
- revaluation;
- exposure;
- current valuations.

---

# 5.3 Forward Rates

Forward rates represent future FX values.

Example:

```text
1M EUR/USD
3M EUR/USD
6M EUR/USD
```

---

### Example

```text
Spot:
1.1000

3M Forward:
1.1075
```

---

# 5.4 Fixings

A fixing is an official published FX rate.

Examples:

```text
ECB Daily Fix

WMR Closing Fix
```

---

### Why Fixings Matter

Many business processes require official rates rather than live market prices.

Examples:

```text
Accounting

Settlement

Regulatory Reporting
```

---

# 5.5 Fixing Lifecycle

A fixing may evolve through several stages.

---

### Preliminary

Initial publication.

Example:

```text
PRELIMINARY
```

---

### Official

Published and confirmed.

Example:

```text
OFFICIAL
```

---

### Corrected

A correction published after the official release.

Example:

```text
CORRECTED
```

---

# Example

```text
09:00
PRELIMINARY

12:00
OFFICIAL

Next Day
CORRECTED
```

---

# 5.6 Why Corrections Matter

Suppose month-end valuation used:

```text
EUR/USD = 1.0800
```

Later:

```text
EUR/USD = 1.0810
```

is published as a correction.

The platform must determine:

```text
Should history change?

Should accounting rerun?

Should reporting rerun?
```

The answer depends on correction policy.

---

# 5.7 Bitemporal Market Data

The library stores two timelines.

---

### Business Time

When the fixing belongs.

Example:

```text
31-Mar
```

---

### Knowledge Time

When the platform learned about it.

Example:

```text
2-Apr
```

---

## Why This Matters

This allows exact reconstruction of historical calculations.

---

# 5.8 Snapshot Pinning

A calculation may be tied to a specific market-data snapshot.

Example:

```text
MONTH_END_2026_03
```

---

### Benefit

Every calculation uses:

- identical rates;
- identical fixing versions;
- identical forward curves.

This guarantees reproducibility.

---

# 5.9 Market Data Quality

Not all rates are equal.

The platform tracks:

```text
Source
Version
Status
Origin
Lineage
```

---

### Why This Matters

Every conversion result should answer:

```text
Which rate was used?

Which source published it?

Which version was selected?

Why was it selected?
```

---

# 5.10 Key Principle

Market data is not just a number.

Every FX rate carries:

```text
Business Meaning
Publication Context
Version History
Audit Lineage
```

The FX Conversion Library preserves all of this information so that every conversion remains explainable, reproducible, and auditable.

# 6. Date Resolution

## Why This Section Exists

One of the most common mistakes in FX systems is assuming that the requested date automatically contains a valid FX rate.

In reality:

```text id="o9q8j1"
Not every calendar day
contains a published FX fixing.
```

Examples:

- weekends;
- public holidays;
- source-specific holidays;
- market closures;
- missing publications.

Before the library can find an FX rate, it must first determine:

```text id="j3g7mz"
Which date should actually be used?
```

This process is called:

```text id="8yn6zt"
Date Resolution
```

---

# 6.1 What Is Date Resolution?

Date Resolution converts a requested date into a valid market-data date.

Example:

Requested:

```text id="i2r8lw"
Sunday
2026-03-08
```

ECB publication calendar:

```text id="5qn4sy"
Friday
2026-03-06

Monday
2026-03-09
```

Resolved Date:

```text id="d3f9uh"
Friday
2026-03-06
```

The library performs this step before any rate lookup occurs.

---

# 6.2 Why Date Resolution Is Needed

Imagine a trader requests:

```text id="0b9vhx"
EUR/USD
Date = Christmas Day
```

The ECB does not publish rates on Christmas Day.

This does not mean:

```text id="s8r2nv"
Rate Missing
```

Instead it means:

```text id="z1h8pt"
No rate was expected.
```

These are completely different situations.

---

# 6.3 Holiday vs Missing Data

This distinction is extremely important.

---

### Holiday

Example:

```text id="m4d9rb"
25-Dec
ECB Holiday
```

Expected publication:

```text id="g8x0wp"
No
```

Result:

```text id="2v9wls"
Move according to policy.
```

---

### Missing Data

Example:

```text id="x4r3gb"
15-Jun
ECB Business Day
```

Expected publication:

```text id="r1k7zu"
Yes
```

Actual publication:

```text id="n9v0eq"
Missing
```

Result:

```text id="c7u5rm"
Potential market-data issue.
```

---

## Key Principle

```text id="d5w8qp"
Holiday ≠ Missing Data
```

The library never treats a holiday as missing market data.

---

# 6.4 Date Resolution Policies

Different businesses prefer different behaviours.

The library supports configurable policies.

---

### Previous Business Day

Move backward until a valid publication date is found.

Example:

```text id="4w3cxo"
Requested:
Sunday

Resolved:
Previous Friday
```

---

### Next Business Day

Move forward until a valid publication date is found.

Example:

```text id="f8h6np"
Requested:
Sunday

Resolved:
Monday
```

---

### Exact Date Only

Require publication on the requested date.

Example:

```text id="w0j2zx"
Requested:
Sunday

Result:
Error
```

---

### Nearest Publication Date

Select the closest valid publication date.

Example:

```text id="m3k9er"
Requested:
Sunday

Friday = 2 days away

Monday = 1 day away

Use Monday
```

---

# 6.5 Publication Calendars

Each source may have a different calendar.

Example:

```text id="a7y5un"
ECB Calendar

WMR Calendar

BOE Calendar

RBI Calendar
```

A valid date for one source may not be valid for another.

---

## Example

```text id="q2v4tm"
ECB Holiday

Internal Treasury Open
```

Date resolution must always use the calendar associated with the selected source.

---

# 6.6 Date Resolution Flow

The library follows a standard sequence.

```text id="9e7bmc"
Requested Date
        ↓
Determine Source
        ↓
Load Source Calendar
        ↓
Resolve Date
        ↓
Validate Publication Date
        ↓
Continue To Rate Lookup
```

---

# 6.7 Date Resolution And Averaging

Date resolution also applies to averaging.

Example:

```text id="l6z2we"
Monthly Average
```

If a holiday appears inside the averaging window:

```text id="e7n1gx"
Do not create a missing value.
```

Instead:

```text id="p0x8qj"
Use valid publication dates
defined by the observation set.
```

---

# 6.8 Key Takeaway

Date resolution determines:

```text id="w9g4yk"
Which date should be used
before searching for an FX rate.
```

Without this step, the library cannot reliably distinguish:

- holidays;
- weekends;
- publication gaps;
- true missing data.

---

# 7. FX Policies

## Why This Section Exists

Two teams can request:

```text id="q6k9zs"
EUR/USD
```

for the same date and legitimately receive different answers.

Why?

Because they may be using different business policies.

An FX policy defines:

```text id="e5t3mw"
How a conversion should behave.
```

---

# 7.1 What Is An FX Policy?

An FX Policy is a collection of business rules.

Examples:

```text id="n4j1yu"
Which source?

Which fallback?

Which interpolation?

Which correction policy?

Which averaging method?
```

---

## Analogy

Think of an FX policy as:

```text id="g7c5nv"
A recipe for obtaining an FX rate.
```

The currency pair alone is not enough.

The policy explains how the answer should be calculated.

---

# 7.2 Why Policies Are Necessary

Different consumers often require different behaviour.

Example:

```text id="o1r8wd"
Accounting

Risk

Settlement

Management Reporting
```

Each may have different requirements.

---

### Accounting Example

Use:

```text id="v8s4yn"
Official ECB Rate
```

---

### Trading Example

Use:

```text id="m6t0jc"
Latest Available Market Rate
```

---

Both are valid.

The policy determines which one applies.

---

# 7.3 Source Selection

The first responsibility of an FX policy is source selection.

Examples:

```text id="s5g3dh"
ECB

WMR

Reuters

Bloomberg

Internal Treasury
```

---

### Example Policy

```text id="r2w8lk"
Primary:
ECB

Fallback:
WMR
```

The library attempts ECB first.

Only if policy allows, it considers WMR.

---

# 7.4 Fallback Policies

Sometimes a rate cannot be obtained.

Examples:

```text id="y8m1qu"
Source unavailable

Pair unavailable

Publication missing
```

Fallback policies determine what happens next.

---

### Example

```text id="x9h2ge"
Try ECB

If unavailable

Try WMR

If unavailable

Try Internal Treasury
```

---

# 7.5 Correction Policies

Fixings may be corrected after publication.

Example:

```text id="w7r5kn"
Original:
1.0800

Corrected:
1.0810
```

Different consumers may handle this differently.

---

### First Official

Use the first official fixing.

Ignore later corrections.

---

### Latest Corrected

Always use the newest corrected version.

---

### Knowledge-Cut

Use whatever version was known at a specific point in time.

---

# 7.6 Interpolation Policies

Forward curves may not contain every date.

Example:

```text id="h3v9mj"
1M

3M

6M
```

Requested:

```text id="a4q7ur"
2M
```

The library must estimate a rate.

This process is called:

```text id="t5n2xp"
Interpolation
```

---

### Policy Example

```text id="n8y4kr"
LOG_LINEAR_CARRY
```

The platform standard.

---

# 7.7 Averaging Policies

Policies also define averaging behaviour.

Examples:

```text id="b5f0qx"
Monthly Average

Pricing Period Average

Weighted Average
```

---

### Policy Components

```text id="g6u1zw"
Observation Set

Weighting

Method

Output Shape
```

---

# 7.8 Accounting Policies

Accounting often requires dedicated FX behaviour.

Examples:

```text id="x7d2mh"
Recognition

Settlement

Revaluation

Translation
```

Each may require a different date and rate source.

---

# 7.9 Policy Versioning

Policies can change over time.

Example:

```text id="v1q7pb"
2026:
ECB

2027:
WMR
```

The library supports effective-dated policy versions.

---

## Why This Matters

Historical calculations must use the policy that was valid at that time.

---

# 7.10 Key Takeaway

An FX policy answers:

```text id="m8z5yr"
How should this conversion
be performed?
```

without changing application code.

Policies allow behaviour to evolve through configuration rather than software releases.

---

# 8. Conversion Purposes And Currency Chains

## Why This Section Exists

A common misconception is:

```text id="u4n9eh"
FX conversion is just
Currency A → Currency B
```

In practice, the purpose of the conversion matters.

The same currency pair may require different treatment depending on why the conversion is being performed.

---

# 8.1 Every Conversion Has A Purpose

The library requires a conversion purpose.

Examples:

```text id="y2m6rw"
Settlement

Accounting

Valuation

Reporting

Exposure
```

---

## Why This Matters

Different business activities require different rates.

Example:

```text id="l3h7pa"
Accounting
```

may use:

```text id="d0v5ky"
Recognition Date FX
```

while:

```text id="k8s2nt"
Valuation
```

may use:

```text id="e1w7zq"
Valuation Date FX
```

---

# 8.2 Settlement Purpose

Settlement converts values for payment.

Example:

```text id="m4p8yv"
Invoice:
100,000 USD

Settlement Currency:
EUR
```

The library determines the appropriate settlement conversion.

---

## Typical Characteristics

Uses:

- contractual rules;
- settlement dates;
- payment currency.

---

# 8.3 Accounting Recognition

Recognition occurs when a transaction is initially recorded.

Example:

```text id="x6r1mg"
Trade booked today.
```

Accounting may require:

```text id="j0p4yk"
Recognition Date FX
```

for initial measurement.

---

# 8.4 Accounting Revaluation

Revaluation updates accounting values using current FX rates.

Example:

```text id="d5n8xe"
Month-End Revaluation
```

The original transaction remains unchanged.

The valuation is refreshed.

---

# 8.5 Translation

Translation converts from functional currency into reporting currency.

Example:

```text id="k3v0yj"
German Entity:
EUR

Group Reporting:
USD
```

Translation converts:

```text id="h9w6pd"
EUR → USD
```

for reporting purposes.

---

# 8.6 Valuation

Valuation determines economic value.

Examples:

```text id="r4m7ku"
Mark-To-Market

Present Value

Risk Metrics
```

---

### Key Rule

Valuation normally operates on:

```text id="s1q8vc"
Present Value
```

not future nominal cashflows.

---

# 8.7 Exposure Analysis

Exposure calculations measure FX sensitivity.

Example:

```text id="g2r5yn"
What happens if EUR/USD
moves by 5%?
```

The library provides consistent FX conversion logic for exposure calculations.

---

# 8.8 Currency Conversion Chain

Many conversions occur in stages.

Example:

```text id="q6n2xh"
Price Currency
        ↓
Settlement Currency
        ↓
Functional Currency
        ↓
Presentation Currency
```

---

# Example

Commodity:

```text id="x8j3vl"
Natural Gas
```

Price:

```text id="a7v1yk"
GBP
```

Invoice:

```text id="r2w4qn"
EUR
```

Functional Currency:

```text id="v7m8dz"
USD
```

Reporting Currency:

```text id="u1p9rt"
CHF
```

The conversion path becomes:

```text id="k5e7mx"
GBP
 ↓
EUR
 ↓
USD
 ↓
CHF
```

Each stage may use different rules.

---

# 8.9 Why Currency Roles Matter

Many systems incorrectly combine these currencies into a single conversion.

This can create:

- accounting errors;
- reporting differences;
- audit findings;
- valuation inconsistencies.

The library treats each role independently.

---

# 8.10 Key Principle

The question is never:

```text id="n2q7ju"
Convert GBP to USD
```

The real question is:

```text id="r8v1ko"
Convert GBP to USD

For what purpose?

Using which policy?

Using which source?

On which date?
```

Only after those questions are answered can the correct FX conversion be determined.
