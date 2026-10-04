package com.power.fx.api.ingest;

import com.power.fx.api.model.FxStoreKind;

import java.util.List;
import java.util.Map;

/**
 * The outcome of {@code FxIngestor.apply(List)}. {@code newGenerations}
 * maps each store kind to its new generation, with {@code -1} meaning
 * "unchanged" -- the sentinel is a {@code DefaultFxIngestor} concern
 * (Phase 2), not an API-level constraint enforced here.
 *
 * @see "Tech spec S4.10"
 */
public record IngestOutcome(
        int applied,
        int rejected,
        int duplicates,
        Map<FxStoreKind, Long> newGenerations,
        List<IngestRejection> rejections,
        List<String> staleKeys,
        List<String> snapshotsNowResolvable) {

    public IngestOutcome {
        newGenerations = newGenerations == null ? Map.of() : Map.copyOf(newGenerations);
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
        staleKeys = staleKeys == null ? List.of() : List.copyOf(staleKeys);
        snapshotsNowResolvable = snapshotsNowResolvable == null
                ? List.of()
                : List.copyOf(snapshotsNowResolvable);
    }
}
