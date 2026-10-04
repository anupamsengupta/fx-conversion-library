package com.power.fx.api.model;

/**
 * One step of a policy's fallback chain. {@code maxSteps} applies to
 * {@code PREVIOUS_PUBLICATION_DAY} (default 3) and is ignored by other
 * kinds.
 *
 * @see "Tech spec S4.4"
 */
public record FallbackStep(FallbackStepKind kind, int maxSteps) {

    public FallbackStep {
        if (kind == null) {
            throw new NullPointerException("kind must not be null");
        }
        if (maxSteps < 0) {
            throw new IllegalArgumentException("maxSteps must not be negative");
        }
    }
}
