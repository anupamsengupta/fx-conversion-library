package com.power.fx.core.curve;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.PairConvention;
import com.power.fx.core.FxErrors;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.snapshot.PinnedState;

/**
 * {@code MarketSnapshot.curve(pair, builder)}-backed {@link
 * ForwardCurveCache}: lazy, idempotent, {@code computeIfAbsent}
 * construction, so concurrent builders can at worst duplicate work, never
 * diverge (S6.9.3, S7.1.4).
 */
public final class SnapshotForwardCurveCache implements ForwardCurveCache {

    private final FxMath fxMath;
    private final int forwardMemoMaxEntriesPerCurve;

    public SnapshotForwardCurveCache(FxMath fxMath, int forwardMemoMaxEntriesPerCurve) {
        this.fxMath = fxMath;
        this.forwardMemoMaxEntriesPerCurve = forwardMemoMaxEntriesPerCurve;
    }

    @Override
    public ForwardCurve curve(CurrencyPair pair, PinnedState state) {
        return state.snapshot().curve(pair, p -> {
            PairConvention convention = state.tenant().pairConvention(p, state.snapshot().asOfDate(), state.knowledgeCut())
                    .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                            "no pair convention loaded for " + p, "pair", p.canonical()));
            return new ForwardCurveBuilder(fxMath).build(p, convention, state.snapshot(), forwardMemoMaxEntriesPerCurve);
        });
    }
}
