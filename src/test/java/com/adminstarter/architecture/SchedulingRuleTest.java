package com.adminstarter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.scheduling.annotation.Scheduled;

/** contracts/dependency-rules.yaml J1: a cron job runs at its hour in the organization's time zone (P-030) */
@AnalyzeClasses(packages = "com.adminstarter", importOptions = ImportOption.DoNotIncludeTests.class)
class SchedulingRuleTest {

    @ArchTest
    static final ArchRule j1_cron_jobs_run_in_the_organization_time_zone = methods()
            .that().areAnnotatedWith(Scheduled.class)
            .should(runInTheOrganizationTimeZone());

    private static ArchCondition<JavaMethod> runInTheOrganizationTimeZone() {
        return new ArchCondition<>("run a cron in AccessControlProperties.TIME_ZONE") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                var scheduled = method.getAnnotationOfType(Scheduled.class);
                if (!scheduled.cron().isEmpty() && !scheduled.zone().equals(AccessControlProperties.TIME_ZONE)) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " runs its cron in the server's time zone; add zone = AccessControlProperties.TIME_ZONE"));
                }
            }
        };
    }
}
