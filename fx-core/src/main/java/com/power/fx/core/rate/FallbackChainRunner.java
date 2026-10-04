package com.power.fx.core.rate;

import com.power.fx.api.model.Purpose;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.snapshot.PinnedState;

import java.util.Optional;

/** Internal port (S5.4): stage 11, the fallback chain (FS S11.3). */
public interface FallbackChainRunner {

    Optional<RateQuote> run(RateLookupMiss miss, ResolvedPolicy policy, PinnedState state);

    /**
     * Purpose-aware overload (documented signature extension, same
     * reasoning as {@code RateSelector}): needed for the purpose-specific
     * forbidden-fallback-step check (S6.4/S11.3).
     */
    default Optional<RateQuote> run(RateLookupMiss miss, ResolvedPolicy policy, PinnedState state, Purpose purpose) {
        return run(miss, policy, state);
    }
}
