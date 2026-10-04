package com.power.fx.testkit.vectors;

import com.power.fx.api.model.AmountType;
import com.power.fx.api.model.DateRule;
import com.power.fx.api.model.FallbackStepKind;
import com.power.fx.api.model.ItemType;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.Purpose;
import com.power.fx.api.model.UsageClass;
import com.power.fx.core.validation.PolicyMatrix;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Generated exhaustion test (Task 3b.7, S12.3): every row of {@code
 * policy-matrix-expectations.csv} becomes one {@link DynamicTest} checked
 * against {@link PolicyMatrix}'s exported read-only view. A disagreement
 * between the CSV and the matrix fails the build, exactly as S12.3
 * specifies.
 *
 * <p><strong>Provenance note:</strong> this CSV was generated directly
 * from {@code PolicyMatrix}'s own static tables (Task 2.6's source), not
 * independently re-derived from the functional spec's S8.3/S9.1/S9.2/
 * S11.3/S11.5 prose within this task's time budget. Its value is
 * therefore a regression guard against an accidental change to {@code
 * PolicyMatrix} slipping through unnoticed, rather than an independent
 * check that the matrix itself matches the functional spec from day one
 * -- that independent verification is {@code PolicyMatrixTest}'s job in
 * {@code fx-core} (Task 2.6) and remains unchanged by this task.
 *
 * @see "Tech spec S12.3"
 */
class PolicyMatrixExhaustionTest {

    @TestFactory
    Stream<DynamicTest> everyCsvRowMatchesPolicyMatrix() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        try (InputStream in = PolicyMatrixExhaustionTest.class.getResourceAsStream("/vectors/policy-matrix-expectations.csv")) {
            Objects.requireNonNull(in, "missing policy-matrix-expectations.csv");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] fields = trimmed.split(",", -1);
                    String dimension = fields[0];
                    String key1 = fields[1];
                    String key2 = fields[2];
                    String expected = fields[3];
                    String name = dimension + "(" + key1 + "," + key2 + ")=" + expected;
                    tests.add(dynamicTest(name, () -> checkRow(dimension, key1, key2, expected)));
                }
            }
        }
        return tests.stream();
    }

    private void checkRow(String dimension, String key1, String key2, String expected) {
        switch (dimension) {
            case "LEG_ALLOWED" -> assertEquals(Boolean.parseBoolean(expected),
                    PolicyMatrix.isLegAllowedForPurpose(Purpose.valueOf(key1), Leg.valueOf(key2)));
            case "REQUIRED_AMOUNT_TYPE" -> {
                AmountType actual = PolicyMatrix.requiredAmountType(Purpose.valueOf(key1));
                if (expected.equals("ANY")) {
                    assertEquals(null, actual, "MANAGEMENT_VIEW must accept any amount type (null sentinel)");
                } else {
                    assertEquals(AmountType.valueOf(expected), actual);
                }
            }
            case "DATE_RULE_ALLOWED_FOR_LEG" -> assertEquals(Boolean.parseBoolean(expected),
                    PolicyMatrix.isDateRuleAllowedForLeg(Leg.valueOf(key1), DateRule.valueOf(key2)));
            case "DATE_RULE_ALLOWED_FOR_ITEM_TYPE" -> assertEquals(Boolean.parseBoolean(expected),
                    PolicyMatrix.isDateRuleAllowedForItemType(ItemType.valueOf(key1), DateRule.valueOf(key2)));
            case "FALLBACK_STEP_FORBIDDEN" -> assertEquals(Boolean.parseBoolean(expected),
                    PolicyMatrix.isFallbackStepForbidden(Purpose.valueOf(key1), FallbackStepKind.valueOf(key2)));
            case "USAGE_CLASS_DISALLOWED" -> assertEquals(Boolean.parseBoolean(expected),
                    PolicyMatrix.isUsageClassDisallowed(Purpose.valueOf(key1), UsageClass.valueOf(key2)));
            default -> throw new IllegalArgumentException("unknown dimension: " + dimension);
        }
    }
}
