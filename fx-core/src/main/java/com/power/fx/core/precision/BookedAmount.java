package com.power.fx.core.precision;

import java.math.BigDecimal;

/** The result of {@link PrecisionEngine#book} (FS S15): full-precision plus booked (rounded once) amount. */
public record BookedAmount(BigDecimal unrounded, BigDecimal booked, int scale) {
}
