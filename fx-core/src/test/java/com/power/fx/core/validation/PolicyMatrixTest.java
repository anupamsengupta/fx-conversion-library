package com.power.fx.core.validation;

import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.FallbackStepKind;
import com.power.fx.api.model.ItemType;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.UsageClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyMatrixTest {

    @Test
    void classInitSelfTestPasses() {
        // Loading the class runs the totality self-test in its static initializer;
        // reaching this line without an exception is the assertion.
        assertTrue(PolicyMatrix.isLegAllowedForPurpose(Purpose.CONTRACT_SETTLEMENT, Leg.CONTRACT));
    }

    @Test
    void vectorF07_mtmRequiresPresentValue() {
        assertEquals(AmountType.PRESENT_VALUE, PolicyMatrix.requiredAmountType(Purpose.UNREALISED_MTM));
    }

    @Test
    void vectorF09_nonMonetaryHistoricalRejectsClosingRate() {
        assertFalse(PolicyMatrix.isDateRuleAllowedForItemType(ItemType.NON_MONETARY_HISTORICAL, DateRule.CLOSING_RATE));
        assertTrue(PolicyMatrix.isDateRuleAllowedForItemType(ItemType.NON_MONETARY_HISTORICAL, DateRule.RECOGNITION_DATE));
    }

    @Test
    void vectorX09_internalEodMtmOnlyRejectedForContractAndAccountingSettlement() {
        assertTrue(PolicyMatrix.isUsageClassDisallowed(Purpose.CONTRACT_SETTLEMENT, UsageClass.MTM_ONLY));
        assertTrue(PolicyMatrix.isUsageClassDisallowed(Purpose.ACCOUNTING_SETTLEMENT, UsageClass.MTM_ONLY));
        assertFalse(PolicyMatrix.isUsageClassDisallowed(Purpose.UNREALISED_MTM, UsageClass.MTM_ONLY));
    }

    @Test
    void interpolateFixingsForbiddenForContractAndAccountingPurposes() {
        assertTrue(PolicyMatrix.isFallbackStepForbidden(Purpose.CONTRACT_SETTLEMENT, FallbackStepKind.INTERPOLATE_FIXINGS));
        assertTrue(PolicyMatrix.isFallbackStepForbidden(Purpose.ACCOUNTING_RECOGNITION, FallbackStepKind.INTERPOLATE_FIXINGS));
        assertTrue(PolicyMatrix.isFallbackStepForbidden(Purpose.ACCOUNTING_REVALUATION, FallbackStepKind.INTERPOLATE_FIXINGS));
        assertTrue(PolicyMatrix.isFallbackStepForbidden(Purpose.ACCOUNTING_SETTLEMENT, FallbackStepKind.INTERPOLATE_FIXINGS));
        assertFalse(PolicyMatrix.isFallbackStepForbidden(Purpose.CASH_PROJECTION, FallbackStepKind.INTERPOLATE_FIXINGS));
    }

    @Test
    void dateRulesByLeg() {
        assertTrue(PolicyMatrix.isDateRuleAllowedForLeg(Leg.CONTRACT, DateRule.PAYMENT_DATE));
        assertFalse(PolicyMatrix.isDateRuleAllowedForLeg(Leg.CONTRACT, DateRule.CLOSING_RATE));
        assertTrue(PolicyMatrix.isDateRuleAllowedForLeg(Leg.ACCOUNTING_TRANSACTION, DateRule.CLOSING_RATE));
        assertTrue(PolicyMatrix.isDateRuleAllowedForLeg(Leg.TRANSLATION, DateRule.HISTORICAL_RATE));
        assertTrue(PolicyMatrix.isDateRuleAllowedForLeg(Leg.MANAGEMENT_VIEW, DateRule.VALUATION_DATE));
    }
}
