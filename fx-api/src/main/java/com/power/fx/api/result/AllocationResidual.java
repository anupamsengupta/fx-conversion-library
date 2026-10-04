package com.power.fx.api.result;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The reconciliation outcome of {@code LargestRemainderAllocator}: the
 * signed difference against independently converted line totals, line
 * count, currency scale and the tolerance it was measured against (FS
 * S15).
 *
 * @see "Tech spec S4.8"
 */
public record AllocationResidual(BigDecimal residual, int lineCount, int scale, BigDecimal tolerance) {

    public AllocationResidual {
        Objects.requireNonNull(residual, "residual must not be null");
        Objects.requireNonNull(tolerance, "tolerance must not be null");
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount must not be negative");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
    }
}
