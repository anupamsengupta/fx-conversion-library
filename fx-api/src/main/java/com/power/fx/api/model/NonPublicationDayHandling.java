package com.power.fx.api.model;

/**
 * How a {@code PublicationDateResolver} handles a raw FX date that falls
 * on a non-publication day (FS S7, D-02).
 *
 * @see "Tech spec S4.1"
 */
public enum NonPublicationDayHandling {
    USE_PREVIOUS,
    USE_NEXT,
    SKIP_OBSERVATION,
    FAIL
}
