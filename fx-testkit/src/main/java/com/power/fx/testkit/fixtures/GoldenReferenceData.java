package com.power.fx.testkit.fixtures;

import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FixedFactorKind;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SettlementCalendar;
import com.power.fx.api.model.SourceEntitlement;
import com.power.fx.api.model.SourceRight;
import com.power.fx.api.model.UsageClass;
import com.power.fx.api.model.VersionEnvelope;
import com.power.fx.api.model.VersionStatus;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Golden reference data for the G01-G09 / C01-C05 / F01-F09 / X01-X11
 * vectors (functional spec S19), built directly from the tech spec's own
 * vector inputs. No platform reference-deal convention (such as
 * {@code T-7788}/{@code TN_0042}) is used -- this repository has no such
 * convention and the FX tech spec defines its own vector fixtures (plan
 * Task 3b.3).
 *
 * <p>Literal tenant ids appear throughout this class by design; {@code
 * fx-testkit} is explicitly AR-10-exempt (S12.5), unlike {@code fx-api}/
 * {@code fx-core} main sources, which never hardcode one.
 *
 * @see "Functional spec S19; tech spec S6.7, S6.9, S6.11, S6.14, S6.8"
 */
public final class GoldenReferenceData {

    public static final String TENANT = "TENANT_GOLDEN";
    public static final String TENANT_NO_WMR = "TENANT_GOLDEN_NO_WMR";
    public static final Instant RECORDED_AT = Instant.parse("2020-01-01T00:00:00Z");
    public static final LocalDate VALID_FROM = LocalDate.of(2000, 1, 1);
    public static final LocalDate CALENDAR_START = LocalDate.of(2026, 1, 1);
    public static final LocalDate CALENDAR_END = LocalDate.of(2026, 12, 31);
    public static final LocalDate GOOD_FRIDAY_2026 = LocalDate.of(2026, 4, 3);
    public static final LocalDate EASTER_MONDAY_2026 = LocalDate.of(2026, 4, 6);

    private GoldenReferenceData() {
    }

    // --- envelopes ---

    public static VersionEnvelope globalEnvelope(String naturalKey, String versionId) {
        return new VersionEnvelope(Scope.GLOBAL, null, naturalKey, versionId, VALID_FROM, null, RECORDED_AT,
                VersionStatus.APPROVED, "loader", "approver", RECORDED_AT, "GOLDEN", null, null, "rel-1");
    }

    public static VersionEnvelope tenantEnvelope(String tenantId, String naturalKey, String versionId) {
        return new VersionEnvelope(Scope.TENANT, tenantId, naturalKey, versionId, VALID_FROM, null, RECORDED_AT,
                VersionStatus.APPROVED, "loader", "approver", RECORDED_AT, "GOLDEN", null, null, "rel-1");
    }

    // --- currencies and pairs ---

    public static CurrencyCode ccy(String code) {
        return new CurrencyCode(code);
    }

    public static CurrencyPair pair(String base, String quote) {
        return new CurrencyPair(ccy(base), ccy(quote));
    }

    public static Currency currency(String code) {
        return currency(code, null, 2);
    }

    public static Currency currency(String code, String majorCurrency, int decimals) {
        return new Currency(globalEnvelope(code, code + "-v1"), new CurrencyCode(code), decimals,
                majorCurrency == null ? null : new CurrencyCode(majorCurrency), "SETTLE-CAL", true);
    }

    /** Every currency touched by G01-G09/C01-C05/F01-F09/X01-X11. */
    public static List<Currency> allCurrencies() {
        return List.of(
                currency("EUR"), currency("USD"), currency("JPY"), currency("GBP"),
                currency("GBp", "GBP", 2), currency("AUD"), currency("INR"), currency("BGN"), currency("HRK"));
    }

    public static PairConvention convention(String base, String quote) {
        CurrencyPair p = pair(base, quote);
        return new PairConvention(globalEnvelope(p.canonical(), p.canonical() + "-v1"), p, 4,
                new BigDecimal("10000"), 2, List.of(), null, ForwardMethod.POINTS, InterpolationMethod.LOG_LINEAR_CARRY,
                new BigDecimal("2"), Map.of());
    }

    public static List<PairConvention> allConventions() {
        return List.of(
                convention("EUR", "USD"), convention("USD", "JPY"), convention("GBP", "USD"),
                convention("AUD", "USD"), convention("GBP", "USD"), convention("USD", "INR"),
                convention("EUR", "GBP"));
    }

    public static FixedFactor fixedFactor(String from, String to, String factor, FixedFactorKind kind,
            boolean preferOverMarket) {
        return new FixedFactor(globalEnvelope(from + ">" + to, from + ">" + to + "-v1"), new CurrencyCode(from),
                new CurrencyCode(to), new BigDecimal(factor), kind, preferOverMarket);
    }

    /** G03's GBp->GBP minor-unit factor and G09's BGN->EUR legal peg. */
    public static List<FixedFactor> allFixedFactors() {
        return List.of(
                fixedFactor("GBp", "GBP", "0.01", FixedFactorKind.MINOR_UNIT, false),
                fixedFactor("BGN", "EUR", "1.95583", FixedFactorKind.LEGAL_PEG, true));
    }

    // --- calendars ---

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

    /** The ECB publication calendar used throughout G/C/F/X, with Good Friday + Easter Monday 2026 excluded (C01-C03). */
    public static PublicationCalendar ecbCalendar() {
        return weekdayCalendar("ECB-CAL", CALENDAR_START, CALENDAR_END, Set.of(GOOD_FRIDAY_2026, EASTER_MONDAY_2026));
    }

    /** A variant with no holidays, for vectors not exercising C01-C03's Easter scenario. */
    public static PublicationCalendar ecbCalendarNoHolidays() {
        return weekdayCalendar("ECB-CAL", CALENDAR_START, CALENDAR_END, Set.of());
    }

    // --- sources and entitlements ---

    public static FixingSource fixingSource(String sourceCode, String publicationCalendarRef) {
        return fixingSource(sourceCode, publicationCalendarRef, UsageClass.INVOICING_ELIGIBLE);
    }

    public static FixingSource fixingSource(String sourceCode, String publicationCalendarRef, UsageClass usageClass) {
        return new FixingSource(globalEnvelope(sourceCode, sourceCode + "-v1"), sourceCode, LocalTime.of(16, 0),
                ZoneId.of("Europe/Berlin"), publicationCalendarRef, new HashSet<>(), null, usageClass);
    }

    public static SourceEntitlement entitlement(String tenantId, String sourceCode) {
        return new SourceEntitlement(tenantEnvelope(tenantId, sourceCode, sourceCode + "-v1"), tenantId, sourceCode,
                Set.of(SourceRight.VALUATION, SourceRight.DISPLAY));
    }

    // --- accounting units ---

    public static AccountingUnit accountingUnit(String unitId, String functionalCcy, LocalDate validFrom, LocalDate validTo) {
        VersionEnvelope env = new VersionEnvelope(Scope.GLOBAL, null, unitId, unitId + "-" + functionalCcy, validFrom,
                validTo, RECORDED_AT, VersionStatus.APPROVED, "loader", "approver", RECORDED_AT, "GOLDEN", null, null,
                "rel-1");
        return new AccountingUnit(env, unitId, "LE-1", new CurrencyCode(functionalCcy), List.of(), null, null);
    }
}
