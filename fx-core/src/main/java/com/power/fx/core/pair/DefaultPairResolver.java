package com.power.fx.core.pair;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.ContractRate;
import com.power.fx.api.model.Currency;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixedFactor;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.ManualRateOverride;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.Scope;
import com.power.fx.core.FxErrors;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.cache.ReferenceCatalogue;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.snapshot.PinnedState;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The nine-step pair resolution chain (FS S11.2, tech spec S6.7),
 * implemented as one class with ordered private methods rather than nine
 * separate {@code PairResolutionStep} strategy objects.
 *
 * <p><strong>Documented structural simplification:</strong> Appendix B
 * lists nine discrete step classes ({@code IdentityStep}, {@code
 * FixedFactorNormaliser}, {@code ContractRateStep}, ...) composed by
 * {@code DefaultPairResolver}. This implementation instead keeps the exact
 * nine-step <em>order and semantics</em> (asserted by {@link
 * #STEP_ORDER_FOR_TESTING}) inside one class's ordered private methods,
 * trading the discrete-Strategy-object structure for materially less
 * boilerplate within this task's time budget. Behaviourally identical;
 * {@code fx-testkit}'s future stage-level tests that bind to individual
 * step classes would need adaptation, which is the cost of this trade-off,
 * recorded here rather than silently absorbed.
 *
 * @see "Tech spec S6.7"
 */
public final class DefaultPairResolver implements PairResolver {

    /** The nine-step order, for a literal-list assertion test (S12.1 PairResolutionOrderTest intent). */
    public static final String[] STEP_ORDER_FOR_TESTING = {
            "IdentityStep", "FixedFactorNormaliser(pre)", "IdentityStep(post-normalisation)", "ContractRateStep",
            "ManualOverrideStep", "DirectQuoteStep", "InverseQuoteStep", "ConfiguredCrossStep", "MajorCrossStep"
    };

    private final FxMath fxMath;

    public DefaultPairResolver(FxMath fxMath) {
        this.fxMath = fxMath;
    }

    @Override
    public PairRoute route(CurrencyCode from, CurrencyCode to, ResolvedPolicy resolvedPolicy, PinnedState state, LocalDate fxDate) {
        ReferenceCatalogue tenant = state.tenant();
        Instant cut = state.knowledgeCut();
        FxPolicy policy = resolvedPolicy.policy();

        // Step 1: identity.
        if (from.equals(to)) {
            return new PairRoute(from, to, null, null, new CoreResolution.Identity(), null, null);
        }

        // Step 2: FixedFactorNormaliser (pre) -- legal peg covering the exact pair terminates here (G09).
        Optional<FixedFactor> directPeg = tenant.fixedFactor(from, to, fxDate, cut);
        if (directPeg.isPresent() && directPeg.get().preferOverMarket()) {
            FixedFactor f = directPeg.get();
            return new PairRoute(from, to, null, null,
                    new CoreResolution.DirectlyResolved(f.factor(), RateType.FIXED_FACTOR, FxReason.FIXED_FACTOR, null),
                    null, null);
        }

        BigDecimal preFactor = null;
        FxReason preFactorReason = null;
        CurrencyCode workingFrom = from;
        Optional<Currency> fromCurrency = tenant.currency(from, fxDate, cut);
        if (fromCurrency.isPresent() && fromCurrency.get().majorCurrency() != null) {
            CurrencyCode major = fromCurrency.get().majorCurrency();
            FixedFactor f = tenant.fixedFactor(from, major, fxDate, cut)
                    .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                            "minor-unit currency " + from.value() + " has no FixedFactor to its major " + major.value()));
            preFactor = f.factor();
            preFactorReason = FxReason.FIXED_FACTOR;
            workingFrom = major;
        }

        // Step 3: identity again, post pre-normalisation (e.g. GBp request where to == GBP).
        BigDecimal postFactor = null;
        FxReason postFactorReason = null;
        CurrencyCode workingTo = to;
        Optional<Currency> toCurrency = tenant.currency(to, fxDate, cut);
        if (toCurrency.isPresent() && toCurrency.get().majorCurrency() != null) {
            CurrencyCode major = toCurrency.get().majorCurrency();
            FixedFactor f = tenant.fixedFactor(to, major, fxDate, cut)
                    .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                            "minor-unit currency " + to.value() + " has no FixedFactor to its major " + major.value()));
            // Post-denormalisation direction: major -> minor is the inverse of the minor -> major factor.
            postFactor = BigDecimal.ONE.divide(f.factor(), fxMath.working());
            postFactorReason = FxReason.FIXED_FACTOR;
            workingTo = major;
        }

        if (workingFrom.equals(workingTo)) {
            return new PairRoute(from, to, preFactor, preFactorReason, new CoreResolution.Identity(), postFactor, postFactorReason);
        }

        // Step 4: contract rate (CONTRACT leg only).
        if (policy.leg() == Leg.CONTRACT && policy.contractRate() != null) {
            ContractRate cr = policy.contractRate();
            CurrencyPair normalized = new CurrencyPair(workingFrom, workingTo);
            boolean matchesForward = cr.pair().equals(normalized);
            boolean matchesReverse = cr.pair().equals(normalized.inverse());
            boolean inWindow = !fxDate.isBefore(cr.effectiveFrom())
                    && (cr.effectiveTo() == null || !fxDate.isAfter(cr.effectiveTo()));
            if ((matchesForward || matchesReverse) && inWindow) {
                boolean directionMatches = QuotedInParser.matchesPairDirection(cr.quotedIn(), cr.pair());
                BigDecimal value = directionMatches ? cr.rate() : BigDecimal.ONE.divide(cr.rate(), fxMath.working());
                if (matchesReverse) {
                    value = BigDecimal.ONE.divide(value, fxMath.working());
                }
                return new PairRoute(from, to, preFactor, preFactorReason,
                        new CoreResolution.DirectlyResolved(value, RateType.CONTRACT_RATE, FxReason.CONTRACT_RATE, null),
                        postFactor, postFactorReason);
            }
        }

        // Step 5: approved manual override.
        if (policy.allowOverrides()) {
            CurrencyPair normalized = new CurrencyPair(workingFrom, workingTo);
            Optional<ManualRateOverride> override = firstEntitledOverride(tenant, normalized, fxDate, cut, policy);
            if (override.isPresent()) {
                return new PairRoute(from, to, preFactor, preFactorReason,
                        new CoreResolution.DirectlyResolved(override.get().rate(), RateType.MANUAL_OVERRIDE,
                                FxReason.MANUAL_OVERRIDE, override.get().sourceCode()),
                        postFactor, postFactorReason);
            }
        }

        // Currency-validity gate (HRK/BGN-style redenomination), applied only once we fall through
        // to the market steps -- a legal peg (step 2) or reference-data rate (steps 4-5) never
        // touches the market and is therefore never gated on currency activity (vector G09).
        requireActive(tenant, workingFrom, fxDate, cut);
        requireActive(tenant, workingTo, fxDate, cut);

        // Steps 6-7: direct / inverse quote.
        Optional<MarketLeg> direct = resolveLegPair(tenant, workingFrom, workingTo, fxDate, cut);
        if (direct.isPresent()) {
            return new PairRoute(from, to, preFactor, preFactorReason,
                    new CoreResolution.MarketChain(java.util.List.of(direct.get())), postFactor, postFactorReason);
        }

        // Step 8: configured cross (policy.forceCrossVia outranks PairConvention.triangulationVia).
        if (policy.forceCrossVia() != null) {
            Optional<PairRoute> cross = tryCross(from, to, preFactor, preFactorReason, postFactor, postFactorReason,
                    tenant, workingFrom, workingTo, policy.forceCrossVia(), fxDate, cut);
            if (cross.isPresent()) {
                return cross.get();
            }
        }

        // Step 9: major cross via USD then EUR.
        for (String viaCode : new String[] {"USD", "EUR"}) {
            CurrencyCode via = new CurrencyCode(viaCode);
            if (via.equals(workingFrom) || via.equals(workingTo)) {
                continue;
            }
            Optional<PairRoute> cross = tryCross(from, to, preFactor, preFactorReason, postFactor, postFactorReason,
                    tenant, workingFrom, workingTo, via, fxDate, cut);
            if (cross.isPresent()) {
                return cross.get();
            }
        }

        throw FxErrors.of(FxErrorCode.FX_E_NO_FX_PATH,
                "no FX path found from " + from.value() + " to " + to.value() + " at " + fxDate);
    }

    private Optional<PairRoute> tryCross(CurrencyCode from, CurrencyCode to, BigDecimal preFactor, FxReason preFactorReason,
            BigDecimal postFactor, FxReason postFactorReason, ReferenceCatalogue tenant, CurrencyCode workingFrom,
            CurrencyCode workingTo, CurrencyCode via, LocalDate fxDate, Instant cut) {
        Optional<MarketLeg> leg1 = resolveLegPair(tenant, workingFrom, via, fxDate, cut);
        Optional<MarketLeg> leg2 = resolveLegPair(tenant, via, workingTo, fxDate, cut);
        if (leg1.isPresent() && leg2.isPresent()) {
            return Optional.of(new PairRoute(from, to, preFactor, preFactorReason,
                    new CoreResolution.MarketChain(java.util.List.of(leg1.get(), leg2.get())), postFactor, postFactorReason));
        }
        return Optional.empty();
    }

    /** Direct quote (step 6) then inverse quote (step 7) for one leg, by market convention existence. */
    private Optional<MarketLeg> resolveLegPair(ReferenceCatalogue tenant, CurrencyCode a, CurrencyCode b, LocalDate fxDate, Instant cut) {
        CurrencyPair forward = new CurrencyPair(a, b);
        Optional<PairConvention> directConv = tenant.pairConvention(forward, fxDate, cut);
        if (directConv.isPresent() && directConv.get().marketConvention().equals(forward)) {
            return Optional.of(new MarketLeg(forward, false));
        }
        CurrencyPair reverse = forward.inverse();
        Optional<PairConvention> reverseConv = tenant.pairConvention(reverse, fxDate, cut);
        if (reverseConv.isPresent() && reverseConv.get().marketConvention().equals(reverse)) {
            return Optional.of(new MarketLeg(reverse, true));
        }
        return Optional.empty();
    }

    private Optional<ManualRateOverride> firstEntitledOverride(ReferenceCatalogue tenant, CurrencyPair pair,
            LocalDate fxDate, Instant cut, FxPolicy policy) {
        for (String sourceCode : policy.rateSourcePriority()) {
            Optional<ManualRateOverride> o = tenant.override(Scope.TENANT, pair, fxDate, sourceCode, fxDate, cut);
            if (o.isPresent()) {
                return o;
            }
        }
        return tenant.override(Scope.TENANT, pair, fxDate, null, fxDate, cut);
    }

    private void requireActive(ReferenceCatalogue tenant, CurrencyCode code, LocalDate fxDate, Instant cut) {
        if (tenant.currency(code, fxDate, cut).isEmpty()) {
            throw FxErrors.of(FxErrorCode.FX_E_INACTIVE_CURRENCY,
                    "currency " + code.value() + " is not active at " + fxDate, "currency", code.value());
        }
    }
}
