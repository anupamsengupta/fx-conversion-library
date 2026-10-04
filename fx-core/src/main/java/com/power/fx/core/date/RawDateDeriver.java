package com.power.fx.core.date;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.AccountingDates;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.DeliveryDay;
import com.power.fx.api.model.EstimatedEventHandling;
import com.power.fx.api.model.EventDate;
import com.power.fx.api.model.FxDateFromObservation;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.PricingObservation;
import com.power.fx.api.model.Rational;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.core.FxErrors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps {@code DateRule} + request context to raw date(s) (FS S7.1, tech
 * spec S6.5 point 1). Missing required inputs raise {@code
 * FX_V_INVALID_POLICY} except the {@code EVENT} rule, which raises {@code
 * FX_E_MISSING_EVENT_DATE}.
 *
 * <p><strong>Judgment call (not fully specified by either spec):</strong>
 * the {@code EVENT} rule does not say which {@link
 * com.power.fx.api.model.EventType} to use when several are present on the
 * request; this implementation selects the single event with the lowest
 * (best) {@code sourceRank} across all entries in {@code context.events()}
 * -- "highest-ranked" per S6.5 point 1 -- rather than requiring the caller
 * or policy to name a specific event type.
 *
 * @see "Tech spec S6.5"
 */
public final class RawDateDeriver {

    public record RawEntry(LocalDate rawDate, Integer pdrSequence, Rational weight, BigDecimal volume,
            LocalDate observationDate) {
    }

    public List<RawEntry> derive(FxPolicy policy, FxRequestContext context) {
        DateRule rule = policy.dateRule();
        TradeDates td = context.tradeDates();
        AccountingDates ad = context.accountingDates();

        return switch (rule) {
            case TRADE_DATE -> single(require(td == null ? null : td.tradeDate(), "tradeDates.tradeDate"));
            case SPECIFIC_DATE -> single(require(td == null ? null : td.specificDate(), "tradeDates.specificDate"));
            case PAYMENT_DATE -> single(require(td == null ? null : td.paymentDate(), "tradeDates.paymentDate"));
            case DELIVERY_DATE -> single(require(td == null ? null : td.deliveryDate(), "tradeDates.deliveryDate"));
            case DELIVERY_DAYS -> deliveryDays(td);
            case PRICING_SET -> pricingSet(policy, context);
            case EVENT -> event(policy, context);
            case RECOGNITION_DATE -> single(require(ad == null ? null : ad.recognitionDate(), "accountingDates.recognitionDate"));
            case SETTLEMENT_DATE -> single(require(ad == null ? null : ad.settlementDate(), "accountingDates.settlementDate"));
            case FAIR_VALUE_DATE -> single(require(ad == null ? null : ad.fairValueDate(), "accountingDates.fairValueDate"));
            case HISTORICAL_RATE -> single(require(ad == null ? null : ad.historicalDate(), "accountingDates.historicalDate"));
            case VALUATION_DATE -> single(context.valuationDate());
            case AVERAGE_RATE, CLOSING_RATE ->
                    throw new IllegalStateException(rule + " is resolved by PublicationDateResolver against a "
                            + "publication calendar, not by RawDateDeriver alone");
        };
    }

    private static LocalDate require(LocalDate date, String fieldName) {
        if (date == null) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY, "missing required date input: " + fieldName,
                    "field", fieldName);
        }
        return date;
    }

    private static List<RawEntry> single(LocalDate date) {
        return List.of(new RawEntry(date, null, null, null, date));
    }

    private static List<RawEntry> deliveryDays(TradeDates td) {
        if (td == null || td.deliveryDays() == null || td.deliveryDays().isEmpty()) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY, "missing required date input: tradeDates.deliveryDays");
        }
        List<RawEntry> result = new ArrayList<>();
        int seq = 0;
        for (DeliveryDay d : td.deliveryDays()) {
            // 6.5.1: power delivery day is already a local LocalDate; gas-day start-date mapping
            // (06:00 D to 06:00 D+1 -> D) would require a per-day commodity/time marker this
            // request shape does not carry, so this implementation treats every DeliveryDay.day()
            // as already the correct local FX date -- a documented simplification for both
            // commodities, since the library has no field distinguishing them at this layer.
            result.add(new RawEntry(d.day(), seq++, null, d.volume(), d.day()));
        }
        return result;
    }

    private static List<RawEntry> pricingSet(FxPolicy policy, FxRequestContext context) {
        if (context.pricingSet() == null || context.pricingSet().observations().isEmpty()) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY, "missing required date input: pricingSet");
        }
        FxDateFromObservation mode = policy.averaging() == null
                ? FxDateFromObservation.SAME_DATE
                : policy.averaging().fxDateFromObservation();
        int offsetDays = policy.averaging() == null ? 0 : policy.averaging().fxDateOffset();
        List<RawEntry> result = new ArrayList<>();
        for (PricingObservation obs : context.pricingSet().observations()) {
            LocalDate raw = mode == FxDateFromObservation.OFFSET
                    ? obs.observationDate().plusDays(offsetDays)
                    : obs.observationDate();
            result.add(new RawEntry(raw, obs.sequence(), obs.weight(), obs.volume(), obs.observationDate()));
        }
        return result;
    }

    private static List<RawEntry> event(FxPolicy policy, FxRequestContext context) {
        Map<com.power.fx.api.model.EventType, EventDate> events = context.events();
        if (events == null || events.isEmpty()) {
            throw FxErrors.of(FxErrorCode.FX_E_MISSING_EVENT_DATE, "no event dates supplied on the request");
        }
        EventDate best = null;
        for (EventDate e : events.values()) {
            if (best == null || e.sourceRank() < best.sourceRank()) {
                best = e;
            }
        }
        if (best.estimated() && policy.estimatedEventHandling() == EstimatedEventHandling.FAIL) {
            throw FxErrors.of(FxErrorCode.FX_E_MISSING_EVENT_DATE,
                    "best-ranked event date is estimated and policy.estimatedEventHandling = FAIL");
        }
        return single(best.date());
    }
}
