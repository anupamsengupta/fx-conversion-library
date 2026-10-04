package com.power.fx.api.model;

/**
 * Whether a {@link FixingSource} or price may be used for invoicing or
 * for mark-to-market purposes only.
 *
 * @see "Tech spec S4.1"
 */
public enum UsageClass {
    INVOICING_ELIGIBLE,
    MTM_ONLY
}
