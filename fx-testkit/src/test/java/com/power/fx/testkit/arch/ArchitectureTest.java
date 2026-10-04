package com.power.fx.testkit.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped AR-01..AR-10 architecture gate (S12.5, Task 3b.1), promoted
 * from {@code fx-core}'s shift-left fixture
 * ({@code ArchitectureShiftLeftTest}/{@code NoTenantLiteralShiftLeftTest},
 * Phase 2 Section 2.1). Identical rule set and opcode list, now packaged
 * here as the formal closure of the shift-left gate -- this is a migration
 * and consolidation, not new rule-writing (plan Task 3b.1).
 *
 * <h2>AR-10 concrete pattern definition</h2>
 * S12.5 states only "no string literal matching a tenant-id pattern" with
 * no regex, naming convention or example anywhere in the tech spec (plan
 * Section 7, new gap #6). This class defines the pattern concretely as
 * {@link #TENANT_ID_PATTERN} -- case-insensitively, either
 * <ul>
 *   <li>the literal word {@code "tenant"} (or {@code "tn"}) immediately
 *       followed by a {@code -} or {@code _} separator and one or more
 *       alphanumerics (e.g. {@code "tenant-001"}, {@code "TN_0042"}), or</li>
 *   <li>a short alphabetic prefix, an {@code _} separator, and a purely
 *       numeric suffix of 3-8 digits (e.g. {@code "ACME_0042"}), the shape
 *       of a synthetic per-tenant code a reference-data generator would
 *       produce. Deliberately {@code _}-only for this generic, word-free
 *       form: a hyphen variant would collide with common algorithm/charset
 *       literals such as {@code "SHA-256"} or {@code "UTF-8"} that
 *       legitimately appear in {@code fx-core} (e.g. {@code Lineage}'s
 *       {@code MessageDigest.getInstance("SHA-256")} call) and are not
 *       tenant ids -- discovered empirically against the real codebase
 *       during the shift-left rehearsal.</li>
 * </ul>
 * This is deliberately narrower than "any string containing the substring
 * tenant" (which would flag ordinary prose/error-message string constants,
 * producing useless noise) and is scoped to string-literal tokens only,
 * not comments or Javadoc. {@code fx-testkit} itself is exempt (its {@code
 * InMemoryTenantContextProvider} and {@code GoldenReferenceData} legitimately
 * hardcode tenant ids for test setup); only {@code fx-api}/{@code fx-core}
 * main sources are scanned.
 *
 * @see "Tech spec S12.5; implementation plan Section 2.1, Task 3b.1, Section 7 gap #6"
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.power.fx.api", "com.power.fx.core");
    }

    /** AR-01: only java.., javax.., jakarta.inject.. and com.power.fx.. dependencies. */
    @Test
    void ar01_onlyAllowlistedPackages() {
        ArchRule forbidden = noClasses()
                .that().resideInAPackage("com.power.fx..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "com.google.inject..", "javax.inject..",
                        "com.fasterxml.jackson..", "com.google.gson..",
                        "org.slf4j..", "org.apache.logging..", "org.apache.commons..");
        forbidden.check(classes);
    }

    /**
     * AR-02: no I/O package dependency, except the A-12 MessageDigest
     * allowlist entry. Scoped to {@code com.power.fx.core}: {@code
     * fx-api}'s {@code FxVersion} legitimately depends on {@code
     * java.io.InputStream}/{@code IOException} for its one-time,
     * class-init-time resource read (A-06's sanctioned exception), the
     * same documented carve-out the shift-left fixture recorded.
     */
    @Test
    void ar02_noIoDependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.power.fx.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.io..", "java.nio.file..", "java.net..", "java.sql..", "javax.sql..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder");
        rule.check(classes);
    }

    /** AR-03: no Random / ThreadLocalRandom / UUID.randomUUID. */
    @Test
    void ar03_noRandomness() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.power.fx..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Random")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.ThreadLocalRandom");
        rule.check(classes);

        ArchRule noRandomUuid = noClasses()
                .that().resideInAPackage("com.power.fx..")
                .should().callMethod(java.util.UUID.class, "randomUUID");
        noRandomUuid.check(classes);
    }

    /** AR-04: no wall-clock calls, except the documented CutoffInstantResolver/MeteredFxConverter exemptions. */
    @Test
    void ar04_noWallClock() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.power.fx..")
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(java.time.LocalDate.class, "now")
                .orShould().callMethod(java.lang.System.class, "currentTimeMillis")
                .orShould().callMethod(java.lang.System.class, "nanoTime")
                .orShould().callMethod(java.time.ZonedDateTime.class, "now");
        rule.check(classes);
    }

    /** AR-06: no {@code BigDecimal(double)} constructor; {@code valueOf(double)} forbidden too. */
    @Test
    void ar06_noBigDecimalFromDouble() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.power.fx..")
                .should().callConstructorWhere(DescribedPredicate.describe(
                        "constructs BigDecimal from a double",
                        call -> call.getTarget().getOwner().isEquivalentTo(java.math.BigDecimal.class)
                                && call.getTarget().getRawParameterTypes().size() == 1
                                && call.getTarget().getRawParameterTypes().get(0).getFullName().equals("double")))
                .orShould().callMethod(java.math.BigDecimal.class, "valueOf", double.class);
        rule.check(classes);
    }

    /**
     * AR-07: the context-free overloads of multiply/divide/add/subtract are
     * forbidden in {@code fx-core}. (A pre-existing {@code fx-api}
     * exception -- {@code ReconciliationTolerance.of(...)}'s two
     * context-free {@code multiply} calls on exact small-integer operands
     * -- is out of this module's scope to fix; flagged for code-reviewer.)
     */
    @Test
    void ar07_bigDecimalArithmeticAlwaysExplicitMathContext() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.power.fx.core..")
                .should().callMethod(java.math.BigDecimal.class, "multiply", java.math.BigDecimal.class)
                .orShould().callMethod(java.math.BigDecimal.class, "divide", java.math.BigDecimal.class)
                .orShould().callMethod(java.math.BigDecimal.class, "add", java.math.BigDecimal.class)
                .orShould().callMethod(java.math.BigDecimal.class, "subtract", java.math.BigDecimal.class);
        rule.check(classes);
    }

    /** AR-08: no {@code BigDecimal.equals} on BigDecimal operands in {@code fx-core} (S10.6). */
    @Test
    void ar08_noBigDecimalEquals() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.power.fx.core..")
                .should().callMethod(java.math.BigDecimal.class, "equals", Object.class);
        rule.check(classes);
    }

    /** AR-09: no enum constant literally named FIXED anywhere in {@code fx-api} (D-12). */
    @Test
    void ar09_noFixedEnumConstant() {
        boolean[] found = {false};
        classes.forEach(javaClass -> {
            if (javaClass.isEnum()) {
                javaClass.getFields().forEach(f -> {
                    if (f.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC)
                            && f.getName().equals("FIXED")) {
                        found[0] = true;
                    }
                });
            }
        });
        if (found[0]) {
            throw new AssertionError("AR-09 violation: an enum constant literally named FIXED exists (D-12)");
        }
    }

    /** AR-10: no tenant-id-shaped string literal in {@code fx-api}/{@code fx-core} main sources. See class Javadoc. */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile(
            "(?i)^(tenant|tn)[-_][a-z0-9]+$|^[a-z]{2,8}_[0-9]{3,8}$");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    void ar10_noTenantShapedStringLiteralsInMainSources() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(
                Path.of("..", "fx-api", "src", "main", "java"),
                Path.of("..", "fx-core", "src", "main", "java"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> scan(p, violations));
            }
        }
        assertTrue(violations.isEmpty(), "AR-10 violations (tenant-id-shaped string literals):\n"
                + String.join("\n", violations));
    }

    private static void scan(Path file, List<String> violations) {
        try {
            String source = stripComments(Files.readString(file));
            Matcher m = STRING_LITERAL.matcher(source);
            while (m.find()) {
                String literal = m.group(1);
                if (TENANT_ID_PATTERN.matcher(literal).matches()) {
                    violations.add(file + ": \"" + literal + "\"");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stripComments(String source) {
        String noBlock = source.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlock.replaceAll("//[^\n]*", "");
    }
}
