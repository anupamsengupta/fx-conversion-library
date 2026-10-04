package com.power.fx.testkit.fixtures;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads one of the {@code id,description,expected[,...]} vector CSV
 * resources (Task 3b.4) into a lookup keyed by vector id, so test classes
 * assert against data rather than inline literals (S12.3's "the
 * expectation table is data, not code" principle, generalised here beyond
 * the policy matrix to the G/C/F/X vector CSVs too).
 */
public final class VectorExpectations {

    private final Map<String, String[]> rows = new LinkedHashMap<>();

    private VectorExpectations() {
    }

    public static VectorExpectations load(String classpathResource) {
        VectorExpectations table = new VectorExpectations();
        try (InputStream in = VectorExpectations.class.getResourceAsStream(classpathResource)) {
            Objects.requireNonNull(in, "missing classpath resource: " + classpathResource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("id,")) {
                        continue;
                    }
                    String[] fields = splitCsvLine(trimmed);
                    table.rows.put(fields[0], fields);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return table;
    }

    /** Field {@code index} (0-based) of the row for {@code id} (0 = id itself, 1 = description, 2.. = data columns). */
    public String field(String id, int index) {
        String[] row = rows.get(id);
        if (row == null) {
            throw new IllegalArgumentException("no such vector id: " + id);
        }
        if (index >= row.length) {
            throw new IllegalArgumentException("vector " + id + " has no field at index " + index);
        }
        return row[index];
    }

    /** Convenience for the common two-column (id,description,expected) schema. */
    public String expected(String id) {
        return field(id, 2);
    }

    public boolean has(String id) {
        return rows.containsKey(id);
    }

    /** Minimal CSV splitter honouring double-quoted fields (the only quoting these resources use). */
    private static String[] splitCsvLine(String line) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
