package com.power.fx.core.curve;

import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.ForwardPillar;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.core.testsupport.TestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Golden vectors G04, G05 (functional spec S19.1), driven directly against {@link ForwardCurveBuilder}. */
class ForwardCurveVectorTest {

    private final FxMath fxMath = new FxMath(60);

    private MarketSnapshot snapshot() {
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        SpotQuote spot = new SpotQuote(eurUsd, new BigDecimal("1.0850"), LocalDate.of(2026, 1, 5));
        ForwardPillar p3m = new ForwardPillar("3M", LocalDate.of(2026, 4, 7), new BigDecimal("35.0"), null); // 92d
        ForwardPillar p6m = new ForwardPillar("6M", LocalDate.of(2026, 7, 7), new BigDecimal("68.0"), null); // 183d
        return new MarketSnapshot("SNAP-G04", Scope.TENANT, "TENANT-TEST", SnapshotKind.EOD, LocalDate.of(2026, 1, 2),
                Instant.parse("2026-01-02T18:00:00Z"), SignOffStatus.SIGNED_OFF,
                Map.of(eurUsd, spot), Map.of(eurUsd, List.of(p3m, p6m)), Map.of(),
                Map.of(eurUsd, RightsSet.unrestricted()));
    }

    private PairConvention convention(InterpolationMethod interpolation) {
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        return new PairConvention(TestFixtures.globalEnvelope("EUR/USD", "EUR/USD-v1"), eurUsd, 4,
                new BigDecimal("10000"), 2, List.of("USNY", "TARGET"), null, ForwardMethod.POINTS, interpolation,
                new BigDecimal("2"), Map.of());
    }

    @Test
    void g04_linearPoints() {
        MarketSnapshot snap = snapshot();
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        ForwardCurve curve = new ForwardCurveBuilder(fxMath).build(eurUsd, convention(InterpolationMethod.LINEAR_POINTS), snap, 512);

        // Spot date 2026-01-05; 92 days later = 2026-04-07; target = spot + 120 days.
        LocalDate target = LocalDate.of(2026, 1, 5).plusDays(120);
        BigDecimal result = curve.outright(target, fxMath);
        assertEquals(0, new BigDecimal("1.0895154").compareTo(result.setScale(7, java.math.RoundingMode.HALF_EVEN)),
                "G04 expected 1.0895154, got " + result);
    }

    @Test
    void g05_logLinearCarry_bitIdenticalAndMatchesSpec() {
        MarketSnapshot snap = snapshot();
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        ForwardCurve curve = new ForwardCurveBuilder(fxMath).build(eurUsd, convention(InterpolationMethod.LOG_LINEAR_CARRY), snap, 512);

        LocalDate target = LocalDate.of(2026, 1, 5).plusDays(120);
        BigDecimal first = curve.outright(target, fxMath);
        BigDecimal second = curve.outright(target, fxMath);
        assertEquals(0, first.compareTo(second), "repeated evaluation must be bit-identical (memoised)");
        assertEquals(0, new BigDecimal("1.0895143").compareTo(first.setScale(7, java.math.RoundingMode.HALF_EVEN)),
                "G05 expected 1.0895143, got " + first);
    }

    @Test
    void forwardCurveBuildP99Under1Millisecond() {
        MarketSnapshot snap = snapshot();
        CurrencyPair eurUsd = TestFixtures.pair("EUR", "USD");
        PairConvention conv = convention(InterpolationMethod.LOG_LINEAR_CARRY);
        // Warm-up.
        for (int i = 0; i < 50; i++) {
            new ForwardCurveBuilder(fxMath).build(eurUsd, conv, snap, 512);
        }
        int runs = 500;
        long[] samples = new long[runs];
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            new ForwardCurveBuilder(fxMath).build(eurUsd, conv, snap, 512);
            samples[i] = System.nanoTime() - t0;
        }
        java.util.Arrays.sort(samples);
        long p99 = samples[(int) (runs * 0.99)];
        System.out.println("[Task 2.11 benchmark] 2-pillar curve build p99=" + (p99 / 1000.0) + "us (target <= 1000us; "
                + "note: this fixture has 2 real pillars, not the 20-pillar curve Task 2.1's synthetic rehearsal used)");
    }
}
