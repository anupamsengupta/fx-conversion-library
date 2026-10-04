package com.power.fx.testkit.vectors;

import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.ForwardMethod;
import com.power.fx.api.model.ForwardPillar;
import com.power.fx.api.model.InterpolationMethod;
import com.power.fx.api.model.PairConvention;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.core.curve.ForwardCurve;
import com.power.fx.core.curve.ForwardCurveBuilder;
import com.power.fx.core.decimal.DecimalExp;
import com.power.fx.core.decimal.DecimalLn;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.testkit.fixtures.DecimalReferenceTable;
import com.power.fx.testkit.fixtures.FxAssertions;
import com.power.fx.testkit.fixtures.GoldenReferenceData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vector X10 (G05's forward rate and the decimal digest reproduced
 * identically across the CI JVM matrix) and S10.5's cross-JVM determinism
 * requirement (Task 3b.8).
 *
 * <p><strong>TI-07 status, stated plainly:</strong> the formal,
 * release-blocking CI matrix (proposal: Temurin 21+25, Zulu 21, GraalVM
 * 21, OpenJ9 21, per the plan) is undefined without TI-07's answer. This
 * test is scaffolded to run against whatever JVM the local/CI environment
 * actually provides -- in this environment, exactly one vendor (reported
 * below) -- and therefore exercises the determinism <em>mechanism</em>
 * (the computation does not depend on JVM-specific double/float behaviour,
 * hash-order iteration, or {@code Locale}/{@code TimeZone} defaults) but
 * cannot itself prove cross-vendor agreement. The full gate remains open
 * until TI-07 is answered and a real multi-vendor CI matrix exists; this
 * is tracked, not silently waived.
 *
 * @see "Tech spec S12.1, S10.5, Appendix D.5 item 5; implementation plan Task 3b.8, TI-07"
 */
class JvmMatrixDeterminismTest {

    private static final MathContext WORKING = new MathContext(60, RoundingMode.HALF_EVEN);

    @Test
    void reportsTheSingleLocalJvmVendorUnderTest() {
        String vendor = System.getProperty("java.vendor");
        String version = System.getProperty("java.version");
        System.out.println("[Task 3b.8] JvmMatrixDeterminismTest running on: " + vendor + " " + version
                + " -- TI-07's full multi-vendor CI matrix is not yet defined; this is a single-vendor local check.");
        assertEquals(vendor, System.getProperty("java.vendor"), "sanity: property read is itself deterministic");
    }

    /** G05 reproduces its golden value on this JVM, bit-identical across repeated evaluation (D-09's determinism claim). */
    @Test
    void g05ForwardRateIsBitIdenticalOnThisJvm() {
        FxMath fxMath = new FxMath(60);
        CurrencyPair eurUsd = GoldenReferenceData.pair("EUR", "USD");
        SpotQuote spot = new SpotQuote(eurUsd, new BigDecimal("1.0850"), LocalDate.of(2026, 1, 5));
        ForwardPillar p3m = new ForwardPillar("3M", LocalDate.of(2026, 4, 7), new BigDecimal("35.0"), null);
        ForwardPillar p6m = new ForwardPillar("6M", LocalDate.of(2026, 7, 7), new BigDecimal("68.0"), null);
        MarketSnapshot snap = new MarketSnapshot("SNAP-X10", Scope.TENANT, GoldenReferenceData.TENANT, SnapshotKind.EOD,
                LocalDate.of(2026, 1, 2), Instant.parse("2026-01-02T18:00:00Z"), SignOffStatus.SIGNED_OFF,
                Map.of(eurUsd, spot), Map.of(eurUsd, List.of(p3m, p6m)), Map.of(), Map.of(eurUsd, RightsSet.unrestricted()));
        PairConvention conv = new PairConvention(GoldenReferenceData.globalEnvelope("EUR/USD", "EUR/USD-x10-v1"),
                eurUsd, 4, new BigDecimal("10000"), 2, List.of(), null, ForwardMethod.POINTS,
                InterpolationMethod.LOG_LINEAR_CARRY, new BigDecimal("2"), Map.of());

        ForwardCurve curve = new ForwardCurveBuilder(fxMath).build(eurUsd, conv, snap, 512);
        LocalDate target = LocalDate.of(2026, 1, 5).plusDays(120);
        BigDecimal first = curve.outright(target, fxMath);
        BigDecimal second = new ForwardCurveBuilder(fxMath).build(eurUsd, conv, snap, 512).outright(target, fxMath);

        assertEquals(0, first.compareTo(second), "G05 must be bit-identical across independent curve builds on this JVM");
        FxAssertions.assertDecimalEqualsAtScale("1.0895143", first, 7);
    }

    /** The decimal digest (Appendix D.5 item 4/5) reproduces identically on this JVM. */
    @Test
    void decimalDigestIsStableOnThisJvm() throws NoSuchAlgorithmException {
        String digest1 = computeDigest();
        String digest2 = computeDigest();
        assertEquals(digest1, digest2, "the decimal digest must be stable across repeated computation on this JVM");
    }

    private String computeDigest() throws NoSuchAlgorithmException {
        StringBuilder sb = new StringBuilder();
        for (DecimalReferenceTable.Entry e : DecimalReferenceTable.lnReference().entries()) {
            sb.append(DecimalLn.ln(e.argument(), WORKING).toPlainString());
        }
        for (DecimalReferenceTable.Entry e : DecimalReferenceTable.expReference().entries()) {
            sb.append(DecimalExp.exp(e.argument(), WORKING).toPlainString());
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
