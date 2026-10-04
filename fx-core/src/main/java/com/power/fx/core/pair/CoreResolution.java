package com.power.fx.core.pair;

import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.RateType;

import java.math.BigDecimal;
import java.util.List;

/**
 * The "core" of a {@link PairRoute}, between the pre- and post-
 * {@code FixedFactorNormaliser} steps: either the identity (step 1/3),
 * a directly-resolved value (steps 4-5: contract rate / manual override),
 * or a market chain of one or two legs (steps 6-9: direct, inverse,
 * configured cross, major cross).
 *
 * @see "Tech spec S6.7"
 */
public sealed interface CoreResolution permits CoreResolution.Identity, CoreResolution.DirectlyResolved,
        CoreResolution.MarketChain {

    record Identity() implements CoreResolution {
    }

    record DirectlyResolved(BigDecimal value, RateType rateType, FxReason reason, String sourceCode)
            implements CoreResolution {
    }

    record MarketChain(List<MarketLeg> legs) implements CoreResolution {
        public MarketChain {
            legs = List.copyOf(legs);
            if (legs.isEmpty() || legs.size() > 2) {
                throw new IllegalArgumentException("a market chain has 1 or 2 legs (at most one intermediate currency): " + legs);
            }
        }
    }
}
