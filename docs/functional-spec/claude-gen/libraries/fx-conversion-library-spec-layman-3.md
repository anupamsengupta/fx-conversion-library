# 18. Non-Functional Requirements

## Why This Section Exists

The earlier sections describe what the FX Conversion Library must do.

Non-functional requirements describe:

```text id="nf001"
How Well It Must Do It
```

Examples:

- performance;
- scalability;
- reliability;
- security;
- auditability;
- maintainability.

A functionally correct solution may still be unusable if it is too slow, unreliable, or difficult to operate.

---

# 18.1 Performance

The library is intended for high-volume CTRM/ETRM workloads.

Examples:

```text id="nf002"
Portfolio Valuation

Risk Calculations

Settlement Processing

Exposure Analysis

Reporting
```

These workloads may require millions of FX conversions per day.

---

## Requirement

The library should support:

```text id="nf003"
Low-Latency

High-Throughput

In-Memory Resolution
```

without requiring network calls during normal operation.

---

## Design Principle

```text id="nf004"
Resolve In Memory

Whenever Possible
```

Network latency should not be part of the conversion path.

---

# 18.2 Scalability

The library must scale with:

```text id="nf005"
Trades

Cashflows

Valuations

Tenants

Users
```

without requiring redesign.

---

## Example

Growth from:

```text id="nf006"
10,000 Conversions / Day
```

to

```text id="nf007"
100,000,000 Conversions / Day
```

should primarily require infrastructure scaling rather than architectural changes.

---

# 18.3 Availability

Business processes depend on FX conversions.

Examples:

```text id="nf008"
Month-End Close

Settlement Runs

Risk Calculations

Intraday Valuation
```

---

## Requirement

The library should remain operational even if:

```text id="nf009"
External Market Data Sources

Administration Systems

Reference Data Systems
```

are temporarily unavailable.

---

## Why?

The library should use locally available cached data whenever possible.

---

# 18.4 Reliability

The library must behave consistently.

Example:

```text id="nf010"
Request A

Produces Result X
```

today.

The same request should continue to produce:

```text id="nf011"
Result X
```

tomorrow if the underlying inputs have not changed.

---

# 18.5 Determinism

One of the most important requirements.

---

## Definition

```text id="nf012"
Same Inputs

Same Outputs
```

every time.

---

## Why?

Financial systems require:

- reconciliation;
- auditability;
- repeatability;
- regulatory compliance.

---

# 18.6 Security

The platform must protect:

```text id="nf013"
Market Data

Policies

Reference Data

Tenant Configuration
```

from unauthorized access.

---

## Requirement

All access must respect:

```text id="nf014"
Tenant Boundaries

User Permissions

Entitlements
```

defined by the platform.

---

# 18.7 Auditability

Every conversion should be explainable.

---

## Example

An auditor asks:

```text id="nf015"
Why Was This Rate Used?
```

The system should provide:

```text id="nf016"
Source

Date

Policy

Version

Resolution Path
```

without requiring investigation of application logs.

---

# 18.8 Observability

Operations teams need visibility into behaviour.

Examples:

```text id="nf017"
Latency

Errors

Fallback Usage

Interpolation Usage

Correction Usage
```

---

## Requirement

The library should emit operational metrics and structured logs.

---

# 18.9 Maintainability

Business rules change frequently.

Examples:

```text id="nf018"
New Sources

New Policies

New Regulations

New Currency Pairs
```

---

## Requirement

Changes should be driven primarily through:

```text id="nf019"
Configuration

Reference Data

Policy Definitions
```

rather than code changes.

---

# 18.10 Extensibility

Future enhancements should not require redesign.

Examples:

```text id="nf020"
New FX Sources

New Averaging Methods

New Interpolation Methods

New Accounting Policies
```

should integrate naturally into the existing framework.

---

# 18.11 Key Principle

The library should be:

```text id="nf021"
Fast

Reliable

Secure

Auditable

Extensible
```

throughout its lifecycle.

---

# 19. Golden Test Scenarios

## Why This Section Exists

A specification is not complete unless it can be verified.

Golden test scenarios define:

```text id="gt001"
Known Inputs

Known Outputs
```

that must always produce identical results.

---

# 19.1 What Is A Golden Test?

A golden test is a business scenario with a predetermined answer.

Example:

```text id="gt002"
Input:
100 USD

Rate:
0.9000

Expected:
90 EUR
```

Any future implementation must produce:

```text id="gt003"
90 EUR
```

exactly.

---

# 19.2 Purpose Of Golden Tests

Golden tests protect against:

- regressions;
- implementation differences;
- configuration errors;
- unintended behavioural changes.

---

# 19.3 Direct Conversion

Scenario:

```text id="gt004"
USD → EUR

Direct Rate Exists
```

Expected:

```text id="gt005"
Direct Resolution
```

No inversion or triangulation.

---

# 19.4 Inverse Conversion

Scenario:

```text id="gt006"
Requested:
USD/EUR

Available:
EUR/USD
```

Expected:

```text id="gt007"
Inverse Calculation
```

using reciprocal logic.

---

# 19.5 Triangulation

Scenario:

```text id="gt008"
Requested:
GBP/INR

Available:
GBP/USD
USD/INR
```

Expected:

```text id="gt009"
Triangulated Result
```

using the configured intermediary currency.

---

# 19.6 Holiday Resolution

Scenario:

```text id="gt010"
Requested Date:
Sunday
```

Policy:

```text id="gt011"
Previous Business Day
```

Expected:

```text id="gt012"
Friday Rate
```

---

# 19.7 Correction Handling

Scenario:

```text id="gt013"
Official:
1.0800

Corrected:
1.0810
```

Expected output depends on correction policy.

---

## Test Variants

```text id="gt014"
First Official

Latest Corrected

Knowledge-Cut
```

must all be verified.

---

# 19.8 Averaging

Scenario:

```text id="gt015"
Monthly Average
```

using a predefined observation set.

Expected:

```text id="gt016"
Known Average Result
```

---

# 19.9 Forward Interpolation

Scenario:

```text id="gt017"
1M Tenor

3M Tenor

Requested:
2M
```

Expected:

```text id="gt018"
Known Interpolated Value
```

using platform-standard interpolation.

---

# 19.10 Entitlement Validation

Scenario:

```text id="gt019"
Bloomberg Data

User Not Entitled
```

Expected:

```text id="gt020"
ACCESS_DENIED
```

---

# 19.11 Replay Validation

Scenario:

```text id="gt021"
Historical Snapshot
```

replayed years later.

Expected:

```text id="gt022"
Identical Result
```

to the original calculation.

---

# 19.12 Key Principle

Golden tests validate:

```text id="gt023"
Business Behaviour
```

rather than implementation details.

---

# 20. Testing Strategy

## Why This Section Exists

Golden tests define what should happen.

Testing strategy defines how it will be verified.

---

# 20.1 Testing Pyramid

The specification recommends a layered testing approach.

```text id="ts001"
Unit Tests
      ↓
Component Tests
      ↓
Integration Tests
      ↓
Golden Scenario Tests
```

---

# 20.2 Unit Tests

Verify individual components.

Examples:

```text id="ts002"
Date Resolver

Interpolation Engine

Triangulation Engine

Policy Resolver
```

---

## Goal

Validate isolated logic.

---

# 20.3 Component Tests

Verify groups of collaborating components.

Example:

```text id="ts003"
Rate Resolution Engine
```

working with:

```text id="ts004"
Policy Resolver

Date Resolver

Source Resolver
```

---

# 20.4 Integration Tests

Verify end-to-end behaviour.

Example:

```text id="ts005"
Application Request

↓

FX Library

↓

Market Data

↓

Result
```

---

## Goal

Ensure components work together correctly.

---

# 20.5 Golden Scenario Tests

Verify business outcomes.

Examples:

```text id="ts006"
Accounting

Settlement

Valuation

Reporting
```

scenarios.

---

## Goal

Protect business behaviour across releases.

---

# 20.6 Regression Testing

Every defect fixed should produce:

```text id="ts007"
A Permanent Test Case
```

to prevent recurrence.

---

# 20.7 Determinism Testing

The platform should repeatedly execute identical requests.

Expected:

```text id="ts008"
Identical Results
```

every time.

---

# 20.8 Replay Testing

Historical snapshots should be replayed periodically.

Goal:

```text id="ts009"
Verify Historical Reproducibility
```

---

# 20.9 Performance Testing

Examples:

```text id="ts010"
Single Conversion

Batch Conversion

Portfolio Valuation
```

under expected and peak workloads.

---

## Goal

Verify:

```text id="ts011"
Latency

Throughput

Resource Consumption
```

meet expectations.

---

# 20.10 Operational Testing

Validate:

```text id="ts012"
Logging

Metrics

Tracing

Alerts
```

before production deployment.

---

# 20.11 Key Principle

Testing should prove:

```text id="ts013"
Correctness

Performance

Reliability

Auditability
```

not just code coverage.

---

# 21. Open Questions And Future Enhancements

## Why This Section Exists

Not every design decision needs to be finalized immediately.

This section captures areas for future consideration.

---

# 21.1 Future Market Data Sources

Potential additions:

```text id="fq001"
Additional FX Vendors

Central Bank Sources

Regional Providers

Internal Treasury Feeds
```

---

## Goal

Expand data coverage without redesigning the platform.

---

# 21.2 Additional Interpolation Methods

Future requirements may introduce:

```text id="fq002"
Linear

Spline

Cubic

Custom Models
```

alongside the platform standard.

---

# 21.3 Advanced Averaging

Potential future support:

```text id="fq003"
Geometric Average

Median

Percentile

Risk-Weighted Average
```

---

# 21.4 Real-Time Streaming

Current design focuses on deterministic conversion.

Future requirements may include:

```text id="fq004"
Streaming FX Updates

Live Revaluation

Real-Time Risk
```

---

# 21.5 Advanced Analytics

Potential future features:

```text id="fq005"
Sensitivity Analysis

Stress Testing

Scenario Modelling
```

using the same conversion infrastructure.

---

# 21.6 Regulatory Expansion

Future regulatory requirements may require:

```text id="fq006"
Additional Audit Metadata

Retention Rules

Regional Compliance Controls
```

---

# 21.7 Cross-Asset Support

The architecture may later support:

```text id="fq007"
Commodities

Interest Rates

Credit Curves

Inflation Indices
```

using similar resolution concepts.

---

# 21.8 Multi-Region Expansion

Current requirements focus on global FX support.

Future expansion may require deeper support for:

```text id="fq008"
APAC Markets

North America

Middle East

Latin America
```

market conventions.

---

# 21.9 Operational Enhancements

Possible future capabilities:

```text id="fq009"
Self-Healing Data Recovery

Automated Replay

Policy Simulation

Impact Analysis
```

---

# 21.10 Key Principle

Future enhancements should:

```text id="fq010"
Extend

Not Replace
```

the existing architecture.

The platform has been designed so that new capabilities can be introduced through additional policies, reference data, and modular components without changing the core conversion framework.

