package com.power.fx.api.request;

import com.power.fx.api.FxSnapshot;
import com.power.fx.api.model.AccountingDates;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.EventDate;
import com.power.fx.api.model.EventType;
import com.power.fx.api.model.ItemType;
import com.power.fx.api.model.PricingDaySet;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.TradeDates;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The context shared by every {@link FxRequest}. Conditional
 * cross-field requirements (e.g. {@code accountingUnitId} required for
 * ACCT_TXN / TRANSLATION / MGMT_VIEW) are enforced by {@code
 * RequestValidator} in {@code fx-core} (S6.4), not here -- this type is a
 * plain carrier.
 *
 * <p>Snapshot precedence (OQ-T05): when a request is issued through a
 * snapshot facade and {@link #snapshot()} is also non-null, the request
 * field wins silently unless the two denote different tenants, in which
 * case {@code FX_E_TENANT_MISMATCH} is raised. No new code is minted for
 * snapshot disagreement.
 *
 * @see "Tech spec S4.7"
 */
public record FxRequestContext(
        Purpose purpose,
        LocalDate valuationDate,
        FxSnapshot snapshot,
        RunMode runMode,
        String accountingUnitId,
        AmountType amountType,
        SettlementAmountState settlementAmountState,
        ItemType itemType,
        TradeDates tradeDates,
        Map<EventType, EventDate> events,
        AccountingDates accountingDates,
        PricingDaySet pricingSet,
        List<ObservationPrice> prices,
        PolicyRef policy,
        String requestId) {

    public FxRequestContext {
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(valuationDate, "valuationDate must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        events = events == null ? Map.of() : Map.copyOf(events);
        prices = prices == null ? List.of() : List.copyOf(prices);
    }
}
