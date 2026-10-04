package com.power.fx.core.leg;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.FxDifferenceClass;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.MonetaryRevaluationRequest;
import com.power.fx.api.result.Lineage;
import com.power.fx.api.result.RevaluationResult;
import com.power.fx.core.FxErrors;
import com.power.fx.core.ResolvedContext;
import com.power.fx.core.ResolvedPolicy;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.lineage.LineageBuilder;
import com.power.fx.core.pair.PairResolver;
import com.power.fx.core.pair.PairRoute;
import com.power.fx.core.precision.BookedAmount;
import com.power.fx.core.precision.PrecisionEngine;
import com.power.fx.core.rate.RateQuote;
import com.power.fx.core.rate.RateSelector;
import com.power.fx.core.snapshot.PinnedState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code revalue()} (FS S9.3, D-04). Signed throughout, so payables and
 * receivables come out correct with no special cases (vectors F03, F04).
 *
 * @see "Tech spec S6.14"
 */
public final class DefaultRevaluationEngine implements RevaluationEngine {

    private final PairResolver pairResolver;
    private final RateSelector rateSelector;
    private final PrecisionEngine precisionEngine;
    private final LineageBuilder lineageBuilder;
    private final FunctionalCurrencyResolver functionalCurrencyResolver;
    private final FxMath fxMath;

    public DefaultRevaluationEngine(PairResolver pairResolver, RateSelector rateSelector,
            PrecisionEngine precisionEngine, LineageBuilder lineageBuilder,
            FunctionalCurrencyResolver functionalCurrencyResolver, FxMath fxMath) {
        this.pairResolver = pairResolver;
        this.rateSelector = rateSelector;
        this.precisionEngine = precisionEngine;
        this.lineageBuilder = lineageBuilder;
        this.functionalCurrencyResolver = functionalCurrencyResolver;
        this.fxMath = fxMath;
    }

    @Override
    public RevaluationResult revalue(MonetaryRevaluationRequest request, PinnedState state) {
        FxRequestContext ctx = request.context();
        if (request.rule() != DateRule.CLOSING_RATE && request.rule() != DateRule.SETTLEMENT_DATE) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "revalue() only accepts CLOSING_RATE or SETTLEMENT_DATE, got " + request.rule());
        }
        LocalDate asOfDate = request.rule() == DateRule.CLOSING_RATE
                ? ctx.valuationDate()
                : requireSettlementDate(ctx);

        CurrencyCode functionalCurrency = functionalCurrencyResolver.resolve(ctx.accountingUnitId(), asOfDate,
                state.tenant(), state.knowledgeCut());

        ResolvedPolicy policy = ResolvedPolicy.resolve(ctx.policy(), state.tenant(), ctx.valuationDate(), state.knowledgeCut());
        PairRoute route = pairResolver.route(request.foreignCurrency(), functionalCurrency, policy, state, asOfDate);
        RateQuote quote = rateSelector.select(route, asOfDate, ctx.valuationDate(), ctx.purpose(), policy, state);

        BigDecimal newFunctionalUnrounded = request.signedForeignAmount().multiply(quote.rate(), fxMath.working())
                .round(FxMath.DECIMAL128);
        BookedAmount booked = precisionEngine.book(newFunctionalUnrounded, functionalCurrency, policy.policy().rounding(), state);

        BigDecimal carrying = request.carryingFunctionalAmount() != null
                ? request.carryingFunctionalAmount()
                : request.signedForeignAmount().multiply(request.carryingRate(), fxMath.working());

        BigDecimal difference = newFunctionalUnrounded.subtract(carrying, FxMath.DECIMAL128);
        FxDifferenceClass classification = request.rule() == DateRule.CLOSING_RATE
                ? FxDifferenceClass.UNREALISED_FX_PNL
                : FxDifferenceClass.REALISED_FX_PNL;

        Lineage lineage = lineageBuilder.build(new ResolvedContext(state, policy, ctx), request.foreignCurrency(),
                functionalCurrency, request.signedForeignAmount(), false);

        return new RevaluationResult(functionalCurrency, newFunctionalUnrounded, booked.booked(), carrying,
                difference, classification, quote.rate(), quote.finality(), quote.path(), lineage, List.of(),
                Optional.empty());
    }

    private static LocalDate requireSettlementDate(FxRequestContext ctx) {
        if (ctx.accountingDates() == null || ctx.accountingDates().settlementDate() == null) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY, "SETTLEMENT_DATE revaluation requires accountingDates.settlementDate");
        }
        return ctx.accountingDates().settlementDate();
    }
}
