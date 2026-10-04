package com.power.fx.cdm;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CdmDecimalCodec} is not blocked by TI-01 (pure parsing utility,
 * no dependency on CDM field names or schema shape) -- see its Javadoc.
 * This test class is fully green, not scaffolded/blocked.
 *
 * @see "Implementation plan Phase 3a Task 3a.1; tech spec S6.17"
 */
class CdmDecimalCodecTest {

    @Test
    void parsesExactDecimalFromString() {
        BigDecimal value = CdmDecimalCodec.parse("1.08500000");
        assertEquals(new BigDecimal("1.08500000"), value);
        // Exactness: trailing zeros are preserved (new BigDecimal(String) semantics, not valueOf(double)).
        assertEquals(8, value.scale());
    }

    @Test
    void parsesHighPrecisionDecimalWithoutDoubleRoundTrip() {
        // A value that is not exactly representable as an IEEE-754 double;
        // BigDecimal.valueOf(double) would silently distort it. new
        // BigDecimal(String) must not.
        String raw = "0.1000000000000000000000000000001";
        BigDecimal value = CdmDecimalCodec.parse(raw);
        assertEquals(new BigDecimal(raw), value);
    }

    @Test
    void parsesNegativeDecimal() {
        assertEquals(new BigDecimal("-0.049862981"), CdmDecimalCodec.parse("-0.049862981"));
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> CdmDecimalCodec.parse("   "));
    }

    @Test
    void rejectsMalformedLiteral() {
        assertThrows(IllegalArgumentException.class, () -> CdmDecimalCodec.parse("not-a-number"));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class, () -> CdmDecimalCodec.parse(null));
    }
}
