package com.power.fx.core.snapshot;

import com.power.fx.core.cache.FixingView;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.memo.ResolutionMemo;

import java.time.Instant;
import java.util.Objects;

/**
 * The single immutable carrier threaded through every resolution stage
 * (S5.4, S7.1.5). A pin captures five references in one allocation --
 * {@code global}, {@code tenant}, {@code fixings}, {@code snapshot},
 * {@code knowledgeCut} -- plus the two generations and the memo, with
 * <strong>no</strong> copying and <strong>no</strong> re-read of any store
 * afterward (A-15). No stage reaches back to a store; this is what makes
 * the resolution path pure and the pin meaningful.
 *
 * @see "Tech spec S5.4, S7.1.5, A-15"
 */
public record PinnedState(
        String tenantId,
        ReferenceCatalogue global,
        ReferenceCatalogue tenant,
        FixingView fixings,
        MarketSnapshot snapshot,
        Instant knowledgeCut,
        long refGeneration,
        long fixingGeneration,
        ResolutionMemo memo) {

    public PinnedState {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(fixings, "fixings must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(knowledgeCut, "knowledgeCut must not be null");
        Objects.requireNonNull(memo, "memo must not be null");
    }
}
