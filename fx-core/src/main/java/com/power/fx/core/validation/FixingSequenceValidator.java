package com.power.fx.core.validation;

import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.core.cache.FixingSeries;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Bitemporal sequencing guard applied to one incoming {@link
 * FixingVersion} against the entries already known (pre-ingest generation
 * plus any already-accepted candidates earlier in the same batch) for its
 * {@code (sourceCode, pair, cutoff, fixingDate)} key (S6.18 {@code
 * FX_I_FIXING_SEQUENCE}):
 * <ul>
 *   <li>a {@code CORRECTED} version requires a prior {@code OFFICIAL}
 *       version on the same date to already exist;</li>
 *   <li>{@code recordedAt} must never regress or repeat against the
 *       greatest {@code recordedAt} already on file for that date.</li>
 * </ul>
 * Stateless: callers supply the "known so far" entry list explicitly.
 *
 * @see "Tech spec S6.18, S8.2"
 */
public final class FixingSequenceValidator {

    public Optional<FxIngestCode> validate(FixingVersion candidate, List<FixingSeries.Entry> existingOnDate) {
        if (candidate.fixingStatus() == FixingStatus.CORRECTED) {
            boolean priorOfficialExists = existingOnDate.stream()
                    .anyMatch(e -> e.status() == FixingStatus.OFFICIAL || e.status() == FixingStatus.CORRECTED);
            if (!priorOfficialExists) {
                return Optional.of(FxIngestCode.FX_I_FIXING_SEQUENCE);
            }
        }

        Instant maxRecordedAt = existingOnDate.stream()
                .map(FixingSeries.Entry::recordedAt)
                .max(Instant::compareTo)
                .orElse(null);
        if (maxRecordedAt != null && !candidate.recordedAt().isAfter(maxRecordedAt)) {
            return Optional.of(FxIngestCode.FX_I_FIXING_SEQUENCE);
        }
        return Optional.empty();
    }
}
