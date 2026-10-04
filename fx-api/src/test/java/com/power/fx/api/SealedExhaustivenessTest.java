package com.power.fx.api;

import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.RateFinality;
import com.power.fx.api.model.RateType;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.SettlementAmountState;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.request.ChainRequest;
import com.power.fx.api.request.ConversionRequest;
import com.power.fx.api.request.FxRequest;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.request.MonetaryRevaluationRequest;
import com.power.fx.api.request.PolicyRef;
import com.power.fx.api.request.RateRequest;
import com.power.fx.api.request.SeriesRequest;
import com.power.fx.api.result.ChainResult;
import com.power.fx.api.result.ConversionResult;
import com.power.fx.api.result.DistributionRestriction;
import com.power.fx.api.result.FxResult;
import com.power.fx.api.result.Lineage;
import com.power.fx.api.result.RateResult;
import com.power.fx.api.result.ReplayableRequest;
import com.power.fx.api.result.RevaluationResult;
import com.power.fx.api.result.SeriesResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Partial, Phase-1 version of {@code SealedExhaustivenessTest} (S12.1).
 *
 * <p><strong>Scope of this version:</strong> this class manually exercises
 * exhaustive {@code switch} expressions over every sealed type declared
 * in {@code fx-api} ({@link FxRequest}, {@link FxResult}, {@link
 * PolicyRef}, {@link com.power.fx.api.model.FixingVersionSelection}) and
 * asserts each permit dispatches correctly. Because none of these
 * switches has a {@code default} branch, this file fails to
 * <em>compile</em> -- not merely fails a test -- the moment a sealed
 * permit is added or removed without a matching update here, which is
 * the mechanical exhaustiveness guarantee S12.1 asks for at the
 * language level.
 *
 * <p>What this version does <strong>not</strong> do, per the
 * implementation plan's Phase 1 acceptance gate: S12.1 places this test
 * class's full intent -- checking the <em>testkit's own</em> exhaustive
 * switches, e.g. inside {@code VectorRunner} and the golden-vector test
 * suites -- against {@code fx-testkit}, which does not exist until Phase
 * 3b. That verification is deferred and re-run there; it is not silently
 * considered closed by this class.
 */
class SealedExhaustivenessTest {

    private static final CurrencyCode EUR = new CurrencyCode("EUR");
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Test
    void fxRequest_everyPermitDispatchesExhaustively() {
        PolicyRef policy = new PolicyRef.ById("POLICY-1");
        FxRequestContext context = minimalContext(policy);

        assertEquals("RATE", describe(new RateRequest(context, EUR, USD)));
        assertEquals("CONVERSION", describe(new ConversionRequest(context, EUR, USD, BigDecimal.TEN, false)));
        assertEquals("SERIES", describe(new SeriesRequest(context, EUR, USD, null, false)));
        assertEquals("CHAIN", describe(
                new ChainRequest(context, EUR, BigDecimal.TEN, USD, policy, policy, policy, null, null)));
        assertEquals("REVALUATION", describe(new MonetaryRevaluationRequest(
                context, EUR, BigDecimal.TEN, BigDecimal.ONE, null, DateRule.CLOSING_RATE)));
    }

    @Test
    void policyRef_everyPermitDispatchesExhaustively() {
        assertEquals("BY_ID", describe(new PolicyRef.ById("POLICY-1")));
    }

    @Test
    void fixingVersionSelection_everyPermitDispatchesExhaustively() {
        assertEquals("FIRST_OFFICIAL",
                describe(new com.power.fx.api.model.FixingVersionSelection.FirstOfficial()));
        assertEquals("LATEST_CORRECTED",
                describe(new com.power.fx.api.model.FixingVersionSelection.LatestCorrected()));
        assertEquals("AS_OF_KNOWLEDGE", describe(
                new com.power.fx.api.model.FixingVersionSelection.AsOfKnowledge(Instant.EPOCH)));
    }

    @Test
    void fxResult_everyPermitDispatchesExhaustively() {
        Lineage lineage = minimalLineage();
        DistributionRestriction restriction = new DistributionRestriction(true, true, true, null);

        RateResult rate = new RateResult(
                EUR, USD, BigDecimal.ONE, RateType.SPOT, RateFinality.CONFIRMED,
                null, null, restriction, lineage, null, Optional.empty());
        ConversionResult conversion = new ConversionResult(
                EUR, USD, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE,
                RateType.SPOT, RateFinality.CONFIRMED, null, null, restriction, lineage, null, Optional.empty());
        SeriesResult series = new SeriesResult(
                EUR, USD, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, null, null, null,
                null, null, RateFinality.CONFIRMED, null, restriction, lineage, null, Optional.empty());
        ChainResult chain = new ChainResult(
                Map.of(), null, RateFinality.CONFIRMED, lineage, null, Optional.empty());
        RevaluationResult revaluation = new RevaluationResult(
                EUR, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, BigDecimal.ONE,
                RateFinality.CONFIRMED, null, lineage, null, Optional.empty());

        assertEquals("RATE", describe((FxResult) rate));
        assertEquals("CONVERSION", describe((FxResult) conversion));
        assertEquals("SERIES", describe((FxResult) series));
        assertEquals("CHAIN", describe((FxResult) chain));
        assertEquals("REVALUATION", describe((FxResult) revaluation));
    }

    // ---- exhaustive dispatch helpers (no default branch, by design) ----

    private static String describe(FxRequest request) {
        return switch (request) {
            case RateRequest r -> "RATE";
            case ConversionRequest r -> "CONVERSION";
            case SeriesRequest r -> "SERIES";
            case ChainRequest r -> "CHAIN";
            case MonetaryRevaluationRequest r -> "REVALUATION";
        };
    }

    private static String describe(PolicyRef policyRef) {
        return switch (policyRef) {
            case PolicyRef.ById byId -> "BY_ID";
            case PolicyRef.Inline inline -> "INLINE";
        };
    }

    private static String describe(com.power.fx.api.model.FixingVersionSelection selection) {
        return switch (selection) {
            case com.power.fx.api.model.FixingVersionSelection.FirstOfficial fo -> "FIRST_OFFICIAL";
            case com.power.fx.api.model.FixingVersionSelection.LatestCorrected lc -> "LATEST_CORRECTED";
            case com.power.fx.api.model.FixingVersionSelection.AsOfKnowledge aok -> "AS_OF_KNOWLEDGE";
        };
    }

    private static String describe(FxResult result) {
        return switch (result) {
            case RateResult r -> "RATE";
            case ConversionResult r -> "CONVERSION";
            case SeriesResult r -> "SERIES";
            case ChainResult r -> "CHAIN";
            case RevaluationResult r -> "REVALUATION";
        };
    }

    private static FxRequestContext minimalContext(PolicyRef policy) {
        return new FxRequestContext(
                Purpose.CONTRACT_SETTLEMENT,
                LocalDate.of(2026, 1, 1),
                null,
                RunMode.AD_HOC,
                null,
                AmountType.NOMINAL,
                SettlementAmountState.UNINVOICED,
                null,
                null,
                null,
                null,
                null,
                null,
                policy,
                "req-1");
    }

    private static Lineage minimalLineage() {
        ReplayableRequest replayKey = new ReplayableRequest(
                Purpose.CONTRACT_SETTLEMENT, null, LocalDate.of(2026, 1, 1), null, null, null,
                null, null, null, null, false, new PolicyRef.ById("POLICY-1"),
                null, null, null, null, null);
        return new Lineage(
                "TN_0042", "SNAP-1", Instant.EPOCH, 1L, 1L, SignOffStatus.SIGNED_OFF,
                "POLICY-1", 1, null, "1.0.0-TEST", "1.0", null, null, replayKey, () -> "{}");
    }
}
