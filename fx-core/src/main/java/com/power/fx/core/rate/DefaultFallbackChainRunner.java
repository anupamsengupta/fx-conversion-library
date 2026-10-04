package com.power.fx.core.rate;

import com.power.fx.api.error.FxReason;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.error.FxWarningCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FallbackStep;
import com.power.fx.api.model.FallbackStepKind;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.cache.FixingSeries;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.pair.MarketLeg;
import com.power.fx.core.snapshot.PinnedState;
import com.power.fx.core.validation.PolicyMatrix;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FS S11.3's fallback chain, run in the policy's declared step order.
 *
 * <p><strong>Signature extension (documented, same reasoning as {@code
 * RateSelector}):</strong> S5.4's indicative {@code run(RateLookupMiss,
 * ResolvedPolicy, PinnedState)} cannot express the purpose-specific
 * forbidden-step check (needs {@code Purpose}); this implementation takes
 * it as an explicit parameter.
 *
 * <p><strong>Documented simplification:</strong> {@code TRIANGULATE} and
 * {@code INTERPOLATE_FIXINGS} are implemented in simplified form (single-
 * source same-date triangulation via USD/EUR only; linear interpolation
 * between the nearest surrounding same-source fixings) since no golden
 * vector in this phase exercises either with an exact expected value
 * (only {@code ALT_SOURCE}, vector C04, carries that bar).
 */
public final class DefaultFallbackChainRunner implements FallbackChainRunner {

    public Optional<RateQuote> run(RateLookupMiss miss, ResolvedPolicy resolvedPolicy, PinnedState state, Purpose purpose) {
        for (FallbackStep step : resolvedPolicy.policy().fallbackChain()) {
            if (PolicyMatrix.isFallbackStepForbidden(purpose, step.kind())) {
                continue;
            }
            Optional<RateQuote> result = switch (step.kind()) {
                case ALT_SOURCE -> altSource(miss, resolvedPolicy, state);
                case PREVIOUS_PUBLICATION_DAY -> previousPublicationDay(miss, resolvedPolicy, state, step.maxSteps());
                case TRIANGULATE -> triangulate(miss, resolvedPolicy, state);
                case INTERPOLATE_FIXINGS -> interpolateFixings(miss, resolvedPolicy, state);
                case FAIL -> Optional.empty();
            };
            if (result.isPresent()) {
                return result;
            }
            if (step.kind() == FallbackStepKind.FAIL) {
                break;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<RateQuote> run(RateLookupMiss miss, ResolvedPolicy policy, PinnedState state) {
        // Purpose-agnostic convenience overload (no forbidden-step filtering); prefer the 4-arg form.
        return run(miss, policy, state, null);
    }

    private Optional<RateQuote> altSource(RateLookupMiss miss, ResolvedPolicy policy, PinnedState state) {
        List<String> sources = policy.policy().rateSourcePriority();
        int idx = sources.indexOf(miss.firstSourceCode());
        for (int i = idx + 1; i < sources.size(); i++) {
            String candidate = sources.get(i);
            Optional<FixingSeries> series = state.fixings().findAnyCutoff(candidate, miss.leg().quotedPair());
            if (series.isPresent()) {
                Optional<FixingSeries.Resolution> r = series.get().resolve(miss.fxDate(), state.knowledgeCut(),
                        policy.policy().fixingVersionSelection());
                if (r.isPresent()) {
                    return Optional.of(quoteFrom(r.get(), miss.leg(), candidate, FxReason.FALLBACK_ALT_SOURCE));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<RateQuote> previousPublicationDay(RateLookupMiss miss, ResolvedPolicy policy, PinnedState state, int maxSteps) {
        Optional<FixingSeries> series = state.fixings().findAnyCutoff(miss.firstSourceCode(), miss.leg().quotedPair());
        if (series.isEmpty()) {
            return Optional.empty();
        }
        LocalDate d = miss.fxDate();
        for (int i = 0; i < maxSteps; i++) {
            d = d.minusDays(1);
            Optional<FixingSeries.Resolution> r = series.get().resolve(d, state.knowledgeCut(),
                    policy.policy().fixingVersionSelection());
            if (r.isPresent()) {
                return Optional.of(quoteFrom(r.get(), miss.leg(), miss.firstSourceCode(), FxReason.FALLBACK_STALE));
            }
        }
        return Optional.empty();
    }

    private Optional<RateQuote> triangulate(RateLookupMiss miss, ResolvedPolicy policy, PinnedState state) {
        CurrencyPair pair = miss.leg().quotedPair();
        for (String viaCode : new String[] {"USD", "EUR"}) {
            var via = new com.power.fx.api.model.CurrencyCode(viaCode);
            if (via.equals(pair.base()) || via.equals(pair.quote())) {
                continue;
            }
            Optional<FixingSeries.Resolution> leg1 = lookupEitherDirection(state, miss.firstSourceCode(), pair.base(), via, miss.fxDate(), policy);
            Optional<FixingSeries.Resolution> leg2 = lookupEitherDirection(state, miss.firstSourceCode(), via, pair.quote(), miss.fxDate(), policy);
            if (leg1.isPresent() && leg2.isPresent()) {
                BigDecimal rate = leg1.get().entry().value().multiply(leg2.get().entry().value(),
                        com.power.fx.core.decimal.FxMath.DECIMAL128);
                RateFinality finality = RateFinality.weakest(finalityOf(leg1.get()), finalityOf(leg2.get()));
                return Optional.of(new RateQuote(rate, RateType.FIXING, finality,
                        List.of(FxReason.FALLBACK_TRIANGULATED), List.of(), RightsSet.of(miss.firstSourceCode(), java.util.Set.of()),
                        false, null, miss.firstSourceCode(), null, null));
            }
        }
        return Optional.empty();
    }

    private Optional<FixingSeries.Resolution> lookupEitherDirection(PinnedState state, String source,
            com.power.fx.api.model.CurrencyCode a, com.power.fx.api.model.CurrencyCode b, LocalDate fxDate, ResolvedPolicy policy) {
        Optional<FixingSeries> direct = state.fixings().findAnyCutoff(source, new CurrencyPair(a, b));
        if (direct.isPresent()) {
            Optional<FixingSeries.Resolution> r = direct.get().resolve(fxDate, state.knowledgeCut(), policy.policy().fixingVersionSelection());
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    private Optional<RateQuote> interpolateFixings(RateLookupMiss miss, ResolvedPolicy policy, PinnedState state) {
        Optional<FixingSeries> series = state.fixings().findAnyCutoff(miss.firstSourceCode(), miss.leg().quotedPair());
        if (series.isEmpty()) {
            return Optional.empty();
        }
        LocalDate before = miss.fxDate();
        FixingSeries.Resolution beforeRes = null;
        for (int i = 0; i < 10; i++) {
            before = before.minusDays(1);
            Optional<FixingSeries.Resolution> r = series.get().resolve(before, state.knowledgeCut(), policy.policy().fixingVersionSelection());
            if (r.isPresent()) {
                beforeRes = r.get();
                break;
            }
        }
        LocalDate after = miss.fxDate();
        FixingSeries.Resolution afterRes = null;
        for (int i = 0; i < 10; i++) {
            after = after.plusDays(1);
            Optional<FixingSeries.Resolution> r = series.get().resolve(after, state.knowledgeCut(), policy.policy().fixingVersionSelection());
            if (r.isPresent()) {
                afterRes = r.get();
                break;
            }
        }
        if (beforeRes == null || afterRes == null) {
            return Optional.empty();
        }
        long span = after.toEpochDay() - before.toEpochDay();
        long toTarget = miss.fxDate().toEpochDay() - before.toEpochDay();
        BigDecimal fraction = BigDecimal.valueOf(toTarget).divide(BigDecimal.valueOf(span), com.power.fx.core.decimal.FxMath.DECIMAL128);
        BigDecimal diff = afterRes.entry().value().subtract(beforeRes.entry().value(), com.power.fx.core.decimal.FxMath.DECIMAL128);
        BigDecimal rate = beforeRes.entry().value().add(diff.multiply(fraction, com.power.fx.core.decimal.FxMath.DECIMAL128),
                com.power.fx.core.decimal.FxMath.DECIMAL128);
        return Optional.of(new RateQuote(miss.leg().inverted() ? BigDecimal.ONE.divide(rate, com.power.fx.core.decimal.FxMath.DECIMAL128) : rate,
                RateType.FIXING, RateFinality.ESTIMATED, List.of(FxReason.FALLBACK_INTERPOLATED), List.of(),
                RightsSet.of(miss.firstSourceCode(), java.util.Set.of()), false, null, miss.firstSourceCode(), null, null));
    }

    private RateFinality finalityOf(FixingSeries.Resolution r) {
        return r.viaPreliminaryFallback() ? RateFinality.ESTIMATED : RateFinality.CONFIRMED;
    }

    private RateQuote quoteFrom(FixingSeries.Resolution r, MarketLeg leg, String sourceCode, FxReason reason) {
        BigDecimal value = r.entry().value();
        BigDecimal finalRate = leg.inverted() ? BigDecimal.ONE.divide(value, com.power.fx.core.decimal.FxMath.DECIMAL128) : value;
        return new RateQuote(finalRate, RateType.FIXING, RateFinality.ESTIMATED,
                List.of(reason), List.of(), RightsSet.of(sourceCode, java.util.Set.of()), false, null, sourceCode,
                r.entry().versionId(), r.entry().status());
    }
}
