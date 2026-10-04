package com.power.fx.api.model;

/**
 * Which raw FX date a {@code RawDateDeriver} derives from the request
 * context before any offset, calendar or roll-convention adjustment is
 * applied (FS S7, D-02: dates are resolved strictly before rate lookup).
 *
 * @see "Tech spec S4.1"
 */
public enum DateRule {
    TRADE_DATE,
    SPECIFIC_DATE,
    PRICING_SET,
    PAYMENT_DATE,
    DELIVERY_DATE,
    DELIVERY_DAYS,
    EVENT,
    RECOGNITION_DATE,
    AVERAGE_RATE,
    CLOSING_RATE,
    SETTLEMENT_DATE,
    VALUATION_DATE,
    FAIR_VALUE_DATE,
    HISTORICAL_RATE
}
