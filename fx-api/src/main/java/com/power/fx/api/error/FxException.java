package com.power.fx.api.error;

import java.util.Objects;

/**
 * The runtime exception thrown by {@code FxResult.orThrow()} when a
 * result carries an error. Business failures are otherwise always
 * returned as results, never thrown (FS S14.1).
 *
 * @see "Tech spec S4.2"
 */
public final class FxException extends RuntimeException {

    private final FxError error;

    public FxException(FxError error) {
        super(Objects.requireNonNull(error, "error must not be null").message());
        this.error = error;
    }

    public FxError error() {
        return error;
    }
}
