package com.power.fx.core.leg;

import com.power.fx.api.request.MonetaryRevaluationRequest;
import com.power.fx.api.result.RevaluationResult;
import com.power.fx.core.snapshot.PinnedState;

/** Internal port: {@code revalue()} (FS S9.3, D-04). */
public interface RevaluationEngine {

    RevaluationResult revalue(MonetaryRevaluationRequest request, PinnedState state);
}
