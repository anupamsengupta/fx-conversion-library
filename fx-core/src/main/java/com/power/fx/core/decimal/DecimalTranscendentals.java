package com.power.fx.core.decimal;

import java.math.BigDecimal;

/**
 * Internal port (S5.4) for deterministic decimal {@code ln}/{@code exp}.
 * Not public API; bound to {@link FxMath} in {@code fx-guice}'s
 * {@code FxModule} (S9.1: {@code bind(DecimalTranscendentals.class)
 * .to(FxMath.class)}).
 *
 * @see "Tech spec S5.4, S6.10"
 */
public interface DecimalTranscendentals {

    BigDecimal ln(BigDecimal x);

    BigDecimal exp(BigDecimal x);
}
