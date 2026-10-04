package com.power.fx.core.curve;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.ForwardPillar;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.Rational;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.core.FxErrors;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.snapshot.MarketSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a {@link ForwardCurve} once per {@code (tenant, marketSnapshotId,
 * pair)} (S6.9). {@code spotDate} is always pillar index 0 (lnCarry/points
 * = 0), so "extrapolate before the first pillar from spot with zero
 * carry" falls out of ordinary interpolation.
 */
public final class ForwardCurveBuilder {

    private final FxMath fxMath;

    public ForwardCurveBuilder(FxMath fxMath) {
        this.fxMath = fxMath;
    }

    public ForwardCurve build(CurrencyPair pair, PairConvention convention, MarketSnapshot snapshot,
            int memoMaxEntries) {
        SpotQuote spotQuote = snapshot.spot(pair)
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                        "no spot quote loaded for pair " + pair, "pair", pair.canonical()));
        BigDecimal spot = spotQuote.rate();
        LocalDate spotDate = spotQuote.spotDate();
        BigDecimal pointsScale = convention.pointsScale();
        ForwardMethod method = convention.forwardMethod();
        InterpolationMethod interpolation = convention.interpolation();

        List<ForwardPillar> pillars = new ArrayList<>(snapshot.pillars(pair));
        pillars.removeIf(p -> p.tenor().equalsIgnoreCase("ON") || p.tenor().equalsIgnoreCase("TN"));

        BigDecimal onPoints = findTenorPoints(snapshot.pillars(pair), "ON");
        BigDecimal tnPoints = findTenorPoints(snapshot.pillars(pair), "TN");

        List<LocalDate> valueDates = new ArrayList<>();
        List<BigDecimal> outrights = new ArrayList<>();
        List<Rational> t = new ArrayList<>();
        List<BigDecimal> lnCarry = new ArrayList<>();

        valueDates.add(spotDate);
        outrights.add(spot);
        t.add(Rational.ZERO);
        lnCarry.add(BigDecimal.ZERO);

        Optional<com.power.fx.core.curve.DiscountCurve> baseCurve = snapshot.discountCurve(pair.base());
        Optional<com.power.fx.core.curve.DiscountCurve> quoteCurve = snapshot.discountCurve(pair.quote());

        BigDecimal lastOutright = spot;
        LocalDate lastDate = spotDate;
        for (ForwardPillar p : pillars) {
            BigDecimal outright = switch (method) {
                case POINTS -> p.outright() != null ? p.outright()
                        : spot.add(p.points().divide(pointsScale, fxMath.working()), fxMath.working());
                case CIP -> CipForwardCalculator.cip(spot, baseCurve.orElseThrow(curveMissing(pair)),
                        quoteCurve.orElseThrow(curveMissing(pair)), p.valueDate(), fxMath);
                case HYBRID -> CipForwardCalculator.hybridAnchored(lastOutright, lastDate,
                        baseCurve.orElseThrow(curveMissing(pair)), quoteCurve.orElseThrow(curveMissing(pair)),
                        p.valueDate(), fxMath);
            };
            valueDates.add(p.valueDate());
            outrights.add(outright);
            Rational ti = DayCount.act365f(spotDate, p.valueDate());
            t.add(ti);
            lnCarry.add(fxMath.ln(outright.divide(spot, fxMath.working())));
            lastOutright = outright;
            lastDate = p.valueDate();
        }

        List<BigDecimal> cubicSlopes = List.of();
        if (interpolation == InterpolationMethod.MONOTONE_CUBIC_POINTS && valueDates.size() > 1) {
            List<BigDecimal> pointsList = new ArrayList<>();
            for (int i = 0; i < outrights.size(); i++) {
                pointsList.add(outrights.get(i).subtract(spot, fxMath.working()).multiply(pointsScale, fxMath.working()));
            }
            List<BigDecimal> secants = MonotoneCubicInterpolator.secants(t, pointsList, fxMath);
            List<BigDecimal> rawSlopes = MonotoneCubicInterpolator.initialSlopes(secants, fxMath);
            cubicSlopes = HymanFilter.filter(secants, rawSlopes, fxMath);
        }

        LocalDate maxExtrapolationDate = lastDate.plusDays(
                convention.maxExtrapolationYears().multiply(BigDecimal.valueOf(365), fxMath.working()).intValue());

        RightsSet rights = snapshot.sourceRights(pair);

        return new ForwardCurve(pair, snapshot.marketSnapshotId(), method, interpolation, spot, spotDate, pointsScale,
                valueDates, outrights, t, lnCarry, cubicSlopes, onPoints, tnPoints, maxExtrapolationDate, rights,
                baseCurve.orElse(null), quoteCurve.orElse(null), memoMaxEntries);
    }

    private static java.util.function.Supplier<RuntimeException> curveMissing(CurrencyPair pair) {
        return () -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                "CIP/HYBRID forward requires discount curves for both legs of " + pair, "pair", pair.canonical());
    }

    private static BigDecimal findTenorPoints(List<ForwardPillar> pillars, String tenor) {
        for (ForwardPillar p : pillars) {
            if (p.tenor().equalsIgnoreCase(tenor)) {
                return p.points();
            }
        }
        return null;
    }
}
