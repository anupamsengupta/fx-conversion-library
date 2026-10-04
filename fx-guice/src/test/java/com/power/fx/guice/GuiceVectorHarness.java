package com.power.fx.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.power.fx.api.FxConfig;
import com.power.fx.api.FxConverter;
import com.power.fx.api.FxIngestor;
import com.power.fx.api.FxSnapshot;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestOutcome;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.Scope;
import com.power.fx.api.spi.MarketDataLoader;
import com.power.fx.api.spi.ReferenceDataLoader;
import com.power.fx.api.spi.TenantContextProvider;
import com.power.fx.core.cache.CatalogueBuilder;
import com.power.fx.core.cache.FixingSeries;
import com.power.fx.core.cache.FixingStore;
import com.power.fx.core.cache.FixingView;
import com.power.fx.core.cache.MarketSnapshotStore;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.cache.ReferenceStore;
import com.power.fx.core.cache.SeriesKey;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.testkit.doubles.InMemoryMarketDataLoader;
import com.power.fx.testkit.doubles.InMemoryReferenceDataLoader;
import com.power.fx.testkit.doubles.InMemoryTenantContextProvider;
import com.power.fx.testkit.fixtures.GoldenReferenceData;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-support harness that is the Guice-wired analogue of {@code
 * fx-testkit}'s {@code VectorRunner} (Task 4.4): same public surface and
 * same {@code CatalogueBuilder}/fixing/snapshot publishing mechanics, but
 * every store/engine/converter it exposes comes from a real {@link
 * com.power.fx.guice.FxModule}-installed {@link Injector}, not from
 * hand-{@code new}'d instances. This is what makes {@link WiringTest}'s
 * golden-vector re-run a genuine exercise of the DI-assembled stack rather
 * than a copy of {@code VectorRunner}'s own wiring under a different name.
 *
 * <p>Lives in {@code fx-guice}'s test sources (not {@code fx-testkit}'s
 * main sources, which this task must not modify) and depends on {@code
 * fx-testkit} only at test scope, exactly as {@code fx-guice}'s {@code
 * pom.xml} declares.
 */
final class GuiceVectorHarness {

    private final Injector injector;
    private final ReferenceStore referenceStore;
    private final FixingStore fixingStore;
    private final MarketSnapshotStore snapshotStore;
    private final FxIngestor ingestor;
    private final InMemoryTenantContextProvider tenantContextProvider;
    private final InMemoryReferenceDataLoader referenceDataLoader;
    private final InMemoryMarketDataLoader marketDataLoader;
    private final FxConfig config;
    private final Map<SeriesKey, List<FixingVersion>> pendingFixings = new HashMap<>();

    GuiceVectorHarness() {
        this(false);
    }

    GuiceVectorHarness(boolean meteringEnabled) {
        this.config = new FxConfig(FxConfig.BootstrapMode.EAGER, List.of(), Duration.ofSeconds(5), Duration.ZERO,
                FxConfig.DEFAULT_FIXING_HOT_WINDOW_YEARS, FxConfig.DEFAULT_RETAINED_SNAPSHOTS_PER_TENANT, true,
                FxConfig.DEFAULT_MEMO_MAX_ENTRIES_PER_SNAPSHOT, FxConfig.DEFAULT_FORWARD_MEMO_MAX_ENTRIES_PER_CURVE,
                FxConfig.DEFAULT_DECIMAL_WORKING_PRECISION, FxConfig.DEFAULT_EAGER_INPUTS_HASH, true,
                FxConfig.DEFAULT_FAIL_ON_STALE, FxConfig.DEFAULT_MAX_FALLBACK_STALENESS_DAYS);
        this.tenantContextProvider = new InMemoryTenantContextProvider(GoldenReferenceData.TENANT);
        this.referenceDataLoader = new InMemoryReferenceDataLoader();
        this.marketDataLoader = new InMemoryMarketDataLoader();

        this.injector = Guice.createInjector(new FxModule(config, meteringEnabled), new AbstractModule() {
            @Override
            protected void configure() {
                bind(TenantContextProvider.class).toInstance(tenantContextProvider);
                bind(ReferenceDataLoader.class).toInstance(referenceDataLoader);
                bind(MarketDataLoader.class).toInstance(marketDataLoader);
            }
        });

        this.referenceStore = injector.getInstance(ReferenceStore.class);
        this.fixingStore = injector.getInstance(FixingStore.class);
        this.snapshotStore = injector.getInstance(MarketSnapshotStore.class);
        this.ingestor = injector.getInstance(FxIngestor.class);
    }

    Injector injector() {
        return injector;
    }

    FxConverter converter() {
        return injector.getInstance(FxConverter.class);
    }

    FxMath fxMath() {
        return (FxMath) injector.getInstance(com.power.fx.core.decimal.DecimalTranscendentals.class);
    }

    FxConfig config() {
        return config;
    }

    InMemoryTenantContextProvider tenantContextProvider() {
        return tenantContextProvider;
    }

    void setTenant(String tenantId) {
        tenantContextProvider.setTenant(tenantId);
    }

    // --- real ingestion path ---

    IngestOutcome ingest(List<FxIngestRecord> records) {
        return ingestor.apply(records);
    }

    // --- direct store-population path (mirrors VectorRunner exactly, Task 3b.3 convention) ---

    void publishCatalogue(String tenantId, CatalogueBuilder builder) {
        ReferenceCatalogue previous = referenceStore.catalogue(tenantId);
        builder.seedFrom(previous);
        ReferenceCatalogue cat = builder.build(referenceStore.global(), referenceStore.generation(tenantId) + 1);
        referenceStore.swap(tenantId, cat);
    }

    void publishGlobalCatalogue(CatalogueBuilder builder) {
        ReferenceCatalogue previous = referenceStore.global();
        builder.seedFrom(previous);
        ReferenceCatalogue cat = builder.build(null, (previous == null ? 0 : previous.generation()) + 1);
        referenceStore.swapGlobal(cat);
    }

    void addFixing(FixingVersion version) {
        pendingFixings.computeIfAbsent(new SeriesKey(version.sourceCode(), version.pair(), version.cutoff()),
                k -> new ArrayList<>()).add(version);
    }

    void publishFixings(String tenantId) {
        Map<SeriesKey, FixingSeries> series = new HashMap<>();
        pendingFixings.forEach((key, versions) -> series.put(key, FixingSeries.of(versions)));
        fixingStore.swap(tenantId, new FixingView(series, null, fixingStore.generation(tenantId) + 1));
    }

    void publishSnapshot(MarketSnapshot snapshot) {
        snapshotStore.put(snapshot.tenantId(), snapshot);
    }

    FxSnapshot pin(String marketSnapshotId, Instant knowledgeCut) {
        return converter().pin(marketSnapshotId, knowledgeCut);
    }

    // --- ingest-record convenience builders (identical convention to VectorRunner's) ---

    static FxIngestRecord globalRecord(FxEntityType type, String naturalKey, Object payload) {
        return new FxIngestRecord("evt-" + type + "-" + naturalKey, type, Scope.GLOBAL, null, naturalKey, 1, payload,
                GoldenReferenceData.RECORDED_AT);
    }

    static FxIngestRecord tenantRecord(String tenantId, FxEntityType type, String naturalKey, Object payload) {
        return new FxIngestRecord("evt-" + type + "-" + naturalKey + "-" + tenantId, type, Scope.TENANT, tenantId,
                naturalKey, 1, payload, GoldenReferenceData.RECORDED_AT);
    }

    static FxIngestRecord fixingRecord(String tenantId, FixingVersion fv, long sequence) {
        return new FxIngestRecord("evt-fix-" + fv.versionId(), FxEntityType.FIXING, Scope.TENANT, tenantId,
                fv.sourceCode() + "|" + fv.pair().canonical() + "|" + fv.cutoff(), sequence, fv, fv.recordedAt());
    }
}
