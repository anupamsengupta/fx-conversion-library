package com.power.fx.api.model;

/**
 * The business reason a conversion is requested. Drives {@code PolicyMatrix}
 * resolution in {@code fx-core} (S6.4) and is a required field of
 * {@code FxRequestContext} (D-14).
 *
 * @see "Tech spec S4.1"
 */
public enum Purpose {
    CONTRACT_SETTLEMENT,
    UNREALISED_MTM,
    CASH_PROJECTION,
    ACCOUNTING_RECOGNITION,
    ACCOUNTING_REVALUATION,
    ACCOUNTING_SETTLEMENT,
    TRANSLATION,
    MANAGEMENT_VIEW
}
