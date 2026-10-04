package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One physical delivery day and its volume, used for
 * {@code DateRule.DELIVERY_DAYS} and gas-day mapping.
 *
 * @see "Tech spec S4.7"
 */
public record DeliveryDay(LocalDate day, BigDecimal volume) {

    public DeliveryDay {
        Objects.requireNonNull(day, "day must not be null");
        Objects.requireNonNull(volume, "volume must not be null");
    }
}
