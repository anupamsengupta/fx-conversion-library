package com.power.fx.cdm;

import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.IngestRejection;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.Scope;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Dispatches a {@link CdmFxEvent} to {@link CdmReferenceMapper} (eleven
 * reference entities), {@link CdmFixingMapper} or {@link CdmSnapshotMapper}
 * on {@code entityType}, producing a {@link FxIngestRecord}.
 *
 * <p><strong>Scaffolded and blocked on TI-01</strong> (implementation
 * plan Phase 3a Task 3a.4) -- see {@link CdmFxEvent}'s Javadoc for what
 * is and is not real here. The dispatch logic and the
 * never-throws-on-malformed-input contract below are real and tested now;
 * the field names each per-entity mapper reads are illustrative.
 *
 * <p>Malformed or missing payload fields (including an unparsable
 * top-level {@code scope} or {@code publishedAt}) never escape as an
 * exception: every {@link RuntimeException} raised while mapping is
 * caught here and converted into an {@link IngestRejection} using
 * {@link FxIngestCode#FX_I_INVALID_VALUE} -- the closest existing code
 * for "malformed value" in the S6.18 ingest-code table; {@code fx-cdm}
 * does not mint a new ingest code of its own.
 *
 * <p>Pure function; no transport, no I/O, no scheduler, no dependency on
 * {@code fx-core} (Appendix A, MC-5).
 *
 * @see "Implementation plan Phase 3a Task 3a.4; tech spec S6.17; TI-01"
 */
public final class CdmFxEventMapper {

    private CdmFxEventMapper() {
    }

    public static CdmMappingResult map(CdmFxEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            Object payload = mapPayload(event);
            Scope scope = CdmFields.parseEnum(event.scope(), Scope.class, "scope");
            Instant publishedAt;
            try {
                publishedAt = Instant.parse(event.publishedAt());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("malformed publishedAt: " + event.publishedAt(), e);
            }
            FxIngestRecord record = new FxIngestRecord(
                    event.eventId(),
                    event.entityType(),
                    scope,
                    event.tenantId(),
                    event.naturalKey(),
                    event.sequence(),
                    payload,
                    publishedAt);
            return new CdmMappingResult.Mapped(record);
        } catch (RuntimeException e) {
            String versionId = event.fields().get("versionId");
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new CdmMappingResult.Rejected(
                    new IngestRejection(event.eventId(), versionId, FxIngestCode.FX_I_INVALID_VALUE, message));
        }
    }

    private static Object mapPayload(CdmFxEvent event) {
        FxEntityType type = event.entityType();
        return switch (type) {
            case CURRENCY, PAIR_CONVENTION, FIXED_FACTOR, FIXING_SOURCE,
                 PUBLICATION_CALENDAR, SETTLEMENT_CALENDAR, ACCOUNTING_UNIT,
                 FX_POLICY, ACCOUNTING_FX_POLICY, SOURCE_ENTITLEMENT,
                 MANUAL_RATE_OVERRIDE -> CdmReferenceMapper.map(event);
            case FIXING -> CdmFixingMapper.map(event);
            case MARKET_SNAPSHOT -> CdmSnapshotMapper.map(event);
        };
    }
}
