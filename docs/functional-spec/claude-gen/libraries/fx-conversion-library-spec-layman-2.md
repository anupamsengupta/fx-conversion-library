# 9. Averaging Framework

## Why This Section Exists

Not every business process uses a single FX rate.

Many commodity, energy, and financial contracts use averages.

Examples:

```text
Monthly Average FX

Quarterly Average FX

Pricing Period Average FX

Weighted Average FX
```

Without a standard framework, different teams often implement their own averaging logic.

This typically results in:

- inconsistent valuations;
- reporting differences;
- reconciliation problems;
- audit issues.

The FX Conversion Library provides one platform-wide averaging framework.

---

# 9.1 What Is An Average FX Rate?

Instead of using one observation:

```text
EUR/USD = 1.1000
```

an average uses multiple observations.

Example:

```text
Day 1 = 1.10
Day 2 = 1.12
Day 3 = 1.08
```

Average:

```text
(1.10 + 1.12 + 1.08) / 3
= 1.10
```

---

## Why Businesses Use Averages

Averages reduce sensitivity to daily market volatility.

Example:

```text
Power Contract

Pricing Period:
Entire Month Of June
```

Using a single day's FX rate may create distortions.

Using the monthly average may better represent the commercial agreement.

---

# 9.2 One Framework For All Averaging

The platform deliberately avoids creating separate averaging engines.

Instead every average is built from four components.

```text
Observation Set
        +
Weighting
        +
Calculation Method
        +
Output Shape
```

---

# 9.3 Observation Set

## What Is An Observation Set?

An observation set defines:

```text
Which dates participate
in the average.
```

Examples:

```text
All Business Days

All Trading Days

All Pricing Days

Every Monday

Custom Calendar
```

---

## Example

Monthly Average:

```text
June 2026

Business Days Only
```

Observation Set:

```text
2026-06-01
2026-06-02
2026-06-03
...
2026-06-30
```

excluding holidays.

---

# 9.4 Weighting

Not all observations must contribute equally.

---

## Equal Weighting

Every observation contributes the same amount.

Example:

```text
20 Dates

Each Weight = 5%
```

---

## Volume Weighting

Some observations contribute more than others.

Example:

```text
Day 1 Volume = 100 MW

Day 2 Volume = 500 MW
```

Day 2 has greater influence.

---

## Custom Weighting

Users may define specific weights.

Example:

```text
Peak Days = 70%

Off-Peak Days = 30%
```

---

# 9.5 Averaging Method

Determines how observations are combined.

---

## Arithmetic Average

Most common.

Formula:

```text
Sum(Rates)
---------
Count(Rates)
```

---

## Weighted Average

Formula:

```text
Sum(Rate × Weight)
------------------
Sum(Weight)
```

---

## Future Extensions

Possible future methods:

```text
Geometric Average

Median

Percentile
```

---

# 9.6 Output Shape

Defines what is returned.

Examples:

---

### Single Average Rate

```text
EUR/USD = 1.1035
```

---

### Average Conversion Result

Instead of returning the average rate, return the converted monetary value.

---

### Full Observation Breakdown

Return:

```text
Every Observation

Every Weight

Final Calculation
```

useful for audit.

---

# 9.7 Missing Observations

Sometimes an expected observation is unavailable.

Example:

```text
15-Jun

Rate Missing
```

---

The policy determines behaviour.

Examples:

```text
Fail

Skip

Fallback

Substitute
```

---

# 9.8 Why This Framework Matters

Historically systems often create:

```text
MonthlyAverageCalculator

PricingAverageCalculator

SettlementAverageCalculator

RiskAverageCalculator
```

which all become slightly different.

The unified framework prevents this.

---

# 9.9 Key Principle

Every average can be described as:

```text
Observation Set
+
Weighting
+
Method
+
Output Shape
```

This keeps the platform consistent and extensible.

---

# 10. Pricing Day Resolver (PDR) Integration

## Why This Section Exists

Many commodity contracts do not use calendar dates directly.

Instead they use:

```text
Pricing Days
```

determined by market conventions.

Examples:

```text
All ICE Trading Days

All EEX Trading Days

All Business Days

Last 5 Trading Days
```

The Pricing Day Resolver (PDR) already determines these dates.

The FX Library must reuse them.

---

# 10.1 What Is PDR?

PDR stands for:

```text
Pricing Day Resolver
```

Its responsibility is:

```text
Determine
which dates belong
to a pricing period.
```

---

## Example

Contract:

```text
Average June Power
```

Pricing Rule:

```text
All EEX Trading Days
```

PDR returns:

```text
01-Jun
02-Jun
03-Jun
...
30-Jun
```

excluding holidays and exchange closures.

---

# 10.2 Why FX Needs PDR

Suppose a commodity contract uses:

```text
Average Price
```

across:

```text
20 Pricing Days
```

If the FX system independently chooses:

```text
22 Business Days
```

the averages will not match.

---

Result:

```text
Commodity Price Average

≠

FX Average
```

leading to valuation errors.

---

# 10.3 Single Source Of Truth

The specification requires:

```text
One pricing-day definition
for the entire platform.
```

The FX Library does not recalculate pricing days.

It consumes the pricing-day set produced by PDR.

---

# 10.4 Example

Commodity Pricing:

```text
Trading Days Only
```

PDR returns:

```text
1-Jun
2-Jun
3-Jun
7-Jun
8-Jun
...
```

The FX averaging engine uses exactly the same dates.

---

# 10.5 Benefits

This ensures:

- pricing consistency;
- valuation consistency;
- reporting consistency;
- audit consistency.

---

# 10.6 PDR As An Input

The FX Library treats PDR output as an input dataset.

Example:

```text
PricingDaySet
```

contains:

```text
Date

Weight

Metadata
```

which can be consumed directly by averaging calculations.

---

# 10.7 Why This Matters In Energy Trading

Energy contracts frequently depend on:

```text
Exchange Trading Days

Auction Days

Delivery Days

Peak Periods
```

rather than ordinary calendar days.

PDR already understands these concepts.

Duplicating them inside the FX Library would create maintenance risk.

---

# 10.8 Key Principle

```text
PDR Determines Dates

FX Library Uses Dates
```

Each component has a clear responsibility.

---

# 11. Rate Resolution Engine

## Why This Section Exists

Finding an FX rate is often much more complicated than:

```text
Give me EUR/USD.
```

The requested rate may:

- not exist directly;
- require inversion;
- require triangulation;
- require fallback sources;
- require interpolation;
- require correction selection.

The Rate Resolution Engine performs all of this logic.

---

# 11.1 What Is Rate Resolution?

Rate Resolution is the process of determining:

```text
Which FX rate
should actually be used.
```

---

Input:

```text
Currency Pair

Date

Policy

Purpose
```

Output:

```text
Resolved Rate
+
Audit Information
```

---

# 11.2 Resolution Flow

The engine follows a standard sequence.

```text
Request
    ↓
Resolve Date
    ↓
Select Policy
    ↓
Select Source
    ↓
Find Pair
    ↓
Apply Corrections
    ↓
Apply Interpolation
    ↓
Return Result
```

---

# 11.3 Direct Pair Resolution

Best case scenario.

Requested:

```text
EUR/USD
```

Available:

```text
EUR/USD
```

Result:

```text
Use Direct Rate
```

---

# 11.4 Inverse Resolution

Requested:

```text
USD/EUR
```

Available:

```text
EUR/USD = 1.1000
```

Result:

```text
USD/EUR
=
1 / 1.1000
=
0.909091
```

---

# 11.5 Triangulation

Requested:

```text
GBP/INR
```

Available:

```text
GBP/USD
USD/INR
```

Result:

```text
GBP/USD
×
USD/INR
=
GBP/INR
```

---

## Why Triangulation Matters

Many currency pairs are not actively quoted.

Triangulation dramatically increases coverage.

---

# 11.6 Source Resolution

The policy determines:

```text
Primary Source

Fallback Sources
```

Example:

```text
ECB
 ↓
WMR
 ↓
Internal Treasury
```

---

The engine follows this sequence.

---

# 11.7 Correction Resolution

Multiple versions of the same fixing may exist.

Example:

```text
Official = 1.0800

Corrected = 1.0810
```

The correction policy determines which version is selected.

---

### First Official

Use:

```text
1.0800
```

---

### Latest Corrected

Use:

```text
1.0810
```

---

### Knowledge-Cut

Use whatever version was known at a specified historical point.

---

# 11.8 Forward Resolution

If the request requires a future date:

```text
3 Months Forward
```

the engine retrieves the appropriate forward rate.

---

Example:

```text
EUR/USD

3M Forward
```

---

# 11.9 Interpolation

Sometimes an exact maturity is unavailable.

Available:

```text
1M

3M
```

Requested:

```text
2M
```

---

The engine estimates the missing value using the configured interpolation policy.

---

Default platform method:

```text
LOG_LINEAR_CARRY
```

---

# 11.10 Rate Finality

Every resolved rate carries a status.

Examples:

```text
CONFIRMED

ESTIMATED

UNRESOLVED
```

---

## CONFIRMED

Obtained directly from authoritative data.

---

## ESTIMATED

Derived through:

```text
Interpolation

Fallback

Approximation
```

---

## UNRESOLVED

No valid rate could be determined.

---

# 11.11 Audit Information

The engine returns not only the rate but also:

```text
Source

Date

Version

Resolution Path

Policy

Correction Choice
```

---

## Example

```text
EUR/USD = 1.1042

Source:
ECB

Publication Date:
2026-06-30

Version:
Official

Resolution:
Direct

Finality:
Confirmed
```

---

# 11.12 Why Auditability Matters

A trader, accountant, or auditor may ask:

```text
Why did the system
produce this number?
```

The engine must be able to explain every decision.

---

# 11.13 Key Principle

The Rate Resolution Engine is the heart of the FX Library.

Its responsibility is not merely:

```text
Find A Rate
```

but rather:

```text
Find The Correct Rate

Using The Correct Date

Using The Correct Policy

Using The Correct Source

And Explain Why.
```

# 12. Forward Curves And Interpolation

## Why This Section Exists

Spot FX rates are available for today.

However, many business processes require FX rates for future dates.

Examples:

```text id="k9u2qa"
Future Settlement

Cash Forecasting

Exposure Projection

Forward Contracts

Long-Term Planning
```

To support these scenarios, the library uses forward curves.

---

# 12.1 What Is A Forward Curve?

A forward curve represents FX rates for future dates.

Example:

```text id="m5j8nx"
Spot     = 1.1000

1 Month  = 1.1020

3 Month  = 1.1060

6 Month  = 1.1110

12 Month = 1.1200
```

Each point represents an expected exchange rate for a future maturity.

---

## Visual Representation

```text id="x4d7cr"
Today
  |
  | Spot
  |
  |------1M
  |
  |------------3M
  |
  |-------------------6M
  |
  |--------------------------------12M
```

---

# 12.2 Why Forward Curves Exist

Future exchange rates are usually different from today's spot rate.

This difference is primarily caused by:

```text id="p6t4me"
Interest Rate Differentials
```

between the two currencies.

---

### Example

Suppose:

```text id="t1h8vk"
USD Interest Rate = 5%

EUR Interest Rate = 2%
```

Future EUR/USD rates may differ from today's spot rate.

---

## Important Clarification

Forward rates are not predictions.

They are market-observed prices for future delivery.

---

# 12.3 Curve Tenors

Forward curves are usually published at specific maturities.

Examples:

```text id="j3y9qp"
Spot

1W

1M

3M

6M

12M
```

These maturities are called:

```text id="q5w2na"
Tenors
```

---

# 12.4 The Missing Tenor Problem

Suppose the curve contains:

```text id="n7m5rt"
1M

3M
```

but a user requests:

```text id="b8c1yv"
2M
```

No direct rate exists.

The library must estimate one.

This process is called:

```text id="g9e4zw"
Interpolation
```

---

# 12.5 What Is Interpolation?

Interpolation estimates a value between known points.

Example:

Known:

```text id="r2k7jt"
1M = 1.1020

3M = 1.1060
```

Requested:

```text id="s5q8nv"
2M
```

Estimated:

```text id="v1m3hy"
1.1040
```

depending on methodology.

---

# 12.6 Why Interpolation Matters

Without a standard interpolation method:

```text id="f8d0jk"
Valuation Service

Risk Service

Settlement Service
```

might all calculate different rates.

Result:

```text id="u4e7ma"
Different Answers

For The Same Trade
```

which is unacceptable.

---

# 12.7 Platform Standard

The specification defines a single platform-wide default:

```text id="x2w5pz"
LOG_LINEAR_CARRY
```

---

## Why One Method?

Consistency is more important than mathematical preference.

The platform must produce identical results everywhere.

---

# 12.8 Extrapolation

Interpolation occurs between known points.

Extrapolation occurs outside known points.

Example:

Available:

```text id="n0v6tr"
1M

3M

6M
```

Requested:

```text id="c7h1uk"
18M
```

---

This is much riskier.

The library controls extrapolation through policy.

---

## Typical Behaviours

```text id="m9q4xa"
Reject

Use Last Known Point

Controlled Extrapolation
```

depending on policy.

---

# 12.9 Forward Rate Auditability

Every forward calculation records:

```text id="g5t9vn"
Source Curve

Tenors Used

Interpolation Method

Policy Version

Calculation Path
```

---

## Why This Matters

Auditors often ask:

```text id="r3j8ko"
Was this rate observed
or calculated?
```

The system must answer clearly.

---

# 12.10 Key Principle

Forward rates are not simply retrieved.

Sometimes they must be:

```text id="k8n1zd"
Resolved

Interpolated

Validated

Explained
```

before use.

---

# 13. Entitlements And Data Governance

## Why This Section Exists

Market data is often licensed.

Not every customer is allowed to access every source.

Example:

```text id="w7m4zp"
Tenant A
Purchased WMR

Tenant B
Did Not Purchase WMR
```

The platform must enforce these restrictions.

---

# 13.1 What Are Entitlements?

Entitlements define:

```text id="c4n2xk"
Who Is Allowed
To Access What Data
```

---

Examples:

```text id="t8v5ha"
ECB

WMR

Bloomberg

Reuters

Internal Treasury
```

may have different access rules.

---

# 13.2 Why Entitlements Matter

Market-data vendors charge licensing fees.

A customer should never receive data they have not purchased.

---

## Example

Allowed:

```text id="d3r9mn"
Tenant A

ECB
WMR
```

Not Allowed:

```text id="u1w7je"
Tenant A

Bloomberg
```

The library must prevent access.

---

# 13.3 Entitlements Are Evaluated During Resolution

The rate-resolution process does not simply find data.

It also checks:

```text id="v9q6pr"
Is This Data Allowed?
```

before returning a result.

---

## Example

Requested:

```text id="r5t2kg"
EUR/USD
```

Available:

```text id="z8c4hy"
Bloomberg
```

User Permission:

```text id="m2n7wd"
No Bloomberg Access
```

Result:

```text id="p4v1uk"
Rate Rejected
```

even though data exists.

---

# 13.4 Entitlement Inheritance

One of the most important concepts in the specification.

Restrictions propagate through calculations.

---

## Example

Input:

```text id="k1d8fx"
WMR EUR/USD
```

Derived:

```text id="f9q3zt"
Triangulated GBP/CHF
```

---

The derived result still carries:

```text id="e7h5pu"
WMR Restrictions
```

because WMR data contributed to the calculation.

---

# 13.5 Why Propagation Matters

Otherwise a user could bypass licensing restrictions by requesting derived values.

Example:

```text id="s0v2nc"
Restricted Data
      ↓
Derived Calculation
      ↓
Unrestricted Result
```

This is not permitted.

---

# 13.6 Tenant Isolation

The platform is multi-tenant.

Example:

```text id="q6w9pj"
Tenant A

Tenant B

Tenant C
```

must remain isolated.

---

## Requirement

Tenant A must never access:

```text id="n3m7va"
Tenant B Overrides

Tenant B Policies

Tenant B Market Data
```

unless explicitly shared.

---

# 13.7 Shared Reference Data

Some information may be shared.

Examples:

```text id="w5j4ny"
ECB Rates

Currency Definitions

Publication Calendars
```

These are platform-level assets.

---

Tenant-specific customizations remain isolated.

---

# 13.8 Data Lineage

Every result records:

```text id="j8h2tx"
Source

Version

Transformation Path

Calculation Method
```

This is called:

```text id="g0r7vq"
Lineage
```

---

## Example

```text id="f2m5kr"
ECB EUR/USD

↓

Inverse

↓

Triangulation

↓

Final Result
```

The entire chain is preserved.

---

# 13.9 Governance Goals

The entitlement model protects:

- licensing compliance;
- tenant isolation;
- auditability;
- traceability;
- regulatory obligations.

---

# 13.10 Key Principle

The question is not merely:

```text id="a6p8yj"
Can We Find The Rate?
```

The question is:

```text id="k4r1mv"
Can We Legally

And Correctly

Use The Rate?
```

---

# 14. Corrections, Replay And Determinism

## Why This Section Exists

One of the most difficult problems in financial systems is explaining historical results.

Example:

```text id="z9u3nh"
Valuation Run

March 31
```

produced:

```text id="e1m7qd"
EUR/USD = 1.0800
```

Six months later:

```text id="r5k2yw"
EUR/USD = 1.0810
```

exists in the system.

An auditor asks:

```text id="v8p6cx"
Why was 1.0800 used?
```

The platform must be able to answer.

---

# 14.1 What Is A Correction?

A correction occurs when previously published market data is amended.

Example:

```text id="m4w7ja"
Original

EUR/USD = 1.0800
```

Later:

```text id="y3t8pn"
Corrected

EUR/USD = 1.0810
```

---

Both versions remain important.

---

# 14.2 Why Corrections Matter

Different business processes treat corrections differently.

---

### Settlement

An invoice may already have been issued.

The original rate remains legally relevant.

---

### Accounting

Accounting may need to reflect the corrected rate.

---

### Reporting

Reports may require restatement.

---

Because requirements differ, correction handling is policy-driven.

---

# 14.3 Correction Policies

The library supports multiple correction strategies.

---

## First Official

Use the first official fixing.

Ignore later corrections.

---

### Example

```text id="a7v2yk"
Official = 1.0800

Corrected = 1.0810

Result = 1.0800
```

---

## Latest Corrected

Always use the newest version.

---

### Example

```text id="h5r8wc"
Official = 1.0800

Corrected = 1.0810

Result = 1.0810
```

---

## Knowledge-Cut

Use whatever version was known at a specified point in time.

---

### Example

```text id="g2n6up"
As Of:
1-Apr
```

Result:

```text id="c9q4ye"
1.0800
```

because correction was not yet known.

---

# 14.4 Replayability

Replayability means:

```text id="k7p1nv"
Reproduce Historical Results
Exactly
```

---

## Example

A valuation run executed:

```text id="j8r4dk"
31-Mar-2026
```

using:

```text id="f0v3xb"
Snapshot 123
```

Five years later:

```text id="m1k9zh"
Replay Snapshot 123
```

must produce the same answer.

---

# 14.5 Snapshot Pinning

Every major calculation may be attached to a snapshot.

Example:

```text id="x5d2rm"
MONTH_END_2026_03
```

The snapshot contains:

```text id="w8h4cn"
Rates

Versions

Policies

Reference Data
```

used during the calculation.

---

## Why This Matters

Without snapshots:

```text id="v2q7sy"
Results Drift Over Time
```

as data changes.

---

# 14.6 Bitemporal Data

The specification requires two timelines.

---

### Business Time

When the rate applies.

Example:

```text id="r4w9mx"
31-Mar
```

---

### Knowledge Time

When the platform learned about the rate.

Example:

```text id="d6k3pu"
02-Apr
```

---

Together these support exact historical reconstruction.

---

# 14.7 Determinism

Determinism means:

```text id="u7j5kr"
Same Input

Same Output

Every Time
```

---

## Requirement

Running the same request twice should produce identical results when:

```text id="e3n8yt"
Inputs

Policies

Snapshots
```

are unchanged.

---

# 14.8 Why Determinism Matters

Financial systems require:

- reconciliation;
- auditability;
- reproducibility;
- regulatory compliance.

Non-deterministic calculations create operational risk.

---

# 14.9 Audit Trail

Every conversion records:

```text id="c5v1mp"
Source

Date

Version

Policy

Correction Choice

Resolution Path
```

---

## Example

```text id="y9r6ta"
EUR/USD

Source:
ECB

Version:
Official

Correction Policy:
First Official

Resolution:
Direct
```

---

# 14.10 Key Principle

The platform must always be able to answer:

```text id="x8w4de"
What Rate Was Used?

Why Was It Used?

What Alternatives Existed?

Could We Reproduce It Today?
```

If those questions cannot be answered, the conversion is not considered fully auditable.

# 15. Public API And Request Model

## Why This Section Exists

Everything described so far explains:

- how FX data is stored;
- how rates are resolved;
- how policies work;
- how auditability is maintained.

This section explains how applications actually use the FX Library.

In other words:

```text id="d9m2ru"
How does another system
request an FX conversion?
```

---

# 15.1 Library Consumption Model

The FX Conversion Library is embedded inside business services.

Example:

```text id="m5x8ty"
Valuation Service

Settlement Service

Accounting Service

Reporting Service
```

Each service calls the library directly.

No REST call is required.

---

## Example

```text id="k7r3pz"
Application
     ↓
FX Library
     ↓
In-Memory Resolution
     ↓
Result
```

---

# 15.2 What Must A Request Contain?

Every conversion request should provide enough information for the library to make a deterministic decision.

At minimum:

```text id="f2w7na"
Amount

Source Currency

Target Currency

Purpose

Date
```

---

## Example

```text id="u4m1kv"
Amount:
100000

Currency:
USD

Target:
EUR

Purpose:
SETTLEMENT

Date:
2026-06-30
```

---

# 15.3 Why Purpose Is Mandatory

The specification requires every conversion to have a purpose.

Example:

```text id="g9v2xt"
SETTLEMENT

VALUATION

ACCOUNTING

REPORTING

EXPOSURE
```

---

## Why?

Because:

```text id="x6k4jr"
USD → EUR
```

may legitimately produce different answers depending on business context.

---

# 15.4 Optional Request Information

Some requests may require additional information.

Examples:

```text id="p7n8wu"
Accounting Unit

Snapshot

Policy Override

Observation Set

Knowledge Time

Forward Date
```

---

## Example

Accounting conversion:

```text id="w1q6dm"
Accounting Unit:
UK_ENTITY

Functional Currency:
GBP
```

The accounting unit influences conversion behaviour.

---

# 15.5 Conversion Response

The response contains much more than a converted amount.

Example:

```text id="b8r4zt"
Converted Value

Rate Used

Source

Date

Policy

Finality

Audit Metadata
```

---

# Example

```text id="r3y7nk"
Input:
100 USD

Output:
92.15 EUR

Rate:
0.9215
```

---

# 15.6 Explainability

The API should support explanation.

Example:

```text id="h2k8vp"
Why was this rate selected?
```

---

The response should provide:

```text id="y6n4mc"
Source

Date

Resolution Path

Policy

Correction Decision
```

---

# Example

```text id="m7q5de"
Source:
ECB

Date:
2026-06-30

Resolution:
Direct

Version:
Official
```

---

# 15.7 Batch Conversions

Applications often convert thousands of values.

Example:

```text id="e1x9wg"
Portfolio Valuation

Risk Aggregation

Settlement Runs
```

---

Instead of:

```text id="u5v8ny"
1 Request
=
1 Conversion
```

the library supports:

```text id="s4r2pj"
1 Request
=
Many Conversions
```

---

## Benefit

Reduced overhead.

Improved throughput.

Simpler application code.

---

# 15.8 Conversion Context

The library may receive contextual information.

Example:

```text id="d7w3rm"
Tenant

User

Accounting Unit

Snapshot

Purpose
```

This context influences behaviour without requiring custom code.

---

# 15.9 Idempotent Behaviour

The same request should produce the same answer when:

```text id="a6t4kn"
Data

Policies

Snapshot
```

remain unchanged.

---

## Key Principle

```text id="q9v7hz"
Same Request

Same Inputs

Same Result
```

---

# 15.10 Key Takeaway

The API is designed around:

```text id="z8r5up"
Determinism

Auditability

Explainability

Performance
```

rather than simply returning a number.

---

# 16. Precision, Rounding And Numerical Rules

## Why This Section Exists

Financial systems cannot tolerate ambiguity in calculations.

A difference of:

```text id="n4y6tw"
0.01
```

may be insignificant for a retail purchase.

For a large energy portfolio:

```text id="k8m2xd"
0.01
```

can represent thousands of dollars.

---

# 16.1 Financial Precision Requirements

All calculations must use decimal arithmetic.

Examples:

```text id="u2q5nv"
BigDecimal

DECIMAL128
```

---

## Not Allowed

```text id="r9x1pk"
float

double
```

for financial calculations.

---

## Why?

Floating-point arithmetic may produce small inconsistencies.

Example:

```text id="v5d3jk"
0.1 + 0.2
```

is not always represented exactly in binary systems.

---

# 16.2 Platform Principle

The specification prioritizes:

```text id="m7k4yr"
Correctness

Consistency

Auditability
```

over raw computational speed.

---

# 16.3 Currency Minor Units

Currencies have different decimal requirements.

Examples:

```text id="t6r8wn"
USD = 2 decimals

EUR = 2 decimals

JPY = 0 decimals

KWD = 3 decimals
```

---

## Why This Matters

The same numerical value may be valid in one currency and invalid in another.

Example:

```text id="f4x2mc"
100.123 USD
```

is not a valid settlement amount.

---

# 16.4 Internal Precision vs Display Precision

The library distinguishes:

```text id="e7n3vb"
Calculation Precision

Display Precision
```

---

### Internal Precision

Used during calculations.

Typically much higher.

---

### Display Precision

Used when presenting results.

Typically matches currency rules.

---

## Example

Internal:

```text id="q3m9wt"
1.123456789
```

Displayed:

```text id="w8r4pa"
1.12
```

---

# 16.5 Rounding

At some point values must be rounded.

The specification centralizes rounding behaviour.

---

## Why?

Without a platform standard:

```text id="h5q2nu"
Accounting Service

Valuation Service

Settlement Service
```

may all round differently.

---

# 16.6 Typical Rounding Modes

Examples:

```text id="u9t4xe"
HALF_UP

HALF_EVEN

DOWN

UP
```

---

## Example

Value:

```text id="v2p8zk"
1.235
```

Rounded to 2 decimals:

```text id="s6y4rd"
1.24
```

depending on policy.

---

# 16.7 When Rounding Occurs

An important design decision.

The library avoids premature rounding.

---

Bad:

```text id="x8r3vq"
Calculate

Round

Calculate

Round
```

---

Good:

```text id="m4n9yh"
Calculate

Calculate

Calculate

Round Once
```

---

## Why?

Repeated rounding introduces cumulative error.

---

# 16.8 Rate Precision

FX rates frequently require more precision than settlement amounts.

Example:

```text id="q7v2we"
EUR/USD

1.10234791
```

---

The library preserves sufficient precision throughout the calculation chain.

---

# 16.9 Deterministic Mathematics

The same inputs must always produce:

```text id="p5x8mn"
The Same Numerical Result
```

regardless of:

```text id="k1r7tv"
Server

Region

Execution Time
```

---

# 16.10 Key Principle

```text id="w3q5nc"
Financial Accuracy

Before Convenience
```

Every calculation must be reproducible and explainable.

---

# 17. Error Handling And Operational Behaviour

## Why This Section Exists

Not every conversion request succeeds.

Examples:

```text id="n6v2pk"
Missing Data

Invalid Currency

Entitlement Violation

Policy Misconfiguration

Unavailable Source
```

The library must handle these situations consistently.

---

# 17.1 Error Philosophy

The goal is not merely to detect errors.

The goal is to provide:

```text id="f5m9rh"
Clear

Actionable

Auditable
```

error information.

---

# 17.2 Missing Rate

Example:

Requested:

```text id="t7x4nu"
EUR/XYZ
```

No rate exists.

---

Result:

```text id="h3q8vw"
RATE_NOT_FOUND
```

rather than a generic failure.

---

# 17.3 Unsupported Currency

Example:

```text id="k8r2my"
ABC
```

not present in currency master.

---

Result:

```text id="y5t1pn"
UNSUPPORTED_CURRENCY
```

---

# 17.4 Date Resolution Failure

Example:

```text id="u6v3xq"
Exact Date Required
```

but no publication exists.

---

Result:

```text id="n4k7we"
DATE_RESOLUTION_FAILED
```

---

# 17.5 Entitlement Failure

Example:

```text id="c9m2yd"
Bloomberg Data Requested

User Not Entitled
```

---

Result:

```text id="g7r5vn"
ACCESS_DENIED
```

---

# 17.6 Policy Failure

Example:

```text id="e2q8wk"
Invalid Policy Configuration
```

---

Result:

```text id="r6v1ph"
POLICY_CONFIGURATION_ERROR
```

---

# 17.7 Forward Curve Failure

Example:

```text id="t4m9zn"
Requested:
24M

Curve Ends:
12M
```

and policy forbids extrapolation.

---

Result:

```text id="w8x3qc"
MATURITY_OUT_OF_RANGE
```

---

# 17.8 Ambiguous Resolution

Example:

```text id="v3r6np"
Multiple Valid Sources

No Selection Rule
```

---

Result:

```text id="x7k4yt"
AMBIGUOUS_RATE_SELECTION
```

---

# 17.9 Fail Fast Principle

The library prefers:

```text id="h9w5rm"
Immediate Detection
```

over:

```text id="q4n8xp"
Silent Assumptions
```

---

## Example

Bad:

```text id="k5m1vd"
Unknown Currency

Automatically Use USD
```

---

Good:

```text id="s8v3qy"
Unknown Currency

Return Explicit Error
```

---

# 17.10 Operational Logging

The library records operational events.

Examples:

```text id="n2q7me"
Fallback Used

Interpolation Used

Correction Applied

Entitlement Denied
```

---

## Why?

Operations teams need visibility into unusual behaviour.

---

# 17.11 Metrics

The platform should expose metrics.

Examples:

```text id="u4w8pn"
Conversions Per Second

Average Latency

Error Count

Fallback Count

Interpolation Count
```

---

# 17.12 Retry Behaviour

Because the library is in-process, most failures are not transient.

Examples:

```text id="r8x2yt"
Unsupported Currency

Missing Policy

Entitlement Failure
```

Retries will not help.

---

The caller should fix the underlying issue.

---

# 17.13 Auditability Of Failures

Failures are important business events.

The system records:

```text id="w5n9kc"
Request

Inputs

Policy

Reason For Failure
```

---

## Example

```text id="m7r4ve"
Request:
GBP/XYZ

Result:
RATE_NOT_FOUND
```

---

# 17.14 Key Principle

The library should never fail mysteriously.

Every failure must answer:

```text id="z2v8yn"
What Happened?

Why Did It Happen?

How Can It Be Fixed?
```

Clear failures reduce operational risk and significantly improve supportability.

