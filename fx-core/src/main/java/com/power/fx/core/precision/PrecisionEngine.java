package com.power.fx.core.precision;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.RoundingSpec;
import com.power.fx.core.snapshot.PinnedState;

import java.math.BigDecimal;

/** Internal port (S5.4): stage 15 (FS S15). */
public interface PrecisionEngine {

    BookedAmount book(BigDecimal unrounded, CurrencyCode ccy, RoundingSpec spec, PinnedState state);
}
