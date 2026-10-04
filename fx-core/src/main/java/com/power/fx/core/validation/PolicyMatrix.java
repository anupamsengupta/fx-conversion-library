package com.power.fx.core.validation;

import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.FallbackStepKind;
import com.power.fx.api.model.ItemType;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.UsageClass;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Static, immutable rule tables for request/policy admissibility (FS S8.3,
 * S9.1, S9.2, S11.3, S11.5). Built once at class-init with a self-test
 * asserting totality over the relevant enum cross-products, so a new enum
 * constant fails the build fast instead of silently defaulting to
 * "allowed" (S6.4's binding requirement).
 *
 * @see "Tech spec S6.4; functional spec S8.3, S9.1, S9.2, S11.3, S11.5"
 */
public final class PolicyMatrix {

    /** 1. Rule x Leg (FS S8.3). */
    private static final Map<Leg, Set<DateRule>> RULE_BY_LEG = new EnumMap<>(Leg.class);

    /** 2/6. Purpose -> allowed legs, Purpose -> required AmountType. */
    private static final Map<Purpose, Set<Leg>> ALLOWED_LEGS = new EnumMap<>(Purpose.class);
    private static final Map<Purpose, AmountType> REQUIRED_AMOUNT_TYPE = new EnumMap<>(Purpose.class);

    /** 3. Purpose x ItemType (applies on the ACCOUNTING_TRANSACTION leg, FS S9.2). */
    private static final Map<ItemType, Set<DateRule>> DATE_RULE_BY_ITEM_TYPE = new EnumMap<>(ItemType.class);

    /** 4. Purpose -> forbidden fallback steps (FS S11.3). */
    private static final Map<Purpose, Set<FallbackStepKind>> FORBIDDEN_FALLBACK_STEPS = new EnumMap<>(Purpose.class);

    /** 5. Purpose -> disallowed usageClass (FS S11.5, vector X09). */
    private static final Map<Purpose, Set<UsageClass>> DISALLOWED_USAGE_CLASSES = new EnumMap<>(Purpose.class);

    static {
        RULE_BY_LEG.put(Leg.CONTRACT, EnumSet.of(
                DateRule.TRADE_DATE, DateRule.SPECIFIC_DATE, DateRule.PRICING_SET, DateRule.PAYMENT_DATE,
                DateRule.DELIVERY_DATE, DateRule.DELIVERY_DAYS, DateRule.EVENT));
        RULE_BY_LEG.put(Leg.ACCOUNTING_TRANSACTION, EnumSet.of(
                DateRule.RECOGNITION_DATE, DateRule.AVERAGE_RATE, DateRule.CLOSING_RATE, DateRule.SETTLEMENT_DATE,
                DateRule.VALUATION_DATE, DateRule.FAIR_VALUE_DATE));
        RULE_BY_LEG.put(Leg.TRANSLATION, EnumSet.of(
                DateRule.AVERAGE_RATE, DateRule.CLOSING_RATE, DateRule.HISTORICAL_RATE));
        RULE_BY_LEG.put(Leg.MANAGEMENT_VIEW, EnumSet.of(
                DateRule.AVERAGE_RATE, DateRule.CLOSING_RATE, DateRule.VALUATION_DATE));

        ALLOWED_LEGS.put(Purpose.CONTRACT_SETTLEMENT, EnumSet.of(Leg.CONTRACT));
        ALLOWED_LEGS.put(Purpose.UNREALISED_MTM,
                EnumSet.of(Leg.ACCOUNTING_TRANSACTION, Leg.TRANSLATION, Leg.MANAGEMENT_VIEW));
        ALLOWED_LEGS.put(Purpose.CASH_PROJECTION, EnumSet.of(Leg.CONTRACT, Leg.ACCOUNTING_TRANSACTION));
        ALLOWED_LEGS.put(Purpose.ACCOUNTING_RECOGNITION, EnumSet.of(Leg.ACCOUNTING_TRANSACTION));
        ALLOWED_LEGS.put(Purpose.ACCOUNTING_REVALUATION, EnumSet.of(Leg.ACCOUNTING_TRANSACTION));
        ALLOWED_LEGS.put(Purpose.ACCOUNTING_SETTLEMENT, EnumSet.of(Leg.ACCOUNTING_TRANSACTION));
        ALLOWED_LEGS.put(Purpose.TRANSLATION, EnumSet.of(Leg.TRANSLATION));
        ALLOWED_LEGS.put(Purpose.MANAGEMENT_VIEW, EnumSet.of(Leg.MANAGEMENT_VIEW));

        REQUIRED_AMOUNT_TYPE.put(Purpose.CONTRACT_SETTLEMENT, AmountType.NOMINAL);
        REQUIRED_AMOUNT_TYPE.put(Purpose.UNREALISED_MTM, AmountType.PRESENT_VALUE);
        REQUIRED_AMOUNT_TYPE.put(Purpose.CASH_PROJECTION, AmountType.NOMINAL_FUTURE);
        REQUIRED_AMOUNT_TYPE.put(Purpose.ACCOUNTING_RECOGNITION, AmountType.NOMINAL);
        REQUIRED_AMOUNT_TYPE.put(Purpose.ACCOUNTING_REVALUATION, AmountType.NOMINAL);
        REQUIRED_AMOUNT_TYPE.put(Purpose.ACCOUNTING_SETTLEMENT, AmountType.NOMINAL);
        REQUIRED_AMOUNT_TYPE.put(Purpose.TRANSLATION, AmountType.NOMINAL);
        REQUIRED_AMOUNT_TYPE.put(Purpose.MANAGEMENT_VIEW, null); // "Any" per FS 9.1

        DATE_RULE_BY_ITEM_TYPE.put(ItemType.MONETARY, EnumSet.of(
                DateRule.RECOGNITION_DATE, DateRule.AVERAGE_RATE, DateRule.CLOSING_RATE, DateRule.SETTLEMENT_DATE,
                DateRule.VALUATION_DATE));
        DATE_RULE_BY_ITEM_TYPE.put(ItemType.NON_MONETARY_HISTORICAL, EnumSet.of(
                DateRule.RECOGNITION_DATE, DateRule.AVERAGE_RATE));
        DATE_RULE_BY_ITEM_TYPE.put(ItemType.NON_MONETARY_FAIR_VALUE, EnumSet.of(DateRule.FAIR_VALUE_DATE));

        for (Purpose p : Purpose.values()) {
            Set<FallbackStepKind> forbidden = (p == Purpose.CONTRACT_SETTLEMENT
                    || p == Purpose.ACCOUNTING_RECOGNITION
                    || p == Purpose.ACCOUNTING_REVALUATION
                    || p == Purpose.ACCOUNTING_SETTLEMENT)
                    ? EnumSet.of(FallbackStepKind.INTERPOLATE_FIXINGS)
                    : EnumSet.noneOf(FallbackStepKind.class);
            FORBIDDEN_FALLBACK_STEPS.put(p, forbidden);
        }

        for (Purpose p : Purpose.values()) {
            Set<UsageClass> disallowed = (p == Purpose.CONTRACT_SETTLEMENT || p == Purpose.ACCOUNTING_SETTLEMENT)
                    ? EnumSet.of(UsageClass.MTM_ONLY)
                    : EnumSet.noneOf(UsageClass.class);
            DISALLOWED_USAGE_CLASSES.put(p, disallowed);
        }

        selfTestTotality();
    }

    private PolicyMatrix() {
    }

    private static void selfTestTotality() {
        for (Leg leg : Leg.values()) {
            if (!RULE_BY_LEG.containsKey(leg)) {
                throw new IllegalStateException("PolicyMatrix totality violation: no date-rule set for leg " + leg);
            }
        }
        for (Purpose p : Purpose.values()) {
            if (!ALLOWED_LEGS.containsKey(p)) {
                throw new IllegalStateException("PolicyMatrix totality violation: no allowed-legs entry for purpose " + p);
            }
            if (!REQUIRED_AMOUNT_TYPE.containsKey(p)) {
                throw new IllegalStateException("PolicyMatrix totality violation: no amount-type entry for purpose " + p);
            }
            if (!FORBIDDEN_FALLBACK_STEPS.containsKey(p)) {
                throw new IllegalStateException("PolicyMatrix totality violation: no fallback-step entry for purpose " + p);
            }
            if (!DISALLOWED_USAGE_CLASSES.containsKey(p)) {
                throw new IllegalStateException("PolicyMatrix totality violation: no usage-class entry for purpose " + p);
            }
        }
        for (ItemType it : ItemType.values()) {
            if (!DATE_RULE_BY_ITEM_TYPE.containsKey(it)) {
                throw new IllegalStateException("PolicyMatrix totality violation: no date-rule entry for item type " + it);
            }
        }
    }

    public static boolean isDateRuleAllowedForLeg(Leg leg, DateRule rule) {
        Set<DateRule> allowed = RULE_BY_LEG.get(leg);
        return allowed != null && allowed.contains(rule);
    }

    public static boolean isLegAllowedForPurpose(Purpose purpose, Leg leg) {
        Set<Leg> allowed = ALLOWED_LEGS.get(purpose);
        return allowed != null && allowed.contains(leg);
    }

    /** {@code null} means "any amount type accepted" (MANAGEMENT_VIEW). */
    public static AmountType requiredAmountType(Purpose purpose) {
        return REQUIRED_AMOUNT_TYPE.get(purpose);
    }

    public static boolean isDateRuleAllowedForItemType(ItemType itemType, DateRule rule) {
        Set<DateRule> allowed = DATE_RULE_BY_ITEM_TYPE.get(itemType);
        return allowed != null && allowed.contains(rule);
    }

    public static boolean isFallbackStepForbidden(Purpose purpose, FallbackStepKind kind) {
        Set<FallbackStepKind> forbidden = FORBIDDEN_FALLBACK_STEPS.get(purpose);
        return forbidden != null && forbidden.contains(kind);
    }

    public static boolean isUsageClassDisallowed(Purpose purpose, UsageClass usageClass) {
        Set<UsageClass> disallowed = DISALLOWED_USAGE_CLASSES.get(purpose);
        return disallowed != null && disallowed.contains(usageClass);
    }

    /** Read-only exported view for {@code fx-testkit}'s future generated {@code PolicyMatrixExhaustionTest}. */
    public static Set<Leg> allowedLegs(Purpose purpose) {
        return Set.copyOf(ALLOWED_LEGS.getOrDefault(purpose, Set.of()));
    }
}
