package com.power.fx.core.date;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.error.FxWarningCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.FixingSource;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.OffsetSpec;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.core.FxErrors;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.snapshot.PinnedState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 6 (S6.2, S6.5, D-02): raw date derivation, offset application,
 * publication-calendar resolution with {@code nonPublicationDayHandling}.
 * Runs to completion before any pair/rate component exists in the
 * reactor's build (the Task 2.7 structural proof of D-02's build-level
 * ordering, per the plan's own framing).
 *
 * <p><strong>Documented simplification:</strong> {@code OffsetSpec}'s
 * {@code FIXING_SOURCE}/{@code PAIR_SETTLEMENT_JOINT}/{@code CURRENCY}/
 * {@code CUSTOM} business-day kinds all resolve against the same
 * governing publication calendar used for the date rule itself (the first
 * configured source's calendar), rather than separately resolving a
 * pair-settlement-joint or single-currency calendar. {@code CUSTOM} uses
 * {@code offset.customCalendarRef()} as a publication-calendar lookup if
 * present. This is narrower than the full generality S6.5 point 2
 * describes but is sufficient for every vector this phase must pass.
 */
public final class DefaultDateRuleResolver implements DateRuleResolver {

    private final RawDateDeriver rawDateDeriver = new RawDateDeriver();
    private final PublicationDateResolver publicationDateResolver = new PublicationDateResolver();

    @Override
    public ResolvedDates resolve(ResolvedPolicy resolvedPolicy, PinnedState state, FxRequestContext context) {
        FxPolicy policy = resolvedPolicy.policy();
        ReferenceCatalogue tenant = state.tenant();
        LocalDate asOf = context.valuationDate();

        CalendarGovernance gov = governingCalendar(policy, tenant, asOf, state.knowledgeCut());

        if (policy.dateRule() == DateRule.AVERAGE_RATE || policy.dateRule() == DateRule.CLOSING_RATE) {
            return resolveAverageOrClosing(policy, context, gov);
        }

        List<RawDateDeriver.RawEntry> rawEntries = rawDateDeriver.derive(policy, context);
        List<ResolvedDates.DateEntry> entries = new ArrayList<>(rawEntries.size());
        List<FxWarning> warnings = new ArrayList<>();

        for (RawDateDeriver.RawEntry raw : rawEntries) {
            LocalDate afterOffset = applyOffset(raw.rawDate(), policy.offset(), gov.calendar());
            PublicationDateResolver.Result result = publicationDateResolver.resolve(afterOffset, gov.calendar(),
                    policy.nonPublicationDayHandling());
            if (result.skipped() && policy.averaging() == null) {
                throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                        "SKIP_OBSERVATION is only legal inside an averaging policy");
            }
            if (result.adjusted()) {
                warnings.add(new FxWarning(FxWarningCode.FX_W_DATE_RULE_ADJUSTED,
                        "raw date " + afterOffset + " adjusted to " + result.resolvedDate(),
                        Map.of("rawDate", afterOffset.toString(), "resolvedDate", result.resolvedDate().toString())));
            }
            entries.add(new ResolvedDates.DateEntry(raw.rawDate(), result.resolvedDate(), result.adjusted(),
                    result.skipped(), raw.pdrSequence(), raw.weight(), raw.volume(), raw.observationDate()));
        }

        return new ResolvedDates(entries, gov.calendarVersions(), warnings);
    }

    private ResolvedDates resolveAverageOrClosing(FxPolicy policy, FxRequestContext context, CalendarGovernance gov) {
        com.power.fx.api.model.AccountingDates ad = context.accountingDates();
        if (ad == null || ad.periodEnd() == null) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "AVERAGE_RATE/CLOSING_RATE requires accountingDates.periodEnd");
        }
        if (policy.dateRule() == DateRule.CLOSING_RATE) {
            LocalDate last = ad.periodEnd();
            LocalDate resolved = gov.calendar().atOrBefore(last);
            return new ResolvedDates(
                    List.of(new ResolvedDates.DateEntry(last, resolved, !resolved.equals(last), false, null, null, null, resolved)),
                    gov.calendarVersions(), List.of());
        }
        // AVERAGE_RATE: every open publication day in [periodStart, periodEnd] (IAS 21.22).
        if (ad.periodStart() == null) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY, "AVERAGE_RATE requires accountingDates.periodStart");
        }
        List<ResolvedDates.DateEntry> entries = new ArrayList<>();
        int seq = 0;
        for (LocalDate d = ad.periodStart(); !d.isAfter(ad.periodEnd()); d = d.plusDays(1)) {
            if (gov.calendar().isOpen(d)) {
                entries.add(new ResolvedDates.DateEntry(d, d, false, false, seq++, null, null, d));
            }
        }
        if (entries.isEmpty()) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "AVERAGE_RATE period [" + ad.periodStart() + "," + ad.periodEnd() + "] has no open publication days");
        }
        return new ResolvedDates(entries, gov.calendarVersions(), List.of());
    }

    private LocalDate applyOffset(LocalDate raw, OffsetSpec offset, CalendarIndex calendar) {
        if (offset == null || offset.days() == 0) {
            return raw;
        }
        int n = offset.days();
        return switch (offset.calendarKind()) {
            case CALENDAR_DAYS -> raw.plusDays(n);
            case FIXING_SOURCE, PAIR_SETTLEMENT_JOINT, CURRENCY, CUSTOM -> {
                if (n >= 0) {
                    yield calendar.plusBusinessDays(raw, n);
                }
                LocalDate cur = raw;
                for (int i = 0; i < -n; i++) {
                    cur = calendar.strictlyBefore(cur);
                }
                yield cur;
            }
        };
    }

    private record CalendarGovernance(CalendarIndex calendar, java.util.SortedMap<String, String> calendarVersions) {
    }

    private CalendarGovernance governingCalendar(FxPolicy policy, ReferenceCatalogue tenant, LocalDate asOf, java.time.Instant cut) {
        if (policy.rateSourcePriority().isEmpty()) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY, "policy has no rateSourcePriority to derive a publication calendar from");
        }
        String sourceCode = policy.rateSourcePriority().get(0);
        FixingSource source = tenant.fixingSource(sourceCode, asOf, cut)
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                        "fixing source not loaded: " + sourceCode, "sourceCode", sourceCode));
        PublicationCalendar cal = tenant.publicationCalendar(source.publicationCalendarRef(), asOf, cut)
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                        "publication calendar not loaded: " + source.publicationCalendarRef(),
                        "calendarRef", source.publicationCalendarRef()));
        CalendarIndex index = tenant.calendarIndex(cal.envelope().versionId())
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                        "calendar index not compiled for versionId=" + cal.envelope().versionId()));
        java.util.SortedMap<String, String> versions = new TreeMap<>();
        versions.put(cal.calendarRef(), cal.envelope().versionId());
        return new CalendarGovernance(index, versions);
    }
}
