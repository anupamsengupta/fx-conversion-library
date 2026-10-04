package com.power.fx.api.model;

/**
 * Forward-pillar interpolation method. {@code LOG_LINEAR_CARRY} is the
 * default (D-15).
 *
 * @see "Tech spec S4.1"
 */
public enum InterpolationMethod {
    LOG_LINEAR_CARRY,
    LINEAR_POINTS,
    MONOTONE_CUBIC_POINTS
}
