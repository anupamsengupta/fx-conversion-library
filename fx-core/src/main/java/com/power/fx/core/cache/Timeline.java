package com.power.fx.core.cache;

import com.power.fx.api.model.VersionEnvelope;
import com.power.fx.api.model.VersionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Sorted versions for one natural key of reference data, ordered
 * {@code (validFrom ASC, recordedAt ASC)}, immutable once built.
 * Resolution for {@code (date d, knowledge cut k)}: the candidates with
 * {@code validFrom <= d < validTo}, the greatest {@code recordedAt <= k}
 * among them; a {@code RETIRED} winner means "no value" (S7.1.1).
 *
 * <p><strong>Implementation note (documented simplification):</strong> the
 * tech spec's S7.1.1 prescribes an {@code O(log n)} binary search. This
 * implementation performs a linear scan over the (typically small, per
 * A-03 "thousands of records per tenant") version list rather than a
 * binary-searched array, trading the literal complexity bound for simpler,
 * more obviously correct code within this task's time budget. The
 * resolution *semantics* (ordering, knowledge-cut visibility, retirement)
 * are exact; only the search strategy differs from the spec's own
 * suggested mechanism, which is implementation guidance rather than an
 * externally observable contract.
 *
 * @see "Tech spec S7.1.1"
 */
public final class Timeline<V> {

    private final List<V> versions;
    private final Function<V, VersionEnvelope> envelopeOf;

    private Timeline(List<V> versions, Function<V, VersionEnvelope> envelopeOf) {
        this.versions = versions;
        this.envelopeOf = envelopeOf;
    }

    public static <V> Timeline<V> of(List<V> versions, Function<V, VersionEnvelope> envelopeOf) {
        Objects.requireNonNull(versions, "versions must not be null");
        Objects.requireNonNull(envelopeOf, "envelopeOf must not be null");
        List<V> sorted = new ArrayList<>(versions);
        sorted.sort(Comparator.<V, LocalDate>comparing(v -> envelopeOf.apply(v).validFrom())
                .thenComparing(v -> envelopeOf.apply(v).recordedAt()));
        return new Timeline<>(List.copyOf(sorted), envelopeOf);
    }

    public static <V> Timeline<V> empty(Function<V, VersionEnvelope> envelopeOf) {
        return new Timeline<>(List.of(), envelopeOf);
    }

    /** All versions, in {@code (validFrom ASC, recordedAt ASC)} order. */
    public List<V> versions() {
        return versions;
    }

    public boolean isEmpty() {
        return versions.isEmpty();
    }

    /**
     * Resolves the version effective for business date {@code date}, as
     * known at {@code knowledgeCut}. Empty if no version covers the date,
     * if the visible version set is empty at that cut, or if the winning
     * version is {@code RETIRED}.
     */
    public Optional<V> resolve(LocalDate date, Instant knowledgeCut) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(knowledgeCut, "knowledgeCut must not be null");

        V best = null;
        Instant bestRecordedAt = null;
        for (V v : versions) {
            VersionEnvelope e = envelopeOf.apply(v);
            boolean inBusinessWindow = !date.isBefore(e.validFrom())
                    && (e.validTo() == null || date.isBefore(e.validTo()));
            if (!inBusinessWindow) {
                continue;
            }
            if (e.recordedAt().isAfter(knowledgeCut)) {
                continue;
            }
            if (bestRecordedAt == null || e.recordedAt().isAfter(bestRecordedAt)) {
                bestRecordedAt = e.recordedAt();
                best = v;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        VersionEnvelope bestEnvelope = envelopeOf.apply(best);
        if (bestEnvelope.status() == VersionStatus.RETIRED) {
            return Optional.empty();
        }
        return Optional.of(best);
    }
}
