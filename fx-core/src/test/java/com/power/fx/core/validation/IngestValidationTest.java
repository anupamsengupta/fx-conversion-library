package com.power.fx.core.validation;

import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.ingest.FixingCorrection;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestOutcome;
import com.power.fx.api.ingest.SnapshotAvailability;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.DiscountCurvePayload;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixedFactorKind;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.MarketSnapshotPayload;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.VersionEnvelope;
import com.power.fx.api.model.VersionStatus;
import com.power.fx.api.spi.FxEventListener;
import com.power.fx.api.spi.FxMetrics;
import com.power.fx.api.spi.ReferenceDataLoader;
import com.power.fx.core.DefaultFxIngestor;
import com.power.fx.core.cache.InMemoryFixingStore;
import com.power.fx.core.cache.InMemoryMarketSnapshotStore;
import com.power.fx.core.cache.InMemoryReferenceStore;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.testsupport.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises all eight {@code FX_I_*} ingest-validation codes (S6.18 table)
 * through the real {@link DefaultFxIngestor#apply} pipeline, with one
 * documented exception: see {@link #fxIApprovalInvalid_structurallyUnreachable()}.
 *
 * @see "Tech spec S6.18, S8.2; implementation plan Task 2.17"
 */
class IngestValidationTest {

    private static final String TENANT = "TENANT-INGEST-TEST";

    private InMemoryReferenceStore referenceStore;
    private InMemoryFixingStore fixingStore;
    private InMemoryMarketSnapshotStore snapshotStore;
    private RecordingListener listener;
    private DefaultFxIngestor ingestor;

    @BeforeEach
    void setUp() {
        referenceStore = new InMemoryReferenceStore();
        fixingStore = new InMemoryFixingStore();
        snapshotStore = new InMemoryMarketSnapshotStore(8);
        listener = new RecordingListener();
        ingestor = new DefaultFxIngestor(referenceStore, fixingStore, snapshotStore, new FxMath(60), listener,
                FxMetrics.noop(), new NoopLoader());
    }

    // --- FX_I_SCOPE_VIOLATION ---

    @Test
    void fxIScopeViolation_tenantScopeWithNullTenantId() {
        Currency usd = currency("USD");
        FxIngestRecord r = new FxIngestRecord("evt-1", FxEntityType.CURRENCY, Scope.TENANT, null, "USD", 1, usd,
                TestFixtures.RECORDED_AT);
        IngestOutcome outcome = ingestor.apply(List.of(r));
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_SCOPE_VIOLATION, outcome.rejections().get(0).code());
        assertTrue(listener.rejectedCodes.contains(FxIngestCode.FX_I_SCOPE_VIOLATION));
    }

    @Test
    void fxIScopeViolation_payloadEnvelopeDisagreesWithRecord() {
        // Payload's own envelope says GLOBAL, but the ingest record declares TENANT -- nothing
        // ties these two together at the FxIngestRecord level (unlike VersionEnvelope's own
        // internal scope/tenantId pairing), so this is a genuinely reachable mismatch.
        Currency usd = currency("USD"); // envelope is GLOBAL (TestFixtures.globalEnvelope)
        FxIngestRecord r = new FxIngestRecord("evt-2", FxEntityType.CURRENCY, Scope.TENANT, TENANT, "USD", 1, usd,
                TestFixtures.RECORDED_AT);
        IngestOutcome outcome = ingestor.apply(List.of(r));
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_SCOPE_VIOLATION, outcome.rejections().get(0).code());
    }

    // --- FX_I_APPROVAL_INVALID ---

    /**
     * {@code VersionEnvelope}'s own compact constructor (verified
     * separately by {@code fx-api}'s {@code ValueTypeValidationTest})
     * already throws {@code IllegalArgumentException} for every one of
     * this code's three sub-conditions (null/equal {@code approvedBy},
     * {@code approvedAt != recordedAt}) at construction time. There is
     * therefore no way to construct a well-typed reference-data or {@link
     * ManualRateOverride} payload that violates them and pass it through
     * {@code DefaultFxIngestor.apply(...)} -- the offending object simply
     * cannot exist in this codebase's object graph. This test documents
     * that {@link IngestValidator}'s defence-in-depth check is exercised
     * (and passes, as it must) on every validly-constructed payload, and
     * records -- rather than silently omits -- the fact that the
     * violation itself is structurally unreachable. Vector X07 ("manual
     * override event with author = approver") is consequently **not**
     * independently demonstrable at the {@code DefaultFxIngestor} level
     * beyond what {@code VersionEnvelope}'s own four-eyes test already
     * proves; see the Job 1 report for the full explanation.
     */
    @Test
    void fxIApprovalInvalid_structurallyUnreachable() {
        ManualRateOverride override = new ManualRateOverride(TestFixtures.tenantEnvelope(TENANT, "EUR/USD", "ov-1"),
                Scope.TENANT, TestFixtures.pair("EUR", "USD"), LocalDate.of(2026, 6, 5), "ECB",
                new BigDecimal("1.0850"), "MANUAL_CORRECTION", "TCK-1");
        FxIngestRecord r = new FxIngestRecord("evt-3", FxEntityType.MANUAL_RATE_OVERRIDE, Scope.TENANT, TENANT,
                "EUR/USD", 1, override, TestFixtures.RECORDED_AT);
        IngestOutcome outcome = ingestor.apply(List.of(r));
        assertEquals(1, outcome.applied());
        assertEquals(0, outcome.rejected());
    }

    // --- FX_I_SNAPSHOT_IMMUTABLE ---

    @Test
    void fxISnapshotImmutable_reingestOfAlreadyPublishedIdRejected() {
        // S8.2 step 2 dedupes by (marketSnapshotId, chunkIndex) identity *within one ingestor's
        // lifetime*, so simply resending the same chunk to the *same* ingestor instance is a
        // no-op duplicate, not a violation -- that is the spec's own idempotency rule, and
        // corrections are supposed to arrive as a new marketSnapshotId instead (S7.1.4). The
        // genuinely reachable trigger for FX_I_SNAPSHOT_IMMUTABLE is a snapshot id the *store*
        // already holds (published by any means -- another ingestor instance, a bootstrap load,
        // or, as here, direct test seeding) receiving a fresh, never-before-seen ingest attempt:
        // MarketSnapshotStore.put's own immutability guard fires exactly as it would in production.
        var existing = new com.power.fx.core.snapshot.MarketSnapshot("SNAP-IMMUT", Scope.TENANT, TENANT,
                SnapshotKind.EOD, LocalDate.of(2026, 12, 31), TestFixtures.RECORDED_AT, SignOffStatus.SIGNED_OFF,
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
        snapshotStore.put(TENANT, existing);

        MarketSnapshotPayload chunk0 = snapshotChunk("SNAP-IMMUT", 0, 1, true);
        IngestOutcome outcome = ingestor.apply(List.of(withSequence(chunk0, 1)));
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_SNAPSHOT_IMMUTABLE, outcome.rejections().get(0).code());
    }

    // --- FX_I_SNAPSHOT_INCOMPLETE ---

    @Test
    void fxISnapshotIncomplete_completionMarkerBeforeAllChunks() {
        // chunkCount = 2, but only chunk 0 (carrying the completion marker) is ever sent.
        MarketSnapshotPayload chunk0 = snapshotChunk("SNAP-INCOMPLETE", 0, 2, true);
        IngestOutcome outcome = ingestor.apply(List.of(withSequence(chunk0, 1)));
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_SNAPSHOT_INCOMPLETE, outcome.rejections().get(0).code());
        assertTrue(outcome.snapshotsNowResolvable().isEmpty());
    }

    // --- FX_I_FIXING_SEQUENCE ---

    @Test
    void fxIFixingSequence_correctedWithoutPriorOfficialRejected() {
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        LocalDate fixingDate = LocalDate.of(2026, 4, 2);
        FixingVersion corrected = fixing(eurUsd, "ECB", fixingDate, "1.0810", FixingStatus.CORRECTED,
                Instant.parse("2026-04-03T18:00:00Z"), "v-corrected", "v-missing-official");
        IngestOutcome outcome = ingestor.apply(List.of(fixingRecord(corrected, 1)));
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_FIXING_SEQUENCE, outcome.rejections().get(0).code());
    }

    @Test
    void fxIFixingSequence_recordedAtRegressionRejected() {
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        LocalDate fixingDate = LocalDate.of(2026, 4, 2);
        FixingVersion official = fixing(eurUsd, "ECB", fixingDate, "1.0800", FixingStatus.OFFICIAL,
                Instant.parse("2026-04-02T14:20:00Z"), "v-official", null);
        IngestOutcome first = ingestor.apply(List.of(fixingRecord(official, 1)));
        assertEquals(1, first.applied());

        // A second OFFICIAL "replay" at an earlier (or equal) recordedAt must regress-reject.
        FixingVersion stale = fixing(eurUsd, "ECB", fixingDate, "1.0799", FixingStatus.OFFICIAL,
                Instant.parse("2026-04-02T10:00:00Z"), "v-stale", null);
        IngestOutcome second = ingestor.apply(List.of(fixingRecord(stale, 2)));
        assertEquals(1, second.rejected());
        assertEquals(FxIngestCode.FX_I_FIXING_SEQUENCE, second.rejections().get(0).code());
    }

    // --- FX_I_OVERLAP ---

    @Test
    void fxIOverlap_sameRecordedAtOverlappingValidityRejected() {
        Instant recordedAt = TestFixtures.RECORDED_AT;
        VersionEnvelope envA = new VersionEnvelope(Scope.GLOBAL, null, "ZZZ", "ZZZ-vA",
                LocalDate.of(2020, 1, 1), null, recordedAt, VersionStatus.APPROVED, "loader", "approver", recordedAt,
                "TEST", null, null, "rel-1");
        VersionEnvelope envB = new VersionEnvelope(Scope.GLOBAL, null, "ZZZ", "ZZZ-vB",
                LocalDate.of(2021, 1, 1), null, recordedAt, VersionStatus.APPROVED, "loader", "approver", recordedAt,
                "TEST", null, null, "rel-1");
        Currency a = new Currency(envA, new CurrencyCode("ZZZ"), 2, null, "CAL", true);
        Currency b = new Currency(envB, new CurrencyCode("ZZZ"), 2, null, "CAL", true);
        FxIngestRecord rA = new FxIngestRecord("evt-a", FxEntityType.CURRENCY, Scope.GLOBAL, null, "ZZZ", 1, a, recordedAt);
        FxIngestRecord rB = new FxIngestRecord("evt-b", FxEntityType.CURRENCY, Scope.GLOBAL, null, "ZZZ", 2, b, recordedAt);

        IngestOutcome outcome = ingestor.apply(List.of(rA, rB));
        assertEquals(1, outcome.applied());
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_OVERLAP, outcome.rejections().get(0).code());
    }

    // --- FX_I_INVALID_VALUE ---

    @Test
    void fxIInvalidValue_nonPositiveFixedFactorRejected() {
        FixedFactor bad = new FixedFactor(TestFixtures.globalEnvelope("GBp>GBP", "GBp>GBP-bad"),
                new CurrencyCode("GBp"), new CurrencyCode("GBP"), BigDecimal.ZERO, FixedFactorKind.MINOR_UNIT, false);
        FxIngestRecord r = new FxIngestRecord("evt-badfactor", FxEntityType.FIXED_FACTOR, Scope.GLOBAL, null,
                "GBp>GBP", 1, bad, TestFixtures.RECORDED_AT);
        IngestOutcome outcome = ingestor.apply(List.of(r));
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_INVALID_VALUE, outcome.rejections().get(0).code());
    }

    @Test
    void fxIInvalidValue_nonPositiveFixingRejected() {
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        FixingVersion badFixing = fixing(eurUsd, "ECB", LocalDate.of(2026, 4, 2), "0.00", FixingStatus.OFFICIAL,
                Instant.parse("2026-04-02T14:20:00Z"), "v-bad", null);
        IngestOutcome outcome = ingestor.apply(List.of(fixingRecord(badFixing, 1)));
        assertEquals(1, outcome.rejected());
        assertEquals(FxIngestCode.FX_I_INVALID_VALUE, outcome.rejections().get(0).code());
    }

    // --- FX_I_SEQUENCE_GAP ---

    @Test
    void fxISequenceGap_detectedButRecordStillApplied() {
        FixedFactor v1 = new FixedFactor(TestFixtures.globalEnvelope("AAA>BBB", "AAA>BBB-v1"),
                new CurrencyCode("AAA"), new CurrencyCode("BBB"), new BigDecimal("2.0"), FixedFactorKind.LEGAL_PEG, true);
        FxIngestRecord r1 = new FxIngestRecord("evt-g1", FxEntityType.FIXED_FACTOR, Scope.GLOBAL, null, "AAA>BBB", 1,
                v1, TestFixtures.RECORDED_AT);
        IngestOutcome first = ingestor.apply(List.of(r1));
        assertEquals(1, first.applied());
        assertTrue(first.staleKeys().isEmpty());

        // sequence jumps from 1 to 5: a gap.
        VersionEnvelope env5 = new VersionEnvelope(Scope.GLOBAL, null, "AAA>BBB", "AAA>BBB-v5",
                LocalDate.of(2021, 1, 1), null, TestFixtures.RECORDED_AT.plusSeconds(10), VersionStatus.APPROVED,
                "loader", "approver", TestFixtures.RECORDED_AT.plusSeconds(10), "TEST", null, null, "rel-1");
        FixedFactor v5 = new FixedFactor(env5, new CurrencyCode("AAA"), new CurrencyCode("BBB"),
                new BigDecimal("2.5"), FixedFactorKind.LEGAL_PEG, true);
        FxIngestRecord r5 = new FxIngestRecord("evt-g5", FxEntityType.FIXED_FACTOR, Scope.GLOBAL, null, "AAA>BBB", 5,
                v5, TestFixtures.RECORDED_AT.plusSeconds(10));
        IngestOutcome second = ingestor.apply(List.of(r5));

        assertEquals(1, second.applied(), "a sequence gap still applies the record (S8.2 step 4)");
        assertFalse(second.staleKeys().isEmpty());
        assertTrue(second.rejections().stream().anyMatch(rej -> rej.code() == FxIngestCode.FX_I_SEQUENCE_GAP));
        assertTrue(listener.rejectedCodes.contains(FxIngestCode.FX_I_SEQUENCE_GAP));
    }

    // --- helpers ---

    private Currency currency(String code) {
        return new Currency(TestFixtures.globalEnvelope(code, code + "-v1"), new CurrencyCode(code), 2, null, "CAL", true);
    }

    private FixingVersion fixing(CurrencyPair pair, String source, LocalDate date, String value, FixingStatus status,
            Instant recordedAt, String versionId, String correctionOf) {
        return new FixingVersion(Scope.TENANT, TENANT, source, pair, date, "16:00", new BigDecimal(value), status,
                recordedAt, versionId, correctionOf, date);
    }

    private FxIngestRecord fixingRecord(FixingVersion fv, long sequence) {
        return new FxIngestRecord("evt-fix-" + fv.versionId(), FxEntityType.FIXING, Scope.TENANT, TENANT,
                fv.sourceCode() + "|" + fv.pair().canonical() + "|" + fv.cutoff(), sequence, fv, fv.recordedAt());
    }

    private MarketSnapshotPayload snapshotChunk(String id, int chunkIndex, int chunkCount, boolean completionMarker) {
        return new MarketSnapshotPayload(id, Scope.TENANT, TENANT, SnapshotKind.EOD, LocalDate.of(2026, 12, 31),
                TestFixtures.RECORDED_AT, SignOffStatus.SIGNED_OFF, List.of(), java.util.Map.of(),
                List.<DiscountCurvePayload>of(), chunkIndex, chunkCount, completionMarker);
    }

    private FxIngestRecord withSequence(MarketSnapshotPayload chunk, long sequence) {
        return new FxIngestRecord("evt-snap-" + chunk.marketSnapshotId() + "-" + chunk.chunkIndex() + "-" + sequence,
                FxEntityType.MARKET_SNAPSHOT, Scope.TENANT, TENANT, chunk.marketSnapshotId(), sequence, chunk,
                TestFixtures.RECORDED_AT);
    }

    private static final class RecordingListener implements FxEventListener {
        final List<FixingCorrection> corrections = new ArrayList<>();
        final List<SnapshotAvailability> availabilities = new ArrayList<>();
        final List<FxIngestCode> rejectedCodes = new ArrayList<>();

        @Override
        public void onFixingCorrected(FixingCorrection correction) {
            corrections.add(correction);
        }

        @Override
        public void onRejected(FxIngestRecord record, FxIngestCode code, String message) {
            rejectedCodes.add(code);
        }

        @Override
        public void onSnapshotAvailable(SnapshotAvailability availability) {
            availabilities.add(availability);
        }
    }

    private static final class NoopLoader implements ReferenceDataLoader {
        @Override
        public List<FxIngestRecord> loadGlobal() {
            return List.of();
        }

        @Override
        public List<FxIngestRecord> loadTenant(String tenantId) {
            return List.of();
        }

        @Override
        public List<FxIngestRecord> loadChangesSince(String tenantId, Instant watermark) {
            return List.of();
        }

        @Override
        public List<FxIngestRecord> loadKey(String tenantId, FxEntityType entityType, String naturalKey) {
            return List.of();
        }
    }
}
