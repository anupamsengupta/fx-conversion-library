package com.power.fx.core.date;

import com.power.fx.api.error.FxWarning;
import com.power.fx.api.model.Rational;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Collections;

/**
 * Stage 6 output (S6.5 point 4): one {@link DateEntry} per raw/resolved
 * date pair (one for a single-rate rule; one per PDR observation / delivery
 * day / average-period publication day for multi-date rules), plus the
 * {@code calendarRef -> versionId} map that flows into {@code
 * Lineage.calendarVersions}.
 */
public record ResolvedDates(List<DateEntry> entries, SortedMap<String, String> calendarVersions,
        List<FxWarning> warnings) {

    public ResolvedDates {
        Objects.requireNonNull(entries, "entries must not be null");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        entries = List.copyOf(entries);
        calendarVersions = Collections.unmodifiableSortedMap(
                calendarVersions == null ? new TreeMap<>() : new TreeMap<>(calendarVersions));
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public DateEntry primary() {
        return entries.get(0);
    }

    public record DateEntry(
            LocalDate rawDate,
            LocalDate resolvedDate,
            boolean dateRuleAdjusted,
            boolean skipped,
            Integer pdrSequence,
            Rational weight,
            BigDecimal volume,
            LocalDate observationDate) {
    }
}
