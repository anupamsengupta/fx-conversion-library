package com.power.fx.core.rate;

import com.power.fx.api.model.Purpose;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.pair.PairRoute;
import com.power.fx.core.snapshot.PinnedState;

import java.time.LocalDate;

/**
 * Internal port (S5.4): stage 10 (plus, in this implementation, the
 * forward resolution of stage 12 -- see {@link DefaultRateSelector}'s
 * class Javadoc for why those are merged).
 *
 * <p><strong>Signature extension (documented):</strong> S5.4's indicative
 * {@code select(PairRoute, LocalDate, ResolvedPolicy, PinnedState)} omits
 * {@code valuationDate} and {@code purpose}, both of which the S6.8
 * decision table's rows R1/R4/R6 (date comparison) and the
 * purpose-specific forbidden-fallback-step check structurally require.
 * This implementation adds them explicitly.
 */
public interface RateSelector {

    RateQuote select(PairRoute route, LocalDate fxDate, LocalDate valuationDate, Purpose purpose,
            ResolvedPolicy policy, PinnedState state);
}
