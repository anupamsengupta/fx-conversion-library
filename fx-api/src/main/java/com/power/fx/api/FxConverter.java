package com.power.fx.api;

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

import java.time.Instant;
import java.util.List;

/**
 * The library's public facade (Pattern #14 Facade).
 *
 * <p>Binding semantics:
 * <ul>
 *   <li>Business failures are returned as results, never thrown.
 *       {@code orThrow()} on the result is the opt-in.
 *   <li>{@link #batch(List)} is index-aligned and never fails wholesale:
 *       exactly one {@link FxResult} per input request, in input order
 *       (A-10, FS S14.1).
 *   <li>When a request is issued through a {@link FxSnapshot} facade and
 *       the request's own {@code context().snapshot()} is also non-null,
 *       the <strong>request field wins silently</strong>; the two are
 *       compared only to detect a tenant disagreement, which raises
 *       {@code FX_E_TENANT_MISMATCH}. No new code is minted for a
 *       snapshot-id disagreement that is not also a tenant disagreement
 *       (OQ-T05).
 *   <li>{@link #prewarm(PrewarmRequest)} is the only method on this
 *       interface permitted to perform I/O (through the loader SPIs).
 *       It is explicitly off the resolution path, so D-01 continues to
 *       hold for every other method.
 * </ul>
 *
 * @see "Tech spec S5.1"
 */
public interface FxConverter {

    FxSnapshot pin(String marketSnapshotId);

    /**
     * Pins a snapshot at an explicit knowledge cut. Exists only for audit
     * replay; {@link #pin(String)} uses the snapshot's own {@code
     * fixingKnowledgeCut} and is the form resolution code should use
     * (FS S6.4).
     */
    FxSnapshot pin(String marketSnapshotId, Instant knowledgeCut);

    PrewarmOutcome prewarm(PrewarmRequest request);

    RateResult rate(RateRequest request);

    ConversionResult convert(ConversionRequest request);

    SeriesResult convertSeries(SeriesRequest request);

    ChainResult convertChain(ChainRequest request);

    RevaluationResult revalue(MonetaryRevaluationRequest request);

    CorrectionImpact assessCorrectionImpact(Lineage previous, FxSnapshot current);

    /**
     * Index-aligned batch resolution. Never fails as a whole: each input
     * request yields exactly one {@link FxResult}, success or failure,
     * at the same index (A-10).
     */
    List<FxResult> batch(List<? extends FxRequest> requests);
}
