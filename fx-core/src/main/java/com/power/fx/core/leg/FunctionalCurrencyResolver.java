package com.power.fx.core.leg;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.AccountingUnit;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.core.FxErrors;
import com.power.fx.core.cache.ReferenceCatalogue;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Resolves the functional currency of an {@link AccountingUnit} valid on
 * the leg's own accounting date (recognition, closing, settlement or
 * valuation) -- prospective only, which falls out naturally from the
 * bitemporal {@code Timeline} lookup (a version is only returned if it is
 * valid as of the queried business date, IAS 21.35). Vector F08.
 *
 * @see "Tech spec S6.14"
 */
public final class FunctionalCurrencyResolver {

    public CurrencyCode resolve(String accountingUnitId, LocalDate asOfDate, ReferenceCatalogue tenant, Instant cut) {
        AccountingUnit unit = tenant.accountingUnit(accountingUnitId, asOfDate, cut)
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_FUNCTIONAL_CCY_NOT_FOUND,
                        "no accounting unit found for " + accountingUnitId + " at " + asOfDate,
                        "accountingUnitId", accountingUnitId));
        if (unit.functionalCurrency() == null) {
            throw FxErrors.of(FxErrorCode.FX_E_FUNCTIONAL_CCY_NOT_FOUND,
                    "accounting unit " + accountingUnitId + " has no functional currency at " + asOfDate,
                    "accountingUnitId", accountingUnitId);
        }
        return unit.functionalCurrency();
    }
}
