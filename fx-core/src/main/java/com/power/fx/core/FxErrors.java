package com.power.fx.core;

import com.power.fx.api.error.FxError;
import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxException;

import java.util.Map;

/**
 * Small helper for constructing {@link FxException}s with a code, message
 * and structured details, used throughout {@code fx-core} wherever a stage
 * raises a resolution error (S6.18's code-to-component map).
 */
public final class FxErrors {

    private FxErrors() {
    }

    public static FxException of(FxErrorCode code, String message) {
        return new FxException(new FxError(code, message, Map.of()));
    }

    public static FxException of(FxErrorCode code, String message, Map<String, String> details) {
        return new FxException(new FxError(code, message, details));
    }

    public static FxException of(FxErrorCode code, String message, String detailKey, String detailValue) {
        return new FxException(new FxError(code, message, Map.of(detailKey, detailValue)));
    }
}
