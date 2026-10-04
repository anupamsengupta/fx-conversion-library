package com.power.fx.core.lineage;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Purpose-built RFC 8785-flavoured canonical JSON for {@code inputsHash}
 * (S6.13): no Jackson, no Gson (D-01, JDK-only). Deviates from literal
 * RFC 8785 in exactly the one place the tech spec mandates: every decimal
 * is emitted as a JSON <em>string</em> (never a number), because RFC
 * 8785's own number canonicalisation goes through ECMAScript IEEE-754
 * double serialisation, which would silently destroy 34-digit precision.
 *
 * <p>Members are ordered by UTF-16 code-unit ordering of their names --
 * exactly what {@link String#compareTo(String)} (and therefore a plain
 * {@link TreeMap}) already implements. {@code null}-valued members are
 * omitted. Arrays keep source order. Record values are canonicalised by
 * reflecting over their {@link RecordComponent}s, so every field of
 * {@code ResolvedPolicy}/{@code ReplayableRequest} (including nested
 * records) is covered without per-field hand-mapping -- an engineering
 * trade-off for this task's time budget, and arguably more robust against
 * a forgotten field than a hand-written mapper would be.
 *
 * @see "Tech spec S6.13"
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String canonicalize(Object root) {
        StringBuilder sb = new StringBuilder();
        writeValue(root, sb);
        return sb.toString();
    }

    public static String canonicalizeMap(Map<String, ?> members) {
        StringBuilder sb = new StringBuilder();
        writeMap(members, sb);
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Optional<?> opt) {
            writeValue(opt.orElse(null), sb);
            return;
        }
        if (value instanceof BigDecimal bd) {
            writeString(normalizeDecimal(bd), sb);
            return;
        }
        if (value instanceof String s) {
            writeString(s, sb);
            return;
        }
        if (value instanceof Enum<?> e) {
            writeString(e.name(), sb);
            return;
        }
        if (value instanceof LocalDate d) {
            writeString(d.toString(), sb);
            return;
        }
        if (value instanceof Instant i) {
            writeString(normalizeInstant(i), sb);
            return;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value.toString());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writeMap(map, sb);
            return;
        }
        if (value instanceof Collection<?> coll) {
            writeArray(coll, sb);
            return;
        }
        if (value.getClass().isRecord()) {
            writeRecord(value, sb);
            return;
        }
        writeString(value.toString(), sb);
    }

    private static void writeRecord(Object record, StringBuilder sb) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        SortedMap<String, Object> members = new TreeMap<>();
        for (RecordComponent rc : components) {
            try {
                Object v = rc.getAccessor().invoke(record);
                if (v instanceof Optional<?> opt) {
                    if (opt.isEmpty()) {
                        continue;
                    }
                    v = opt.get();
                }
                if (v == null) {
                    continue; // null-valued members omitted (S6.13 rule 4)
                }
                members.put(rc.getName(), v);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("failed to canonicalise record component " + rc.getName(), e);
            }
        }
        writeMap(members, sb);
    }

    private static void writeMap(Map<?, ?> map, StringBuilder sb) {
        SortedMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            String key = e.getKey() instanceof Enum<?> en ? en.name() : String.valueOf(e.getKey());
            sorted.put(key, e.getValue());
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(e.getKey(), sb);
            sb.append(':');
            writeValue(e.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(Collection<?> coll, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object v : coll) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(v, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** {@code stripTrailingZeros().toPlainString()}, {@code "0"} for zero (S6.13 rule 2). */
    static String normalizeDecimal(BigDecimal bd) {
        if (bd.signum() == 0) {
            return "0";
        }
        return bd.stripTrailingZeros().toPlainString();
    }

    /** ISO-8601, UTC, nanosecond-normalised (S6.13 rule 5). */
    static String normalizeInstant(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
