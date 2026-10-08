package com.adminstarter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

/** contracts/dependency-rules.yaml R10: transactions begin in services only */
@AnalyzeClasses(packages = "com.adminstarter", importOptions = ImportOption.DoNotIncludeTests.class)
class TransactionRuleTest {

    @ArchTest
    static final ArchRule r10_transactional_classes_are_services = noClasses()
            .that().resideOutsideOfPackage("..service..")
            .should().beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule r10_transactional_methods_are_in_services = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..service..")
            .should().beAnnotatedWith(Transactional.class);
}
