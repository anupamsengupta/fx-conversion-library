package com.power.fx.api.model;

/**
 * A physical or commercial event type that may carry its own date
 * evidence in {@code FxRequestContext.events}.
 *
 * @see "Tech spec S4.1"
 */
public enum EventType {
    BL,
    NOR,
    COD,
    TITLE_TRANSFER,
    INVOICE
}
