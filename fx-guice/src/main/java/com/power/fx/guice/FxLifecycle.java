package com.power.fx.guice;

import com.power.fx.api.FxConfig;
import com.power.fx.api.FxIngestor;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.MarketDataChangeSet;
import com.power.fx.api.ingest.MarketWatermark;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.spi.MarketDataLoader;
import com.power.fx.api.spi.ReferenceDataLoader;
import com.power.fx.core.cache.FixingStore;
import com.power.fx.core.cache.FixingView;
import com.power.fx.core.cache.HotWindowPolicy;

import java.io.Closeable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code Closeable} start/stop lifecycle hook (S8.4, S9.1's component
 * inventory, TI-04) around a {@code ScheduledExecutorService} driving
 * {@link FxConfig#reconciliationInterval()}-cadence calls to {@link
 * ReferenceDataLoader#loadChangesSince} / {@link
 * MarketDataLoader#loadChangesSince} and {@link HotWindowPolicy} pruning.
 *
 * <h2>TI-04: scheduler ownership is an open design-review checkpoint, not a
 * closed decision</h2>
 * The tech spec's own S8.4 prose places the scheduler inside {@code
 * DefaultFxIngestor} ("owned by {@code DefaultFxIngestor}"), but the actual
 * Phase 2 {@code DefaultFxIngestor} has no {@code ScheduledExecutorService}
 * field at all -- consistent with D-01's "no clock on the resolution path"
 * discipline, which a scheduler would violate if it lived in {@code
 * fx-core}. The implementation plan's own Task 4.3 resolves this tension by
 * placing the scheduler here, in {@code fx-guice}, which is outside
 * D-01/AR-04's scan scope (see {@code fx-testkit}'s {@code
 * ArchitectureTest}, which only ever imports {@code com.power.fx.api}/
 * {@code com.power.fx.core}). This class follows the plan's placement, but
 * TI-04 itself ("library-owned scheduler vs. a host-supplied scheduler SPI")
 * remains explicitly unresolved per the plan -- a reversal would delete this
 * class and relocate the responsibility to a new host-facing SPI. Hosts
 * that prefer their own scheduler can already opt out today by constructing
 * {@link FxConfig} with {@code reconciliationInterval = Duration.ZERO}
 * (S8.4's own documented escape hatch) and calling the loaders themselves;
 * {@link #start()} below honours that by being a no-op when the interval is
 * zero or negative.
 *
 * <h2>Tenant enumeration gap (new, not in the tech spec)</h2>
 * Every reconciliation cycle needs the set of tenants to reconcile, but no
 * SPI in this reactor enumerates tenants -- {@link
 * com.power.fx.api.spi.TenantContextProvider} only resolves the
 * <em>current</em> (request-scoped) tenant, and neither loader SPI exposes a
 * "list known tenants" method. This class therefore takes an explicit,
 * host-supplied {@code Set<String>} of tenant ids to reconcile, which a host
 * updates (e.g. via {@link #setTenantIds}) as tenants are onboarded. This is
 * a new gap surfaced while implementing Task 4.3, not a tech-spec
 * requirement this class silently invents a workaround for.
 *
 * <h2>Market-data-to-ingest-record bridge (new, not in the tech spec)</h2>
 * {@link ReferenceDataLoader#loadChangesSince} already returns {@link
 * FxIngestRecord}s ready for {@link FxIngestor#apply}, so that half of
 * reconciliation is a direct, well-defined pass-through. {@link
 * MarketDataLoader#loadChangesSince}, by contrast, returns a {@link
 * MarketDataChangeSet} of raw {@link FixingVersion}s -- the tech spec
 * defines no mapping from that shape into the {@code FxIngestRecord} shape
 * ingestion requires (S8.1 shows only the CDM-to-{@code FxIngestRecord}
 * mapping, explicitly performed by the host, not the library). {@link
 * #toFixingRecords} is this class's best-effort bridge: it synthesises a
 * deterministic {@code eventId} and a local, per-{@code (tenantId, source,
 * pair, cutoff)} monotonic {@code sequence} counter seeded at 1. This
 * sequence counter is <strong>not</strong> guaranteed to agree with
 * whatever sequence numbering a real host's reconciliation feed uses
 * elsewhere for the same key, which could trip {@code
 * DefaultFxIngestor}'s {@code FX_I_SEQUENCE_GAP} contiguity check on a key
 * that has also been fed through the host's own direct ingest path. Flagged
 * here, not silently resolved, for solutions-architect/code-reviewer
 * attention -- mirroring TI-04's own "flag it, don't silently resolve it"
 * instruction.
 */
public final class FxLifecycle implements Closeable {

    private final FxConfig config;
    private final FxIngestor ingestor;
    private final ReferenceDataLoader referenceDataLoader;
    private final MarketDataLoader marketDataLoader;
    private final FixingStore fixingStore;
    private final ScheduledExecutorService scheduler;

    private volatile Set<String> tenantIds;
    private final ConcurrentHashMap<String, Instant> referenceWatermarks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MarketWatermark> marketWatermarks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> fixingSequenceCounters = new ConcurrentHashMap<>();

    private volatile ScheduledFuture<?> scheduledTask;

    public FxLifecycle(FxConfig config, FxIngestor ingestor, ReferenceDataLoader referenceDataLoader,
            MarketDataLoader marketDataLoader, FixingStore fixingStore, Set<String> initialTenantIds) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.ingestor = Objects.requireNonNull(ingestor, "ingestor must not be null");
        this.referenceDataLoader = Objects.requireNonNull(referenceDataLoader, "referenceDataLoader must not be null");
        this.marketDataLoader = Objects.requireNonNull(marketDataLoader, "marketDataLoader must not be null");
        this.fixingStore = Objects.requireNonNull(fixingStore, "fixingStore must not be null");
        this.tenantIds = Set.copyOf(Objects.requireNonNull(initialTenantIds, "initialTenantIds must not be null"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(FxLifecycle::newDaemonThread);
    }

    private static Thread newDaemonThread(Runnable r) {
        Thread t = new Thread(r, "fx-lifecycle-reconciliation");
        t.setDaemon(true);
        return t;
    }

    /** Hosts onboarding/retiring tenants update the reconciliation set without a restart. */
    public void setTenantIds(Set<String> tenantIds) {
        this.tenantIds = Set.copyOf(Objects.requireNonNull(tenantIds, "tenantIds must not be null"));
    }

    /**
     * Starts the periodic reconciliation/hot-window task. A no-op when
     * {@link FxConfig#reconciliationInterval()} is zero or negative --
     * S8.4's documented escape hatch for hosts that prefer their own
     * scheduler and call the loaders themselves.
     */
    public void start() {
        long millis = config.reconciliationInterval().toMillis();
        if (millis <= 0) {
            return;
        }
        scheduledTask = scheduler.scheduleAtFixedRate(this::runOnce, millis, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs one reconciliation + hot-window-pruning cycle immediately,
     * synchronously, over every tenant currently registered. Exposed
     * (rather than purely {@code private}) so tests can exercise the cycle
     * deterministically instead of racing the scheduler's own cadence.
     */
    public void runOnce() {
        for (String tenantId : tenantIds) {
            try {
                reconcileTenant(tenantId);
                pruneHotWindow(tenantId);
            } catch (RuntimeException e) {
                // A scheduled task must never die because one tenant's reconciliation failed --
                // the next cycle retries. Swallowing here is deliberate; a host-supplied
                // FxEventListener/FxMetrics implementation already observes ingest-level failures
                // through DefaultFxIngestor's own rejection/metrics path.
            }
        }
    }

    private void reconcileTenant(String tenantId) {
        Instant referenceWatermark = referenceWatermarks.getOrDefault(tenantId, Instant.EPOCH);
        List<FxIngestRecord> referenceChanges = referenceDataLoader.loadChangesSince(tenantId, referenceWatermark);
        if (!referenceChanges.isEmpty()) {
            ingestor.apply(referenceChanges);
            Instant newWatermark = referenceChanges.stream()
                    .map(FxIngestRecord::publishedAt)
                    .max(Instant::compareTo)
                    .orElse(referenceWatermark);
            referenceWatermarks.put(tenantId, newWatermark);
        }

        MarketWatermark marketWatermark = marketWatermarks.getOrDefault(tenantId,
                new MarketWatermark(tenantId, Instant.EPOCH, null));
        MarketDataChangeSet changes = marketDataLoader.loadChangesSince(marketWatermark);
        if (!changes.fixings().isEmpty()) {
            ingestor.apply(toFixingRecords(tenantId, changes.fixings()));
        }
        // changes.snapshots() intentionally not re-ingested here: snapshot publication is
        // chunked (SnapshotAssembler/SnapshotCompletionTracker, S7.1.4) and this reconciliation
        // bridge does not reconstruct chunk boundaries from a MarketSnapshotPayload list -- a
        // host wanting reconciled snapshots applies them via FxIngestor.apply directly with its
        // own chunking, exactly as its primary ingest path already must.
        marketWatermarks.put(tenantId, changes.watermark());
    }

    /** See the class Javadoc's "Market-data-to-ingest-record bridge" section. */
    private List<FxIngestRecord> toFixingRecords(String tenantId, List<FixingVersion> fixings) {
        return fixings.stream()
                .map(fv -> {
                    String naturalKey = fv.sourceCode() + "|" + fv.pair().canonical() + "|" + fv.cutoff();
                    String counterKey = tenantId + "|" + naturalKey;
                    long sequence = fixingSequenceCounters.computeIfAbsent(counterKey, k -> new AtomicLong(0))
                            .incrementAndGet();
                    return new FxIngestRecord("fx-lifecycle-reconcile-" + fv.versionId(), FxEntityType.FIXING,
                            fv.scope(), tenantId, naturalKey, sequence, fv, fv.recordedAt());
                })
                .toList();
    }

    /**
     * {@link HotWindowPolicy#prune} needs a host-supplied "today" bound
     * (its own Javadoc: never read from a clock in {@code fx-core}). {@code
     * fx-guice} is outside D-01/AR-04's scan scope (see this class's
     * Javadoc), so reading the wall clock here -- at the host-integration
     * boundary, for a scheduled maintenance cadence rather than for any
     * resolution result -- is in-scope for this module, not a violation of
     * the rule those tests mechanically enforce.
     */
    private void pruneHotWindow(String tenantId) {
        FixingView view = fixingStore.view(tenantId);
        if (view == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDateRange window = new LocalDateRange(today.minusYears(config.fixingHotWindowYears()), today);
        FixingView pruned = HotWindowPolicy.prune(view, window);
        fixingStore.swap(tenantId, pruned);
    }

    @Override
    public void close() {
        ScheduledFuture<?> task = scheduledTask;
        if (task != null) {
            task.cancel(false);
        }
        scheduler.shutdown();
    }
}
