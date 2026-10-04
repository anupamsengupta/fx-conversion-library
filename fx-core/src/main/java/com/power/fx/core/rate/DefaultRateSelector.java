package com.power.fx.core.rate;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FutureDateTreatment;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.core.FxErrors;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.cache.FixingSeries;
import com.power.fx.core.curve.ForwardCurve;
import com.power.fx.core.curve.ForwardCurveCache;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.pair.CoreResolution;
import com.power.fx.core.pair.MarketLeg;
import com.power.fx.core.pair.PairRoute;
import com.power.fx.core.snapshot.PinnedState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FS S11.1's rate-selection decision table (S6.8).
 *
 * <p><strong>Documented architectural simplification:</strong> the tech
 * spec stages forward-curve resolution as a separate pipeline stage 12,
 * run only after stages 10-11 produce a quote still "needing forward".
 * This implementation resolves forward rates <em>inline</em>, inside
 * {@link #select}, because {@link PinnedState} already carries the
 * snapshot (and therefore the {@link ForwardCurveCache}) at rate-selection
 * time, and a two-leg cross where either leg needs a forward cannot be
 * cleanly expressed as "return a pending quote, resolve forward later,
 * then cross" without a materially larger composite-quote type. The
 * observable behaviour (R5/R6 rows, correct finality and reasons, G04/G05
 * forward values) is identical; only the pipeline's internal staging
 * differs from the spec's own stage numbering.
 *
 * @see "Tech spec S6.8"
 */
public final class DefaultRateSelector implements RateSelector {

    private final FxMath fxMath;
    private final ForwardCurveCache forwardCurveCache;
    private final FallbackChainRunner fallbackChainRunner;

    public DefaultRateSelector(FxMath fxMath, ForwardCurveCache forwardCurveCache, FallbackChainRunner fallbackChainRunner) {
        this.fxMath = fxMath;
        this.forwardCurveCache = forwardCurveCache;
        this.fallbackChainRunner = fallbackChainRunner;
    }

    @Override
    public RateQuote select(PairRoute route, LocalDate fxDate, LocalDate valuationDate, Purpose purpose,
            ResolvedPolicy policy, PinnedState state) {
        RateQuote core = switch (route.core()) {
            case CoreResolution.Identity ignored -> new RateQuote(BigDecimal.ONE, RateType.FIXED_FACTOR,
                    RateFinality.CONFIRMED, List.of(FxReason.IDENTITY), List.of(), RightsSet.unrestricted(), false,
                    null, null, null, null);
            case CoreResolution.DirectlyResolved dr -> new RateQuote(dr.value(), dr.rateType(), RateFinality.CONFIRMED,
                    List.of(dr.reason()), List.of(), RightsSet.unrestricted(), false, null, dr.sourceCode(), null, null);
            case CoreResolution.MarketChain mc -> resolveMarketChain(mc, fxDate, valuationDate, purpose, policy, state);
        };
        return applyFixedFactors(route, core);
    }

    private RateQuote applyFixedFactors(PairRoute route, RateQuote core) {
        BigDecimal rate = core.rate();
        List<FxReason> reasons = new ArrayList<>(core.reasons());
        if (route.preFactor() != null) {
            rate = route.preFactor().multiply(rate, fxMath.working());
            if (!reasons.contains(route.preFactorReason())) {
                reasons.add(route.preFactorReason());
            }
        }
        if (route.postFactor() != null) {
            rate = rate.multiply(route.postFactor(), fxMath.working());
            if (!reasons.contains(route.postFactorReason())) {
                reasons.add(route.postFactorReason());
            }
        }
        if (route.preFactor() == null && route.postFactor() == null) {
            return core;
        }
        return new RateQuote(rate.round(FxMath.DECIMAL128), core.rateType(), core.finality(), reasons, core.path(),
                core.rights(), core.needsForward(), core.forwardValueDate(), core.source(), core.fixingVersionId(),
                core.fixingStatus());
    }

    private RateQuote resolveMarketChain(CoreResolution.MarketChain mc, LocalDate fxDate, LocalDate valuationDate,
            Purpose purpose, ResolvedPolicy policy, PinnedState state) {
        List<RateQuote> legQuotes = new ArrayList<>();
        for (MarketLeg leg : mc.legs()) {
            legQuotes.add(resolveLeg(leg, fxDate, valuationDate, purpose, policy, state));
        }
        if (legQuotes.size() == 1) {
            return legQuotes.get(0);
        }
        RateQuote l1 = legQuotes.get(0);
        RateQuote l2 = legQuotes.get(1);
        BigDecimal combined = l1.rate().multiply(l2.rate(), fxMath.working()).round(FxMath.DECIMAL128);
        List<FxReason> reasons = new ArrayList<>(l1.reasons());
        for (FxReason r : l2.reasons()) {
            if (!reasons.contains(r)) {
                reasons.add(r);
            }
        }
        reasons.add(FxReason.TRIANGULATED);
        RateFinality finality = RateFinality.weakest(l1.finality(), l2.finality());
        RightsSet rights = l1.rights().intersect(l2.rights());
        return new RateQuote(combined, RateType.SPOT, finality, reasons, List.of(), rights, false, null, l1.source(),
                null, null);
    }

    /** Resolves one market leg's contribution, already inverted if {@code leg.inverted()} (R1-R6). */
    private RateQuote resolveLeg(MarketLeg leg, LocalDate fxDate, LocalDate valuationDate, Purpose purpose,
            ResolvedPolicy policy, PinnedState state) {
        CurrencyPair quotedPair = leg.quotedPair();
        List<String> sources = policy.policy().rateSourcePriority();
        if (sources.isEmpty()) {
            throw FxErrors.of(FxErrorCode.FX_E_SOURCE_NOT_ENTITLED, "no entitled source available for " + quotedPair);
        }
        String firstSource = sources.get(0);
        checkUsageClass(firstSource, purpose, state);

        Optional<FixingSeries> series = state.fixings().findAnyCutoff(firstSource, quotedPair);
        Optional<FixingSeries.Resolution> resolution = series.flatMap(s ->
                s.resolve(fxDate, state.knowledgeCut(), policy.policy().fixingVersionSelection()));

        if (resolution.isPresent()) {
            FixingSeries.Resolution r = resolution.get();
            RateFinality finality = r.viaPreliminaryFallback() ? RateFinality.ESTIMATED : RateFinality.CONFIRMED;
            FxReason reason = r.viaPreliminaryFallback() ? FxReason.PRELIM_FIXING : FxReason.FIXING;
            BigDecimal value = leg.inverted() ? invert(r.entry().value()) : r.entry().value();
            return new RateQuote(value, RateType.FIXING, finality, List.of(reason), List.of(),
                    rightsFor(firstSource, fxDate, state), false, null, firstSource, r.entry().versionId(),
                    r.entry().status());
        }

        // Miss: branch on fxDate vs valuationDate (R3/R5/R6).
        if (fxDate.isBefore(valuationDate)) {
            RateLookupMiss miss = new RateLookupMiss(leg, firstSource, fxDate, true);
            Optional<RateQuote> fallback = fallbackChainRunner.run(miss, policy, state, purpose);
            if (fallback.isPresent()) {
                RateQuote fb = fallback.get();
                BigDecimal value = leg.inverted() ? invert(fb.rate()) : fb.rate();
                return fb.withRate(value).withFinality(RateFinality.ESTIMATED, null);
            }
            throw FxErrors.of(FxErrorCode.FX_E_RATE_NOT_FOUND,
                    "no fixing found for " + quotedPair + " at " + fxDate + " and fallback chain exhausted");
        }

        if (fxDate.isEqual(valuationDate)) {
            Optional<SpotQuote> spot = state.snapshot().spot(quotedPair);
            if (spot.isPresent()) {
                BigDecimal value = leg.inverted() ? invert(spot.get().rate()) : spot.get().rate();
                return new RateQuote(value, RateType.SPOT, RateFinality.ESTIMATED, List.of(FxReason.PRE_PUBLICATION),
                        List.of(), rightsFor(firstSource, fxDate, state), false, null, firstSource, null, null);
            }
            // fall through to forward below if no spot either.
        }

        // R6 (fxDate after valuationDate) and the no-spot branch of R5: forward.
        if (policy.policy().futureDateTreatment() == FutureDateTreatment.SPOT) {
            Optional<SpotQuote> spot = state.snapshot().spot(quotedPair);
            if (spot.isPresent()) {
                BigDecimal value = leg.inverted() ? invert(spot.get().rate()) : spot.get().rate();
                return new RateQuote(value, RateType.SPOT, RateFinality.ESTIMATED, List.of(FxReason.FORWARD_RATE),
                        List.of(), rightsFor(firstSource, fxDate, state), false, null, firstSource, null, null);
            }
        }
        ForwardCurve curve = forwardCurveCache.curve(quotedPair, state);
        BigDecimal outright = curve.outright(fxDate, fxMath);
        BigDecimal value = leg.inverted() ? invert(outright) : outright;
        return new RateQuote(value, RateType.FORWARD, RateFinality.ESTIMATED, List.of(FxReason.FORWARD_RATE),
                List.of(), curve.sourceRights(), false, null, firstSource, null, null);
    }

    /** FS S11.5 / vector X09: {@code usageClass = MTM_ONLY} sources rejected for settlement purposes. */
    private void checkUsageClass(String sourceCode, Purpose purpose, PinnedState state) {
        state.tenant().fixingSource(sourceCode, state.snapshot().asOfDate(), state.knowledgeCut()).ifPresent(src -> {
            if (com.power.fx.core.validation.PolicyMatrix.isUsageClassDisallowed(purpose, src.usageClass())) {
                throw FxErrors.of(FxErrorCode.FX_V_SOURCE_NOT_ALLOWED,
                        "source " + sourceCode + " has usageClass " + src.usageClass() + ", not allowed for purpose " + purpose,
                        "sourceCode", sourceCode);
            }
        });
    }

    /** Looks up the actual entitlement rights for {@code sourceCode}, for {@code DistributionRestriction} (D-10). */
    private RightsSet rightsFor(String sourceCode, LocalDate fxDate, PinnedState state) {
        return state.tenant().entitlement(sourceCode, fxDate, state.knowledgeCut())
                .map(e -> RightsSet.of(sourceCode, e.rights()))
                .orElse(RightsSet.of(sourceCode, java.util.Set.of()));
    }

    private BigDecimal invert(BigDecimal value) {
        return BigDecimal.ONE.divide(value, FxMath.DECIMAL128);
    }
}
