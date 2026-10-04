package com.power.fx.core.cache;

import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FixingVersionSelection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One immutable, bitemporal fixing series: all versions of all fixing
 * dates for one {@code (sourceCode, pair, cutoff)} key, resolvable by
 * {@code (fixingDate, knowledgeCut, FixingVersionSelection)} (S7.1.3,
 * Appendix C's {@code selectFixing}).
 *
 * <p><strong>Documented simplification:</strong> the tech spec sketches a
 * compressed-sparse-row layout of parallel primitive arrays
 * ({@code dayOffset[]}, {@code versionFrom[]}, {@code values[]}, ...).
 * This implementation uses a {@link NavigableMap}{@code <LocalDate,
 * List<Entry>>} instead: {@code O(log n)} date lookup is preserved (via
 * {@link TreeMap}'s red-black tree), the per-date version list is small
 * (S10a.3's own estimate is ~1.3 versions/fixing), and the resolution
 * *algorithm* (binary-search-equivalent date lookup, {@code recordedAt}
 * upper-bound visibility filter, the exact FS S6.1 selection rule over the
 * visible prefix) is implemented exactly per Appendix C, trading the raw
 * array memory-density optimisation for materially simpler, more
 * obviously correct code within this task's time budget.
 *
 * @see "Tech spec S7.1.3, Appendix C"
 */
public final class FixingSeries {

    public record Entry(String versionId, Instant recordedAt, FixingStatus status, BigDecimal value,
            String correctionOf, LocalDate fixingDate, LocalDate valueDate) {
    }

    /** The resolved entry, plus whether it was found only via the preliminary fallback. */
    public record Resolution(Entry entry, boolean viaPreliminaryFallback) {
    }

    private final NavigableMap<LocalDate, List<Entry>> byDate;

    private FixingSeries(NavigableMap<LocalDate, List<Entry>> byDate) {
        this.byDate = byDate;
    }

    public static FixingSeries empty() {
        return new FixingSeries(new TreeMap<>());
    }

    public static FixingSeries of(List<FixingVersion> versions) {
        NavigableMap<LocalDate, List<Entry>> map = new TreeMap<>();
        for (FixingVersion v : versions) {
            Entry e = new Entry(v.versionId(), v.recordedAt(), v.fixingStatus(), v.value(), v.correctionOf(),
                    v.fixingDate(), v.valueDate());
            map.computeIfAbsent(v.fixingDate(), k -> new ArrayList<>()).add(e);
        }
        map.values().forEach(list -> list.sort(Comparator.comparing(Entry::recordedAt)));
        return new FixingSeries(map);
    }

    /** A copy of this series with {@code additions} merged in (used by incremental ingest). */
    public FixingSeries withAdded(List<FixingVersion> additions) {
        NavigableMap<LocalDate, List<Entry>> copy = new TreeMap<>();
        byDate.forEach((d, list) -> copy.put(d, new ArrayList<>(list)));
        for (FixingVersion v : additions) {
            Entry e = new Entry(v.versionId(), v.recordedAt(), v.fixingStatus(), v.value(), v.correctionOf(),
                    v.fixingDate(), v.valueDate());
            copy.computeIfAbsent(v.fixingDate(), k -> new ArrayList<>()).add(e);
        }
        copy.values().forEach(list -> list.sort(Comparator.comparing(Entry::recordedAt)));
        return new FixingSeries(copy);
    }

    /** A copy of this series retaining only entries with {@code fixingDate} inside {@code window}. */
    public FixingSeries prunedTo(com.power.fx.api.model.LocalDateRange window) {
        NavigableMap<LocalDate, List<Entry>> pruned = new TreeMap<>();
        byDate.forEach((d, list) -> {
            if (!d.isBefore(window.startInclusive()) && !d.isAfter(window.endInclusive())) {
                pruned.put(d, list);
            }
        });
        return new FixingSeries(pruned);
    }

    public boolean hasDate(LocalDate fixingDate) {
        return byDate.containsKey(fixingDate);
    }

    public List<Entry> entriesOn(LocalDate fixingDate) {
        List<Entry> list = byDate.get(fixingDate);
        return list == null ? List.of() : List.copyOf(list);
    }

    public Map<LocalDate, List<Entry>> asMap() {
        return byDate;
    }

    /**
     * Resolves one fixing per the FS S6.1 selection rule (Appendix C
     * {@code selectFixing}). Empty if the date has no entries at all (a
     * MISS -- stage 11/fallback-eligible only if the calendar reports the
     * day open) or if no entry is visible at {@code knowledgeCut}.
     */
    public Optional<Resolution> resolve(LocalDate fixingDate, Instant knowledgeCut, FixingVersionSelection selection) {
        Objects.requireNonNull(fixingDate, "fixingDate must not be null");
        Objects.requireNonNull(knowledgeCut, "knowledgeCut must not be null");
        Objects.requireNonNull(selection, "selection must not be null");

        List<Entry> all = byDate.get(fixingDate);
        if (all == null || all.isEmpty()) {
            return Optional.empty();
        }
        List<Entry> visible = all.stream().filter(e -> !e.recordedAt().isAfter(knowledgeCut)).toList();
        if (visible.isEmpty()) {
            return Optional.empty();
        }

        Entry chosen = switch (selection) {
            case FixingVersionSelection.FirstOfficial ignored ->
                    firstWhere(visible, e -> e.status() == FixingStatus.OFFICIAL);
            case FixingVersionSelection.LatestCorrected ignored ->
                    lastWhere(visible, e -> e.status() == FixingStatus.OFFICIAL || e.status() == FixingStatus.CORRECTED);
            case FixingVersionSelection.AsOfKnowledge asOf -> {
                Instant effectiveCut = asOf.asOf().isBefore(knowledgeCut) ? asOf.asOf() : knowledgeCut;
                yield lastWhere(visible, e -> (e.status() == FixingStatus.OFFICIAL || e.status() == FixingStatus.CORRECTED)
                        && !e.recordedAt().isAfter(effectiveCut));
            }
        };

        if (chosen != null) {
            return Optional.of(new Resolution(chosen, false));
        }

        Entry preliminary = lastWhere(visible, e -> e.status() == FixingStatus.PRELIMINARY);
        if (preliminary != null) {
            return Optional.of(new Resolution(preliminary, true));
        }
        return Optional.empty();
    }

    private static Entry firstWhere(List<Entry> visible, java.util.function.Predicate<Entry> pred) {
        for (Entry e : visible) {
            if (pred.test(e)) {
                return e;
            }
        }
        return null;
    }

    private static Entry lastWhere(List<Entry> visible, java.util.function.Predicate<Entry> pred) {
        Entry result = null;
        for (Entry e : visible) {
            if (pred.test(e)) {
                result = e;
            }
        }
        return result;
    }
}
