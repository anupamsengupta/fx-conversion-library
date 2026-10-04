package com.power.fx.core.averaging;

import com.power.fx.api.model.AveragingMethod;
import com.power.fx.api.model.AveragingSpec;
import com.power.fx.api.model.Rational;
import com.power.fx.core.date.ResolvedDates;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link ObservationSet} the rest of the pipeline consumes.
 * {@code averagingMethod = NONE} (or no averaging spec at all) yields
 * exactly one observation, so single-rate and averaging share one code
 * path (D-07) -- there is no bypass branch anywhere in this pipeline.
 *
 * @see "Tech spec S6.11"
 */
public final class ObservationSetBuilder {

    public ObservationSet build(ResolvedDates dates, AveragingSpec averaging) {
        if (averaging == null || averaging.method() == AveragingMethod.NONE) {
            ResolvedDates.DateEntry e = dates.primary();
            return new ObservationSet(List.of(new Observation(0, e.observationDate(), e.rawDate(), e.resolvedDate(),
                    Rational.ONE, null, e.volume(), false)));
        }

        List<ResolvedDates.DateEntry> survivors = dates.entries().stream().filter(e -> !e.skipped()).toList();
        if (survivors.isEmpty()) {
            throw new IllegalStateException("every observation was skipped; nothing left to average");
        }
        List<Rational> rawWeights = WeightResolver.resolve(averaging.weighting(), survivors);
        Rational sum = Rational.ZERO;
        for (Rational w : rawWeights) {
            sum = sum.plus(w);
        }
        List<Observation> observations = new ArrayList<>();
        for (int i = 0; i < survivors.size(); i++) {
            ResolvedDates.DateEntry e = survivors.get(i);
            // SKIP_OBSERVATION renormalises weights exactly over survivors (S6.11/S7.1).
            Rational w = sum.num().signum() == 0 ? Rational.ZERO : rawWeights.get(i).dividedBy(sum);
            int seq = e.pdrSequence() != null ? e.pdrSequence() : i;
            observations.add(new Observation(seq, e.observationDate(), e.rawDate(), e.resolvedDate(), w, null,
                    e.volume(), false));
        }
        return new ObservationSet(observations);
    }
}
