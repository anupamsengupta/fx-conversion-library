package com.power.fx.core.lineage;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.result.Lineage;
import com.power.fx.core.ResolvedContext;

import java.math.BigDecimal;

/** Internal port (S5.4): stage 17, lineage construction (S6.13). */
public interface LineageBuilder {

    Lineage build(ResolvedContext ctx, CurrencyCode fromCcy, CurrencyCode toCcy, BigDecimal fromAmount, boolean isUnitPrice);
}
