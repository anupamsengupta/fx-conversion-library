package com.power.fx.api.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The trade/delivery dates a {@code RawDateDeriver} may read from,
 * depending on the request's {@link DateRule} (FS S7).
 *
 * @see "Tech spec S4.7"
 */
public record TradeDates(
        LocalDate tradeDate,
        LocalDate specificDate,
        LocalDate paymentDate,
        LocalDate deliveryDate,
        LocalDate deliveryStart,
        LocalDate deliveryEnd,
        List<DeliveryDay> deliveryDays,
        ZoneId marketZone) {

    public TradeDates {
        deliveryDays = deliveryDays == null ? List.of() : List.copyOf(deliveryDays);
    }
}
