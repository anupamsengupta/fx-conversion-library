package com.power.fx.core.averaging;

import com.power.fx.api.model.Rational;
import com.power.fx.api.model.Weighting;
import com.power.fx.core.date.ResolvedDates;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves per-observation weights (S6.11): {@code FROM_PRICING_SET} uses
 * the PDR {@link Rational} weights carried on each {@link
 * ResolvedDates.DateEntry} exactly; {@code EQUAL} divides evenly;
 * {@code VOLUME} weights by {@link ResolvedDates.DateEntry#volume()}
 * exactly as a rational; {@code CUSTOM} is not implemented (no golden
 * vector exercises it and {@code AveragingSpec} carries no custom-weight
 * list field to resolve it against).
 */
public final class WeightResolver {

    private WeightResolver() {
    }

    public static List<Rational> resolve(Weighting weighting, List<ResolvedDates.DateEntry> entries) {
        return switch (weighting) {
            case FROM_PRICING_SET -> entries.stream().map(ResolvedDates.DateEntry::weight).toList();
            case EQUAL -> {
                Rational w = Rational.of(1, entries.size());
                List<Rational> result = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                    result.add(w);
                }
                yield result;
            }
            case VOLUME -> {
                List<Rational> volumes = entries.stream().map(e -> toRational(e.volume())).toList();
                Rational total = Rational.ZERO;
                for (Rational v : volumes) {
                    total = total.plus(v);
                }
                final Rational finalTotal = total;
                yield volumes.stream().map(v -> v.dividedBy(finalTotal)).toList();
            }
            case CUSTOM -> throw new UnsupportedOperationException(
                    "Weighting.CUSTOM is not implemented: AveragingSpec carries no custom-weight list to resolve "
                            + "it against, and no golden vector in this phase exercises it");
        };
    }

    private static Rational toRational(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("VOLUME weighting requires a volume on every observation");
        }
        BigInteger unscaled = value.unscaledValue();
        int scale = value.scale();
        if (scale >= 0) {
            return new Rational(unscaled, BigInteger.TEN.pow(scale));
        }
        return new Rational(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
    }
}
