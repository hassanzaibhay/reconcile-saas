package com.reconcile.verification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * The discrepancy read-side query port (application layer) must stay framework-free, matching every
 * other repository/port interface in this module, and its adapter classes must live in the expected
 * hexagonal layers.
 */
class DiscrepancyReadArchTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.reconcile");

    private static final String[] QUERY_PORT_TYPES = {
        "DiscrepancyQueryPort", "DiscrepancyListQuery", "DiscrepancyListRow", "EntryMoney"
    };

    @Test
    void discrepancyQueryPortHasNoSpringImports() {
        noClasses()
                .that()
                .haveNameMatching(namePattern())
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("the discrepancy read query port is a pure application-layer contract")
                .check(CLASSES);
    }

    @Test
    void discrepancyQueryPortHasNoJpaImports() {
        noClasses()
                .that()
                .haveNameMatching(namePattern())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                .because("the discrepancy read query port is a pure application-layer contract")
                .check(CLASSES);
    }

    @Test
    void discrepancyReadAdaptersResideInHexagonalLayers() {
        classes()
                .that()
                .haveSimpleName("JpaDiscrepancyQueryAdapter")
                .should()
                .resideInAPackage("..adapter.persistence..")
                .check(CLASSES);

        classes()
                .that()
                .haveSimpleName("DiscrepancyReadController")
                .should()
                .resideInAPackage("..adapter.web..")
                .check(CLASSES);
    }

    private static String namePattern() {
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < QUERY_PORT_TYPES.length; i++) {
            if (i > 0) {
                pattern.append('|');
            }
            pattern.append(".*\\.").append(QUERY_PORT_TYPES[i]);
        }
        return pattern.toString();
    }
}
