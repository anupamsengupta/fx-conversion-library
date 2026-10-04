package com.power.fx.core.precision;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.RoundingSpec;
import com.power.fx.core.FxErrors;
import com.power.fx.core.snapshot.PinnedState;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stage 15 (FS S15): {@code toAmountUnrounded} is always the full DECIMAL128
 * value; {@code toAmountBooked} is rounded exactly once, to the target
 * currency's decimals ({@code HALF_UP} default, {@code HALF_EVEN}
 * configurable per policy).
 */
public final class DefaultPrecisionEngine implements PrecisionEngine {

    @Override
    public BookedAmount book(BigDecimal unrounded, CurrencyCode ccy, RoundingSpec spec, PinnedState state) {
        Currency currency = state.tenant().currency(ccy, state.snapshot().asOfDate(), state.knowledgeCut())
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                        "currency not loaded: " + ccy.value(), "currency", ccy.value()));
        int scale = currency.decimals();
        RoundingMode mode = (spec != null && spec.amountRounding() != null) ? spec.amountRounding() : RoundingMode.HALF_UP;
        BigDecimal booked = unrounded.setScale(scale, mode);
        return new BookedAmount(unrounded, booked, scale);
    }
}
