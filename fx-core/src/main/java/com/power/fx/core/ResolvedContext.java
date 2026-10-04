package com.power.fx.core;

import com.power.fx.api.request.FxRequestContext;
import com.power.fx.core.snapshot.PinnedState;

import java.util.Objects;

/**
 * Carries everything {@code LineageBuilder} needs to build one leg's
 * {@code Lineage}: the pinned state, the resolved policy and the original
 * request context (S6.13).
 */
public record ResolvedContext(PinnedState state, ResolvedPolicy policy, FxRequestContext requestContext) {

    public ResolvedContext {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(requestContext, "requestContext must not be null");
    }
}
