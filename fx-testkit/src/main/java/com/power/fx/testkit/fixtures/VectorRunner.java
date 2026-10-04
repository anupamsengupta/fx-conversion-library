package com.power.fx.testkit.fixtures;

import com.power.fx.api.FxConfig;
import com.power.fx.api.FxSnapshot;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestOutcome;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.Scope;
import com.power.fx.core.DefaultFxConverter;
import com.power.fx.core.DefaultFxHealth;
import com.power.fx.core.DefaultFxIngestor;
import com.power.fx.core.ConversionPipeline;
import com.power.fx.core.averaging.DefaultAveragingEngine;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.cache.FixingSeries;
import com.power.fx.core.cache.FixingView;
import com.power.fx.core.cache.InMemoryFixingStore;
import com.power.fx.core.cache.InMemoryMarketSnapshotStore;
import com.power.fx.core.cache.InMemoryReferenceStore;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.cache.SeriesKey;
import com.power.fx.core.curve.SnapshotForwardCurveCache;
import com.power.fx.core.date.DefaultDateRuleResolver;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.DefaultEntitlementResolver;
import com.power.fx.core.leg.ChainEngine;
import com.power.fx.core.leg.DefaultChainEngine;
import com.power.fx.core.leg.DefaultRevaluationEngine;
import com.power.fx.core.leg.FunctionalCurrencyResolver;
import com.power.fx.core.leg.RevaluationEngine;
import com.power.fx.core.lineage.DefaultLineageBuilder;
import com.power.fx.core.pair.DefaultPairResolver;
import com.power.fx.core.precision.DefaultPrecisionEngine;
import com.power.fx.core.rate.DefaultFallbackChainRunner;
import com.power.fx.core.rate.DefaultRateSelector;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.testkit.doubles.InMemoryMarketDataLoader;
import com.power.fx.testkit.doubles.InMemoryReferenceDataLoader;
import com.power.fx.testkit.doubles.InMemoryTenantContextProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shipped harness wiring a complete, hand-assembled {@link
 * DefaultFxConverter} stack (the same collaborator set {@code fx-guice}'s
 * {@code FxModule} will later bind, Phase 4) for the golden vector test
 * suites (Task 3b.3). Supports both the direct store-population path
 * (existing Phase 2 convention) and the real {@link DefaultFxIngestor}
 * path (Job 1 backfill of Task 2.17) via {@link #ingest}.
 *
 * @see "Tech spec S6.1, S6.2, S9.1; implementation plan Task 3b.3"
 */
public final class VectorRunner {

    private final InMemoryReferenceStore referenceStore = new InMemoryReferenceStore();
    private final InMemoryFixingStore fixingStore = new InMemoryFixingStore();
    private final InMemoryMarketSnapshotStore snapshotStore;
    private final FxMath fxMath;
    private final FxConfig config;
    private final InMemoryTenantContextProvider tenantContextProvider;
    private final InMemoryReferenceDataLoader referenceDataLoader = new InMemoryReferenceDataLoader();
    private final InMemoryMarketDataLoader marketDataLoader = new InMemoryMarketDataLoader();
    private final DefaultFxConverter converter;
    private final DefaultFxIngestor ingestor;
    private final Map<SeriesKey, List<FixingVersion>> pendingFixings = new HashMap<>();

    public VectorRunner() {
        this(new FxMath(60), 8);
    }

    public VectorRunner(FxMath fxMath, int retainedSnapshotsPerTenant) {
        this.fxMath = fxMath;
        this.snapshotStore = new InMemoryMarketSnapshotStore(retainedSnapshotsPerTenant);
        this.config = new FxConfig(FxConfig.BootstrapMode.EAGER, List.of(), Duration.ofSeconds(5), Duration.ZERO,
                FxConfig.DEFAULT_FIXING_HOT_WINDOW_YEARS, retainedSnapshotsPerTenant, true,
                FxConfig.DEFAULT_MEMO_MAX_ENTRIES_PER_SNAPSHOT, FxConfig.DEFAULT_FORWARD_MEMO_MAX_ENTRIES_PER_CURVE,
                FxConfig.DEFAULT_DECIMAL_WORKING_PRECISION, FxConfig.DEFAULT_EAGER_INPUTS_HASH, true,
                FxConfig.DEFAULT_FAIL_ON_STALE, FxConfig.DEFAULT_MAX_FALLBACK_STALENESS_DAYS);
        this.tenantContextProvider = new InMemoryTenantContextProvider(GoldenReferenceData.TENANT);

        DefaultFxHealth health = new DefaultFxHealth(referenceStore, snapshotStore);
        var dateRuleResolver = new DefaultDateRuleResolver();
        var entitlementResolver = new DefaultEntitlementResolver();
        var pairResolver = new DefaultPairResolver(fxMath);
        var forwardCurveCache = new SnapshotForwardCurveCache(fxMath, config.forwardMemoMaxEntriesPerCurve());
        var fallbackChainRunner = new DefaultFallbackChainRunner();
        var rateSelector = new DefaultRateSelector(fxMath, forwardCurveCache, fallbackChainRunner);
        var averagingEngine = new DefaultAveragingEngine();
        var precisionEngine = new DefaultPrecisionEngine();
        var lineageBuilder = new DefaultLineageBuilder();
        var functionalCurrencyResolver = new FunctionalCurrencyResolver();

        ConversionPipeline pipeline = new ConversionPipeline(dateRuleResolver, entitlementResolver, pairResolver,
                rateSelector, averagingEngine, precisionEngine, lineageBuilder, fxMath);
        ChainEngine chainEngine = new DefaultChainEngine(dateRuleResolver, entitlementResolver, pairResolver,
                rateSelector, averagingEngine, precisionEngine, lineageBuilder, functionalCurrencyResolver, fxMath);
        RevaluationEngine revaluationEngine = new DefaultRevaluationEngine(pairResolver, rateSelector, precisionEngine,
                lineageBuilder, functionalCurrencyResolver, fxMath);

        this.converter = new DefaultFxConverter(tenantContextProvider, referenceStore, fixingStore, snapshotStore,
                health, pipeline, chainEngine, revaluationEngine, config);
        this.ingestor = new DefaultFxIngestor(referenceStore, fixingStore, snapshotStore, fxMath, com.power.fx.api.spi.FxEventListener.noop(),
                com.power.fx.api.spi.FxMetrics.noop(), referenceDataLoader);
    }

    public DefaultFxConverter converter() {
        return converter;
    }

    public InMemoryTenantContextProvider tenantContextProvider() {
        return tenantContextProvider;
    }

    public void setTenant(String tenantId) {
        tenantContextProvider.setTenant(tenantId);
    }

    public InMemoryReferenceDataLoader referenceDataLoader() {
        return referenceDataLoader;
    }

    public InMemoryMarketDataLoader marketDataLoader() {
        return marketDataLoader;
    }

    public FxMath fxMath() {
        return fxMath;
    }

    public FxConfig config() {
        return config;
    }

    // --- real ingestion path (Job 1 / Task 2.17) ---

    public IngestOutcome ingest(List<FxIngestRecord> records) {
        return ingestor.apply(records);
    }

    public DefaultFxIngestor ingestor() {
        return ingestor;
    }

    // --- direct store-population path (Phase 2 convention, still useful for large fixture setup) ---

    /**
     * Seeds {@code builder} from the tenant's current catalogue (if any)
     * before building, so repeated calls across a test's setup
     * accumulate rather than replace (matching {@code DefaultFxIngestor}'s
     * own copy-on-write discipline, S8.2 step 5).
     */
    public void publishCatalogue(String tenantId, CatalogueBuilder builder) {
        ReferenceCatalogue previous = referenceStore.catalogue(tenantId);
        builder.seedFrom(previous);
        ReferenceCatalogue cat = builder.build(referenceStore.global(), referenceStore.generation(tenantId) + 1);
        referenceStore.swap(tenantId, cat);
    }

    public void publishGlobalCatalogue(CatalogueBuilder builder) {
        ReferenceCatalogue previous = referenceStore.global();
        builder.seedFrom(previous);
        ReferenceCatalogue cat = builder.build(null, (previous == null ? 0 : previous.generation()) + 1);
        referenceStore.swapGlobal(cat);
    }

    public void addFixing(FixingVersion version) {
        pendingFixings.computeIfAbsent(new SeriesKey(version.sourceCode(), version.pair(), version.cutoff()),
                k -> new ArrayList<>()).add(version);
    }

    public void publishFixings(String tenantId) {
        Map<SeriesKey, FixingSeries> series = new HashMap<>();
        pendingFixings.forEach((key, versions) -> series.put(key, FixingSeries.of(versions)));
        fixingStore.swap(tenantId, new FixingView(series, null, fixingStore.generation(tenantId) + 1));
    }

    public void publishSnapshot(MarketSnapshot snapshot) {
        snapshotStore.put(snapshot.tenantId(), snapshot);
    }

    public FxSnapshot pin(String marketSnapshotId, java.time.Instant knowledgeCut) {
        return converter.pin(marketSnapshotId, knowledgeCut);
    }

    // --- ingest-record convenience builders ---

    public static FxIngestRecord globalRecord(FxEntityType type, String naturalKey, Object payload) {
        return new FxIngestRecord("evt-" + type + "-" + naturalKey, type, Scope.GLOBAL, null, naturalKey, 1, payload,
                GoldenReferenceData.RECORDED_AT);
    }

    public static FxIngestRecord tenantRecord(String tenantId, FxEntityType type, String naturalKey, Object payload) {
        return new FxIngestRecord("evt-" + type + "-" + naturalKey + "-" + tenantId, type, Scope.TENANT, tenantId,
                naturalKey, 1, payload, GoldenReferenceData.RECORDED_AT);
    }

    public static FxIngestRecord fixingRecord(String tenantId, FixingVersion fv, long sequence) {
        return new FxIngestRecord("evt-fix-" + fv.versionId(), FxEntityType.FIXING, Scope.TENANT, tenantId,
                fv.sourceCode() + "|" + fv.pair().canonical() + "|" + fv.cutoff(), sequence, fv, fv.recordedAt());
    }
}
