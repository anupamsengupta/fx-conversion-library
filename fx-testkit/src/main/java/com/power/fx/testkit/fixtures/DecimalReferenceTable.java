package com.power.fx.testkit.fixtures;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads the {@code ln}/{@code exp} decimal reference tables (Appendix D.5)
 * from CSV classpath resources. <strong>Provisional, non-authoritative
 * (TI-06):</strong> the shipped CSVs carry forward Phase 2 Task 2.1's
 * ~50-point set (computed offline via Python's {@code decimal.Decimal}
 * module at 80 digits) rather than the full, independently
 * MPFR/mpmath-cross-checked 2,000-point table Appendix D.5 describes --
 * that full table is blocked on TI-06 and is not fabricated here. Every
 * CSV header line states this explicitly.
 *
 * @see "Tech spec Appendix D.5; implementation plan Task 3b.4, TI-06"
 */
public final class DecimalReferenceTable {

    public record Entry(BigDecimal argument, BigDecimal expected) {
    }

    private final List<Entry> entries;
    private final boolean provisional;

    private DecimalReferenceTable(List<Entry> entries, boolean provisional) {
        this.entries = List.copyOf(entries);
        this.provisional = provisional;
    }

    public static DecimalReferenceTable lnReference() {
        return load("/decimal/ln-reference-70dp.csv");
    }

    public static DecimalReferenceTable expReference() {
        return load("/decimal/exp-reference-70dp.csv");
    }

    public List<Entry> entries() {
        return entries;
    }

    /** {@code true} for every table this module ships (TI-06 is not yet resolved). */
    public boolean isProvisional() {
        return provisional;
    }

    private static DecimalReferenceTable load(String resourcePath) {
        List<Entry> result = new ArrayList<>();
        boolean provisional = false;
        try (InputStream in = DecimalReferenceTable.class.getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(in, "missing classpath resource: " + resourcePath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        if (trimmed.toUpperCase(java.util.Locale.ROOT).contains("PROVISIONAL")) {
                            provisional = true;
                        }
                        continue;
                    }
                    if (trimmed.equalsIgnoreCase("argument,expected")) {
                        continue;
                    }
                    String[] parts = trimmed.split(",", 2);
                    result.add(new Entry(new BigDecimal(parts[0].strip()), new BigDecimal(parts[1].strip())));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DecimalReferenceTable(result, provisional);
    }
}
