package com.power.fx.core.testsupport;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementCalendar;
import com.power.fx.api.model.UsageClass;
import com.power.fx.api.model.VersionEnvelope;
import com.power.fx.api.model.VersionStatus;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Hand-rolled reference-data builders shared across {@code fx-core}'s own
 * unit tests, exercising the real vector scenarios (reference deal: the
 * G/C/F/X vectors from the functional spec, S19) without a dependency on
 * {@code fx-testkit} (which does not exist yet, Phase 3b). A throwaway,
 * test-scope-only counterpart to {@code fx-testkit}'s future {@code
 * GoldenReferenceData}.
 */
public final class TestFixtures {

    public static final Instant RECORDED_AT = Instant.parse("2020-01-01T00:00:00Z");
    public static final LocalDate VALID_FROM = LocalDate.of(2000, 1, 1);

    private TestFixtures() {
    }

    public static VersionEnvelope globalEnvelope(String naturalKey, String versionId) {
        return new VersionEnvelope(Scope.GLOBAL, null, naturalKey, versionId, VALID_FROM, null, RECORDED_AT,
                VersionStatus.APPROVED, "loader", "approver", RECORDED_AT, "TEST", null, null, "rel-1");
    }

    public static VersionEnvelope tenantEnvelope(String tenantId, String naturalKey, String versionId) {
        return new VersionEnvelope(Scope.TENANT, tenantId, naturalKey, versionId, VALID_FROM, null, RECORDED_AT,
                VersionStatus.APPROVED, "loader", "approver", RECORDED_AT, "TEST", null, null, "rel-1");
    }

    /** A publication calendar open on every weekday in {@code [start,end]} except {@code holidays}. */
    public static PublicationCalendar weekdayCalendar(String calendarRef, LocalDate start, LocalDate end,
            Set<LocalDate> holidays) {
        SortedSet<LocalDate> openDays = new TreeSet<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) {
                openDays.add(d);
            }
        }
        return new PublicationCalendar(globalEnvelope(calendarRef, calendarRef + "-v1"), calendarRef,
                ZoneId.of("Europe/Berlin"), new LocalDateRange(start, end), openDays);
    }

    public static SettlementCalendar weekdaySettlementCalendar(String calendarRef, LocalDate start, LocalDate end,
            Set<LocalDate> holidays) {
        SortedSet<LocalDate> openDays = new TreeSet<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) {
                openDays.add(d);
            }
        }
        return new SettlementCalendar(globalEnvelope(calendarRef, calendarRef + "-v1"), calendarRef,
                ZoneId.of("Europe/Berlin"), new LocalDateRange(start, end), openDays,
                Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
    }

    public static FixingSource fixingSource(String sourceCode, String publicationCalendarRef, CurrencyPair... pairs) {
        Set<CurrencyPair> pairSet = new HashSet<>(Set.of(pairs));
        return new FixingSource(globalEnvelope(sourceCode, sourceCode + "-v1"), sourceCode,
                LocalTime.of(16, 0), ZoneId.of("Europe/Berlin"), publicationCalendarRef, pairSet, null,
                UsageClass.INVOICING_ELIGIBLE);
    }

    public static CurrencyCode ccy(String code) {
        return new CurrencyCode(code);
    }

    public static CurrencyPair pair(String base, String quote) {
        return new CurrencyPair(ccy(base), ccy(quote));
    }
}
