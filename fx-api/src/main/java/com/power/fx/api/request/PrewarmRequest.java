package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.LocalDateRange;

import java.time.Instant;
import java.util.Set;

/**
 * An explicit prewarm instruction, off the resolution path. {@code
 * prewarm} is the only {@code FxConverter} method permitted to perform
 * I/O (S5.1).
 *
 * @see "Tech spec S4.7"
 */
public record PrewarmRequest(
        Set<String> marketSnapshotIds,
        Set<String> sourceCodes,
        Set<CurrencyPair> pairs,
        LocalDateRange dateRange,
        Instant knowledgeCut) {

    public PrewarmRequest {
        marketSnapshotIds = marketSnapshotIds == null ? Set.of() : Set.copyOf(marketSnapshotIds);
        sourceCodes = sourceCodes == null ? Set.of() : Set.copyOf(sourceCodes);
        pairs = pairs == null ? Set.of() : Set.copyOf(pairs);
    }
}
