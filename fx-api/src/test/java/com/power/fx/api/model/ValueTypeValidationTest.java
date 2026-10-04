package com.power.fx.api.model;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compact-constructor validation tests for {@code fx-api}'s core value
 * types: {@link VersionEnvelope} four-eyes rule, {@link Rational}
 * reduction and sign normalisation, {@link CurrencyPair} identity and
 * canonical form, and {@link PricingDaySet} sequence monotonicity.
 *
 * <p>Runs fully in Phase 1, no {@code fx-core}/{@code fx-testkit}
 * dependency (S12.1).
 */
class ValueTypeValidationTest {

    // ---- VersionEnvelope: four-eyes rule -------------------------------

    @Test
    void versionEnvelope_rejectsApprovedByEqualToAuthoredBy() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new VersionEnvelope(
                Scope.TENANT, "TN_0042", "EUR/USD", "v1",
                LocalDate.of(2026, 1, 1), null, now, VersionStatus.APPROVED,
                "alice", "alice", now, "SRC", null, null, "REL-1"));
    }

    @Test
    void versionEnvelope_acceptsDistinctAuthorAndApprover() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        VersionEnvelope envelope = new VersionEnvelope(
                Scope.TENANT, "TN_0042", "EUR/USD", "v1",
                LocalDate.of(2026, 1, 1), null, now, VersionStatus.APPROVED,
                "alice", "bob", now, "SRC", null, null, "REL-1");
        assertEquals("alice", envelope.authoredBy());
        assertEquals("bob", envelope.approvedBy());
    }

    @Test
    void versionEnvelope_rejectsApprovedAtNotEqualToRecordedAt() {
        Instant recordedAt = Instant.parse("2026-10-03T00:00:00Z");
        Instant approvedAt = Instant.parse("2026-10-04T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new VersionEnvelope(
                Scope.TENANT, "TN_0042", "EUR/USD", "v1",
                LocalDate.of(2026, 1, 1), null, recordedAt, VersionStatus.APPROVED,
                "alice", "bob", approvedAt, "SRC", null, null, "REL-1"));
    }

    @Test
    void versionEnvelope_rejectsCorrectionOfWithoutReasonCode() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new VersionEnvelope(
                Scope.TENANT, "TN_0042", "EUR/USD", "v2",
                LocalDate.of(2026, 1, 1), null, now, VersionStatus.APPROVED,
                "alice", "bob", now, "SRC", "v1", null, "REL-1"));
    }

    @Test
    void versionEnvelope_rejectsGlobalScopeWithTenantId() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new VersionEnvelope(
                Scope.GLOBAL, "TN_0042", "EUR/USD", "v1",
                LocalDate.of(2026, 1, 1), null, now, VersionStatus.APPROVED,
                "alice", "bob", now, "SRC", null, null, "REL-1"));
    }

    @Test
    void versionEnvelope_rejectsTenantScopeWithoutTenantId() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new VersionEnvelope(
                Scope.TENANT, null, "EUR/USD", "v1",
                LocalDate.of(2026, 1, 1), null, now, VersionStatus.APPROVED,
                "alice", "bob", now, "SRC", null, null, "REL-1"));
    }

    @Test
    void versionEnvelope_rejectsValidFromNotBeforeValidTo() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        LocalDate day = LocalDate.of(2026, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new VersionEnvelope(
                Scope.TENANT, "TN_0042", "EUR/USD", "v1",
                day, day, now, VersionStatus.APPROVED,
                "alice", "bob", now, "SRC", null, null, "REL-1"));
    }

    // ---- Rational: reduction and sign normalisation --------------------

    @Test
    void rational_of6over4_reducesTo3over2() {
        Rational r = Rational.of(6, 4);
        assertEquals(BigInteger.valueOf(3), r.num());
        assertEquals(BigInteger.valueOf(2), r.den());
    }

    @Test
    void rational_normalisesSignOntoNumeratorForNegativeDenominator() {
        Rational r = new Rational(BigInteger.valueOf(3), BigInteger.valueOf(-4));
        assertEquals(BigInteger.valueOf(-3), r.num());
        assertEquals(BigInteger.valueOf(4), r.den());
    }

    @Test
    void rational_rejectsZeroDenominator() {
        assertThrows(ArithmeticException.class, () -> Rational.of(1, 0));
    }

    @Test
    void rational_zeroNumeratorReducesToZeroOverOne() {
        Rational r = Rational.of(0, 5);
        assertEquals(BigInteger.ZERO, r.num());
        assertEquals(BigInteger.ONE, r.den());
    }

    // ---- CurrencyPair ----------------------------------------------------

    @Test
    void currencyPair_eurEur_isIdentity() {
        CurrencyPair pair = new CurrencyPair(new CurrencyCode("EUR"), new CurrencyCode("EUR"));
        assertTrue(pair.isIdentity());
    }

    @Test
    void currencyPair_eurUsd_canonicalFormatIsExact() {
        CurrencyPair pair = new CurrencyPair(new CurrencyCode("EUR"), new CurrencyCode("USD"));
        assertFalse(pair.isIdentity());
        assertEquals("EUR/USD", pair.canonical());
    }

    @Test
    void currencyCode_rejectsNonThreeLetterValue() {
        assertThrows(IllegalArgumentException.class, () -> new CurrencyCode("EURO"));
    }

    // ---- PricingDaySet: sequence monotonicity --------------------------

    @Test
    void pricingDaySet_rejectsDuplicateSequence() {
        PdrRef ref = new PdrRef("evt-1", 1, "hash-1");
        PricingObservation o1 = observation(1, LocalDate.of(2026, 1, 1));
        PricingObservation o2 = observation(1, LocalDate.of(2026, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new PricingDaySet(ref, List.of(o1, o2), "COMPLETE"));
    }

    @Test
    void pricingDaySet_rejectsOutOfOrderSequence() {
        PdrRef ref = new PdrRef("evt-1", 1, "hash-1");
        PricingObservation o1 = observation(2, LocalDate.of(2026, 1, 1));
        PricingObservation o2 = observation(1, LocalDate.of(2026, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new PricingDaySet(ref, List.of(o1, o2), "COMPLETE"));
    }

    @Test
    void pricingDaySet_acceptsStrictlyAscendingSequence() {
        PdrRef ref = new PdrRef("evt-1", 1, "hash-1");
        PricingObservation o1 = observation(1, LocalDate.of(2026, 1, 1));
        PricingObservation o2 = observation(2, LocalDate.of(2026, 1, 2));
        PricingDaySet set = new PricingDaySet(ref, List.of(o1, o2), "COMPLETE");
        assertEquals(2, set.observations().size());
    }

    // Note: PricingObservation/PricingDaySet also assert a positive weight
    // denominator defensively, but Rational's own compact constructor
    // already guarantees den > 0 for every instance reachable through its
    // public API (den == 0 throws ArithmeticException; a negative
    // denominator is sign-normalised onto the numerator), so that branch
    // cannot be exercised through a valid Rational and is not separately
    // tested here.

    private static PricingObservation observation(int sequence, LocalDate date) {
        return new PricingObservation(sequence, date, Rational.of(1, 2), null, null, false);
    }
}
