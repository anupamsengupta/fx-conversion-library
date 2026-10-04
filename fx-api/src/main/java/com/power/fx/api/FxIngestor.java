package com.power.fx.api;

import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestOutcome;

import java.util.List;

/**
 * The ingestion use case (Pattern #17 Command/UseCase).
 *
 * @see "Tech spec S5.3"
 */
public interface FxIngestor {
    IngestOutcome apply(List<FxIngestRecord> records);
}
