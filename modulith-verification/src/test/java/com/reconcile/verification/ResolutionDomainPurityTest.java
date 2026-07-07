package com.reconcile.verification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ResolutionDomainPurityTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.reconcile");

    @Test
    void resolutionTypesHaveNoSpringImports() {
        noClasses()
                .that()
                .haveSimpleNameEndingWith("Pairing")
                .or()
                .haveSimpleNameEndingWith("ClusterResolution")
                .or()
                .haveSimpleNameEndingWith("InvalidResolutionException")
                .and()
                .resideInAPackage("com.reconcile.reconciliation.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("Pairing/ClusterResolution/InvalidResolutionException are pure domain types")
                .check(CLASSES);
    }

    @Test
    void resolutionTypesHaveNoJpaImports() {
        noClasses()
                .that()
                .haveSimpleNameEndingWith("Pairing")
                .or()
                .haveSimpleNameEndingWith("ClusterResolution")
                .or()
                .haveSimpleNameEndingWith("InvalidResolutionException")
                .and()
                .resideInAPackage("com.reconcile.reconciliation.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                .because("Pairing/ClusterResolution/InvalidResolutionException are pure domain types")
                .check(CLASSES);
    }
}
