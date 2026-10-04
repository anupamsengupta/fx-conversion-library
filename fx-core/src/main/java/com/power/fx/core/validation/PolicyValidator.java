package com.power.fx.core.validation;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.FxPolicy;
import com.power.fx.api.model.ItemType;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.core.FxErrors;
import com.power.fx.core.ResolvedPolicy;

import java.util.Map;

/**
 * Stage 4 of the pipeline (S6.2): validates a {@link ResolvedPolicy}
 * against its {@link FxRequestContext} using {@link PolicyMatrix} (FS
 * S8.3, S9.1, S9.2).
 *
 * @see "Tech spec S6.4"
 */
public final class PolicyValidator {

    public void validate(FxRequestContext context, ResolvedPolicy resolvedPolicy) {
        FxPolicy policy = resolvedPolicy.policy();
        Purpose purpose = context.purpose();
        Leg leg = policy.leg();

        if (!PolicyMatrix.isLegAllowedForPurpose(purpose, leg)) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "leg " + leg + " is not allowed for purpose " + purpose,
                    Map.of("leg", leg.name(), "purpose", purpose.name()));
        }

        if (!PolicyMatrix.isDateRuleAllowedForLeg(leg, policy.dateRule())) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "dateRule " + policy.dateRule() + " is not allowed for leg " + leg,
                    Map.of("leg", leg.name(), "dateRule", policy.dateRule().name()));
        }

        ItemType itemType = context.itemType();
        if (leg == Leg.ACCOUNTING_TRANSACTION && itemType != null) {
            if (!PolicyMatrix.isDateRuleAllowedForItemType(itemType, policy.dateRule())) {
                throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                        "dateRule " + policy.dateRule() + " is not allowed for itemType " + itemType,
                        Map.of("itemType", itemType.name(), "dateRule", policy.dateRule().name()));
            }
        }

        AmountType required = PolicyMatrix.requiredAmountType(purpose);
        if (required != null && context.amountType() != required) {
            throw FxErrors.of(FxErrorCode.FX_V_AMOUNT_TYPE_MISMATCH,
                    "purpose " + purpose + " requires amountType " + required + " but got " + context.amountType(),
                    Map.of("purpose", purpose.name(), "required", required.name(),
                            "actual", String.valueOf(context.amountType())));
        }
    }
}
