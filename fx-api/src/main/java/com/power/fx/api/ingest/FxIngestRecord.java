package com.power.fx.api.ingest;

import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.Scope;

import java.time.Instant;
import java.util.Objects;

/**
 * One ingest-ready record of any entity type, produced either directly by
 * a host or by {@code fx-cdm}'s mapper from a {@code CdmFxEvent}.
 *
 * @see "Tech spec S4.10"
 */
public record FxIngestRecord(
        String eventId,
        FxEntityType entityType,
        Scope scope,
        String tenantId,
        String naturalKey,
        long sequence,
        Object payload,
        Instant publishedAt) {

    public FxIngestRecord {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(naturalKey, "naturalKey must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }
}
