package com.power.fx.core.leg;

import com.power.fx.api.request.ChainRequest;
import com.power.fx.api.result.ChainResult;
import com.power.fx.core.snapshot.PinnedState;

/** Internal port: {@code convertChain()} (FS S9, D-04, D-16). */
public interface ChainEngine {

    ChainResult chain(ChainRequest request, PinnedState state);
}
