package com.power.fx.core;

import com.power.fx.api.FxIngestor;
import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.ingest.FixingCorrection;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestOutcome;
import com.power.fx.api.ingest.IngestRejection;
import com.power.fx.api.ingest.SnapshotAvailability;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.FxStoreKind;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.MarketSnapshotPayload;
import com.power.fx.api.model.Scope;
import com.power.fx.api.spi.FxEventListener;
import com.power.fx.api.spi.FxMetrics;
import com.power.fx.api.spi.ReferenceDataLoader;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.cache.FixingStore;
import com.power.fx.core.cache.FixingView;
import com.power.fx.core.cache.MarketSnapshotStore;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.cache.ReferenceStore;
import com.power.fx.core.cache.FixingSeries;
import com.power.fx.core.cache.SeriesKey;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.core.snapshot.SnapshotAssembler;
import com.power.fx.core.snapshot.SnapshotCompletionTracker;
import com.power.fx.core.validation.FixingSequenceValidator;
import com.power.fx.core.validation.IngestValidator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single-writer-per-tenant ingestion engine (S6.1 {@code
 * DefaultFxIngestor}, S8.1-8.5, FS S16): partition by tenant + {@link
 * FxStoreKind} &rarr; deduplicate by identity &rarr; validate (the eight
 * {@code FX_I_*} codes) &rarr; sequence-contiguity check &rarr;
 * copy-on-write build of the affected store &rarr; atomic per-store swap
 * &rarr; notify listeners after the swap &rarr; metrics.
 *
 * <p>GLOBAL-scope records are processed under a dedicated {@link
 * #globalLock}; TENANT-scope records are processed under a per-tenant
 * {@link ReentrantLock} drawn from {@link #tenantLocks}, so one tenant's
 * ingest never blocks another's and a reader is never exposed to a
 * mid-batch, torn generation (the atomic {@code compareAndSet} swap inside
 * {@link InMemoryReferenceStore}/{@link InMemoryFixingStore} is the
 * mechanical, structural guarantee; this class's locks serialise
 * <em>writers</em> only, per S10.4).
 *
 * <p><strong>Documented interpretation of {@link
 * IngestOutcome#newGenerations()} for multi-tenant batches:</strong> that
 * map is keyed only by {@link FxStoreKind}, with no tenant dimension, so a
 * single {@link #apply} call whose records span more than one tenant can
 * only report one generation number per store kind. This implementation
 * reports the last-processed bucket's generation per kind (a last-writer-
 * wins documented limitation of the existing API shape, not something
 * this class can fix without an API change) -- real hosts are expected to
 * batch per tenant in practice, matching the CDM event flow of S8.1.
 *
 * @see "Tech spec S6.1, S8.1-8.5"
 */
public final class DefaultFxIngestor implements FxIngestor {

    /** Sentinel bucket key for GLOBAL-scope records (not a tenant id; safe under AR-10). */
    private static final String GLOBAL_BUCKET = "GLOBAL";

    private final ReferenceStore referenceStore;
    private final FixingStore fixingStore;
    private final MarketSnapshotStore marketSnapshotStore;
    private final FxEventListener eventListener;
    private final FxMetrics metrics;
    private final ReferenceDataLoader referenceDataLoader;
    private final SnapshotCompletionTracker completionTracker = new SnapshotCompletionTracker();
    private final SnapshotAssembler snapshotAssembler;
    private final IngestValidator ingestValidator = new IngestValidator();
    private final FixingSequenceValidator fixingSequenceValidator = new FixingSequenceValidator();

    private final ConcurrentHashMap<String, ReentrantLock> tenantLocks = new ConcurrentHashMap<>();
    private final ReentrantLock globalLock = new ReentrantLock();

    /** Last-seen {@code sequence} per {@code (bucket, entityType, naturalKey[, date])} key (S8.2 step 4). */
    private final ConcurrentHashMap<String, Long> lastSequenceByKey = new ConcurrentHashMap<>();

    /** Identity-dedupe sets per {@code (bucket, storeKind)} key (S8.2 step 2). */
    private final ConcurrentHashMap<String, Set<String>> seenIdentity = new ConcurrentHashMap<>();

    public DefaultFxIngestor(ReferenceStore referenceStore, FixingStore fixingStore,
            MarketSnapshotStore marketSnapshotStore, FxMath fxMath, FxEventListener eventListener, FxMetrics metrics,
            ReferenceDataLoader referenceDataLoader) {
        this.referenceStore = Objects.requireNonNull(referenceStore, "referenceStore must not be null");
        this.fixingStore = Objects.requireNonNull(fixingStore, "fixingStore must not be null");
        this.marketSnapshotStore = Objects.requireNonNull(marketSnapshotStore, "marketSnapshotStore must not be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.referenceDataLoader = Objects.requireNonNull(referenceDataLoader, "referenceDataLoader must not be null");
        this.snapshotAssembler = new SnapshotAssembler(fxMath);
    }

    @Override
    public IngestOutcome apply(List<FxIngestRecord> records) {
        Objects.requireNonNull(records, "records must not be null");

        int rejected = 0;
        List<IngestRejection> rejections = new ArrayList<>();
        List<RejectionNotice> notices = new ArrayList<>();
        Map<String, List<FxIngestRecord>> byBucket = new LinkedHashMap<>();

        for (FxIngestRecord r : records) {
            boolean badPairing = (r.scope() == Scope.TENANT && r.tenantId() == null)
                    || (r.scope() == Scope.GLOBAL && r.tenantId() != null);
            if (badPairing) {
                rejected++;
                String message = "scope/tenantId pairing invalid: scope=" + r.scope() + ", tenantId=" + r.tenantId();
                rejections.add(new IngestRejection(r.eventId(), r.naturalKey(), FxIngestCode.FX_I_SCOPE_VIOLATION, message));
                notices.add(new RejectionNotice(r, FxIngestCode.FX_I_SCOPE_VIOLATION, message));
                continue;
            }
            String bucket = r.scope() == Scope.GLOBAL ? GLOBAL_BUCKET : r.tenantId();
            byBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(r);
        }

        int applied = 0;
        int duplicates = 0;
        Map<FxStoreKind, Long> newGenerations = new EnumMap<>(FxStoreKind.class);
        Set<String> staleKeys = new LinkedHashSet<>();
        List<String> snapshotsNowResolvable = new ArrayList<>();

        for (Map.Entry<String, List<FxIngestRecord>> entry : byBucket.entrySet()) {
            String bucket = entry.getKey();
            ReentrantLock lock = bucket.equals(GLOBAL_BUCKET) ? globalLock
                    : tenantLocks.computeIfAbsent(bucket, k -> new ReentrantLock());
            BucketOutcome bo;
            lock.lock();
            try {
                bo = applyBucket(bucket, entry.getValue());
            } finally {
                lock.unlock();
            }
            applied += bo.applied;
            rejected += bo.rejected;
            duplicates += bo.duplicates;
            newGenerations.putAll(bo.newGenerations);
            rejections.addAll(bo.rejections);
            staleKeys.addAll(bo.staleKeys);
            snapshotsNowResolvable.addAll(bo.snapshotsNowResolvable);
            notices.addAll(bo.rejectionNotices);

            // Step 7 (S8.2): notify after the swap(s) for this bucket.
            for (FixingCorrection c : bo.pendingCorrections) {
                eventListener.onFixingCorrected(c);
            }
            for (SnapshotAvailability a : bo.pendingSnapshotAvailability) {
                eventListener.onSnapshotAvailable(a);
            }
        }

        for (RejectionNotice n : notices) {
            eventListener.onRejected(n.record(), n.code(), n.message());
            metrics.ingestRejected(n.record().tenantId() == null ? GLOBAL_BUCKET : n.record().tenantId(), n.code());
        }

        return new IngestOutcome(applied, rejected, duplicates, newGenerations, rejections,
                List.copyOf(staleKeys), snapshotsNowResolvable);
    }

    private BucketOutcome applyBucket(String bucket, List<FxIngestRecord> records) {
        boolean isGlobal = bucket.equals(GLOBAL_BUCKET);
        String tenantId = isGlobal ? null : bucket;
        String metricsLabel = isGlobal ? GLOBAL_BUCKET : tenantId;

        List<FxIngestRecord> refRecords = new ArrayList<>();
        List<FxIngestRecord> fixingRecords = new ArrayList<>();
        List<FxIngestRecord> snapshotRecords = new ArrayList<>();
        for (FxIngestRecord r : records) {
            switch (storeKindOf(r.entityType())) {
                case REFERENCE -> refRecords.add(r);
                case FIXING -> fixingRecords.add(r);
                case SNAPSHOT -> snapshotRecords.add(r);
            }
        }

        BucketOutcome out = new BucketOutcome();
        if (!refRecords.isEmpty()) {
            applyReference(bucket, isGlobal, tenantId, metricsLabel, refRecords, out);
        }
        if (!fixingRecords.isEmpty()) {
            applyFixings(tenantId, metricsLabel, fixingRecords, out);
        }
        if (!snapshotRecords.isEmpty()) {
            applySnapshots(tenantId, metricsLabel, snapshotRecords, out);
        }
        return out;
    }

    // --- REFERENCE store ---

    private void applyReference(String bucket, boolean isGlobal, String tenantId, String metricsLabel,
            List<FxIngestRecord> records, BucketOutcome out) {
        ReferenceCatalogue previous = isGlobal ? referenceStore.global() : referenceStore.catalogue(tenantId);
        CatalogueBuilder builder = new CatalogueBuilder().seedFrom(previous);

        Map<String, List<FxIngestRecord>> byNaturalKey = new LinkedHashMap<>();
        for (FxIngestRecord r : records) {
            byNaturalKey.computeIfAbsent(r.entityType() + "|" + r.naturalKey(), k -> new ArrayList<>()).add(r);
        }
        Set<FxIngestRecord> overlapping = ingestValidator.findOverlaps(byNaturalKey);

        boolean changed = false;
        for (FxIngestRecord r : records) {
            String dedupeKey = bucket + "|REF|" + r.entityType();
            String identity = IngestValidator.envelopeOf(r.entityType(), r.payload()).versionId();
            if (!markSeen(dedupeKey, identity)) {
                out.duplicates++;
                continue;
            }
            if (overlapping.contains(r)) {
                out.reject(r, FxIngestCode.FX_I_OVERLAP, identity,
                        "overlapping validity at the same recordedAt for natural key " + r.naturalKey());
                continue;
            }
            Optional<FxIngestCode> violation = ingestValidator.validateReference(r);
            if (violation.isPresent()) {
                out.reject(r, violation.get(), identity, "ingest validation failed: " + violation.get());
                continue;
            }

            String seqKey = bucket + "|REF|" + r.entityType() + "|" + r.naturalKey();
            if (checkSequenceGap(seqKey, r.sequence())) {
                out.staleKeys.add(r.naturalKey());
                builder.markStale(r.naturalKey());
                out.flagNonBlocking(r, FxIngestCode.FX_I_SEQUENCE_GAP, identity,
                        "sequence gap detected for " + r.naturalKey());
                if (!isGlobal) {
                    referenceDataLoader.loadKey(tenantId, r.entityType(), r.naturalKey());
                }
                // GLOBAL-scope gaps cannot be targeted via ReferenceDataLoader.loadKey (it is
                // tenant-shaped by signature); documented limitation, see class/report notes.
            }

            builder.add(r.entityType(), r.payload());
            builder.advanceWatermark(r.publishedAt());
            changed = true;
            out.applied++;
        }

        if (changed) {
            long nextGen = (previous == null ? 0 : previous.generation()) + 1;
            ReferenceCatalogue global = isGlobal ? null : referenceStore.global();
            ReferenceCatalogue next = builder.build(global, nextGen);
            boolean swapped = isGlobal ? referenceStore.swapGlobal(next) : referenceStore.swap(tenantId, next);
            if (swapped) {
                out.newGenerations.put(FxStoreKind.REFERENCE, next.generation());
                metrics.generationAdvanced(metricsLabel, FxStoreKind.REFERENCE, next.generation());
            }
        }
        metrics.ingestApplied(metricsLabel, FxStoreKind.REFERENCE, out.applied);
    }

    // --- FIXING store ---

    private void applyFixings(String tenantId, String metricsLabel, List<FxIngestRecord> records, BucketOutcome out) {
        FixingView previous = fixingStore.view(tenantId);
        Map<SeriesKey, FixingSeries> working = previous == null ? new HashMap<>() : new HashMap<>(previous.all());
        boolean changed = false;

        for (FxIngestRecord r : records) {
            FixingVersion fv = (FixingVersion) r.payload();
            String dedupeKey = tenantId + "|FIXING";
            if (!markSeen(dedupeKey, fv.versionId())) {
                out.duplicates++;
                continue;
            }
            Optional<FxIngestCode> violation = ingestValidator.validateFixing(r);
            if (violation.isPresent()) {
                out.reject(r, violation.get(), fv.versionId(), "fixing ingest validation failed: " + violation.get());
                continue;
            }

            SeriesKey key = new SeriesKey(fv.sourceCode(), fv.pair(), fv.cutoff());
            FixingSeries existing = working.get(key);
            List<FixingSeries.Entry> existingOnDate = existing == null ? List.of() : existing.entriesOn(fv.fixingDate());

            Optional<FxIngestCode> seqViolation = fixingSequenceValidator.validate(fv, existingOnDate);
            if (seqViolation.isPresent()) {
                out.reject(r, seqViolation.get(), fv.versionId(), "fixing sequence validation failed: " + seqViolation.get());
                continue;
            }

            String seqKey = tenantId + "|FIXING|" + key + "|" + fv.fixingDate();
            if (checkSequenceGap(seqKey, r.sequence())) {
                out.staleKeys.add(key + "@" + fv.fixingDate());
                out.flagNonBlocking(r, FxIngestCode.FX_I_SEQUENCE_GAP, fv.versionId(),
                        "sequence gap detected for " + key + "@" + fv.fixingDate());
                // No ReferenceDataLoader-shaped equivalent exists for fixing keys (the SPI is
                // reference-data-shaped only, see MarketDataLoader for the fixing-side loader,
                // which has no single-key variant); documented limitation.
            }

            FixingSeries updated = existing == null ? FixingSeries.of(List.of(fv)) : existing.withAdded(List.of(fv));
            working.put(key, updated);
            changed = true;
            out.applied++;

            if (fv.fixingStatus() == FixingStatus.CORRECTED) {
                FixingSeries.Entry old = findCorrectedEntry(fv, existingOnDate);
                out.pendingCorrections.add(new FixingCorrection(tenantId, fv.sourceCode(), fv.pair(), fv.fixingDate(),
                        fv.cutoff(), old == null ? null : old.versionId(), fv.versionId(),
                        old == null ? null : old.value(), fv.value(), fv.recordedAt()));
            }
        }

        if (changed) {
            long nextGen = (previous == null ? 0 : previous.generation()) + 1;
            LocalDateRange window = previous == null ? null : previous.window();
            FixingView next = new FixingView(working, window, nextGen);
            boolean swapped = fixingStore.swap(tenantId, next);
            if (swapped) {
                out.newGenerations.put(FxStoreKind.FIXING, nextGen);
                metrics.generationAdvanced(metricsLabel, FxStoreKind.FIXING, nextGen);
            }
        }
        metrics.ingestApplied(metricsLabel, FxStoreKind.FIXING, out.applied);
    }

    /** The specific prior entry a CORRECTED version supersedes, by {@code correctionOf} if present, else the latest. */
    private FixingSeries.Entry findCorrectedEntry(FixingVersion fv, List<FixingSeries.Entry> existingOnDate) {
        if (fv.correctionOf() != null) {
            for (FixingSeries.Entry e : existingOnDate) {
                if (fv.correctionOf().equals(e.versionId())) {
                    return e;
                }
            }
        }
        FixingSeries.Entry latest = null;
        for (FixingSeries.Entry e : existingOnDate) {
            if (e.status() == FixingStatus.OFFICIAL || e.status() == FixingStatus.CORRECTED) {
                latest = e;
            }
        }
        return latest;
    }

    // --- SNAPSHOT store ---

    private void applySnapshots(String tenantId, String metricsLabel, List<FxIngestRecord> records, BucketOutcome out) {
        for (FxIngestRecord r : records) {
            MarketSnapshotPayload chunk = (MarketSnapshotPayload) r.payload();
            String dedupeKey = tenantId + "|SNAPSHOT";
            String identity = chunk.marketSnapshotId() + "#" + chunk.chunkIndex();
            if (!markSeen(dedupeKey, identity)) {
                out.duplicates++;
                continue;
            }

            Optional<List<MarketSnapshotPayload>> complete = completionTracker.accept(chunk);
            if (complete.isEmpty()) {
                if (chunk.completionMarker()) {
                    out.reject(r, FxIngestCode.FX_I_SNAPSHOT_INCOMPLETE, identity,
                            "completion marker arrived before all chunks for " + chunk.marketSnapshotId());
                } else {
                    out.applied++;
                }
                continue;
            }

            try {
                MarketSnapshot assembled = snapshotAssembler.assemble(complete.get(), RightsSet.unrestricted());
                marketSnapshotStore.put(tenantId, assembled);
                out.applied++;
                out.snapshotsNowResolvable.add(assembled.marketSnapshotId());
                out.pendingSnapshotAvailability.add(new SnapshotAvailability(tenantId, assembled.marketSnapshotId(),
                        assembled.kind(), assembled.asOfDate(), assembled.fixingKnowledgeCut(), assembled.signOffStatus()));
            } catch (MarketSnapshotStore.SnapshotImmutableException e) {
                out.reject(r, FxIngestCode.FX_I_SNAPSHOT_IMMUTABLE, identity, e.getMessage());
            }
        }
        metrics.ingestApplied(metricsLabel, FxStoreKind.SNAPSHOT, out.applied);
    }

    // --- shared helpers ---

    private static FxStoreKind storeKindOf(FxEntityType type) {
        return switch (type) {
            case FIXING -> FxStoreKind.FIXING;
            case MARKET_SNAPSHOT -> FxStoreKind.SNAPSHOT;
            default -> FxStoreKind.REFERENCE;
        };
    }

    private boolean markSeen(String dedupeKey, String identity) {
        Set<String> seen = seenIdentity.computeIfAbsent(dedupeKey, k -> ConcurrentHashMap.newKeySet());
        return seen.add(identity);
    }

    /** @return {@code true} iff {@code sequence} is more than one past the last seen value for {@code key}. */
    private boolean checkSequenceGap(String key, long sequence) {
        Long previous = lastSequenceByKey.get(key);
        boolean gap = previous != null && sequence > previous + 1;
        lastSequenceByKey.merge(key, sequence, Math::max);
        return gap;
    }

    private record RejectionNotice(FxIngestRecord record, FxIngestCode code, String message) {
    }

    private static final class BucketOutcome {
        int applied;
        int rejected;
        int duplicates;
        final Map<FxStoreKind, Long> newGenerations = new EnumMap<>(FxStoreKind.class);
        final List<IngestRejection> rejections = new ArrayList<>();
        final Set<String> staleKeys = new LinkedHashSet<>();
        final List<String> snapshotsNowResolvable = new ArrayList<>();
        final List<RejectionNotice> rejectionNotices = new ArrayList<>();
        final List<FixingCorrection> pendingCorrections = new ArrayList<>();
        final List<SnapshotAvailability> pendingSnapshotAvailability = new ArrayList<>();

        void reject(FxIngestRecord r, FxIngestCode code, String identity, String message) {
            rejected++;
            rejections.add(new IngestRejection(r.eventId(), identity, code, message));
            rejectionNotices.add(new RejectionNotice(r, code, message));
        }

        /** Applied anyway (e.g. a sequence gap), but still logged and notified (S8.2 step 4/7). */
        void flagNonBlocking(FxIngestRecord r, FxIngestCode code, String identity, String message) {
            rejections.add(new IngestRejection(r.eventId(), identity, code, message));
            rejectionNotices.add(new RejectionNotice(r, code, message));
        }
    }
}
