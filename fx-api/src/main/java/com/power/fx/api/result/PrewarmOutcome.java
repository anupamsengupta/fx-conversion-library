package com.power.fx.api.result;

import com.power.fx.api.ingest.IngestRejection;

import java.util.List;

/**
 * The outcome of {@code FxConverter.prewarm(PrewarmRequest)}.
 *
 * @see "Tech spec S4.8"
 */
public record PrewarmOutcome(
        int snapshotsLoaded,
        int fixingSeriesLoaded,
        int fixingVersionsLoaded,
        List<String> notFound,
        List<IngestRejection> rejections) {

    public PrewarmOutcome {
        notFound = notFound == null ? List.of() : List.copyOf(notFound);
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
    }
}
