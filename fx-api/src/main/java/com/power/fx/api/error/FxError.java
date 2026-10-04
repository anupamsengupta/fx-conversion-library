package com.power.fx.api.error;

import java.util.Map;
import java.util.Objects;

/**
 * An error returned in the {@code error} slot of an {@code FxResult}.
 *
 * @see "Tech spec S4.2"
 */
public record FxError(FxErrorCode code, String message, Map<String, String> details) {

    public FxError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
