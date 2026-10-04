package com.power.fx.api.result;

import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.request.PolicyRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Task 1.10 acceptance: {@link Lineage#inputsHash()} is lazily memoised --
 * the backing field is {@code null} immediately after construction, and
 * the canonical-form supplier is invoked exactly once even under
 * repeated calls.
 *
 * <p>This also exercises the design seam resolution for the {@code
 * Lineage}/{@code fx-core} layering tension (implementation plan Section
 * 9, new gap #1): the constructor takes a {@link Supplier}&lt;String&gt;
 * rather than a pre-built canonical string, and {@code Lineage} performs
 * only the final SHA-256 step itself.
 */
class LineageTest {

    @Test
    void inputsHashField_isNullImmediatelyAfterConstruction() throws Exception {
        Lineage lineage = newLineage(() -> "{\"a\":1}");

        Field field = Lineage.class.getDeclaredField("inputsHash");
        field.setAccessible(true);
        assertNull(field.get(lineage), "inputsHash field must be unset immediately after construction (A-13)");
    }

    @Test
    void inputsHash_isComputedExactlyOnceAcrossRepeatedCalls() {
        AtomicInteger supplierCalls = new AtomicInteger();
        Supplier<String> canonicalForm = () -> {
            supplierCalls.incrementAndGet();
            return "{\"a\":1}";
        };
        Lineage lineage = newLineage(canonicalForm);

        String first = lineage.inputsHash();
        String second = lineage.inputsHash();
        String third = lineage.inputsHash();

        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(1, supplierCalls.get(), "canonical-form supplier must be invoked exactly once");
    }

    @Test
    void inputsHash_isStableSha256HexOfCanonicalForm() {
        Lineage lineage = newLineage(() -> "{\"a\":1}");
        String hash = lineage.inputsHash();
        // SHA-256 hex digest is always 64 lowercase hex characters.
        assertEquals(64, hash.length());
        assertEquals(hash.toLowerCase(java.util.Locale.ROOT), hash);
    }

    private static Lineage newLineage(Supplier<String> canonicalForm) {
        ReplayableRequest replayKey = new ReplayableRequest(
                Purpose.CONTRACT_SETTLEMENT,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                new PolicyRef.ById("POLICY-1"),
                null,
                null,
                null,
                null,
                null);

        return new Lineage(
                "TN_0042",
                "SNAP-1",
                Instant.parse("2026-01-01T18:00:00Z"),
                1L,
                1L,
                SignOffStatus.SIGNED_OFF,
                "POLICY-1",
                1,
                null,
                "1.0.0-TEST",
                "1.0",
                null,
                null,
                replayKey,
                canonicalForm);
    }
}
