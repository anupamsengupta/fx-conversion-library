package com.power.fx.core.e2e;

import com.power.fx.api.FxConfig;
import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixedFactorKind;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.api.model.SourceRight;
import com.power.fx.api.model.UsageClass;
import com.power.fx.api.spi.TenantContextProvider;
import com.power.fx.core.ConversionPipeline;
import com.power.fx.core.DefaultFxConverter;
import com.power.fx.core.DefaultFxHealth;
import com.power.fx.core.DefaultFxIngestor;
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
import com.power.fx.core.averaging.DefaultAveragingEngine;
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
import com.power.fx.core.testsupport.TestFixtures;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-rolled end-to-end environment for driving golden vectors through
 * the real {@link DefaultFxConverter}, without {@code fx-testkit} (Phase
 * 3b). Stores can be populated either directly via their public swap/put
 * APIs ({@link #publishCatalogue}/{@link #publishFixings}/{@link
 * #publishSnapshot}, as the Phase 2 tests do) or, since the Job 1
 * ingestion backfill, through the real {@link #ingestor} (Task 2.17) via
 * {@link #ingest}.
 */
final class E2eEnvironment {

    static final String TENANT = "TENANT-E2E";
    static final String TENANT_NO_WMR = "TENANT-NO-WMR";
    static final Instant CUT = Instant.parse("2026-12-31T00:00:00Z");

    final InMemoryReferenceStore referenceStore = new InMemoryReferenceStore();
    final InMemoryFixingStore fixingStore = new InMemoryFixingStore();
    final InMemoryMarketSnapshotStore snapshotStore = new InMemoryMarketSnapshotStore(8);
    final FxMath fxMath = new FxMath(60);
    final FxConfig config;
    final DefaultFxConverter converter;
    final DefaultFxIngestor ingestor;
    final RecordingEventListener eventListener = new RecordingEventListener();
    final Map<SeriesKey, java.util.List<FixingVersion>> fixings = new HashMap<>();
    String currentTenant = TENANT;

    E2eEnvironment() {
        config = new FxConfig(FxConfig.BootstrapMode.EAGER, List.of(), Duration.ofSeconds(5), Duration.ZERO,
                FxConfig.DEFAULT_FIXING_HOT_WINDOW_YEARS, FxConfig.DEFAULT_RETAINED_SNAPSHOTS_PER_TENANT, true,
                FxConfig.DEFAULT_MEMO_MAX_ENTRIES_PER_SNAPSHOT, FxConfig.DEFAULT_FORWARD_MEMO_MAX_ENTRIES_PER_CURVE,
                FxConfig.DEFAULT_DECIMAL_WORKING_PRECISION, FxConfig.DEFAULT_EAGER_INPUTS_HASH, true,
                FxConfig.DEFAULT_FAIL_ON_STALE, FxConfig.DEFAULT_MAX_FALLBACK_STALENESS_DAYS);

        TenantContextProvider tenantContextProvider = () -> Optional.of(currentTenant);
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

        converter = new DefaultFxConverter(tenantContextProvider, referenceStore, fixingStore, snapshotStore, health,
                pipeline, chainEngine, revaluationEngine, config);

        ingestor = new DefaultFxIngestor(referenceStore, fixingStore, snapshotStore, fxMath, eventListener,
                com.power.fx.api.spi.FxMetrics.noop(), new StubReferenceDataLoader());
    }

    /** Drives {@code records} through the real {@link DefaultFxIngestor} (Task 2.17). */
    com.power.fx.api.ingest.IngestOutcome ingest(java.util.List<com.power.fx.api.ingest.FxIngestRecord> records) {
        return ingestor.apply(records);
    }

    /** Captures ingest-time notifications for assertions (vector X03's ordering requirement). */
    static final class RecordingEventListener implements com.power.fx.api.spi.FxEventListener {
        final java.util.List<com.power.fx.api.ingest.FixingCorrection> corrections = new java.util.ArrayList<>();
        final java.util.List<com.power.fx.api.ingest.SnapshotAvailability> availabilities = new java.util.ArrayList<>();
        final java.util.List<com.power.fx.api.error.FxIngestCode> rejectedCodes = new java.util.ArrayList<>();

        @Override
        public void onFixingCorrected(com.power.fx.api.ingest.FixingCorrection correction) {
            corrections.add(correction);
        }

        @Override
        public void onRejected(com.power.fx.api.ingest.FxIngestRecord record, com.power.fx.api.error.FxIngestCode code, String message) {
            rejectedCodes.add(code);
        }

        @Override
        public void onSnapshotAvailable(com.power.fx.api.ingest.SnapshotAvailability availability) {
            availabilities.add(availability);
        }
    }

    /** Minimal stub: this environment never needs the loader to actually return data. */
    private static final class StubReferenceDataLoader implements com.power.fx.api.spi.ReferenceDataLoader {
        @Override
        public java.util.List<com.power.fx.api.ingest.FxIngestRecord> loadGlobal() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.power.fx.api.ingest.FxIngestRecord> loadTenant(String tenantId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.power.fx.api.ingest.FxIngestRecord> loadChangesSince(String tenantId, Instant watermark) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.power.fx.api.ingest.FxIngestRecord> loadKey(String tenantId,
                com.power.fx.api.model.FxEntityType entityType, String naturalKey) {
            return java.util.List.of();
        }
    }

    Currency currency(String code, String major) {
        return new Currency(TestFixtures.globalEnvelope(code, code + "-v1"), new CurrencyCode(code), 2,
                major == null ? null : new CurrencyCode(major), "SETTLE-CAL", true);
    }

    PairConvention convention(String base, String quote) {
        CurrencyPair p = TestFixtures.pair(base, quote);
        return new PairConvention(TestFixtures.globalEnvelope(p.canonical(), p.canonical() + "-v1"), p, 4,
                new BigDecimal("10000"), 2, List.of(), null, ForwardMethod.POINTS, InterpolationMethod.LOG_LINEAR_CARRY,
                new BigDecimal("2"), Map.of());
    }

    void addFixing(String source, String base, String quote, LocalDate date, String value) {
        CurrencyPair pair = TestFixtures.pair(base, quote);
        FixingVersion v = new FixingVersion(Scope.TENANT, currentTenant, source, pair, date, "16:00",
                new BigDecimal(value), FixingStatus.OFFICIAL, date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                source + "-" + pair.canonical() + "-" + date, null, date);
        fixings.computeIfAbsent(new SeriesKey(source, pair, "16:00"), k -> new java.util.ArrayList<>()).add(v);
    }

    void addFixing(String source, String base, String quote, LocalDate date, String value, Instant recordedAt, String versionId, String correctionOf, FixingStatus status) {
        CurrencyPair pair = TestFixtures.pair(base, quote);
        FixingVersion v = new FixingVersion(Scope.TENANT, currentTenant, source, pair, date, "16:00",
                new BigDecimal(value), status, recordedAt, versionId, correctionOf, date);
        fixings.computeIfAbsent(new SeriesKey(source, pair, "16:00"), k -> new java.util.ArrayList<>()).add(v);
    }

    void publishFixings(String tenant) {
        Map<SeriesKey, FixingSeries> series = new HashMap<>();
        fixings.forEach((key, versions) -> series.put(key, FixingSeries.of(versions)));
        fixingStore.swap(tenant, new FixingView(series, null, 1));
    }

    void publishCatalogue(String tenant, CatalogueBuilder builder) {
        ReferenceCatalogue cat = builder.build(referenceStore.global(), 1);
        referenceStore.swap(tenant, cat);
    }

    void publishGlobalCatalogue(CatalogueBuilder builder) {
        ReferenceCatalogue cat = builder.build(null, 1);
        referenceStore.swapGlobal(cat);
    }

    com.power.fx.core.snapshot.MarketSnapshot publishSnapshot(String id, SignOffStatus signOff) {
        var snap = new com.power.fx.core.snapshot.MarketSnapshot(id, Scope.TENANT, currentTenant, SnapshotKind.EOD,
                LocalDate.of(2026, 12, 31), CUT, signOff, Map.of(), Map.of(), Map.of(), Map.of());
        snapshotStore.put(currentTenant, snap);
        return snap;
    }

    SourceEntitlement entitlement(String tenant, String sourceCode) {
        return new SourceEntitlement(TestFixtures.tenantEnvelope(tenant, sourceCode, sourceCode + "-v1"), tenant,
                sourceCode, java.util.Set.of(SourceRight.VALUATION, SourceRight.DISPLAY));
    }

    FixingSource fixingSource(String sourceCode, String calendarRef, UsageClass usageClass) {
        return new FixingSource(TestFixtures.globalEnvelope(sourceCode, sourceCode + "-v1"), sourceCode,
                java.time.LocalTime.of(16, 0), java.time.ZoneId.of("Europe/Berlin"), calendarRef, java.util.Set.of(),
                null, usageClass);
    }

    AccountingUnit accountingUnit(String unitId, String functionalCcy, LocalDate validFrom, LocalDate validTo) {
        var env = new com.power.fx.api.model.VersionEnvelope(Scope.GLOBAL, null, unitId, unitId + "-" + functionalCcy,
                validFrom, validTo, TestFixtures.RECORDED_AT, com.power.fx.api.model.VersionStatus.APPROVED, "loader",
                "approver", TestFixtures.RECORDED_AT, "TEST", null, null, "rel-1");
        return new AccountingUnit(env, unitId, "LE-1", new CurrencyCode(functionalCcy), List.of(), null, null);
    }

    FixedFactor fixedFactor(String from, String to, String factor, FixedFactorKind kind, boolean preferOverMarket) {
        return new FixedFactor(TestFixtures.globalEnvelope(from + ">" + to, from + ">" + to + "-v1"),
                new CurrencyCode(from), new CurrencyCode(to), new BigDecimal(factor), kind, preferOverMarket);
    }
}
