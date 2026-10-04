package com.power.fx.api.model;

/**
 * Classifies a {@link FixedFactor}: a minor-unit relationship (e.g.
 * GBp/GBP) or a legal peg (e.g. HRK/BGN before their respective
 * discontinuation dates). D-12: the literal {@code FIXED} never appears
 * as an enum constant anywhere in this library; this type's name
 * describes the relationship, not a rate-type constant.
 *
 * @see "Tech spec S4.1, D-12"
 */
public enum FixedFactorKind {
    MINOR_UNIT,
    LEGAL_PEG
}
