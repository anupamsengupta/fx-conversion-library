package com.power.fx.core.pair;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.snapshot.PinnedState;

import java.time.LocalDate;

/**
 * Internal port (S5.4): stage 9, the nine-step ordered pair-resolution
 * chain (FS S11.2, S6.7).
 *
 * <p><strong>Design-seam resolution (plan Section 7, new gap #5 / Task
 * 2.8's seam note):</strong> this signature matches S5.4 exactly and does
 * <em>not</em> take a {@code FilteredSources} parameter, even though S6.6
 * point 4 and S6.7 step 6 require that only entitled sources ever reach
 * pair resolution. This implementation resolves that seam by option (a):
 * the caller ({@code ConversionPipeline}) narrows {@code
 * ResolvedPolicy.policy().rateSourcePriority()} to the entitlement-filtered
 * list <strong>before</strong> calling {@link #route}, so by the time this
 * method runs, {@code policy.policy().rateSourcePriority()} already *is*
 * the entitled list. {@code DirectQuoteStep}'s equivalent logic in {@link
 * DefaultPairResolver} never calls {@code EntitlementResolver} itself
 * (option (b) was not chosen). This is recorded here, not left implicit,
 * because {@code fx-testkit}'s future stage-level tests bind to this seam.
 *
 * @see "Tech spec S5.4, S6.6, S6.7"
 */
public interface PairResolver {

    PairRoute route(CurrencyCode from, CurrencyCode to, ResolvedPolicy policy, PinnedState state, LocalDate fxDate);
}
