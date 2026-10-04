package com.power.fx.core;

import com.power.fx.api.FxConfig;
import com.power.fx.api.FxConverter;
import com.power.fx.api.FxHealth;
import com.power.fx.api.FxSnapshot;
import com.power.fx.api.error.FxError;
import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.TenantHealthStatus;
import com.power.fx.api.request.ChainRequest;
import com.power.fx.api.request.ConversionRequest;
import com.power.fx.api.request.FxRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.MonetaryRevaluationRequest;
import com.power.fx.api.request.PrewarmRequest;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.request.SeriesRequest;
import com.power.fx.api.result.ChainResult;
import com.power.fx.api.result.ConversionResult;
import com.power.fx.api.result.CorrectionImpact;
import com.power.fx.api.result.FxResult;
import com.power.fx.api.result.Lineage;
import com.power.fx.api.result.PrewarmOutcome;
import com.power.fx.api.result.RateResult;
import com.power.fx.api.result.RevaluationResult;
import com.power.fx.api.result.SeriesResult;
import com.power.fx.core.leg.ChainEngine;
import com.power.fx.core.leg.RevaluationEngine;
import com.power.fx.core.lineage.MinimalLineage;
import com.power.fx.core.snapshot.PinnedState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared {@link FxConverter} method bodies for {@code DefaultFxConverter}
 * (re-resolves tenant + pin on every call) and {@code PinnedFxSnapshot}
 * (uses a fixed pin). Business failures are caught here and converted into
 * the appropriate {@link FxResult}'s {@code error} slot -- never thrown
 * (FS S14.1); {@code orThrow()} remains the only opt-in.
 */
public abstract class AbstractFxConverter implements FxConverter {

    protected final ConversionPipeline pipeline;
    protected final ChainEngine chainEngine;
    protected final RevaluationEngine revaluationEngine;
    protected final FxConfig config;

    protected AbstractFxConverter(ConversionPipeline pipeline, ChainEngine chainEngine,
            RevaluationEngine revaluationEngine, FxConfig config) {
        this.pipeline = pipeline;
        this.chainEngine = chainEngine;
        this.revaluationEngine = revaluationEngine;
        this.config = config;
    }

    /** Stages 1-2: tenant resolution and pinning (re-resolved, or validated against a fixed pin). */
    protected abstract PinnedState resolvePin(FxRequestContext context);

    protected abstract String tenantIdForErrors();

    @Override
    public RateResult rate(RateRequest request) {
        try {
            PinnedState state = resolvePin(request.context());
            return pipeline.rate(request, state);
        } catch (com.power.fx.api.error.FxException e) {
            return new RateResult(request.from(), request.to(), BigDecimal.ZERO, null, RateFinalityOf(e),
                    List.of(), List.of(), unrestrictedRestriction(),
                    errorLineage(request.context()), List.of(), Optional.of(e.error()));
        }
    }

    @Override
    public ConversionResult convert(ConversionRequest request) {
        try {
            PinnedState state = resolvePin(request.context());
            return pipeline.convert(request, state);
        } catch (com.power.fx.api.error.FxException e) {
            return new ConversionResult(request.from(), request.to(), request.amount(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, com.power.fx.api.model.RateType.FIXING, RateFinalityOf(e),
                    List.of(), List.of(), unrestrictedRestriction(), errorLineage(request.context()), List.of(),
                    Optional.of(e.error()));
        }
    }

    @Override
    public SeriesResult convertSeries(SeriesRequest request) {
        try {
            PinnedState state = resolvePin(request.context());
            return pipeline.convertSeries(request, state);
        } catch (com.power.fx.api.error.FxException e) {
            return new SeriesResult(request.from(), request.to(), null, null, null, null, null, null, List.of(),
                    null, RateFinalityOf(e), List.of(), unrestrictedRestriction(), errorLineage(request.context()),
                    List.of(), Optional.of(e.error()));
        }
    }

    @Override
    public ChainResult convertChain(ChainRequest request) {
        try {
            PinnedState state = resolvePin(request.context());
            return chainEngine.chain(request, state);
        } catch (com.power.fx.api.error.FxException e) {
            return new ChainResult(java.util.Map.of(), List.of(), RateFinalityOf(e), errorLineage(request.context()),
                    List.of(), Optional.of(e.error()));
        }
    }

    @Override
    public RevaluationResult revalue(MonetaryRevaluationRequest request) {
        try {
            PinnedState state = resolvePin(request.context());
            return revaluationEngine.revalue(request, state);
        } catch (com.power.fx.api.error.FxException e) {
            return new RevaluationResult(null, null, null, null, null, null, null, RateFinalityOf(e), List.of(),
                    errorLineage(request.context()), List.of(), Optional.of(e.error()));
        }
    }

    @Override
    public CorrectionImpact assessCorrectionImpact(Lineage previous, FxSnapshot current) {
        // Not implemented in this phase: no ReplayableRequest -> request reconstruction path exists
        // yet (that requires a request-rebuilding adapter this phase did not build). Returns
        // replayable=false with an explicit error rather than guessing, per S6.16 step 1's own
        // "absent -> replayable=false" rule for the case replay inputs cannot be reconstructed.
        return new CorrectionImpact(false, false, null, null, null, null, null, List.of(), Optional.empty(),
                previous, Optional.empty(), Optional.of(new FxError(FxErrorCode.FX_E_DATA_NOT_LOADED,
                        "assessCorrectionImpact is not implemented in this phase", java.util.Map.of())));
    }

    @Override
    public PrewarmOutcome prewarm(PrewarmRequest request) {
        // No loader SPI wiring exists in this phase's test harness (fx-testkit's InMemory* loaders
        // are a Phase 3b deliverable); this is a structural stub honouring the "I/O only here" rule.
        return new PrewarmOutcome(0, 0, 0, List.of(), List.of());
    }

    @Override
    public List<FxResult> batch(List<? extends FxRequest> requests) {
        List<FxResult> results = new ArrayList<>(requests.size());
        for (FxRequest r : requests) {
            results.add(dispatch(r));
        }
        return results;
    }

    private FxResult dispatch(FxRequest r) {
        return switch (r) {
            case RateRequest rr -> rate(rr);
            case ConversionRequest cr -> convert(cr);
            case SeriesRequest sr -> convertSeries(sr);
            case ChainRequest chr -> convertChain(chr);
            case MonetaryRevaluationRequest mr -> revalue(mr);
        };
    }

    private static com.power.fx.api.model.RateFinality RateFinalityOf(com.power.fx.api.error.FxException e) {
        return com.power.fx.api.model.RateFinality.UNRESOLVED;
    }

    private com.power.fx.api.result.DistributionRestriction unrestrictedRestriction() {
        return new com.power.fx.api.result.DistributionRestriction(false, false, false, new java.util.TreeSet<>());
    }

    private Lineage errorLineage(FxRequestContext context) {
        return MinimalLineage.of(tenantIdForErrors(), null, null, null, "UNKNOWN", 0, context);
    }
}
