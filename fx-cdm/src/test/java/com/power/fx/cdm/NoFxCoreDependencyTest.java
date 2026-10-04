package com.power.fx.cdm;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural guard for Appendix A / MC-5: {@code fx-cdm}'s compiled
 * classes must never depend on {@code com.power.fx.core..}. Mappers are
 * pure; a dependency here would let transport concerns reach the engine.
 *
 * <p>This is the "ArchUnit rule confirms {@code fx-cdm} has zero
 * compile-time dependency on {@code fx-core}" named explicitly by Task
 * 3a.4's acceptance criterion. It imports {@code fx-cdm}'s compiled
 * {@code .class} files (not source) from {@code target/classes}, so it
 * also satisfies a compiled-classpath inspection, not just a source grep.
 *
 * @see "Implementation plan Phase 3a Task 3a.4; tech spec Appendix A, MC-5"
 */
class NoFxCoreDependencyTest {

    @Test
    void fxCdmMainClassesDoNotDependOnFxCore() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.power.fx.cdm");

        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAPackage("com.power.fx.core..");

        rule.check(classes);
    }
}
