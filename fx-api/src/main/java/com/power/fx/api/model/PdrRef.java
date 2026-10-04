package com.power.fx.api.model;

import java.util.Objects;

/**
 * A reference to a PDR (pricing-day-reference) published event,
 * consumed by value (D-08, A-08).
 *
 * @see "Tech spec S4.6"
 */
public record PdrRef(String eventId, int version, String inputsHash) {

    public PdrRef {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(inputsHash, "inputsHash must not be null");
    }
}
