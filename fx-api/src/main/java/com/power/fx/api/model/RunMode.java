package com.power.fx.api.model;

/**
 * Whether a request is an official (signed-off, auditable) run or an
 * ad-hoc one. See A-09: {@code RequestValidator} raises
 * {@code FX_V_UNSIGNED_SNAPSHOT} when {@code OFFICIAL} is combined with an
 * unsigned snapshot under {@code Purpose.UNREALISED_MTM}.
 *
 * @see "Tech spec S4.1, A-09"
 */
public enum RunMode {
    OFFICIAL,
    AD_HOC
}
