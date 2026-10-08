package com.adminstarter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/** contracts/naming.yaml: the layer a class is in shows in its name */
@AnalyzeClasses(packages = "com.adminstarter", importOptions = ImportOption.DoNotIncludeTests.class)
class NamingRuleTest {

    @ArchTest
    static final ArchRule controllers = topLevel("..controller..").should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule repositories = topLevel("..repository..").should().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule services = topLevel("..service..").should(endWithAnyOf(
            "Service", "ServiceImpl", "Resolver", "ResolverImpl", "Synchronizer", "Listener", "Mapper", "Support", "Job",
            "Initializer"));

    @ArchTest
    static final ArchRule dtos = topLevel("..dto..").should(endWithAnyOf("Request", "Response", "Summary", "Params", "Change"));

    @ArchTest
    static final ArchRule policies = topLevel("..policy..").should().haveSimpleNameEndingWith("Policy");

    @ArchTest
    static final ArchRule spis = topLevel("..spi..").should(endWithAnyOf("Catalog", "Template", "Definition", "Customizer", "Provider"));

    @ArchTest
    static final ArchRule events_are_past_tense_without_event_suffix = topLevel("..event..")
            .should().haveSimpleNameNotEndingWith("Event")
            .andShould().haveSimpleNameNotEndingWith("Evt");

    @ArchTest
    static final ArchRule entities_have_no_table_prefix = noClasses()
            .that().resideInAPackage("..entity..")
            .should().haveNameMatching(".*\\.(Ms|Tb|Tbl|T_|Ent)[A-Z_].*");

    /** Entities are JavaBeans read through getters, changed only through methods that say what happens (P-013) */
    @ArchTest
    static final ArchRule entities_have_no_public_setters = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..entity..")
            .and().arePublic()
            .should().haveNameMatching("set[A-Z].*");

    private static com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction topLevel(String packages) {
        return classes().that().resideInAPackage(packages).and().areTopLevelClasses()
                .and().doNotHaveSimpleName("package-info");
    }

    private static com.tngtech.archunit.lang.ArchCondition<JavaClass> endWithAnyOf(String... suffixes) {
        var allowed = List.of(suffixes);
        return com.tngtech.archunit.lang.conditions.ArchConditions.have(DescribedPredicate.describe(
                "a simple name ending with one of " + allowed,
                javaClass -> allowed.stream().anyMatch(suffix -> javaClass.getSimpleName().endsWith(suffix))));
    }
}
