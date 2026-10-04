package com.power.fx.api.ingest;

import com.power.fx.api.error.FxIngestCode;

import java.util.Objects;

/**
 * One rejected ingest record.
 *
 * @see "Tech spec S4.10"
 */
public record IngestRejection(String eventId, String versionId, FxIngestCode code, String message) {

    public IngestRejection {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
