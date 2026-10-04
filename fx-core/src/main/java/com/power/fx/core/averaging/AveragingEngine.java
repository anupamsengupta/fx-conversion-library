package com.power.fx.core.averaging;

import com.power.fx.api.model.AveragingSpec;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.rate.RateQuote;

import java.math.BigDecimal;
import java.util.List;

/**
 * Internal port (S5.4): stage 13, {@code RATE_AVERAGE}/{@code
 * PRICE_MATCHED} averaging over an already rate-resolved {@link
 * ObservationSet}.
 *
 * <p><strong>Signature extension (documented):</strong> S5.4's indicative
 * {@code average(ObservationSet, ResolvedPolicy, PinnedState)} omits the
 * per-observation resolved rates, which Appendix C's own pseudocode shows
 * being computed in a loop <em>before</em> the {@code averagingEngine
 * .average(obs, quotes, policy, state)} call -- i.e. the pseudocode itself
 * already implies a {@code quotes} parameter the S5.4 interface text does
 * not list. This implementation makes that parameter, plus the
 * PRICE_MATCHED {@code prices[]} and the plain-total {@code periodAmount}
 * and application-direction flag stage 14 needs, explicit.
 */
public interface AveragingEngine {

    SeriesOutcome average(ObservationSet set, List<RateQuote> quotes, List<BigDecimal> pricesOrNull,
            BigDecimal periodAmountOrNull, boolean invertAtApplication, AveragingSpec spec, FxMath fxMath);
}
