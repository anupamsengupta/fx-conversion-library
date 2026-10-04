package com.power.fx.api.model;

/**
 * Whether an estimated event date may be used for date resolution or
 * must cause a failure.
 *
 * @see "Tech spec S4.1"
 */
public enum EstimatedEventHandling {
    USE_ESTIMATE,
    FAIL
}
