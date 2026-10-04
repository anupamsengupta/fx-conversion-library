package com.power.fx.cdm;

import com.power.fx.api.model.CurrencyCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Shared, package-private flat-string-map field extraction for the
 * per-entity mappers ({@link CdmReferenceMapper}, {@link CdmFixingMapper},
 * {@link CdmSnapshotMapper}). Every method throws an unchecked exception
 * ({@link IllegalArgumentException} or a JDK parsing exception) on a
 * missing or malformed field; none of these propagate past
 * {@link CdmFxEventMapper#map(CdmFxEvent)}, which converts them into an
 * {@code IngestRejection} (Task 3a.4's "malformed input yields a
 * rejection record, never an exception" acceptance criterion).
 *
 * <p>Field <em>names</em> used by the per-entity mappers are illustrative
 * only, pending TI-01 -- see {@link CdmFxEvent}'s Javadoc.
 */
final class CdmFields {

    private CdmFields() {
    }

    static String require(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required CDM field: " + key);
        }
        return value;
    }

    static String optional(Map<String, String> fields, String key) {
        String value = fields.get(key);
        return (value == null || value.isBlank()) ? null : value;
    }

    static int requireInt(Map<String, String> fields, String key) {
        String raw = require(fields, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed integer CDM field " + key + ": " + raw, e);
        }
    }

    static boolean optionalBoolean(Map<String, String> fields, String key, boolean defaultValue) {
        String raw = optional(fields, key);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw);
    }

    static java.math.BigDecimal requireDecimal(Map<String, String> fields, String key) {
        return CdmDecimalCodec.parse(require(fields, key));
    }

    static java.math.BigDecimal optionalDecimal(Map<String, String> fields, String key) {
        String raw = optional(fields, key);
        return raw == null ? null : CdmDecimalCodec.parse(raw);
    }

    static LocalDate requireDate(Map<String, String> fields, String key) {
        String raw = require(fields, key);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("malformed date CDM field " + key + ": " + raw, e);
        }
    }

    static LocalDate optionalDate(Map<String, String> fields, String key) {
        String raw = optional(fields, key);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("malformed date CDM field " + key + ": " + raw, e);
        }
    }

    static Instant requireInstant(Map<String, String> fields, String key) {
        String raw = require(fields, key);
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("malformed instant CDM field " + key + ": " + raw, e);
        }
    }

    static LocalTime requireTime(Map<String, String> fields, String key) {
        String raw = require(fields, key);
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("malformed time CDM field " + key + ": " + raw, e);
        }
    }

    static ZoneId requireZone(Map<String, String> fields, String key) {
        String raw = require(fields, key);
        try {
            return ZoneId.of(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed zone CDM field " + key + ": " + raw, e);
        }
    }

    static <E extends Enum<E>> E requireEnum(Map<String, String> fields, String key, Class<E> type) {
        return parseEnum(require(fields, key), type, key);
    }

    /** Parses a raw (already-extracted) string value as an enum constant, e.g. the top-level {@code scope} field. */
    static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, String fieldLabel) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "unrecognised " + type.getSimpleName() + " value for CDM field " + fieldLabel + ": " + raw, e);
        }
    }

    static CurrencyCode requireCurrency(Map<String, String> fields, String key) {
        try {
            return new CurrencyCode(require(fields, key));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed currency CDM field " + key, e);
        }
    }

    static CurrencyCode optionalCurrency(Map<String, String> fields, String key) {
        String raw = optional(fields, key);
        if (raw == null) {
            return null;
        }
        try {
            return new CurrencyCode(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed currency CDM field " + key, e);
        }
    }

    /** Splits a comma-separated flat-string-map value into a trimmed, non-blank list. Never {@code null}. */
    static List<String> splitList(Map<String, String> fields, String key) {
        String raw = optional(fields, key);
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
