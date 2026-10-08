package com.adminstarter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.authorization.spi.PermissionDefinition;
import com.adminstarter.app.config.AppPermissions;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * contracts/dependency-rules.yaml S1, S2: every endpoint states what it needs, checks permissions and never roles,
 * and names only permissions a catalog declares. AuthController is the exception: it is open to everyone.
 */
@AnalyzeClasses(packages = "com.adminstarter", importOptions = ImportOption.DoNotIncludeTests.class)
class SecurityRuleTest {

    private static final Pattern AUTHORITY = Pattern.compile("hasAuthority\\('([^']*)'\\)");

    @ArchTest
    static final ArchRule s1_every_endpoint_has_preauthorize = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .and().areDeclaredInClassesThat().doNotHaveSimpleName("AuthController")
            .and().areMetaAnnotatedWith(RequestMapping.class)
            .should().beAnnotatedWith(PreAuthorize.class);

    @ArchTest
    static void s2_no_role_checks_and_only_known_permissions(JavaClasses classes) {
        var known = Stream.concat(new CorePermissions().permissions().stream(), new AppPermissions().permissions().stream())
                .map(PermissionDefinition::code)
                .toList();
        var expressions = classes.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(PreAuthorize.class))
                .map(SecurityRuleTest::expression)
                .toList();
        assertThat(expressions).isNotEmpty().noneMatch(expression -> expression.contains("hasRole("));
        expressions.forEach(expression -> AUTHORITY.matcher(expression).results()
                .forEach(match -> assertThat(known).as(expression).contains(match.group(1))));
    }

    private static String expression(JavaMethod method) {
        return method.getAnnotationOfType(PreAuthorize.class).value();
    }
}
