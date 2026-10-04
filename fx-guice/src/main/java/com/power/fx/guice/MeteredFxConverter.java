package com.power.fx.guice;

import com.power.fx.api.FxConverter;
import com.power.fx.api.FxSnapshot;
import com.power.fx.api.request.ChainRequest;
import com.power.fx.api.request.ConversionRequest;
import com.power.fx.api.request.FxRequest;
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
import com.power.fx.api.spi.FxMetrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pattern #13 Decorator: wraps a {@link FxConverter} (in practice, {@link
 * com.power.fx.core.DefaultFxConverter}) and times every request-bearing
 * resolution, reporting via {@link FxMetrics#resolutionLatency}. <strong>The
 * only class in the entire reactor permitted to call {@link
 * System#nanoTime()}</strong> (A-11) -- this is precisely why it lives here,
 * in {@code fx-guice}, and not in {@code fx-core}: AR-04's "no wall clock"
 * ArchUnit rule only ever imports {@code com.power.fx.api}/{@code
 * com.power.fx.core} (see {@code fx-testkit}'s {@code ArchitectureTest}), so
 * this class sits structurally outside that rule's scan scope rather than
 * needing an explicit per-class exemption inside it.
 *
 * <h2>tenantId without a dependency on {@code TenantContextProvider}</h2>
 * S9.1's own code block constructs this class with exactly two arguments --
 * {@code new MeteredFxConverter(core, metrics)} -- no tenant port. Every
 * {@link FxResult} carries a non-null {@link Lineage} (even on its error
 * path, via {@code fx-core}'s {@code MinimalLineage} sentinel, whose
 * {@code tenantId} is {@code "UNKNOWN"} when no tenant was ever resolved),
 * and {@link Lineage#tenantId()} is exactly the tenant the inner call
 * actually resolved against -- a more faithful value for a latency metric
 * than re-reading {@code TenantContextProvider} a second time from the
 * decorator (which could itself race a context change between the two
 * reads). This class therefore derives {@code tenantId} from the result,
 * matching the literal two-argument constructor the tech spec specifies.
 *
 * <h2>Methods without a {@code Purpose}</h2>
 * {@link FxMetrics#resolutionLatency} takes a {@link
 * com.power.fx.api.model.Purpose}, which only the five request-bearing
 * methods ({@link #rate}, {@link #convert}, {@link #convertSeries}, {@link
 * #convertChain}, {@link #revalue}) naturally carry (via {@code
 * request.context().purpose()}). {@link #pin}, {@link #prewarm} and {@link
 * #assessCorrectionImpact} are delegated directly, untimed: S10a.1's NFR
 * this decorator exists to measure ("single convert, warm, curve cached,
 * p99 &lt;= 20us") is specifically about resolution calls, not about pinning
 * or bootstrap I/O, and inventing a placeholder {@code Purpose} for
 * non-resolution calls would pollute the metric rather than clarify it --
 * flagged here as a documented, deliberate scope decision, not an omission.
 *
 * <h2>{@link #batch}</h2>
 * {@link com.power.fx.core.AbstractFxConverter#batch} dispatches each
 * request to {@code this.rate(...)}/{@code this.convert(...)}/etc. on
 * itself. If this decorator simply delegated {@code batch} to {@code
 * delegate.batch(requests)}, every per-request dispatch inside that call
 * would re-enter the <em>undecorated</em> inner converter, never passing
 * back through this class's timers -- silently losing per-request metering
 * for every batched request. This method therefore re-implements A-10's
 * index-aligned, never-fails-wholesale dispatch itself, routing each
 * request through this decorator's own (timed) methods instead.
 *
 * @see "Tech spec S9.1, A-11, Pattern #13 Decorator"
 */
public final class MeteredFxConverter implements FxConverter {

    private final FxConverter delegate;
    private final FxMetrics metrics;

    public MeteredFxConverter(FxConverter delegate, FxMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public FxSnapshot pin(String marketSnapshotId) {
        return delegate.pin(marketSnapshotId);
    }

    @Override
    public FxSnapshot pin(String marketSnapshotId, Instant knowledgeCut) {
        return delegate.pin(marketSnapshotId, knowledgeCut);
    }

    @Override
    public PrewarmOutcome prewarm(PrewarmRequest request) {
        return delegate.prewarm(request);
    }

    @Override
    public RateResult rate(RateRequest request) {
        long start = System.nanoTime();
        RateResult result = delegate.rate(request);
        report(result.lineage(), request.context().purpose(), start);
        return result;
    }

    @Override
    public ConversionResult convert(ConversionRequest request) {
        long start = System.nanoTime();
        ConversionResult result = delegate.convert(request);
        report(result.lineage(), request.context().purpose(), start);
        return result;
    }

    @Override
    public SeriesResult convertSeries(SeriesRequest request) {
        long start = System.nanoTime();
        SeriesResult result = delegate.convertSeries(request);
        report(result.lineage(), request.context().purpose(), start);
        return result;
    }

    @Override
    public ChainResult convertChain(ChainRequest request) {
        long start = System.nanoTime();
        ChainResult result = delegate.convertChain(request);
        report(result.lineage(), request.context().purpose(), start);
        return result;
    }

    @Override
    public RevaluationResult revalue(MonetaryRevaluationRequest request) {
        long start = System.nanoTime();
        RevaluationResult result = delegate.revalue(request);
        report(result.lineage(), request.context().purpose(), start);
        return result;
    }

    @Override
    public CorrectionImpact assessCorrectionImpact(Lineage previous, FxSnapshot current) {
        return delegate.assessCorrectionImpact(previous, current);
    }

    @Override
    public List<FxResult> batch(List<? extends FxRequest> requests) {
        List<FxResult> results = new ArrayList<>(requests.size());
        for (FxRequest r : requests) {
            results.add(switch (r) {
                case RateRequest rr -> rate(rr);
                case ConversionRequest cr -> convert(cr);
                case SeriesRequest sr -> convertSeries(sr);
                case ChainRequest chr -> convertChain(chr);
                case MonetaryRevaluationRequest mr -> revalue(mr);
            });
        }
        return results;
    }

    private void report(Lineage lineage, com.power.fx.api.model.Purpose purpose, long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        metrics.resolutionLatency(lineage.tenantId(), purpose, elapsed);
    }
}
