package com.power.fx.core.cache;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixingStatus;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.FixingVersionSelection;
import com.power.fx.api.model.Scope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 2.3 acceptance: insert v1, supersede with v2 at t2, as-of-t1 returns
 * v1, as-of-t2 returns v2, as-of-now returns v2 -- the bitemporal
 * correctness template cited by the plan (Task 2.3's own acceptance
 * criteria, S12.1's {@code BitemporalTest}).
 *
 * <p>Also exercises vectors X01/X02/X03 at the {@code FixingSeries} level
 * directly (the ECB 2-Apr OFFICIAL/CORRECTED scenario): {@code
 * FirstOfficial} stays immune to the correction; {@code LatestCorrected}
 * picks up the correction only once the pinned cut is past its {@code
 * recordedAt}.
 */
class FixingSeriesBitemporalTest {

    private static final CurrencyPair EURUSD = new CurrencyPair(new CurrencyCode("EUR"), new CurrencyCode("USD"));

    @Test
    void insertSupersedeAndAsOfQueries() {
        LocalDate fixingDate = LocalDate.of(2026, 4, 2);
        Instant t1 = Instant.parse("2026-04-02T14:20:00Z");
        Instant t2 = Instant.parse("2026-04-03T09:00:00Z");
        Instant now = Instant.parse("2026-04-10T00:00:00Z");

        FixingVersion v1 = fixing(fixingDate, "v1", t1, FixingStatus.OFFICIAL, new BigDecimal("1.0800"), null);
        FixingVersion v2 = fixing(fixingDate, "v2", t2, FixingStatus.CORRECTED, new BigDecimal("1.0810"), "v1");

        FixingSeries series = FixingSeries.of(List.of(v1));
        FixingSeries superseded = series.withAdded(List.of(v2));

        // As-of t1 (before v2 was recorded): only v1 is visible.
        Optional<FixingSeries.Resolution> asOfT1 = superseded.resolve(fixingDate, t1, new FixingVersionSelection.LatestCorrected());
        assertTrue(asOfT1.isPresent());
        assertEquals("v1", asOfT1.get().entry().versionId());
        assertEquals(0, new BigDecimal("1.0800").compareTo(asOfT1.get().entry().value()));

        // As-of t2 (v2 now visible): LatestCorrected picks it up.
        Optional<FixingSeries.Resolution> asOfT2 = superseded.resolve(fixingDate, t2, new FixingVersionSelection.LatestCorrected());
        assertTrue(asOfT2.isPresent());
        assertEquals("v2", asOfT2.get().entry().versionId());
        assertEquals(0, new BigDecimal("1.0810").compareTo(asOfT2.get().entry().value()));

        // As-of "now" (long after): still v2, stable.
        Optional<FixingSeries.Resolution> asOfNow = superseded.resolve(fixingDate, now, new FixingVersionSelection.LatestCorrected());
        assertTrue(asOfNow.isPresent());
        assertEquals("v2", asOfNow.get().entry().versionId());
    }

    @Test
    void vectorX01_firstOfficialImmuneToLaterCorrection() {
        LocalDate fixingDate = LocalDate.of(2026, 4, 2);
        Instant recordedOfficial = Instant.parse("2026-04-02T14:20:00Z");
        Instant recordedCorrected = Instant.parse("2026-04-03T10:00:00Z");
        Instant cutAfterCorrection = Instant.parse("2026-04-05T00:00:00Z");

        FixingVersion official = fixing(fixingDate, "official-1", recordedOfficial, FixingStatus.OFFICIAL,
                new BigDecimal("1.0800"), null);
        FixingVersion corrected = fixing(fixingDate, "corrected-1", recordedCorrected, FixingStatus.CORRECTED,
                new BigDecimal("1.0810"), "official-1");

        FixingSeries series = FixingSeries.of(List.of(official, corrected));

        Optional<FixingSeries.Resolution> result = series.resolve(fixingDate, cutAfterCorrection,
                new FixingVersionSelection.FirstOfficial());
        assertTrue(result.isPresent());
        assertEquals(0, new BigDecimal("1.0800").compareTo(result.get().entry().value()),
                "X01: FirstOfficial must remain 1.0800 even after the correction exists");
    }

    @Test
    void vectorsX02X03_latestCorrectedCutDependent() {
        LocalDate fixingDate = LocalDate.of(2026, 4, 2);
        Instant recordedOfficial = Instant.parse("2026-04-02T14:20:00Z");
        Instant recordedCorrected = Instant.parse("2026-04-03T10:00:00Z");

        FixingVersion official = fixing(fixingDate, "official-1", recordedOfficial, FixingStatus.OFFICIAL,
                new BigDecimal("1.0800"), null);
        FixingVersion corrected = fixing(fixingDate, "corrected-1", recordedCorrected, FixingStatus.CORRECTED,
                new BigDecimal("1.0810"), "official-1");
        FixingSeries series = FixingSeries.of(List.of(official, corrected));

        // X02: cut 2-Apr 18:00 -- before the correction was recorded -> 1.0800.
        Instant cutX02 = Instant.parse("2026-04-02T18:00:00Z");
        Optional<FixingSeries.Resolution> x02 = series.resolve(fixingDate, cutX02, new FixingVersionSelection.LatestCorrected());
        assertTrue(x02.isPresent());
        assertEquals(0, new BigDecimal("1.0800").compareTo(x02.get().entry().value()));

        // X03: cut 3-Apr 18:00 -- after the correction -> 1.0810.
        Instant cutX03 = Instant.parse("2026-04-03T18:00:00Z");
        Optional<FixingSeries.Resolution> x03 = series.resolve(fixingDate, cutX03, new FixingVersionSelection.LatestCorrected());
        assertTrue(x03.isPresent());
        assertEquals(0, new BigDecimal("1.0810").compareTo(x03.get().entry().value()));
    }

    @Test
    void missingDateIsAMiss() {
        FixingSeries series = FixingSeries.empty();
        assertFalse(series.resolve(LocalDate.of(2026, 1, 1), Instant.now(), new FixingVersionSelection.LatestCorrected())
                .isPresent());
    }

    @Test
    void noVisibleVersionAtCutFallsBackToPreliminary() {
        LocalDate fixingDate = LocalDate.of(2026, 4, 2);
        Instant recordedPrelim = Instant.parse("2026-04-02T10:00:00Z");
        FixingVersion prelim = fixing(fixingDate, "prelim-1", recordedPrelim, FixingStatus.PRELIMINARY,
                new BigDecimal("1.0795"), null);
        FixingSeries series = FixingSeries.of(List.of(prelim));

        Optional<FixingSeries.Resolution> result = series.resolve(fixingDate, Instant.parse("2026-04-02T12:00:00Z"),
                new FixingVersionSelection.LatestCorrected());
        assertTrue(result.isPresent());
        assertTrue(result.get().viaPreliminaryFallback());
        assertEquals(0, new BigDecimal("1.0795").compareTo(result.get().entry().value()));
    }

    private static FixingVersion fixing(LocalDate fixingDate, String versionId, Instant recordedAt,
            FixingStatus status, BigDecimal value, String correctionOf) {
        return new FixingVersion(Scope.TENANT, "TENANT-TEST", "ECB", EURUSD, fixingDate, "16:00CET", value,
                status, recordedAt, versionId, correctionOf, fixingDate);
    }
}
