package com.reconcile.verification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class DomainPurityTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.reconcile");

    @Test
    void domainPackagesHaveNoSpringImports() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .and()
                // package-info declares @NamedInterface (module export metadata, not domain logic)
                .doNotHaveSimpleName("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("domain packages must be framework-free")
                .check(CLASSES);
    }

    @Test
    void domainPackagesHaveNoJpaImports() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .and()
                .doNotHaveSimpleName("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                .because("domain packages must be framework-free")
                .check(CLASSES);
    }
}
