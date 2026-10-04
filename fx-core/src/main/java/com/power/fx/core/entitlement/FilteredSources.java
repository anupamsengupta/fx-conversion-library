package com.power.fx.core.entitlement;

import com.power.fx.api.error.FxWarning;

import java.util.List;

/**
 * Stage 8 output (D-10, S6.6): the entitled subset of {@code
 * rateSourcePriority}, in priority order, plus the skip warnings for
 * dropped sources. Only this list is threaded past stage 8 into stage 9
 * (see {@code PairResolver}'s class Javadoc for how the pipeline resolves
 * the S6.6/S6.7 seam so that stage 9 never sees an unentitled source).
 */
public record FilteredSources(List<String> allowed, List<FxWarning> warnings) {

    public FilteredSources {
        allowed = List.copyOf(allowed);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
