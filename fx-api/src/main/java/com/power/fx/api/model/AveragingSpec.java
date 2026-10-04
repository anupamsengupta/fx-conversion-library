package com.power.fx.api.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The unified averaging specification attached to an {@link FxPolicy}
 * (D-07): method x observation set x weighting x output shape.
 *
 * @see "Tech spec S4.4"
 */
public record AveragingSpec(
        AveragingMethod method,
        ObservationSetKind observationSet,
        WindowSpec window,
        Weighting weighting,
        OutputShape outputShape,
        FxDateFromObservation fxDateFromObservation,
        int fxDateOffset,
        boolean averageInverted,
        List<LocalDate> explicitDates) {

    public AveragingSpec {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(observationSet, "observationSet must not be null");
        Objects.requireNonNull(weighting, "weighting must not be null");
        Objects.requireNonNull(outputShape, "outputShape must not be null");
        Objects.requireNonNull(fxDateFromObservation, "fxDateFromObservation must not be null");
        explicitDates = explicitDates == null ? List.of() : List.copyOf(explicitDates);
    }
}
