package com.reconcile.verification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class DiscrepancyDomainPurityTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.reconcile");

    @Test
    void discrepancySealedHierarchyHasNoSpringImports() {
        noClasses()
                .that()
                .haveSimpleNameContaining("Discrepancy")
                .and()
                .resideInAPackage("com.reconcile.reconciliation.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("sealed Discrepancy/Unmatched/Ambiguous are pure domain records")
                .check(CLASSES);
    }

    @Test
    void discrepancySealedHierarchyHasNoJpaImports() {
        noClasses()
                .that()
                .haveSimpleNameContaining("Discrepancy")
                .and()
                .resideInAPackage("com.reconcile.reconciliation.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                .because("sealed Discrepancy/Unmatched/Ambiguous are pure domain records")
                .check(CLASSES);
    }
}
