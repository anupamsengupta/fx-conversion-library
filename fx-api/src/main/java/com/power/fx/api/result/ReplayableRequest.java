package com.power.fx.api.result;

import com.power.fx.api.model.AccountingDates;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.EventDate;
import com.power.fx.api.model.EventType;
import com.power.fx.api.model.ItemType;
import com.power.fx.api.model.PricingDaySet;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.TradeDates;
import com.power.fx.api.request.ObservationPrice;
import com.power.fx.api.request.PolicyRef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A self-sufficient projection of a request, embedded in {@link Lineage}
 * so that {@code assessCorrectionImpact(Lineage, FxSnapshot)} can recompute
 * without a reference lookup, even for inline policies or PDR-linked
 * series (FS S14.1/S14.3 does not list such a field; this type is this
 * tech spec's design answer -- see S6.16 and OQ-T04). {@code
 * PolicyRef.Inline} is embedded verbatim.
 *
 * @see "Tech spec S4.9, OQ-T04"
 */
public record ReplayableRequest(
        Purpose purpose,
        RunMode runMode,
        LocalDate valuationDate,
        AmountType amountType,
        SettlementAmountState settlementAmountState,
        ItemType itemType,
        String accountingUnitId,
        CurrencyCode fromCcy,
        CurrencyCode toCcy,
        BigDecimal fromAmount,
        boolean isUnitPrice,
        PolicyRef policy,
        TradeDates tradeDates,
        Map<EventType, EventDate> events,
        AccountingDates accountingDates,
        PricingDaySet pricingSet,
        List<ObservationPrice> prices) {

    public ReplayableRequest {
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(valuationDate, "valuationDate must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        events = events == null ? Map.of() : Map.copyOf(events);
        prices = prices == null ? List.of() : List.copyOf(prices);
    }
}
