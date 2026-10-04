package com.power.fx.core.curve;

import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.Rational;
import com.power.fx.core.entitlement.RightsSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An immutable forward curve, built once per {@code (tenant,
 * marketSnapshotId, pair)} and cached inside the owning {@link
 * com.power.fx.core.snapshot.MarketSnapshot} (S6.9, S7.1.4).
 *
 * <p>Construction (POINTS / CIP / HYBRID) and interpolation
 * (LOG_LINEAR_CARRY / LINEAR_POINTS / MONOTONE_CUBIC_POINTS) are performed
 * by {@link ForwardCurveBuilder} and the {@code *Interpolator} classes;
 * this class is the immutable, queryable result plus its own bounded query
 * memo (S6.9.3).
 *
 * @see "Tech spec S6.9, S7.1.4"
 */
public final class ForwardCurve {

    private final CurrencyPair pair;
    private final String marketSnapshotId;
    private final ForwardMethod method;
    private final InterpolationMethod interpolation;
    private final BigDecimal spot;
    private final LocalDate spotDate;
    private final BigDecimal pointsScale;
    private final List<LocalDate> valueDates; // ascending
    private final List<BigDecimal> outrights; // parallel to valueDates
    private final List<Rational> t; // ACT/365F from spotDate, parallel
    private final List<BigDecimal> lnCarry; // ln(F_i/spot), LOG_LINEAR_CARRY only, parallel; else empty
    private final List<BigDecimal> cubicSlopes; // Hyman-filtered, MONOTONE_CUBIC_POINTS only; else empty
    private final BigDecimal onPoints; // nullable
    private final BigDecimal tnPoints; // nullable
    private final LocalDate maxExtrapolationDate;
    private final RightsSet sourceRights;
    private final DiscountCurve baseDiscountCurve; // nullable, CIP/HYBRID only
    private final DiscountCurve quoteDiscountCurve; // nullable, CIP/HYBRID only
    private final int memoMaxEntries;

    private final ConcurrentHashMap<LocalDate, BigDecimal> memo = new ConcurrentHashMap<>();

    public ForwardCurve(CurrencyPair pair, String marketSnapshotId, ForwardMethod method,
            InterpolationMethod interpolation, BigDecimal spot, LocalDate spotDate, BigDecimal pointsScale,
            List<LocalDate> valueDates, List<BigDecimal> outrights, List<Rational> t, List<BigDecimal> lnCarry,
            List<BigDecimal> cubicSlopes, BigDecimal onPoints, BigDecimal tnPoints, LocalDate maxExtrapolationDate,
            RightsSet sourceRights, DiscountCurve baseDiscountCurve, DiscountCurve quoteDiscountCurve,
            int memoMaxEntries) {
        this.pair = pair;
        this.marketSnapshotId = marketSnapshotId;
        this.method = method;
        this.interpolation = interpolation;
        this.spot = spot;
        this.spotDate = spotDate;
        this.pointsScale = pointsScale;
        this.valueDates = List.copyOf(valueDates);
        this.outrights = List.copyOf(outrights);
        this.t = List.copyOf(t);
        this.lnCarry = List.copyOf(lnCarry);
        this.cubicSlopes = List.copyOf(cubicSlopes);
        this.onPoints = onPoints;
        this.tnPoints = tnPoints;
        this.maxExtrapolationDate = maxExtrapolationDate;
        this.sourceRights = sourceRights;
        this.baseDiscountCurve = baseDiscountCurve;
        this.quoteDiscountCurve = quoteDiscountCurve;
        this.memoMaxEntries = memoMaxEntries;
    }

    public CurrencyPair pair() {
        return pair;
    }

    public String marketSnapshotId() {
        return marketSnapshotId;
    }

    public ForwardMethod method() {
        return method;
    }

    public InterpolationMethod interpolation() {
        return interpolation;
    }

    public BigDecimal spot() {
        return spot;
    }

    public LocalDate spotDate() {
        return spotDate;
    }

    public BigDecimal pointsScale() {
        return pointsScale;
    }

    public List<LocalDate> valueDates() {
        return valueDates;
    }

    public List<BigDecimal> outrights() {
        return outrights;
    }

    public List<Rational> t() {
        return t;
    }

    public List<BigDecimal> lnCarry() {
        return lnCarry;
    }

    public List<BigDecimal> cubicSlopes() {
        return cubicSlopes;
    }

    public BigDecimal onPoints() {
        return onPoints;
    }

    public BigDecimal tnPoints() {
        return tnPoints;
    }

    public LocalDate maxExtrapolationDate() {
        return maxExtrapolationDate;
    }

    public RightsSet sourceRights() {
        return sourceRights;
    }

    public DiscountCurve baseDiscountCurve() {
        return baseDiscountCurve;
    }

    public DiscountCurve quoteDiscountCurve() {
        return quoteDiscountCurve;
    }

    /** Bounded query memo (S6.9.3): refuses new entries past {@code memoMaxEntries} rather than evicting. */
    public BigDecimal memoizedOutright(LocalDate valueDate, java.util.function.Function<LocalDate, BigDecimal> compute) {
        BigDecimal cached = memo.get(valueDate);
        if (cached != null) {
            return cached;
        }
        BigDecimal computed = compute.apply(valueDate);
        if (memo.size() < memoMaxEntries) {
            memo.putIfAbsent(valueDate, computed);
        }
        return computed;
    }

    public int memoSize() {
        return memo.size();
    }

    /**
     * The query algorithm of S6.9: short end below spot; bracketed
     * interpolation per {@link #interpolation()}; flat-carry-rate
     * extrapolation beyond the last pillar up to {@link
     * #maxExtrapolationDate()}, then CIP if both discount curves exist,
     * else {@code FX_E_EXTRAPOLATION_LIMIT}. {@code valueDates} always
     * includes {@code spotDate} as its first entry (lnCarry/points = 0),
     * so "extrapolate before the first real pillar from spot with zero
     * carry" (S6.9 point 4) falls out of ordinary bracketed interpolation
     * rather than needing separate code.
     */
    public BigDecimal outright(LocalDate valueDate, com.power.fx.core.decimal.FxMath fxMath) {
        if (!valueDate.isAfter(spotDate)) {
            return com.power.fx.core.curve.ShortEndAdjuster.adjust(spot, spotDate, valueDate, onPoints, tnPoints,
                    pointsScale, fxMath);
        }
        return memoizedOutright(valueDate, vd -> computeAtOrBeyondSpot(vd, fxMath));
    }

    private BigDecimal computeAtOrBeyondSpot(LocalDate valueDate, com.power.fx.core.decimal.FxMath fxMath) {
        LocalDate last = valueDates.get(valueDates.size() - 1);
        if (!valueDate.isAfter(last)) {
            int hi = 0;
            while (valueDates.get(hi).isBefore(valueDate)) {
                hi++;
            }
            if (valueDates.get(hi).isEqual(valueDate)) {
                return outrights.get(hi);
            }
            int lo = hi - 1;
            Rational tQuery = com.power.fx.core.curve.DayCount.act365f(spotDate, valueDate);
            return switch (interpolation) {
                case LOG_LINEAR_CARRY -> com.power.fx.core.curve.LogLinearCarryInterpolator.interpolate(spot,
                        lnCarry.get(lo), lnCarry.get(hi), t.get(lo), t.get(hi), tQuery, fxMath);
                case LINEAR_POINTS -> {
                    BigDecimal pointsLo = outrights.get(lo).subtract(spot, fxMath.working()).multiply(pointsScale, fxMath.working());
                    BigDecimal pointsHi = outrights.get(hi).subtract(spot, fxMath.working()).multiply(pointsScale, fxMath.working());
                    yield com.power.fx.core.curve.PointsInterpolator.interpolate(spot, pointsScale, pointsLo, pointsHi,
                            t.get(lo), t.get(hi), tQuery, fxMath);
                }
                case MONOTONE_CUBIC_POINTS -> {
                    BigDecimal dt = t.get(hi).toDecimal(fxMath.working()).subtract(t.get(lo).toDecimal(fxMath.working()), fxMath.working());
                    BigDecimal u = tQuery.toDecimal(fxMath.working()).subtract(t.get(lo).toDecimal(fxMath.working()), fxMath.working())
                            .divide(dt, fxMath.working());
                    BigDecimal pLo = outrights.get(lo).subtract(spot, fxMath.working()).multiply(pointsScale, fxMath.working());
                    BigDecimal pHi = outrights.get(hi).subtract(spot, fxMath.working()).multiply(pointsScale, fxMath.working());
                    BigDecimal mLo = cubicSlopes.get(lo);
                    BigDecimal mHi = cubicSlopes.get(hi);
                    BigDecimal points = com.power.fx.core.curve.MonotoneCubicInterpolator.hermite(pLo, pHi, mLo, mHi, dt, u, fxMath);
                    yield spot.add(points.divide(pointsScale, fxMath.working()), fxMath.working());
                }
            };
        }
        // Beyond the last pillar: flat implied carry rate, scaled linearly in t, up to maxExtrapolationDate.
        if (!valueDate.isAfter(maxExtrapolationDate)) {
            BigDecimal tLast = t.get(t.size() - 1).toDecimal(fxMath.working());
            BigDecimal carryRatePerYear = tLast.signum() == 0
                    ? BigDecimal.ZERO
                    : lnCarry.get(lnCarry.size() - 1).divide(tLast, fxMath.working());
            Rational tQuery = com.power.fx.core.curve.DayCount.act365f(spotDate, valueDate);
            BigDecimal lnAtQuery = carryRatePerYear.multiply(tQuery.toDecimal(fxMath.working()), fxMath.working());
            return spot.multiply(fxMath.exp(lnAtQuery), fxMath.working());
        }
        if (baseDiscountCurve != null && quoteDiscountCurve != null) {
            return com.power.fx.core.curve.CipForwardCalculator.cip(spot, baseDiscountCurve, quoteDiscountCurve, valueDate, fxMath);
        }
        throw com.power.fx.core.FxErrors.of(com.power.fx.api.error.FxErrorCode.FX_E_EXTRAPOLATION_LIMIT,
                "value date " + valueDate + " is beyond the forward curve's extrapolation limit " + maxExtrapolationDate
                        + " for pair " + pair + " and no discount curves are available for CIP extrapolation");
    }
}
