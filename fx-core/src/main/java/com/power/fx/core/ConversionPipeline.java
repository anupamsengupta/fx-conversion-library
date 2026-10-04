package com.power.fx.core;

import com.power.fx.api.error.FxReason;
import com.power.fx.api.error.FxWarning;
import com.power.fx.api.model.AveragingMethod;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.request.ObservationPrice;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.request.ConversionRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.request.SeriesRequest;
import com.power.fx.api.result.ConversionResult;
import com.power.fx.api.result.DistributionRestriction;
import com.power.fx.api.result.Lineage;
import com.power.fx.api.result.ObservationResult;
import com.power.fx.api.result.RateResult;
import com.power.fx.api.result.SeriesResult;
import com.power.fx.core.averaging.AveragingEngine;
import com.power.fx.core.averaging.Observation;
import com.power.fx.core.averaging.ObservationSet;
import com.power.fx.core.averaging.ObservationSetBuilder;
import com.power.fx.core.averaging.SeriesOutcome;
import com.power.fx.core.date.DateRuleResolver;
import com.power.fx.core.date.ResolvedDates;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.EntitlementResolver;
import com.power.fx.core.entitlement.FilteredSources;
import com.power.fx.core.lineage.LineageBuilder;
import com.power.fx.core.memo.MemoKey;
import com.power.fx.core.pair.PairResolver;
import com.power.fx.core.pair.PairRoute;
import com.power.fx.core.precision.BookedAmount;
import com.power.fx.core.precision.PrecisionEngine;
import com.power.fx.core.rate.RateQuote;
import com.power.fx.core.rate.RateSelector;
import com.power.fx.core.snapshot.PinnedState;
import com.power.fx.core.validation.PolicyValidator;
import com.power.fx.core.validation.RequestValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The fixed-order stage sequence of S6.2 (stages 3-17; stages 1-2 are
 * {@code DefaultFxConverter}'s job, stage 18 the memo-put wrapping this
 * class's callers do). The only place stage order is encoded (Pattern #11
 * composed Strategy).
 *
 * <p>As documented on {@code DefaultRateSelector}, stage 12 (FORWARD) is
 * resolved inline within {@link RateSelector#select}, not as a separate
 * call from this class.
 *
 * @see "Tech spec S6.2, Appendix C"
 */
public final class ConversionPipeline {

    private final DateRuleResolver dateRuleResolver;
    private final EntitlementResolver entitlementResolver;
    private final PairResolver pairResolver;
    private final RateSelector rateSelector;
    private final AveragingEngine averagingEngine;
    private final PrecisionEngine precisionEngine;
    private final LineageBuilder lineageBuilder;
    private final FxMath fxMath;
    private final PolicyValidator policyValidator = new PolicyValidator();
    private final RequestValidator requestValidator = new RequestValidator();
    private final ObservationSetBuilder observationSetBuilder = new ObservationSetBuilder();

    public ConversionPipeline(DateRuleResolver dateRuleResolver, EntitlementResolver entitlementResolver,
            PairResolver pairResolver, RateSelector rateSelector, AveragingEngine averagingEngine,
            PrecisionEngine precisionEngine, LineageBuilder lineageBuilder, FxMath fxMath) {
        this.dateRuleResolver = dateRuleResolver;
        this.entitlementResolver = entitlementResolver;
        this.pairResolver = pairResolver;
        this.rateSelector = rateSelector;
        this.averagingEngine = averagingEngine;
        this.precisionEngine = precisionEngine;
        this.lineageBuilder = lineageBuilder;
        this.fxMath = fxMath;
    }

    /** Stages 3-13: policy, validation, dates, observations, entitlement, pair, rate (+ inline forward). */
    public QuoteResolution resolveQuotes(FxRequestContext context, CurrencyCode from, CurrencyCode to, PinnedState state) {
        ResolvedPolicy policy = ResolvedPolicy.resolve(context.policy(), state.tenant(), context.valuationDate(), state.knowledgeCut());
        policyValidator.validate(context, policy);
        requestValidator.validateUnsignedSnapshot(context.purpose(), context.runMode(), state.snapshot().signOffStatus());
        AveragingMethod method = policy.policy().averaging() == null ? AveragingMethod.NONE : policy.policy().averaging().method();
        requestValidator.validatePriceSeriesMatch(method, context.pricingSet(), context.prices());

        ResolvedDates dates = dateRuleResolver.resolve(policy, state, context);
        ObservationSet obsSet = observationSetBuilder.build(dates, policy.policy().averaging());

        FilteredSources filtered = entitlementResolver.filter(policy.policy().rateSourcePriority(), dates.primary().resolvedDate(), state);
        List<FxWarning> warnings = new ArrayList<>(filtered.warnings());
        warnings.addAll(dates.warnings());
        if (filtered.allowed().isEmpty()) {
            boolean hasEntitledFallback = false; // simplification: fallback-source entitlement is not pre-checked here
            if (!hasEntitledFallback) {
                throw FxErrors.of(com.power.fx.api.error.FxErrorCode.FX_E_SOURCE_NOT_ENTITLED,
                        "no entitled source remains for " + from.value() + "/" + to.value());
            }
        }
        ResolvedPolicy narrowed = policy.withSourcePriority(filtered.allowed().isEmpty() ? policy.policy().rateSourcePriority() : filtered.allowed());

        List<RateQuote> quotes = new ArrayList<>();
        for (Observation o : obsSet.observations()) {
            PairRoute route = pairResolver.route(from, to, narrowed, state, o.resolvedFxDate());
            RateQuote q = rateSelector.select(route, o.resolvedFxDate(), context.valuationDate(), context.purpose(), narrowed, state);
            quotes.add(q);
        }
        return new QuoteResolution(policy, narrowed, dates, obsSet, quotes, warnings);
    }

    public RateResult rate(RateRequest request, PinnedState state) {
        FxRequestContext ctx = request.context();
        QuoteResolution qr = resolveQuotes(ctx, request.from(), request.to(), state);
        RateQuote q = qr.quotes().get(0);
        Lineage lineage = lineageBuilder.build(new ResolvedContext(state, qr.policy(), ctx), request.from(), request.to(), null, false);
        DistributionRestriction restriction = q.rights().toDistributionRestriction();
        return new RateResult(request.from(), request.to(), q.rate(), q.rateType(), q.finality(), q.reasons(),
                q.path(), restriction, lineage, qr.warnings(), java.util.Optional.empty());
    }

    public ConversionResult convert(ConversionRequest request, PinnedState state) {
        FxRequestContext ctx = request.context();
        QuoteResolution qr = resolveQuotes(ctx, request.from(), request.to(), state);
        SeriesOutcome outcome = averagingEngine.average(qr.observationSet(), qr.quotes(), null, request.amount(), false,
                qr.narrowedPolicy().policy().averaging(), fxMath);
        BookedAmount booked = precisionEngine.book(outcome.totalUnrounded(), request.to(),
                qr.narrowedPolicy().policy().rounding(), state);
        DistributionRestriction restriction = outcome.rights().toDistributionRestriction();
        Lineage lineage = lineageBuilder.build(new ResolvedContext(state, qr.policy(), ctx), request.from(), request.to(),
                request.amount(), request.isUnitPrice());
        RateType rateType = qr.quotes().get(0).rateType();
        return new ConversionResult(request.from(), request.to(), request.amount(), outcome.totalUnrounded(),
                booked.booked(), outcome.averageRate(), rateType, outcome.finality(), outcome.reasons(), List.of(),
                restriction, lineage, qr.warnings(), java.util.Optional.empty());
    }

    public SeriesResult convertSeries(SeriesRequest request, PinnedState state) {
        FxRequestContext ctx = request.context();
        QuoteResolution qr = resolveQuotes(ctx, request.from(), request.to(), state);
        FxPolicy policy = qr.narrowedPolicy().policy();
        List<BigDecimal> prices = null;
        if (policy.averaging() != null && policy.averaging().method() == AveragingMethod.PRICE_MATCHED) {
            Map<Integer, BigDecimal> bySeq = new java.util.HashMap<>();
            for (ObservationPrice p : ctx.prices()) {
                bySeq.put(p.sequence(), p.price());
            }
            prices = new ArrayList<>();
            for (Observation o : qr.observationSet().observations()) {
                prices.add(bySeq.get(o.sequence()));
            }
        }
        SeriesOutcome outcome = averagingEngine.average(qr.observationSet(), qr.quotes(), prices, request.periodAmount(),
                false, policy.averaging(), fxMath);

        List<ObservationResult> obsResults = new ArrayList<>();
        for (int i = 0; i < qr.observationSet().observations().size(); i++) {
            SeriesOutcome.ObservationOutcome oo = outcome.observationOutcomes().get(i);
            BigDecimal amountBooked = oo.amountUnrounded() == null ? null
                    : precisionEngine.book(oo.amountUnrounded(), request.to(), policy.rounding(), state).booked();
            obsResults.add(new ObservationResult(oo.observation().sequence(), oo.observation().observationDate(),
                    oo.observation().rawFxDate(), oo.observation().resolvedFxDate(), oo.observation().weight(),
                    oo.observation().price(), oo.rate(), oo.amountUnrounded(), amountBooked, RateType.FIXING,
                    oo.finality(), oo.reasons(), List.of(), false));
        }

        BigDecimal totalBooked = outcome.totalUnrounded() == null ? null
                : precisionEngine.book(outcome.totalUnrounded(), request.to(), policy.rounding(), state).booked();
        DistributionRestriction restriction = outcome.rights().toDistributionRestriction();
        Lineage lineage = lineageBuilder.build(new ResolvedContext(state, qr.policy(), ctx), request.from(), request.to(),
                request.periodAmount(), request.isUnitPrice());

        return new SeriesResult(request.from(), request.to(), outcome.totalUnrounded(), totalBooked,
                outcome.averageRate(), outcome.confirmedAverage(), outcome.estimatedAverage(), outcome.confirmedPortion(),
                obsResults, null, outcome.finality(), outcome.reasons(), restriction, lineage, qr.warnings(),
                java.util.Optional.empty());
    }

    public record QuoteResolution(ResolvedPolicy policy, ResolvedPolicy narrowedPolicy, ResolvedDates dates,
            ObservationSet observationSet, List<RateQuote> quotes, List<FxWarning> warnings) {
    }
}
