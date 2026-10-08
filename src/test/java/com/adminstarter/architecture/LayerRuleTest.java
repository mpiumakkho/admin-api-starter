package com.adminstarter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** contracts/dependency-rules.yaml R4: controller → service → repository → entity; dto never sees an entity */
@AnalyzeClasses(packages = "com.adminstarter", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRuleTest {

    @ArchTest
    static final ArchRule r4a_controllers_do_not_use_repositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule r4b_entities_do_not_depend_on_upper_layers = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..dto..");

    @ArchTest
    static final ArchRule r4c_repositories_do_not_use_services = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule r4d_dtos_do_not_use_entities = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule r6_security_does_not_touch_repositories = noClasses()
            .that().resideInAPackage("com.adminstarter.accesscontrol.security..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");
}
