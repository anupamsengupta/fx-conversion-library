package com.power.fx.core.leg;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxException;
import com.power.fx.api.error.FxReason;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.request.ChainRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.ManagementViewSpec;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.result.ChainResult;
import com.power.fx.api.result.ConversionResult;
import com.power.fx.api.result.DistributionRestriction;
import com.power.fx.api.result.Lineage;
import com.power.fx.api.result.ManagementViewResult;
import com.power.fx.api.result.LegResult;
import com.power.fx.core.FxErrors;
import com.power.fx.core.ResolvedContext;
import com.power.fx.core.ResolvedPolicy;
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
import com.power.fx.core.entitlement.RestrictionPropagator;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.lineage.LineageBuilder;
import com.power.fx.core.pair.PairResolver;
import com.power.fx.core.pair.PairRoute;
import com.power.fx.core.precision.BookedAmount;
import com.power.fx.core.precision.PrecisionEngine;
import com.power.fx.core.rate.RateQuote;
import com.power.fx.core.rate.RateSelector;
import com.power.fx.core.snapshot.PinnedState;
import com.power.fx.core.validation.PolicyMatrix;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CONTRACT -&gt; ACCOUNTING_TRANSACTION -&gt; TRANSLATION, then zero or more
 * MANAGEMENT_VIEW computations (FS S9, D-04, D-16). Vectors F01, F02, F05.
 *
 * <p><strong>Documented simplification:</strong> each leg's policy is
 * resolved and its {@code dateRule} is checked against {@code leg} via
 * {@link PolicyMatrix#isDateRuleAllowedForLeg}, but the full {@code
 * PolicyValidator.validate} purpose/leg/amountType cross-check is
 * <strong>not</strong> run here. {@code ChainRequest} carries one shared
 * {@link FxRequestContext} (one {@code purpose}, one {@code amountType})
 * across three structurally distinct legs with three distinct policies;
 * applying the single-conversion purpose gate across all three would
 * reject a well-formed chain whose shared context purpose does not list
 * every leg (e.g. {@code CONTRACT_SETTLEMENT} permits only the
 * {@code CONTRACT} leg). This is a real seam the tech spec does not
 * resolve for the chain shape specifically; flagged here rather than
 * silently forcing a one-purpose-per-leg invention.
 *
 * @see "Tech spec S6.14"
 */
public final class DefaultChainEngine implements ChainEngine {

    private final DateRuleResolver dateRuleResolver;
    private final EntitlementResolver entitlementResolver;
    private final PairResolver pairResolver;
    private final RateSelector rateSelector;
    private final AveragingEngine averagingEngine;
    private final PrecisionEngine precisionEngine;
    private final LineageBuilder lineageBuilder;
    private final FunctionalCurrencyResolver functionalCurrencyResolver;
    private final FxMath fxMath;
    private final ObservationSetBuilder observationSetBuilder = new ObservationSetBuilder();

    public DefaultChainEngine(DateRuleResolver dateRuleResolver, EntitlementResolver entitlementResolver,
            PairResolver pairResolver, RateSelector rateSelector, AveragingEngine averagingEngine,
            PrecisionEngine precisionEngine, LineageBuilder lineageBuilder,
            FunctionalCurrencyResolver functionalCurrencyResolver, FxMath fxMath) {
        this.dateRuleResolver = dateRuleResolver;
        this.entitlementResolver = entitlementResolver;
        this.pairResolver = pairResolver;
        this.rateSelector = rateSelector;
        this.averagingEngine = averagingEngine;
        this.precisionEngine = precisionEngine;
        this.lineageBuilder = lineageBuilder;
        this.functionalCurrencyResolver = functionalCurrencyResolver;
        this.fxMath = fxMath;
    }

    @Override
    public ChainResult chain(ChainRequest request, PinnedState state) {
        FxRequestContext ctx = request.context();
        Map<Leg, LegResult> legs = new LinkedHashMap<>();
        List<com.power.fx.api.error.FxWarning> warnings = new ArrayList<>();
        Lineage chainLineage = null;

        boolean upstreamUnresolved = false;
        ConversionResult contractResult = null;

        // CONTRACT leg.
        if (request.contractPolicy() != null) {
            try {
                LegOutcome outcome = resolveLeg(ctx, request.contractPolicy(), request.priceCurrency(),
                        request.settlementCurrency(), request.priceAmount(), state);
                contractResult = outcome.conversion();
                chainLineage = outcome.conversion().lineage();
                legs.put(Leg.CONTRACT, new LegResult(Leg.CONTRACT, ctx.purpose(), contractResult, null, null, true));
                warnings.addAll(outcome.warnings());
            } catch (FxException e) {
                legs.put(Leg.CONTRACT, unresolvedLeg(Leg.CONTRACT, ctx, request.priceCurrency(), request.settlementCurrency(),
                        request.priceAmount(), e));
                upstreamUnresolved = true;
            }
        }

        // ACCOUNTING_TRANSACTION leg.
        ConversionResult acctResult = null;
        CurrencyCode functionalCurrency = null;
        if (!upstreamUnresolved && request.accountingPolicy() != null && contractResult != null) {
            try {
                functionalCurrency = functionalCurrencyResolver.resolve(ctx.accountingUnitId(), ctx.valuationDate(),
                        state.tenant(), state.knowledgeCut());
                BigDecimal leg2Input = legTwoInput(ctx, contractResult);
                LegOutcome outcome = resolveLeg(ctx, request.accountingPolicy(), request.settlementCurrency(),
                        functionalCurrency, leg2Input, state);
                acctResult = outcome.conversion();
                legs.put(Leg.ACCOUNTING_TRANSACTION, new LegResult(Leg.ACCOUNTING_TRANSACTION, ctx.purpose(),
                        acctResult, functionalCurrency, ctx.accountingUnitId(), true));
                warnings.addAll(outcome.warnings());
            } catch (FxException e) {
                legs.put(Leg.ACCOUNTING_TRANSACTION, unresolvedLeg(Leg.ACCOUNTING_TRANSACTION, ctx,
                        request.settlementCurrency(), functionalCurrency, null, e));
                upstreamUnresolved = true;
            }
        } else if (request.accountingPolicy() != null) {
            legs.put(Leg.ACCOUNTING_TRANSACTION, upstreamUnresolvedLeg(Leg.ACCOUNTING_TRANSACTION, ctx));
        }

        // TRANSLATION leg.
        if (!upstreamUnresolved && request.translationPolicy() != null && acctResult != null
                && request.presentationCurrencies() != null && !request.presentationCurrencies().isEmpty()) {
            try {
                CurrencyCode presentationCcy = request.presentationCurrencies().get(0);
                LegOutcome outcome = resolveLeg(ctx, request.translationPolicy(), functionalCurrency, presentationCcy,
                        acctResult.toAmountBooked(), state);
                legs.put(Leg.TRANSLATION, new LegResult(Leg.TRANSLATION, ctx.purpose(), outcome.conversion(),
                        functionalCurrency, ctx.accountingUnitId(), true));
                warnings.addAll(outcome.warnings());
            } catch (FxException e) {
                legs.put(Leg.TRANSLATION, unresolvedLeg(Leg.TRANSLATION, ctx, functionalCurrency, null, null, e));
            }
        } else if (request.translationPolicy() != null) {
            legs.put(Leg.TRANSLATION, upstreamUnresolvedLeg(Leg.TRANSLATION, ctx));
        }

        // MANAGEMENT_VIEW (D-16): computed on read, never persistable.
        List<ManagementViewResult> views = new ArrayList<>();
        if (request.managementViews() != null && functionalCurrency != null && acctResult != null) {
            for (ManagementViewSpec spec : request.managementViews()) {
                try {
                    LegOutcome outcome = resolveLeg(ctx, spec.policy(), functionalCurrency, spec.reportCurrency(),
                            acctResult.toAmountBooked(), state);
                    views.add(new ManagementViewResult(spec.reportCurrency(), outcome.conversion()));
                } catch (FxException ignored) {
                    // Management views are display-only; a resolution failure here does not fail the chain.
                }
            }
        }

        RateFinality overall = legs.values().stream().map(l -> l.conversion().finality())
                .reduce(RateFinality.CONFIRMED, RateFinality::weakest);

        if (chainLineage == null) {
            chainLineage = com.power.fx.core.lineage.MinimalLineage.of(state.tenantId(), state.snapshot().marketSnapshotId(),
                    state.knowledgeCut(), state.snapshot().signOffStatus(), "UNKNOWN", 0, ctx);
        }

        return new ChainResult(legs, views, overall, chainLineage, warnings, Optional.empty());
    }

    private BigDecimal legTwoInput(FxRequestContext ctx, ConversionResult leg1) {
        SettlementAmountState sas = ctx.settlementAmountState();
        if (sas == SettlementAmountState.INVOICED || sas == SettlementAmountState.SETTLED) {
            return leg1.toAmountBooked();
        }
        return leg1.toAmountUnrounded();
    }

    private record LegOutcome(ConversionResult conversion, List<com.power.fx.api.error.FxWarning> warnings) {
    }

    private LegOutcome resolveLeg(FxRequestContext ctx, PolicyRef policyRef, CurrencyCode from, CurrencyCode to,
            BigDecimal amount, PinnedState state) {
        ResolvedPolicy policy = ResolvedPolicy.resolve(policyRef, state.tenant(), ctx.valuationDate(), state.knowledgeCut());
        if (!PolicyMatrix.isDateRuleAllowedForLeg(policy.policy().leg(), policy.policy().dateRule())) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "dateRule " + policy.policy().dateRule() + " not allowed for leg " + policy.policy().leg());
        }
        if (policy.policy().leg() == Leg.ACCOUNTING_TRANSACTION && ctx.itemType() != null
                && !PolicyMatrix.isDateRuleAllowedForItemType(ctx.itemType(), policy.policy().dateRule())) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "dateRule " + policy.policy().dateRule() + " not allowed for itemType " + ctx.itemType());
        }

        ResolvedDates dates = dateRuleResolver.resolve(policy, state, ctx);
        ObservationSet obsSet = observationSetBuilder.build(dates, policy.policy().averaging());
        FilteredSources filtered = entitlementResolver.filter(policy.policy().rateSourcePriority(), dates.primary().resolvedDate(), state);
        List<com.power.fx.api.error.FxWarning> warnings = new ArrayList<>(filtered.warnings());
        ResolvedPolicy narrowed = policy.withSourcePriority(filtered.allowed().isEmpty() ? policy.policy().rateSourcePriority() : filtered.allowed());

        List<RateQuote> quotes = new ArrayList<>();
        for (Observation o : obsSet.observations()) {
            PairRoute route = pairResolver.route(from, to, narrowed, state, o.resolvedFxDate());
            quotes.add(rateSelector.select(route, o.resolvedFxDate(), ctx.valuationDate(), ctx.purpose(), narrowed, state));
        }
        SeriesOutcome outcome = averagingEngine.average(obsSet, quotes, null, amount, false, narrowed.policy().averaging(), fxMath);
        BookedAmount booked = precisionEngine.book(outcome.totalUnrounded(), to, narrowed.policy().rounding(), state);
        DistributionRestriction restriction = outcome.rights().toDistributionRestriction();
        Lineage lineage = lineageBuilder.build(new ResolvedContext(state, policy, ctx), from, to, amount, false);
        RateType rateType = quotes.get(0).rateType();
        ConversionResult result = new ConversionResult(from, to, amount, outcome.totalUnrounded(), booked.booked(),
                outcome.averageRate(), rateType, outcome.finality(), outcome.reasons(), List.of(), restriction,
                lineage, warnings, Optional.empty());
        return new LegOutcome(result, warnings);
    }

    private LegResult unresolvedLeg(Leg leg, FxRequestContext ctx, CurrencyCode from, CurrencyCode to, BigDecimal amount,
            FxException cause) {
        Lineage lineage = com.power.fx.core.lineage.MinimalLineage.of(null, null, null, null, "UNKNOWN", 0, ctx);
        ConversionResult failed = new ConversionResult(
                from == null ? new CurrencyCode("XXX") : from,
                to == null ? new CurrencyCode("XXX") : to,
                amount == null ? BigDecimal.ZERO : amount,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, RateType.FIXING, RateFinality.UNRESOLVED,
                List.of(FxReason.UPSTREAM_UNRESOLVED), List.of(),
                new DistributionRestriction(false, false, false, new java.util.TreeSet<>()), lineage, List.of(),
                Optional.of(cause.error()));
        return new LegResult(leg, ctx.purpose(), failed, null, null, false);
    }

    private LegResult upstreamUnresolvedLeg(Leg leg, FxRequestContext ctx) {
        Lineage lineage = com.power.fx.core.lineage.MinimalLineage.of(null, null, null, null, "UNKNOWN", 0, ctx);
        ConversionResult skipped = new ConversionResult(new CurrencyCode("XXX"), new CurrencyCode("XXX"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, RateType.FIXING, RateFinality.UNRESOLVED,
                List.of(FxReason.UPSTREAM_UNRESOLVED), List.of(),
                new DistributionRestriction(false, false, false, new java.util.TreeSet<>()), lineage, List.of(),
                Optional.empty());
        return new LegResult(leg, ctx.purpose(), skipped, null, null, false);
    }
}
