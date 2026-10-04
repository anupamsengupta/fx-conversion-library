package com.power.fx.core.date;

import com.power.fx.api.request.FxRequestContext;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.snapshot.PinnedState;

/**
 * Internal port (S5.4): stage 6 of the pipeline, strictly before stages
 * 9-12 (D-02).
 */
public interface DateRuleResolver {

    ResolvedDates resolve(ResolvedPolicy policy, PinnedState state, FxRequestContext context);
}
