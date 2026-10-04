package com.power.fx.cdm;

import com.power.fx.api.model.FxEntityType;

import java.util.Map;
import java.util.Objects;

/**
 * <strong>Placeholder CDM event envelope, pending TI-01.</strong>
 *
 * <p>TI-01 (the CDM schema artifact Maven GAV, event/topic naming, and
 * payload field names for all thirteen {@link FxEntityType} values) is
 * unresolved -- see the implementation plan, Phase 3a Task 3a.1, tech
 * spec S4.11/Appendix A ("{@code fx-cdm} depends on CDM schema artifact,
 * compile, TI-01"), and Appendix A-02. There is no real schema to depend
 * on, so this type is a self-contained, in-module stand-in: it is
 * <strong>not</strong> derived from any external artifact.
 *
 * <p><strong>Every field name and type below is illustrative scaffolding,
 * not a derived schema fact.</strong> In particular:
 * <ul>
 *   <li>{@code fields} is a flat {@code Map<String, String>} precisely
 *       because the real payload shape (nested objects, numeric types,
 *       per-entity schemas) is unknown. A real CDM payload will almost
 *       certainly be richer (e.g. {@code Map<String, Object>}, as tech
 *       spec S4.11's own illustrative sketch shows) and per-entity typed
 *       rather than a single generic envelope.</li>
 *   <li>{@code publishedAt} is a {@code String} (expected ISO-8601
 *       instant) rather than {@code java.time.Instant} deliberately: the
 *       real wire encoding (epoch millis? ISO-8601? something else?) is
 *       unknown, and parsing a string is a pure, deterministic operation
 *       (no system clock is read anywhere in this module, consistent with
 *       D-01).</li>
 *   <li>{@code entityType} is typed as the real {@link FxEntityType} enum
 *       (not a raw string, unlike tech spec S4.11's sketch) because that
 *       enum already exists in {@code fx-api} and is not itself blocked
 *       by TI-01 -- reusing it lets the dispatcher in
 *       {@link CdmFxEventMapper} be exhaustive and type-safe now.</li>
 * </ul>
 *
 * <p>When TI-01 is answered, this record is expected to be replaced
 * wholesale (not incrementally refined) by whatever shape the real CDM
 * schema artifact defines, and {@link CdmFxEventMapper} and its
 * per-entity mappers re-pointed at it.
 *
 * @param eventId     transport-level event identifier; carried through
 *                    verbatim to {@code FxIngestRecord.eventId} /
 *                    {@code IngestRejection.eventId}.
 * @param entityType  discriminates which per-entity mapper applies.
 * @param scope       {@code "GLOBAL"} or {@code "TENANT"}, matching
 *                    {@link com.power.fx.api.model.Scope}'s constant
 *                    names (parsed, never guessed, by the mappers).
 * @param tenantId    required iff {@code scope} is {@code "TENANT"}.
 * @param naturalKey  the entity's natural key.
 * @param sequence    monotonic per-key sequence number, per
 *                    {@code FxIngestRecord.sequence}.
 * @param publishedAt illustrative ISO-8601 instant string; see class
 *                    Javadoc.
 * @param fields      the flat, illustrative payload bag; never
 *                    {@code null} (defaults to empty).
 * @see "Implementation plan Phase 3a Task 3a.1; tech spec S4.11, S6.17; TI-01"
 */
public record CdmFxEvent(
        String eventId,
        FxEntityType entityType,
        String scope,
        String tenantId,
        String naturalKey,
        long sequence,
        String publishedAt,
        Map<String, String> fields) {

    public CdmFxEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(naturalKey, "naturalKey must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
