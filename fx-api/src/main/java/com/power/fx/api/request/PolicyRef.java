package com.power.fx.api.request;

import com.power.fx.api.model.FxPolicy;

import java.util.Objects;

/**
 * A reference to the policy governing a request/leg: either a lookup by
 * id against the reference catalogue, or a trade-terms policy embedded
 * inline (FS S5/S8.1). {@code Inline} is embedded verbatim into
 * {@code Lineage.replayKey} so a trade-terms policy replays without a
 * reference lookup (OQ-T04).
 *
 * @see "Tech spec S4.7"
 */
public sealed interface PolicyRef permits PolicyRef.ById, PolicyRef.Inline {

    record ById(String policyId) implements PolicyRef {
        public ById {
            Objects.requireNonNull(policyId, "policyId must not be null");
        }
    }

    record Inline(FxPolicy policy) implements PolicyRef {
        public Inline {
            Objects.requireNonNull(policy, "policy must not be null");
        }
    }
}
