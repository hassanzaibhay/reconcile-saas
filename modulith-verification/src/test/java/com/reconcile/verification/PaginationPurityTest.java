package com.reconcile.verification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * The shared pagination envelope + cursor codec must stay framework-free so sub-slices 2/3 can reuse it
 * in any layer (including {@code domain}) without dragging in Spring/JPA.
 */
class PaginationPurityTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.reconcile");

    @Test
    void paginationHasNoSpringImports() {
        noClasses()
                .that()
                .resideInAPackage("..shared.web.pagination..")
                .and()
                // package-info declares @NamedInterface (module export metadata, not pagination logic)
                .doNotHaveSimpleName("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("the pagination codec must be framework-free")
                .check(CLASSES);
    }

    @Test
    void paginationHasNoJpaImports() {
        noClasses()
                .that()
                .resideInAPackage("..shared.web.pagination..")
                .and()
                .doNotHaveSimpleName("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                .because("the pagination codec must be framework-free")
                .check(CLASSES);
    }
}
