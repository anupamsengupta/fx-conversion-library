package com.power.fx.api.error;

import java.util.Map;
import java.util.Objects;

/**
 * A non-fatal warning attached to an otherwise successful {@code FxResult}.
 *
 * @see "Tech spec S4.2"
 */
public record FxWarning(FxWarningCode code, String message, Map<String, String> details) {

    public FxWarning {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
