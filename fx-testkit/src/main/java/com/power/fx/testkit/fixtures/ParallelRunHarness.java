package com.power.fx.testkit.fixtures;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Accepts a CSV of legacy-system inputs and outputs and reports per-row
 * differences against this library's own result, classified by stage
 * (date, pair, rate, rounding) so breaks are attributable rather than
 * merely counted (FS S20, S12.4). The library's contribution to a
 * parallel-run exercise is this harness plus {@link VectorRunner}; sourcing
 * production legacy data is a host exercise this class does not perform.
 *
 * @see "Tech spec S12.4"
 */
public final class ParallelRunHarness {

    public enum BreakStage {
        DATE, PAIR, RATE, ROUNDING, NONE
    }

    public record LegacyRow(String id, String fromCcy, String toCcy, BigDecimal legacyRate, BigDecimal legacyAmount) {
    }

    public record BreakReport(String id, BreakStage stage, BigDecimal legacyValue, BigDecimal libraryValue, BigDecimal difference) {
    }

    private ParallelRunHarness() {
    }

    /** Parses a simple {@code id,fromCcy,toCcy,legacyRate,legacyAmount} CSV (header row required). */
    public static List<LegacyRow> parse(Reader csv) {
        List<LegacyRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(csv)) {
            String header = reader.readLine(); // discard header
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",");
                rows.add(new LegacyRow(parts[0], parts[1], parts[2], new BigDecimal(parts[3]), new BigDecimal(parts[4])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    /**
     * Compares each legacy row's rate and amount against the values produced by
     * {@code rateLookup}/{@code amountLookup}, classifying any non-zero difference
     * as a {@code RATE} or {@code ROUNDING} break (date/pair classification requires
     * host-side context this harness does not have and is left to the caller to
     * refine by pre-filtering rows).
     */
    public static List<BreakReport> compare(List<LegacyRow> rows, Function<LegacyRow, BigDecimal> rateLookup,
            Function<LegacyRow, BigDecimal> amountLookup, BigDecimal toleranceFraction) {
        List<BreakReport> breaks = new ArrayList<>();
        for (LegacyRow row : rows) {
            BigDecimal libraryRate = rateLookup.apply(row);
            BigDecimal rateDiff = libraryRate.subtract(row.legacyRate()).abs();
            BigDecimal rateTolerance = row.legacyRate().abs().multiply(toleranceFraction);
            if (rateDiff.compareTo(rateTolerance) > 0) {
                breaks.add(new BreakReport(row.id(), BreakStage.RATE, row.legacyRate(), libraryRate, rateDiff));
                continue;
            }
            BigDecimal libraryAmount = amountLookup.apply(row);
            BigDecimal amountDiff = libraryAmount.subtract(row.legacyAmount()).abs();
            BigDecimal amountTolerance = row.legacyAmount().abs().multiply(toleranceFraction);
            if (amountDiff.compareTo(amountTolerance) > 0) {
                breaks.add(new BreakReport(row.id(), BreakStage.ROUNDING, row.legacyAmount(), libraryAmount, amountDiff));
            }
        }
        return breaks;
    }
}
