package com.power.fx.core.entitlement;

import java.util.List;

/**
 * The S6.6 derivation table: folds entitlement rights with intersection
 * (most-restrictive propagation, D-10) across every kind of derived rate.
 *
 * <pre>
 * Direct quote            -> rights of that source
 * Inverse quote           -> rights of that source (inversion adds no source)
 * Cross / triangulation   -> intersect of both legs' sources
 * Forward from points     -> intersect of spot source and the points source
 * Forward from CIP        -> intersect of spot source and both discount-curve sources
 * Average                 -> intersect over all observations
 * Fixed factor, contract rate, manual override, identity -> unrestricted
 * Leg chain               -> intersect over legs; exposed per leg AND chain-level
 * </pre>
 *
 * @see "Tech spec S6.6"
 */
public final class RestrictionPropagator {

    private RestrictionPropagator() {
    }

    public static RightsSet direct(RightsSet sourceRights) {
        return sourceRights;
    }

    public static RightsSet inverse(RightsSet sourceRights) {
        return sourceRights;
    }

    public static RightsSet cross(RightsSet leg1, RightsSet leg2) {
        return leg1.intersect(leg2);
    }

    public static RightsSet forwardFromPoints(RightsSet spotSource, RightsSet pointsSource) {
        return spotSource.intersect(pointsSource);
    }

    public static RightsSet forwardFromCip(RightsSet spotSource, RightsSet baseCurveSource, RightsSet quoteCurveSource) {
        return spotSource.intersect(baseCurveSource).intersect(quoteCurveSource);
    }

    public static RightsSet average(List<RightsSet> observationRights) {
        return fold(observationRights);
    }

    public static RightsSet legChain(List<RightsSet> legRights) {
        return fold(legRights);
    }

    public static RightsSet unrestricted() {
        return RightsSet.unrestricted();
    }

    private static RightsSet fold(List<RightsSet> rights) {
        if (rights.isEmpty()) {
            return RightsSet.unrestricted();
        }
        RightsSet result = rights.get(0);
        for (int i = 1; i < rights.size(); i++) {
            result = result.intersect(rights.get(i));
        }
        return result;
    }
}
