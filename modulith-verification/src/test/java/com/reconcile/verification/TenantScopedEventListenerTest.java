package com.reconcile.verification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.reconcile.shared.domain.TenantScopedEvent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;

class TenantScopedEventListenerTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.reconcile");

    private static final ArchCondition<JavaMethod> CONSUMES_TENANT_SCOPED_EVENT =
            new ArchCondition<>("have exactly one parameter that is a TenantScopedEvent subtype") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    var params = method.getRawParameterTypes();
                    boolean satisfied =
                            params.size() == 1 && params.get(0).isAssignableTo(TenantScopedEvent.class.getName());
                    if (!satisfied) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName()
                                        + " must have exactly one parameter that is a"
                                        + " TenantScopedEvent subtype — bypassing this"
                                        + " breaks tenant isolation via"
                                        + " TenantAwareEventListenerAdvice"));
                    }
                }
            };

    @Test
    void applicationModuleListenerMethodsConsumeTenantScopedEvent() {
        methods()
                .that()
                .areAnnotatedWith(ApplicationModuleListener.class)
                .should(CONSUMES_TENANT_SCOPED_EVENT)
                .check(CLASSES);
    }
}
