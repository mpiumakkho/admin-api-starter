package com.adminstarter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** contracts/dependency-rules.yaml R9: no dumping grounds */
@AnalyzeClasses(packages = "com.adminstarter", importOptions = ImportOption.DoNotIncludeTests.class)
class ForbiddenPackageTest {

    @ArchTest
    static final ArchRule r9_no_util_packages = noClasses()
            .should().resideInAnyPackage("..util..", "..utils..", "..helper..", "..helpers..", "..misc..", "..commons..", "..base..");

    @ArchTest
    static final ArchRule no_utils_or_helper_classes = noClasses()
            .should().haveSimpleNameEndingWith("Utils")
            .orShould().haveSimpleNameEndingWith("Helper");
}
