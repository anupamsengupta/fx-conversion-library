package com.power.fx.api.result;

import com.power.fx.api.error.FxError;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of {@code assessCorrectionImpact(Lineage previous,
 * FxSnapshot current)}: a recompute-and-diff against the original
 * lineage (D-06, FS S14.1).
 *
 * @see "Tech spec S4.8"
 */
public record CorrectionImpact(
        boolean replayable,
        boolean materiallyChanged,
        BigDecimal previousAmount,
        BigDecimal newAmount,
        BigDecimal difference,
        BigDecimal previousRate,
        BigDecimal newRate,
        List<FixingVersionChange> changedInputs,
        Optional<FxResult> recomputed,
        Lineage previousLineage,
        Optional<Lineage> newLineage,
        Optional<FxError> error) {

    public CorrectionImpact {
        Objects.requireNonNull(previousLineage, "previousLineage must not be null");
        changedInputs = changedInputs == null ? List.of() : List.copyOf(changedInputs);
        recomputed = recomputed == null ? Optional.empty() : recomputed;
        newLineage = newLineage == null ? Optional.empty() : newLineage;
        error = error == null ? Optional.empty() : error;
    }
}
