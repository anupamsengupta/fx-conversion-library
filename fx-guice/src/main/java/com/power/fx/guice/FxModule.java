package com.power.fx.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.power.fx.api.FxConfig;
import com.power.fx.api.FxConverter;
import com.power.fx.api.FxHealth;
import com.power.fx.api.FxIngestor;
import com.power.fx.api.spi.FxEventListener;
import com.power.fx.api.spi.FxMetrics;
import com.power.fx.api.spi.MarketDataLoader;
import com.power.fx.api.spi.ReferenceDataLoader;
import com.power.fx.api.spi.TenantContextProvider;
import com.power.fx.core.ConversionPipeline;
import com.power.fx.core.DefaultFxConverter;
import com.power.fx.core.DefaultFxHealth;
import com.power.fx.core.DefaultFxIngestor;
import com.power.fx.core.averaging.AveragingEngine;
import com.power.fx.core.averaging.DefaultAveragingEngine;
import com.power.fx.core.cache.FixingStore;
import com.power.fx.core.cache.InMemoryFixingStore;
import com.power.fx.core.cache.InMemoryMarketSnapshotStore;
import com.power.fx.core.cache.InMemoryReferenceStore;
import com.power.fx.core.cache.MarketSnapshotStore;
import com.power.fx.core.cache.ReferenceStore;
import com.power.fx.core.curve.ForwardCurveCache;
import com.power.fx.core.curve.SnapshotForwardCurveCache;
import com.power.fx.core.date.DateRuleResolver;
import com.power.fx.core.date.DefaultDateRuleResolver;
import com.power.fx.core.decimal.DecimalTranscendentals;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.DefaultEntitlementResolver;
import com.power.fx.core.entitlement.EntitlementResolver;
import com.power.fx.core.leg.ChainEngine;
import com.power.fx.core.leg.DefaultChainEngine;
import com.power.fx.core.leg.DefaultRevaluationEngine;
import com.power.fx.core.leg.FunctionalCurrencyResolver;
import com.power.fx.core.leg.RevaluationEngine;
import com.power.fx.core.lineage.DefaultLineageBuilder;
import com.power.fx.core.lineage.LineageBuilder;
import com.power.fx.core.pair.DefaultPairResolver;
import com.power.fx.core.pair.PairResolver;
import com.power.fx.core.precision.DefaultPrecisionEngine;
import com.power.fx.core.precision.PrecisionEngine;
import com.power.fx.core.rate.DefaultFallbackChainRunner;
import com.power.fx.core.rate.DefaultRateSelector;
import com.power.fx.core.rate.FallbackChainRunner;
import com.power.fx.core.rate.RateSelector;

/**
 * The {@code AbstractModule} of S9.1: binds every internal port to its
 * {@code fx-core} default implementation, the three bitemporal stores, and
 * the ingestion/health facades, then exposes {@link FxConverter} via a
 * {@code @Provides} method that conditionally wraps {@link
 * DefaultFxConverter} in {@link MeteredFxConverter} (A-11, Pattern #13).
 *
 * <h2>TI-02: why this module is heavier than S9.1's own code block</h2>
 * S9.1's indicative listing shows plain {@code bind(X.class).to(Y.class)
 * .in(Singleton.class)} calls throughout, which only works out of the box
 * for a target class that Guice can construct itself -- either a public
 * no-arg constructor, or a constructor annotated {@code @Inject}. The
 * implementation plan's Task 4.1 flagged this as TI-02: "if {@code
 * jakarta.inject} on {@code fx-core} constructors is rejected, every
 * binding ... must become a {@code @Provides} method with explicit
 * constructor argument wiring instead." Inspecting the actual Phase 2
 * code (not re-guessing from the plan) shows exactly that rejection
 * happened: of every class bound below, only {@link FxMath} carries an
 * {@code @Inject}-annotated constructor (it needs {@link FxConfig}, which
 * this module binds to a fixed instance, so Guice can construct it
 * automatically). Every other default implementation either:
 * <ul>
 *   <li>has a public no-arg constructor ({@link InMemoryReferenceStore},
 *       {@link InMemoryFixingStore}, {@link DefaultDateRuleResolver},
 *       {@link DefaultEntitlementResolver}, {@link DefaultAveragingEngine},
 *       {@link DefaultPrecisionEngine}, {@link DefaultLineageBuilder},
 *       {@link DefaultFallbackChainRunner}, {@link
 *       FunctionalCurrencyResolver}) -- plain {@code bind().to()} still
 *       works for these, since Guice's just-in-time binding resolution
 *       falls back to a public no-arg constructor when no {@code @Inject}
 *       constructor exists, or
 *   <li>has a real, multi-argument, non-{@code @Inject} constructor
 *       ({@link InMemoryMarketSnapshotStore}, {@link DefaultPairResolver},
 *       {@link SnapshotForwardCurveCache}, {@link DefaultRateSelector},
 *       {@link ConversionPipeline}, {@link DefaultChainEngine}, {@link
 *       DefaultRevaluationEngine}, {@link DefaultFxHealth}, {@link
 *       DefaultFxIngestor}, {@link DefaultFxConverter}) -- these
 *       <strong>require</strong> an explicit {@code @Provides} method
 *       below, with Guice resolving each method's own parameters from the
 *       bindings already declared in this module (ordinary Guice
 *       provider-method injection, not manual {@code injector.getInstance}
 *       calls).
 * </ul>
 * This is the TI-02 fallback the plan priced in as "a materially larger
 * wiring task" -- it is exactly that, but no larger than necessary: only
 * the classes that actually need it get a {@code @Provides} method.
 *
 * @see "Tech spec S9.1; implementation plan Section 7 Task 4.1, Section 8.3 TI-02"
 */
public final class FxModule extends AbstractModule {

    private final FxConfig config;
    private final boolean meteringEnabled;

    /**
     * @param config the host-supplied library configuration.
     * @param meteringEnabled whether {@link FxConverter} resolves to the
     *         {@link MeteredFxConverter} decorator or the plain {@link
     *         DefaultFxConverter}. <strong>Not</strong> a {@link FxConfig}
     *         field: S9.1's indicative code reads {@code
     *         cfg.meteringEnabled()}, but {@code FxConfig}'s actual S4.12
     *         field list (as built in Phase 1) has no such field -- the
     *         same kind of "spec prose promises a config flag that the
     *         record never grew" gap Phase 1 already documented for
     *         {@code eagerCurveBuild} (implementation plan Section 7, new
     *         gap #3). This module does not unilaterally add a field to
     *         {@code FxConfig} (a library-module boundary this task must
     *         not cross), so the toggle is a constructor argument of this
     *         Guice module instead -- a host wires it the same way it
     *         wires every other Guice module constructor argument.
     */
    public FxModule(FxConfig config, boolean meteringEnabled) {
        this.config = config;
        this.meteringEnabled = meteringEnabled;
    }

    @Override
    protected void configure() {
        bind(FxConfig.class).toInstance(config);

        // --- bitemporal stores (S7.1, S9.1) ---
        bind(ReferenceStore.class).to(InMemoryReferenceStore.class).in(Singleton.class);
        bind(FixingStore.class).to(InMemoryFixingStore.class).in(Singleton.class);
        // MarketSnapshotStore: InMemoryMarketSnapshotStore(int retainedSnapshotsPerTenant) has no
        // no-arg/@Inject constructor (TI-02) -- bound via the @Provides method below instead of a
        // plain bind().to() here.

        // --- decimal foundations (S6.10) ---
        // FxMath carries @Inject(FxConfig) (Phase 2's one TI-02-compliant class), so plain
        // bind().in(Singleton.class) lets Guice construct it; DecimalTranscendentals links to the
        // same singleton instance.
        bind(FxMath.class).in(Singleton.class);
        bind(DecimalTranscendentals.class).to(FxMath.class).in(Singleton.class);

        // --- ten internal-port-to-default-implementation bindings (S9.1) ---
        // No-arg-constructible: plain bind().to() resolves via Guice's implicit no-arg fallback.
        bind(DateRuleResolver.class).to(DefaultDateRuleResolver.class).in(Singleton.class);
        bind(EntitlementResolver.class).to(DefaultEntitlementResolver.class).in(Singleton.class);
        bind(AveragingEngine.class).to(DefaultAveragingEngine.class).in(Singleton.class);
        bind(PrecisionEngine.class).to(DefaultPrecisionEngine.class).in(Singleton.class);
        bind(LineageBuilder.class).to(DefaultLineageBuilder.class).in(Singleton.class);
        bind(FallbackChainRunner.class).to(DefaultFallbackChainRunner.class).in(Singleton.class);
        bind(FunctionalCurrencyResolver.class).in(Singleton.class);
        // PairResolver, RateSelector, ForwardCurveCache: @Provides methods below (TI-02, real
        // constructor arguments).

        // --- ingestion / health facades (S9.1) ---
        // DefaultFxHealth / DefaultFxIngestor: @Provides methods below (TI-02).
        bind(FxIngestor.class).to(DefaultFxIngestor.class).in(Singleton.class);
        bind(FxHealth.class).to(DefaultFxHealth.class).in(Singleton.class);

        // --- three required host-supplied SPIs (S9.1) ---
        // FxConverter is provided below so the metering decorator can wrap it.
        requireBinding(TenantContextProvider.class);
        requireBinding(ReferenceDataLoader.class);
        requireBinding(MarketDataLoader.class);

        // --- two optional SPIs, defaulting to their no-op singletons (S9.1) ---
        OptionalBinder.newOptionalBinder(binder(), FxEventListener.class)
                .setDefault().toInstance(FxEventListener.noop());
        OptionalBinder.newOptionalBinder(binder(), FxMetrics.class)
                .setDefault().toInstance(FxMetrics.noop());
    }

    // --- TI-02 @Provides methods: classes whose real (non-@Inject) constructors Guice cannot
    // auto-wire. Each method's own parameters are themselves resolved from this module's bindings
    // by ordinary Guice provider-method injection -- no manual injector.getInstance() calls. ---

    @Provides
    @Singleton
    MarketSnapshotStore marketSnapshotStore(FxConfig cfg) {
        return new InMemoryMarketSnapshotStore(cfg.retainedSnapshotsPerTenant());
    }

    @Provides
    @Singleton
    PairResolver pairResolver(FxMath fxMath) {
        return new DefaultPairResolver(fxMath);
    }

    @Provides
    @Singleton
    ForwardCurveCache forwardCurveCache(FxMath fxMath, FxConfig cfg) {
        return new SnapshotForwardCurveCache(fxMath, cfg.forwardMemoMaxEntriesPerCurve());
    }

    @Provides
    @Singleton
    RateSelector rateSelector(FxMath fxMath, ForwardCurveCache forwardCurveCache, FallbackChainRunner fallbackChainRunner) {
        return new DefaultRateSelector(fxMath, forwardCurveCache, fallbackChainRunner);
    }

    @Provides
    @Singleton
    ConversionPipeline conversionPipeline(DateRuleResolver dateRuleResolver, EntitlementResolver entitlementResolver,
            PairResolver pairResolver, RateSelector rateSelector, AveragingEngine averagingEngine,
            PrecisionEngine precisionEngine, LineageBuilder lineageBuilder, FxMath fxMath) {
        return new ConversionPipeline(dateRuleResolver, entitlementResolver, pairResolver, rateSelector,
                averagingEngine, precisionEngine, lineageBuilder, fxMath);
    }

    @Provides
    @Singleton
    ChainEngine chainEngine(DateRuleResolver dateRuleResolver, EntitlementResolver entitlementResolver,
            PairResolver pairResolver, RateSelector rateSelector, AveragingEngine averagingEngine,
            PrecisionEngine precisionEngine, LineageBuilder lineageBuilder,
            FunctionalCurrencyResolver functionalCurrencyResolver, FxMath fxMath) {
        return new DefaultChainEngine(dateRuleResolver, entitlementResolver, pairResolver, rateSelector,
                averagingEngine, precisionEngine, lineageBuilder, functionalCurrencyResolver, fxMath);
    }

    @Provides
    @Singleton
    RevaluationEngine revaluationEngine(PairResolver pairResolver, RateSelector rateSelector,
            PrecisionEngine precisionEngine, LineageBuilder lineageBuilder,
            FunctionalCurrencyResolver functionalCurrencyResolver, FxMath fxMath) {
        return new DefaultRevaluationEngine(pairResolver, rateSelector, precisionEngine, lineageBuilder,
                functionalCurrencyResolver, fxMath);
    }

    @Provides
    @Singleton
    DefaultFxHealth defaultFxHealth(ReferenceStore referenceStore, MarketSnapshotStore marketSnapshotStore) {
        return new DefaultFxHealth(referenceStore, marketSnapshotStore);
    }

    @Provides
    @Singleton
    DefaultFxIngestor defaultFxIngestor(ReferenceStore referenceStore, FixingStore fixingStore,
            MarketSnapshotStore marketSnapshotStore, FxMath fxMath, FxEventListener eventListener, FxMetrics metrics,
            ReferenceDataLoader referenceDataLoader) {
        return new DefaultFxIngestor(referenceStore, fixingStore, marketSnapshotStore, fxMath, eventListener, metrics,
                referenceDataLoader);
    }

    @Provides
    @Singleton
    DefaultFxConverter defaultFxConverter(TenantContextProvider tenantContextProvider, ReferenceStore referenceStore,
            FixingStore fixingStore, MarketSnapshotStore marketSnapshotStore, FxHealth health,
            ConversionPipeline pipeline, ChainEngine chainEngine, RevaluationEngine revaluationEngine, FxConfig cfg) {
        return new DefaultFxConverter(tenantContextProvider, referenceStore, fixingStore, marketSnapshotStore, health,
                pipeline, chainEngine, revaluationEngine, cfg);
    }

    /**
     * S9.1's own {@code @Provides} method, verbatim in intent: wraps {@link
     * DefaultFxConverter} in {@link MeteredFxConverter} (A-11, Pattern #13)
     * when metering is enabled, otherwise exposes the plain core converter.
     * {@code cfg.meteringEnabled()} in the tech spec's code block is this
     * module's own {@link #meteringEnabled} constructor argument instead
     * (see that field's Javadoc for why).
     */
    @Provides
    @Singleton
    FxConverter fxConverter(DefaultFxConverter core, FxMetrics metrics) {
        return meteringEnabled ? new MeteredFxConverter(core, metrics) : core;
    }
}
