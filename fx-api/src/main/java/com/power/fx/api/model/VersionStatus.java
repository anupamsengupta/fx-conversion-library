package com.power.fx.api.model;

/**
 * Bitemporal version status carried by {@link VersionEnvelope}. A
 * {@code RETIRED} version is treated as absent by {@code Timeline}
 * resolution (S7.1.1).
 *
 * @see "Tech spec S4.1"
 */
public enum VersionStatus {
    APPROVED,
    RETIRED
}
