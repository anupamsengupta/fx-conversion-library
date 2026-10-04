package com.power.fx.core.validation;

import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.model.AccountingFxPolicy;
import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.SettlementCalendar;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.api.model.VersionEnvelope;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Stage-3 ingest validation (S8.2 step 3, S6.18's eight {@code FX_I_*}
 * codes). Stateless: one instance is safe to share across all tenants.
 *
 * <p><strong>Reachability note on {@code FX_I_APPROVAL_INVALID}:</strong>
 * {@link VersionEnvelope}'s own compact constructor already enforces all
 * three sub-conditions ({@code approvedBy} non-null, {@code approvedBy !=
 * authoredBy}, {@code approvedAt == recordedAt}) at construction time, so
 * {@link #checkApproval(VersionEnvelope)} is structurally unreachable for
 * any payload built through the normal Java constructor path -- the
 * offending object simply cannot exist. The check is retained here for
 * S6.18 parity (defence in depth) and in case a future payload type is
 * ever produced by a path that bypasses {@code VersionEnvelope}'s
 * constructor (e.g. a reflective deserialiser). This is a newly
 * discovered gap, not resolved by the tech spec or implementation plan;
 * see the Job 1 report for the full explanation and vector X07's status.
 *
 * @see "Tech spec S6.18, S8.2"
 */
public final class IngestValidator {

    /**
     * Full per-record validation for a REFERENCE-store entity type:
     * scope/tenant consistency between the ingest envelope and the
     * payload's own {@link VersionEnvelope}, the (structurally
     * unreachable, see class Javadoc) four-eyes re-check, and value
     * validity.
     */
    public Optional<FxIngestCode> validateReference(FxIngestRecord record) {
        VersionEnvelope env = envelopeOf(record.entityType(), record.payload());

        Optional<FxIngestCode> scopeViolation = checkScopeConsistency(record, env.scope(), env.tenantId());
        if (scopeViolation.isPresent()) {
            return scopeViolation;
        }
        Optional<FxIngestCode> approvalViolation = checkApproval(env);
        if (approvalViolation.isPresent()) {
            return approvalViolation;
        }
        return checkValue(record.entityType(), record.payload());
    }

    /** Full per-record validation for a FIXING-store entity (one {@link FixingVersion}). */
    public Optional<FxIngestCode> validateFixing(FxIngestRecord record) {
        FixingVersion fv = (FixingVersion) record.payload();

        Optional<FxIngestCode> scopeViolation = checkScopeConsistency(record, fv.scope(), fv.tenantId());
        if (scopeViolation.isPresent()) {
            return scopeViolation;
        }
        if (fv.value().signum() <= 0) {
            return Optional.of(FxIngestCode.FX_I_INVALID_VALUE);
        }
        return Optional.empty();
    }

    /**
     * {@code FX_I_SCOPE_VIOLATION}: the ingest record's own declared
     * {@code scope}/{@code tenantId} must agree with the payload's own
     * envelope-level scope/tenant. Nothing in {@link FxIngestRecord}'s
     * compact constructor ties these two together (unlike {@link
     * VersionEnvelope}), so a host or mapper that mismatches them is a
     * genuinely reachable, testable violation -- this is the "TENANT
     * record shadowing GLOBAL where not allowed" half of S6.18's
     * description, expressed as the only concrete, structurally-enforced
     * check this codebase's data model supports. The broader "allowed to
     * shadow" governance question (which entity types may legitimately be
     * TENANT-scoped overlays of a GLOBAL one) has no explicit per-type
     * flag anywhere in the reference-data model and is therefore not
     * independently checked here -- see the Job 1 report for this
     * flagged limitation.
     */
    private Optional<FxIngestCode> checkScopeConsistency(FxIngestRecord record, com.power.fx.api.model.Scope
            envScope, String envTenantId) {
        if (envScope != record.scope() || !Objects.equals(envTenantId, record.tenantId())) {
            return Optional.of(FxIngestCode.FX_I_SCOPE_VIOLATION);
        }
        return Optional.empty();
    }

    /** See the class Javadoc: structurally unreachable via normal construction, retained for parity. */
    private Optional<FxIngestCode> checkApproval(VersionEnvelope env) {
        if (env.approvedBy() == null
                || env.approvedBy().equals(env.authoredBy())
                || !env.approvedAt().equals(env.recordedAt())) {
            return Optional.of(FxIngestCode.FX_I_APPROVAL_INVALID);
        }
        return Optional.empty();
    }

    /**
     * {@code FX_I_INVALID_VALUE}: non-positive rate/factor, a negative
     * {@code decimals}, or an empty calendar coverage set. {@code
     * Currency.decimals() < 0} can never be observed in practice ({@link
     * Currency}'s own compact constructor already rejects it, the same
     * reachability caveat as {@link #checkApproval}); the other branches
     * (fixed factor, manual override, calendar coverage) have no such
     * compact-constructor guard and are genuinely reachable.
     */
    private Optional<FxIngestCode> checkValue(FxEntityType type, Object payload) {
        boolean invalid = switch (type) {
            case CURRENCY -> ((Currency) payload).decimals() < 0;
            case FIXED_FACTOR -> ((FixedFactor) payload).factor().signum() <= 0;
            case MANUAL_RATE_OVERRIDE -> ((ManualRateOverride) payload).rate().signum() <= 0;
            case PUBLICATION_CALENDAR -> ((PublicationCalendar) payload).publicationDates().isEmpty();
            case SETTLEMENT_CALENDAR -> ((SettlementCalendar) payload).businessDates().isEmpty();
            default -> false;
        };
        return invalid ? Optional.of(FxIngestCode.FX_I_INVALID_VALUE) : Optional.empty();
    }

    /**
     * {@code FX_I_OVERLAP}: within one ingest batch, two records for the
     * same {@code (entityType, naturalKey)} sharing the same {@code
     * recordedAt} with overlapping {@code [validFrom, validTo)} windows
     * are ambiguous -- the later one in batch order is flagged. Only
     * within-batch overlaps are detected (not against the pre-existing
     * catalogue generation); see the Job 1 report for this documented
     * scope limitation.
     */
    public Set<FxIngestRecord> findOverlaps(Map<String, List<FxIngestRecord>> byNaturalKey) {
        Set<FxIngestRecord> overlapping = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<FxIngestRecord> group : byNaturalKey.values()) {
            if (group.size() < 2) {
                continue;
            }
            List<VersionEnvelope> envelopes = new ArrayList<>(group.size());
            for (FxIngestRecord r : group) {
                envelopes.add(envelopeOf(r.entityType(), r.payload()));
            }
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    VersionEnvelope a = envelopes.get(i);
                    VersionEnvelope b = envelopes.get(j);
                    if (a.recordedAt().equals(b.recordedAt()) && overlapsValidity(a, b)) {
                        overlapping.add(group.get(j));
                    }
                }
            }
        }
        return overlapping;
    }

    private boolean overlapsValidity(VersionEnvelope a, VersionEnvelope b) {
        LocalDate aEnd = a.validTo();
        LocalDate bEnd = b.validTo();
        boolean aStartsBeforeBEnds = bEnd == null || a.validFrom().isBefore(bEnd);
        boolean bStartsBeforeAEnds = aEnd == null || b.validFrom().isBefore(aEnd);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    /** Extracts the embedded {@link VersionEnvelope} for any REFERENCE-store entity type. */
    public static VersionEnvelope envelopeOf(FxEntityType type, Object payload) {
        return switch (type) {
            case CURRENCY -> ((Currency) payload).envelope();
            case PAIR_CONVENTION -> ((PairConvention) payload).envelope();
            case FIXED_FACTOR -> ((FixedFactor) payload).envelope();
            case FIXING_SOURCE -> ((FixingSource) payload).envelope();
            case PUBLICATION_CALENDAR -> ((PublicationCalendar) payload).envelope();
            case SETTLEMENT_CALENDAR -> ((SettlementCalendar) payload).envelope();
            case ACCOUNTING_UNIT -> ((AccountingUnit) payload).envelope();
            case FX_POLICY -> ((FxPolicy) payload).envelope();
            case ACCOUNTING_FX_POLICY -> ((AccountingFxPolicy) payload).envelope();
            case SOURCE_ENTITLEMENT -> ((SourceEntitlement) payload).envelope();
            case MANUAL_RATE_OVERRIDE -> ((ManualRateOverride) payload).envelope();
            default -> throw new IllegalArgumentException("not a reference-data entity type: " + type);
        };
    }
}
