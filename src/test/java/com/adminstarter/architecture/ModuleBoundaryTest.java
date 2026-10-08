package com.adminstarter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/** contracts/dependency-rules.yaml R1, R2, R2b, R5, R7, R8 */
@AnalyzeClasses(packages = "com.adminstarter", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    static final String ROOT = "com.adminstarter.accesscontrol";
    static final List<String> MODULES =
            List.of("identity", "authentication", "authorization", "audit", "notification", "settings", "liveupdate");
    /** What other modules must not touch: everything but service, dto, event and spi */
    static final List<String> PRIVATE_LAYERS = List.of("repository", "entity", "controller", "policy");

    @ArchTest
    static final ArchRule r1_foundation_does_not_depend_on_application = noClasses()
            .that().resideInAPackage(ROOT + "..")
            .should().dependOnClassesThat().resideInAPackage("com.adminstarter.app..");

    @ArchTest
    static void r2_modules_use_only_each_others_public_surface(JavaClasses classes) {
        for (var from : MODULES) {
            for (var to : MODULES) {
                if (!from.equals(to)) {
                    noClasses().that().resideInAPackage(ROOT + "." + from + "..")
                            .should().dependOnClassesThat().resideInAnyPackage(privateLayersOf(to))
                            .check(classes);
                }
            }
        }
    }

    @ArchTest
    static void r2b_application_uses_only_the_public_surface(JavaClasses classes) {
        for (var module : MODULES) {
            noClasses().that().resideInAPackage("com.adminstarter.app..")
                    .should().dependOnClassesThat().resideInAnyPackage(privateLayersOf(module))
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @ArchTest
    static final ArchRule r5_audit_only_listens = classes()
            .that().resideInAPackage(ROOT + ".audit..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    ROOT + ".audit..", ROOT + ".common..", ROOT + ".*.event..", ROOT,
                    "java..", "jakarta..", "org.springframework..", "tools.jackson..", "com.fasterxml.jackson.annotation..",
                    "org.jspecify..", "org.hibernate..", "org.slf4j..");

    @ArchTest
    static final ArchRule r7_common_depends_on_no_module = noClasses()
            .that().resideInAPackage(ROOT + ".common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    MODULES.stream().map(module -> ROOT + "." + module + "..").toArray(String[]::new));

    @ArchTest
    static final ArchRule r8_no_cycles_between_modules = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();

    private static String[] privateLayersOf(String module) {
        return PRIVATE_LAYERS.stream().map(layer -> ROOT + "." + module + "." + layer + "..").toArray(String[]::new);
    }
}
